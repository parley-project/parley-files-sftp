package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.shell.ProcessShellCommandFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceProcessBuilder;

/**
 * Programs started on the server through SftpFileSourceFactory.processBuilder, against two MINA
 * servers of the test's own: one that runs commands (with the system's sh, so not on Windows), and
 * one that only allows SFTP.
 */
public class SftpProcessBuilderTest {

	private static SshServer commands;
	private static SshServer sftpOnly;
	private static Path dir;

	@BeforeAll
	static void startServers() throws Exception {
		assumeFalse(System.getProperty("os.name", "").toLowerCase().startsWith("windows"),
				"The test server runs commands with sh");
		TestServer.provider();   // skips where there is no SSH provider
		commands = server(true);
		sftpOnly = server(false);
		dir = Files.createTempDirectory("sftp-process").toRealPath();
	}

	@AfterAll
	static void stopServers() throws IOException {
		if( commands != null ) {
			commands.stop(true);
		}
		if( sftpOnly != null ) {
			sftpOnly.stop(true);
		}
		if( dir != null ) {
			Files.deleteIfExists(dir);
		}
	}

	private static SshServer server(boolean runsCommands) throws Exception {
		KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
		g.initialize(new ECGenParameterSpec("secp256r1"));
		SshServer server = SshServer.setUpDefaultServer();
		server.setHost(InetAddress.getLoopbackAddress().getHostAddress());
		server.setPort(0);
		server.setKeyPairProvider(KeyPairProvider.wrap(g.generateKeyPair()));
		server.setPasswordAuthenticator((u, p, session) -> TestServer.USER.equals(u) && TestServer.PASSWORD.equals(p));
		server.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
		if( runsCommands ) {
			server.setCommandFactory(ProcessShellCommandFactory.INSTANCE);
		}
		server.start();
		return server;
	}

	/** A factory connected to server, with runCommands as given. Disconnect it when done. */
	private static SftpFileSourceFactory connect(String impl, SshServer server, boolean runCommands) throws IOException {
		Properties p = TestServer.properties(impl, server.getPort());
		p.setProperty(SftpFileSourceFactory.PROP_HOST, InetAddress.getLoopbackAddress().getHostAddress());
		p.setProperty(SftpFileSourceFactory.PROP_RUN_COMMANDS, ""+runCommands);
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		f.setConnectionProperties(p);
		assertTrue(f.connect());
		return f;
	}

	private static String readAll(InputStream in) throws IOException {
		try (InputStream i = in) {
			return new String(i.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@ParameterizedTest
	@MethodSource("us.bringardner.parley.files.sftp.TestServer#providers")
	void runsInTheDirectoryWithTheEnvironmentAndInput(String impl) throws Exception {
		SftpFileSourceFactory f = connect(impl, commands, true);
		try {
			FileSource home = f.createFileSource(dir.toString());
			assertTrue(home.getCapabilities().runsCommands());
			FileSourceProcessBuilder b = f.processBuilder("sh", "-c", "pwd; echo \"$PARLEY_TEST\"; cat; echo oops >&2; exit 3");
			assertNotNull(b);
			b.directory(home).redirectErrorStream(true).environment().put("PARLEY_TEST", "it's set");
			Process p = b.start();
			try {
				try (OutputStream in = p.getOutputStream()) {
					in.write("from stdin\n".getBytes(StandardCharsets.UTF_8));
				}
				assertEquals(dir+"\nit's set\nfrom stdin\noops\n", readAll(p.getInputStream()));
				assertTrue(p.waitFor(30, TimeUnit.SECONDS));
				assertEquals(3, p.exitValue());
				assertEquals("", readAll(p.getErrorStream()));
			} finally {
				p.destroy();
			}
		} finally {
			f.disConnect();
		}
	}

	@ParameterizedTest
	@MethodSource("us.bringardner.parley.files.sftp.TestServer#providers")
	void outputAndErrorStreamWhileItRuns(String impl) throws Exception {
		SftpFileSourceFactory f = connect(impl, commands, true);
		try {
			// a megabyte, many times what any of the libraries buffers, and stderr after it
			Process p = f.processBuilder("sh", "-c", "head -c 1000000 /dev/zero | tr '\\0' x; echo done >&2; exit 0").start();
			try {
				p.getOutputStream().close();
				String out = readAll(p.getInputStream());
				assertEquals(1000000, out.length());
				assertEquals("done\n", readAll(p.getErrorStream()));
				assertEquals(0, p.waitFor());
			} finally {
				p.destroy();
			}
		} finally {
			f.disConnect();
		}
	}

	@ParameterizedTest
	@MethodSource("us.bringardner.parley.files.sftp.TestServer#providers")
	void offUnlessTheConnectionTurnsItOn(String impl) throws Exception {
		SftpFileSourceFactory f = connect(impl, commands, false);
		try {
			assertNull(f.processBuilder("true"));
			assertFalse(f.createFileSource(dir.toString()).getCapabilities().runsCommands());
		} finally {
			f.disConnect();
		}
	}

	@ParameterizedTest
	@MethodSource("us.bringardner.parley.files.sftp.TestServer#providers")
	void aServerThatOnlyAllowsSftpSaysSo(String impl) throws Exception {
		SftpFileSourceFactory f = connect(impl, sftpOnly, true);
		try {
			IOException e = assertThrows(IOException.class, () -> f.processBuilder("true").start().destroy());
			assertTrue(e.getMessage().contains("the account may be limited to SFTP"), e.getMessage());
			// the connection still works for files
			assertTrue(f.createFileSource(dir.toString()).isDirectory());
		} finally {
			f.disConnect();
		}
	}

	@ParameterizedTest
	@MethodSource("us.bringardner.parley.files.sftp.TestServer#providers")
	void quotingKeepsEveryWordWhole(String impl) throws Exception {
		SftpFileSourceFactory f = connect(impl, commands, true);
		try {
			Process p = f.processBuilder("printf", "[%s]", "a b", "it's", "$HOME", "*", "").start();
			try {
				p.getOutputStream().close();
				assertEquals("[a b][it's][$HOME][*][]", readAll(p.getInputStream()));
				assertEquals("", readAll(p.getErrorStream()));
				assertEquals(0, p.waitFor());
			} finally {
				p.destroy();
			}
		} finally {
			f.disConnect();
		}
	}
}
