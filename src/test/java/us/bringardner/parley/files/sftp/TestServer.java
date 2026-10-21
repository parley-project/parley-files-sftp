package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Properties;
import java.util.List;
import java.util.Collections;
import java.util.stream.Stream;

import com.sshtools.common.files.direct.NioFileFactory.NioFileFactoryBuilder;
import com.sshtools.common.publickey.SshKeyPairGenerator;
import com.sshtools.server.InMemoryPasswordAuthenticator;
import us.bringardner.parley.files.sftp.spi.SshProviders;
import us.bringardner.parley.files.test.FileSourceTestSupport;
import com.sshtools.server.SshServer;

/**
 * The SSH server the tests connect to, chosen once per test run by the
 * system property parley.sftp.test.server:
 * <ul>
 * <li>"auto" (the default): the OpenSSH server on localhost:22 if unittest1 /
 * 0000 can log in there, otherwise the embedded server.</li>
 * <li>"local": the server on localhost:22, with the accounts unittest1 to
 * unittest4 (see the README's Testing section). Tests fail if it isn't there.</li>
 * <li>"embedded": an embedded Maverick server, started on a free port, with
 * one account (unittest1 / 0000) whose home directory is
 * embedded-sftp/home in the test run's temporary folder. It needs nothing installed, so CI can run the
 * tests.</li>
 * </ul>
 * Tests that need real OpenSSH (links, Unix permissions and groups, other
 * accounts, shell commands, OpenSSH's channel limit) call
 * {@link #assumeOpenSsh()}, which skips them, with a message, on the embedded
 * server.
 */
public final class TestServer {

	static final String SYSTEM_PROPERTY = "parley.sftp.test.server";
	static final String HOST = "localhost";
	static final String USER = "unittest1";
	static final String PASSWORD = "0000";
	static final int LOCAL_PORT = 22;

	private static SshServer embedded;
	private static boolean embeddedStarted;
	private static int embeddedPort;
	/** The resolved choice; null until first asked. */
	private static Boolean local;

	private TestServer() {
	}

	/** True when the tests use the OpenSSH server on localhost:22. */
	static synchronized boolean isLocal() {
		if( local == null ) {
			String mode = System.getProperty(SYSTEM_PROPERTY, "auto").trim().toLowerCase();
			switch (mode) {
			case "local":
				local = true;
				break;
			case "embedded":
				local = false;
				break;
			case "auto":
				local = canLogInLocally();
				break;
			default:
				throw new IllegalArgumentException("-D"+SYSTEM_PROPERTY+" must be auto, local or embedded, not "+mode);
			}
			System.out.println("SFTP tests use "+(local
					? "the OpenSSH server on "+HOST+":"+LOCAL_PORT
					: "the embedded SSH server; tests that need OpenSSH are skipped"));
		}
		return local;
	}

