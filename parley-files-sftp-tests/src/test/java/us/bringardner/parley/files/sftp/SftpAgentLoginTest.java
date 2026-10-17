package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceFactory;
import us.bringardner.parley.files.IRandomAccessStream;
import us.bringardner.parley.files.sftp.spi.SshProviders;
import us.bringardner.parley.ssh.client.SshAgent;
import us.bringardner.parley.ssh.server.AuthorizedKeysAuthenticator;
import us.bringardner.parley.ssh.server.HostKeyProviders;
import us.bringardner.parley.ssh.server.SshServer;
import us.bringardner.parley.ssh.sftp.server.SftpSubsystemFactory;

/**
 * auth=Agent end to end, against a parley-ssh server that accepts the keys the running ssh-agent
 * holds: the parley implementation logs in through the agent, and so does the parley connection
 * that JSch's random access writes go through. Needs an ssh-agent with at least one key
 * (SSH_AUTH_SOCK, as on most developer machines; Java 16 or later on macOS and Linux); skipped
 * otherwise.
 */
public class SftpAgentLoginTest {

	private static SshServer server;

	@BeforeAll
	static void start() throws Exception {
		assumeTrue(SshAgent.isSupported() && SshAgent.isAvailable(), "no ssh-agent here (SSH_AUTH_SOCK), or Java before 16");
		List<SshAgent.Identity> ids;
		try (SshAgent agent = SshAgent.connect()) {
			ids = agent.getIdentities();
		}
		assumeTrue(!ids.isEmpty(), "the ssh-agent holds no keys");

		File dir = Files.createTempDirectory("parley-sftp-agent-test").toFile();
		StringBuilder keys = new StringBuilder();
		for (SshAgent.Identity id : ids) {
			keys.append(keyType(id.getBlob())).append(' ').append(Base64.getEncoder().encodeToString(id.getBlob())).append('\n');
		}
		File authorized = new File(dir, "authorized_keys");
		Files.write(authorized.toPath(), keys.toString().getBytes(StandardCharsets.UTF_8));
		File root = new File(dir, "root");
		assertTrue(root.mkdirs());

		server = new SshServer(0);
		server.setHostKeyProvider(HostKeyProviders.ephemeral());
		server.setPublicKeyAuthenticator(AuthorizedKeysAuthenticator.forFile(authorized));
		server.addSubsystem(SftpSubsystemFactory.forRoot(FileSourceFactory.getDefaultFactory().createFileSource(root.getCanonicalPath())));
		server.startAndWait(5000);
	}

	@AfterAll
	static void stop() throws InterruptedException {
		if( server != null ) {
			server.stop(5000, false);
		}
	}

	/** The key type at the start of a public key blob (a length-prefixed string). */
	private static String keyType(byte[] blob) {
		ByteBuffer b = ByteBuffer.wrap(blob);
		byte[] name = new byte[b.getInt()];
		b.get(name);
		return new String(name, StandardCharsets.US_ASCII);
	}

	private static SftpFileSourceFactory connect(String implementation) throws IOException {
		Properties p = new SftpFileSourceFactory().getConnectProperties();
		p.setProperty(SftpFileSourceFactory.PROP_USER, "agent-test");
		p.setProperty(SftpFileSourceFactory.PROP_HOST, "localhost");
		p.setProperty(SftpFileSourceFactory.PROP_PORT, ""+server.getLocalPort());
		p.setProperty(SftpFileSourceFactory.PROP_AUTH, SftpFileSourceFactory.AUTH_AGENT);
		p.setProperty(SftpFileSourceFactory.PROP_STRICT_HOST_KEY_CHECKING, "no");
		p.setProperty(SftpFileSourceFactory.PROP_IMPLEMENTATION, implementation);
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		assertTrue(f.connect(p), "didn't connect with "+implementation);
		return f;
	}

	private static void write(FileSource f, byte[] data) throws IOException {
		try (OutputStream out = f.getOutputStream()) {
			out.write(data);
		}
	}

	private static byte[] read(FileSource f) throws IOException {
		try (InputStream in = f.getInputStream()) {
			return in.readAllBytes();
		}
	}

	@Test
	void parleyLogsInThroughTheAgent() throws IOException {
		SftpFileSourceFactory f = connect(SshProviders.PARLEY);
		try {
			FileSource file = f.createFileSource("/parley.txt");
			write(file, "hello".getBytes(StandardCharsets.US_ASCII));
			assertEquals("hello", new String(read(file), StandardCharsets.US_ASCII));
		} finally {
			f.disConnect();
		}
	}

	@Test
	void jschRandomAccessWritesGoThroughParleyWithTheAgent() throws IOException {
		SftpFileSourceFactory f = connect("jsch");
		try {
			FileSource file = f.createFileSource("/jsch.bin");
			write(file, new byte[10]);
			try (IRandomAccessStream r = file.getRandomAccessStream("rw")) {
				r.seek(3);
				r.write(7);
			}
			assertEquals(SshProviders.PARLEY, f.randomAccessCompanion().getEffectiveImplementation());
			assertEquals(7, read(file)[3], "the byte is where it was written");
		} finally {
			f.disConnect();
		}
	}
}
