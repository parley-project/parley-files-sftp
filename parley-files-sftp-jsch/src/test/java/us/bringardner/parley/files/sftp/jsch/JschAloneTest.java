package us.bringardner.parley.files.sftp.jsch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.files.IRandomAccessStream;
import us.bringardner.parley.files.sftp.SftpFileSourceFactory;
import us.bringardner.parley.files.sftp.spi.SshProviders;
import us.bringardner.parley.ssh.server.HostKeyProviders;
import us.bringardner.parley.ssh.server.SshPrincipal;
import us.bringardner.parley.ssh.server.SshServer;
import us.bringardner.parley.ssh.sftp.server.SftpSubsystemFactory;

/**
 * JSch as the only provider on the class path (this module's tests have no other; the server is
 * parley-ssh's, which registers no provider): it is used without being named, reading and
 * writing work, and a random access write throws, because JSch can't write at a position.
 */
public class JschAloneTest {

	private static SshServer server;

	@BeforeAll
	static void start() throws Exception {
		File root = Files.createTempDirectory("parley-sftp-jsch-test").toFile();
		server = new SshServer(0);
		server.setHostKeyProvider(HostKeyProviders.ephemeral());
		server.setPasswordAuthenticator((user, pw, ctx) -> "secret".equals(new String(pw)) ? new SshPrincipal(user) : null);
		server.addSubsystem(SftpSubsystemFactory.forRoot(FileSourceFactory.getDefaultFactory().createFileSource(root.getCanonicalPath())));
		server.startAndWait(5000);
	}

	@AfterAll
	static void stop() throws InterruptedException {
		if( server != null ) {
			server.stop(5000, false);
		}
	}

	/** Connected with no provider named. */
	private static SftpFileSourceFactory connect() throws IOException {
		Properties p = new SftpFileSourceFactory().getConnectProperties();
		p.setProperty(SftpFileSourceFactory.PROP_USER, "jsch-test");
		p.setProperty(SftpFileSourceFactory.PROP_PASSWORD, "secret");
		p.setProperty(SftpFileSourceFactory.PROP_HOST, "localhost");
		p.setProperty(SftpFileSourceFactory.PROP_PORT, ""+server.getLocalPort());
		p.setProperty(SftpFileSourceFactory.PROP_STRICT_HOST_KEY_CHECKING, "no");
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		assertTrue(f.connect(p));
		return f;
	}

	@Test
	void jschIsTheOnlyProviderAndIsUsed() throws IOException {
		assertEquals(java.util.Collections.singleton(JschProvider.NAME), SshProviders.available().keySet());
		SftpFileSourceFactory f = connect();
		try {
			assertEquals(JschProvider.NAME, f.getEffectiveImplementation());
			FileSource file = f.createFileSource("/a.txt");
			try (OutputStream out = file.getOutputStream()) {
				out.write("hello".getBytes(StandardCharsets.US_ASCII));
			}
			try (InputStream in = file.getInputStream()) {
				assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.US_ASCII));
			}
			try (IRandomAccessStream r = file.getRandomAccessStream("r")) {
				r.seek(1);
				assertEquals('e', r.read(), "reading at a position works with JSch");
			}
		} finally {
			f.disConnect();
		}
	}

	@Test
	void writingAtAPositionThrows() throws IOException {
		SftpFileSourceFactory f = connect();
		try {
			FileSource file = f.createFileSource("/b.bin");
			try (OutputStream out = file.getOutputStream()) {
				out.write(new byte[10]);
			}
			IOException e;
			try (IRandomAccessStream r = file.getRandomAccessStream("rw")) {
				r.seek(3);
				e = assertThrows(IOException.class, () -> r.write(7), "the write itself fails");
			}
			assertTrue(e.getMessage().contains("can't write at a position"), e.getMessage());
			assertTrue(e.getMessage().contains("parley-files-sftp-parley"), e.getMessage());
		} finally {
			f.disConnect();
		}
	}

}