	/** True if unittest1 / 0000 can log in on localhost:22. */
	private static boolean canLogInLocally() {
		try (Socket s = new Socket()) {
			s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), LOCAL_PORT), 2000);
		} catch (IOException e) {
			return false;   // nothing listening: don't wait for an SSH timeout
		}
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		Properties p = properties(null, LOCAL_PORT);
		p.setProperty(SftpFileSourceFactory.PROP_CONNECT_TIMEOUT, "5000");
		f.setConnectionProperties(p);
		try {
			return f.connect();
		} catch (IOException | RuntimeException e) {
			return false;
		} finally {
			try {
				f.disConnect();
			} catch (IOException e) {
				// only a probe
			}
		}
	}

	/** The port of the server the tests use, starting the embedded one if needed. */
	static synchronized int port() throws IOException {
		if( isLocal()) {
			return LOCAL_PORT;
		}
		if( !embeddedStarted ) {
			startEmbedded();
		}
		return embeddedPort;
	}

	// ------------------------------------------------------------ the provider under test

	/**
	 * System property a provider project sets (in surefire's systemPropertyVariables) to name
	 * what its library can't do, comma separated: {@value #WRITE_AT_POSITION}, {@value #AGENT_LOGIN}.
	 * The suite skips those tests; the provider's own tests check that it refuses them.
	 */
	public static final String UNSUPPORTED_PROPERTY = "parley.sftp.test.unsupported";
	/** Writing at a position in a file (random access writing). */
	public static final String WRITE_AT_POSITION = "writeAtPosition";
	/** Logging in with an ssh-agent (auth=Agent). */
	public static final String AGENT_LOGIN = "agentLogin";

	/**
	 * The provider the suite tests: the one on the class path (see SshProviders.defaultName()).
	 * Skips the calling test, or the whole class from @BeforeAll, if there is none (as in
	 * parley-files-sftp itself: the suite runs in the provider projects).
	 */
	public static String provider() {
		assumeTrue(!SshProviders.available().isEmpty(), "No SSH provider on the class path: the suite runs in the provider projects");
		return SshProviders.defaultName();
	}

	/** For @MethodSource: the provider under test. */
	public static List<String> providers() {
		return Collections.singletonList(provider());
	}

	/** @return false if the provider project says its library can't do 'feature' (see UNSUPPORTED_PROPERTY) */
	public static boolean supports(String feature) {
		String list = System.getProperty(UNSUPPORTED_PROPERTY, "");
		for (String s : list.split(",")) {
			if( s.trim().equalsIgnoreCase(feature)) {
				return false;
			}
		}
		return true;
	}

	/** Skips the calling test if the provider can't do 'feature'. */
	public static void assumeSupported(String feature) {
		assumeTrue(supports(feature), "the "+provider()+" provider can't do this ("+feature+")");
	}

	/**
	 * A factory with a connection of its own (a session key no other uses), as another program
	 * would have: changes made through it are made "elsewhere".
	 */
	static SftpFileSourceFactory connectSeparately(String implementation) throws IOException {
		Properties p = properties(implementation, port());
		p.setProperty(SftpFileSourceFactory.PROP_SESSION_KEY, "separate-"+SEPARATE.incrementAndGet()+"-"+System.nanoTime());
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		f.setConnectionProperties(p);
		assertTrue(f.connect(), "Factory did not connect to "+HOST+":"+port());
		return f;
	}

	private static final java.util.concurrent.atomic.AtomicInteger SEPARATE = new java.util.concurrent.atomic.AtomicInteger();

	/** The test files, from the class path (src/test/resources/TestFiles), so the suite runs from a jar. */
	private static final String[] TEST_FILES = {"AnalyzerDerby.properties", "AnalyzerMySql.properties", "Hotel California.txt"};
	private static String testFilesDir;

	/** A local directory with the test files (copied once per run from the class path). */
	static synchronized String testFilesDir() throws IOException {
		if( testFilesDir == null ) {
			Path dir = Paths.get(FileSourceTestSupport.scratchDir("TestFiles"));
			Files.createDirectories(dir);
			for (String name : TEST_FILES) {
				try (java.io.InputStream in = TestServer.class.getResourceAsStream("/TestFiles/"+name)) {
					if( in == null ) {
						throw new IOException("TestFiles/"+name+" isn't on the test class path");
					}
					Files.copy(in, dir.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				}
			}
			testFilesDir = dir.toString();
		}
		return testFilesDir;
	}

	/** A factory connected as unittest1 to the server the tests use. */
	static SftpFileSourceFactory connect(String implementation) throws IOException {
		return connect(implementation, port());
	}

	static SftpFileSourceFactory connect(String implementation, int port) throws IOException {
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		f.setConnectionProperties(properties(implementation, port));
		assertTrue(f.connect(), "Factory did not connect to "+HOST+":"+port);
		return f;
	}

	/** Connection properties for unittest1 on the given port; a null implementation means the default. */
	static Properties properties(String implementation, int port) {
		Properties p = new SftpFileSourceFactory().getConnectProperties();
		p.setProperty(SftpFileSourceFactory.PROP_USER, USER);
		p.setProperty(SftpFileSourceFactory.PROP_HOST, HOST);
		p.setProperty(SftpFileSourceFactory.PROP_PORT, ""+port);
		p.setProperty(SftpFileSourceFactory.PROP_PASSWORD, PASSWORD);
		// the test servers' keys aren't in known_hosts; host key checking has its own tests
		p.setProperty(SftpFileSourceFactory.PROP_STRICT_HOST_KEY_CHECKING, "no");
		if( implementation != null ) {
			p.setProperty(SftpFileSourceFactory.PROP_IMPLEMENTATION, implementation);
		}
		return p;
	}

	/**
	 * Skips the calling test (or the whole class, from @BeforeAll) unless the
	 * tests use the OpenSSH server on localhost:22.
	 */
	static void assumeOpenSsh() {
		assumeTrue(isLocal(), "Needs the OpenSSH server on "+HOST+":"+LOCAL_PORT+" with the unittest accounts");
	}

	/**
	 * True if the tests use the embedded Maverick server (not on Windows, which gets MINA's). It logs
	 * in as USER while the files belong to whoever runs the tests, a symbolic link it makes keeps the
	 * host's absolute path (outside its sandbox), and it has no hard links.
	 */
	static boolean isEmbeddedMaverick() {
		return !isLocal() && !System.getProperty("os.name", "").toLowerCase().startsWith("windows");
	}

	private static void startEmbedded() throws IOException {
		Path root = Paths.get(FileSourceTestSupport.scratchDir("embedded-sftp"));
		deleteAll(root);
		File home = root.resolve("home").toFile();
		if( !home.mkdirs()) {
			throw new IOException("Can't create "+home);
		}
		if( System.getProperty("os.name", "").toLowerCase().startsWith("windows") ) {
			startEmbeddedMina(home);
			return;
		}
		try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			embeddedPort = s.getLocalPort();
		}
		try {
			SshServer server = new SshServer(InetAddress.getLoopbackAddress(), embeddedPort);
			server.addHostKey(SshKeyPairGenerator.generateKeyPair(SshKeyPairGenerator.ED25519));
			server.addAuthenticator(new InMemoryPasswordAuthenticator().addUser(USER, PASSWORD.toCharArray()));
			server.setFileFactory(con -> NioFileFactoryBuilder.create()
					.withHome(home)
					.withSandbox(true)   // "/" is the home directory; nothing outside it is reachable
					.build());
			server.start();
			embedded = server;
		} catch (Exception e) {
			throw new IOException("Can't start the embedded SSH server: "+e, e);
		}
		Runtime.getRuntime().addShutdownHook(new Thread(embedded::close, "embedded SSH server shutdown"));
		embeddedStarted = true;
	}

	/** The embedded server on Windows: MINA's, in a class of its own so other systems run without MINA. */
	private static void startEmbeddedMina(File home) throws IOException {
		embeddedPort = EmbeddedMinaServer.start(home, USER, PASSWORD);
		embeddedStarted = true;
	}

	private static void deleteAll(Path dir) throws IOException {
		if( Files.exists(dir)) {
			try (Stream<Path> paths = Files.walk(dir)) {
				for (Path p : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
					Files.delete(p);
				}
			}
		}
	}
}
