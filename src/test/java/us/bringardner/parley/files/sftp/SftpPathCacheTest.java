package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.sshtools.common.files.AbstractFileFactory;
import com.sshtools.common.files.direct.NioFileFactory.NioFileFactoryBuilder;
import com.sshtools.common.permissions.PermissionDeniedException;
import com.sshtools.common.policy.FileFactory;
import com.sshtools.common.publickey.SshKeyPairGenerator;
import com.sshtools.common.ssh.SshConnection;
import com.sshtools.server.InMemoryPasswordAuthenticator;
import com.sshtools.server.SshServer;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.test.FileSourceTestSupport;

/**
 * The answers kept for canonical paths (SftpFileSourceFactory.pathLstat):
 * kept for the attribute cache time, dropped by a change made through the
 * factory and by refresh(). Serves the local file system, as
 * SftpCanonicalPathTest does, so links can be changed behind the server's back.
 */
@org.junit.jupiter.api.condition.DisabledOnOs(value = org.junit.jupiter.api.condition.OS.WINDOWS, disabledReason = "serves the local file system by absolute path; the embedded server has no usable file factory on Windows")
public class SftpPathCacheTest {

	static final int PORT = 2226;
	static final String USER = "cache";
	static final String PASSWORD = "paths";

	static SshServer server;
	static SftpFileSourceFactory factory;
	static Path base;
	static boolean links;

	@BeforeAll
	public static void setUp() throws Exception {
		TestServer.provider();   // skips the class if no SSH provider is on the class path
		base = Paths.get(FileSourceTestSupport.scratchDir("SftpPathCacheTest"));
		deleteAll(base);
		Files.createDirectories(base.resolve("a"));
		Files.createDirectories(base.resolve("b"));
		Files.createDirectories(base.resolve("c"));
		try {
			Files.createSymbolicLink(base.resolve("link"), base.resolve("a"));
			Files.createSymbolicLink(base.resolve("mine"), base.resolve("a"));
			links = true;
		} catch (IOException | UnsupportedOperationException e) {
			links = false;
		}

		server = new SshServer(java.net.InetAddress.getLoopbackAddress(), PORT);
		server.addHostKey(SshKeyPairGenerator.generateKeyPair(SshKeyPairGenerator.ECDSA, 256));
		server.addAuthenticator(new InMemoryPasswordAuthenticator().addUser(USER, PASSWORD.toCharArray()));
		server.setFileFactory(new FileFactory() {
			@Override
			public AbstractFileFactory<?> getFileFactory(SshConnection con) throws IOException, PermissionDeniedException {
				return NioFileFactoryBuilder.create().withCurrentDirectoryAsHome().withoutSandbox().build();
			}
		});
		server.start();
		long start = System.currentTimeMillis();
		while( !server.isRunning() && System.currentTimeMillis()-start < 5000) {
			Thread.sleep(100);
		}
		assertTrue(server.isRunning(), "SSH server did not start");

		factory = new SftpFileSourceFactory();
		Properties p = factory.getConnectProperties();
		p.setProperty(SftpFileSourceFactory.PROP_USER, USER);
		p.setProperty(SftpFileSourceFactory.PROP_HOST, "localhost");
		p.setProperty(SftpFileSourceFactory.PROP_PORT, ""+PORT);
		p.setProperty(SftpFileSourceFactory.PROP_PASSWORD, PASSWORD);
		p.setProperty(SftpFileSourceFactory.PROP_ATTRIBUTE_CACHE_TTL, "1m");   // long: nothing expires during a test
		p.setProperty(SftpFileSourceFactory.PROP_STRICT_HOST_KEY_CHECKING, "no");
		factory.setConnectionProperties(p);
		assertTrue(factory.connect(), "factory did not connect");
	}

	@AfterAll
	public static void tearDown() throws Exception {
		if( factory != null ) {
			factory.disConnect();
		}
		if( server != null ) {
			server.stop();
		}
		if( base != null ) {
			deleteAll(base);
		}
	}

	private static FileSource remote(String relative) throws IOException {
		return factory.createFileSource(base.toString().replace('\\', '/')+"/"+relative);
	}

	private static String real(String relative) throws IOException {
		return base.resolve(relative).toRealPath().toString().replace('\\', '/');
	}

	private static void relink(String link, String target) throws IOException {
		Files.delete(base.resolve(link));
		Files.createSymbolicLink(base.resolve(link), base.resolve(target));
	}

	@Test
	public void keptUntilRefresh() throws IOException {
		assumeTrue(links, "symbolic links not supported here");
		FileSource link = remote("link");
		assertEquals(real("a"), link.getCanonicalPath());
		relink("link", "b");   // behind the server's back, as another program would
		assertEquals(real("a"), remote("link").getCanonicalPath(), "kept for the attribute cache time");
		link.refresh();
		assertEquals(real("b"), remote("link").getCanonicalPath(), "refresh() asks again");
	}

	@Test
	public void droppedByAChangeThroughTheFactory() throws IOException {
		assumeTrue(links, "symbolic links not supported here");
		assertEquals(real("a"), remote("mine").getCanonicalPath());
		relink("mine", "c");
		assertTrue(remote("a/new-dir").mkdir());   // any change made through the factory
		assertEquals(real("c"), remote("mine").getCanonicalPath());
	}

	@Test
	public void ttlZeroKeepsNothing() throws IOException {
		assumeTrue(links, "symbolic links not supported here");
		long old = factory.getAttributeCacheTtl();
		factory.setAttributeCacheTtl(0);
		try {
			Files.createSymbolicLink(base.resolve("now"), base.resolve("a"));
			assertEquals(real("a"), remote("now").getCanonicalPath());
			relink("now", "b");
			assertEquals(real("b"), remote("now").getCanonicalPath());
		} finally {
			factory.setAttributeCacheTtl(old);
		}
	}

	@Test
	public void aListingOfAFileIsNull() throws IOException {
		Files.write(base.resolve("a/plain.txt"), new byte[3]);
		FileSource f = remote("a/plain.txt");
		assertNull(f.listFiles());
		assertNull(remote("a/missing").listFiles());
	}

	private static void deleteAll(Path dir) throws IOException {
		if( !Files.exists(dir, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		try(Stream<Path> s = Files.walk(dir)) {
			s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}
}
