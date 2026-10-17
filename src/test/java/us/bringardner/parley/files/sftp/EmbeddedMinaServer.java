package us.bringardner.parley.files.sftp;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Collections;

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

/**
 * The embedded test server on Windows. Maverick's file factory can't be used there: it strips the
 * backslashes from its own home path ("C:UsersX...home/a.txt is not a valid file path"), and
 * closes the connection when it is given the real path. Apache MINA's SFTP server, whose virtual
 * file system is rooted in the same directory, works. In a class of its own so that TestServer, and
 * the tests, also run without MINA (an optional dependency) on other systems.
 */
final class EmbeddedMinaServer {

	private EmbeddedMinaServer() {
	}

	/** Starts the server for one user, with a shutdown hook to stop it. @return its port */
	static int start(File home, String user, String password) throws IOException {
		SshServer server;
		try {
			KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
			g.initialize(new ECGenParameterSpec("secp256r1"));
			server = SshServer.setUpDefaultServer();
			server.setHost(InetAddress.getLoopbackAddress().getHostAddress());
			server.setPort(0);
			server.setKeyPairProvider(KeyPairProvider.wrap(g.generateKeyPair()));
			server.setPasswordAuthenticator((u, p, session) -> user.equals(u) && password.equals(p));
			server.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
			server.setFileSystemFactory(new VirtualFileSystemFactory(home.toPath()));
			server.start();
		} catch (Exception e) {
			throw new IOException("Can't start the embedded SSH server: "+e, e);
		}
		SshServer started = server;
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				started.stop(true);
			} catch (IOException e) {
				// shutting down
			}
		}, "embedded SSH server shutdown"));
		return started.getPort();
	}
}
