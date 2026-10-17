package us.bringardner.parley.files.sftp.mina;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Duration;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.keyverifier.KnownHostsServerKeyVerifier;
import org.apache.sshd.client.keyverifier.RejectAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.core.CoreModuleProperties;

import us.bringardner.parley.files.sftp.spi.SshConnection;
import us.bringardner.parley.files.sftp.spi.SshProvider;
import us.bringardner.parley.files.sftp.spi.SshProviders;
import us.bringardner.parley.files.sftp.spi.SshSettings;

/** SSH through Apache MINA SSHD. */
public class MinaProvider implements SshProvider {

	/** Unanswered keepalives before the connection is dropped (OpenSSH's default). */
	static final int SERVER_ALIVE_COUNT_MAX = 3;

	/** The name that selects it: "mina". */
	public static final String NAME = "mina";

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getDescription() {
		return "Apache MINA SSHD (org.apache.sshd:sshd-sftp)";
	}

	@Override
	public SshConnection connect(SshSettings s) throws IOException {
		if( s.isUseAgent() ) {
			throw new IOException("Logging in with ssh-agent isn't supported by the mina SSH library yet; use implementation=jsch");
		}
		// One client per connection: the host-key policy is set on the client,
		// and it differs between connections.
		SshClient client = SshClient.setUpDefaultClient();
		boolean ok = false;
		try {
			if( s.isStrictHostKeyChecking() ) {
				Path knownHosts = Paths.get(SshProviders.knownHostsFile(s));
				client.setServerKeyVerifier(new KnownHostsServerKeyVerifier(RejectAllServerKeyVerifier.INSTANCE, knownHosts));
			} else {
				client.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
			}
			if( s.getServerAliveIntervalMs() > 0 ) {
				CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, Duration.ofMillis(s.getServerAliveIntervalMs()));
				// Ask for a reply and give up after this many go unanswered, like
				// OpenSSH's ServerAliveCountMax. Without it MINA's heartbeats are
				// one-way, so a dead connection could look alive for many minutes.
				CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.set(client, SERVER_ALIVE_COUNT_MAX);
			}
			client.start();

			Duration timeout = s.getConnectTimeoutMs() > 0 ? Duration.ofMillis(s.getConnectTimeoutMs()) : Duration.ofDays(365);
			ClientSession session;
			try {
				session = client.connect(s.getUser(), s.getHost(), s.getPort()).verify(timeout).getSession();
			} catch (SshException e) {
				throw hostKeyError(s, e);
			}
			try {
				if( s.getPassword() != null ) {
					session.addPasswordIdentity(s.getPassword());
				}
				for (KeyPair kp : loadKeys(s)) {
					session.addPublicKeyIdentity(kp);
				}
				session.auth().verify(timeout);
			} catch (SshException e) {
				session.close(true);
				throw hostKeyError(s, e);
			} catch (IOException | RuntimeException e) {
				session.close(true);
				throw e;
			}
			ok = true;
			return new MinaConnection(client, session, timeout);
		} finally {
			if( !ok ) {
				client.stop();
			}
		}
	}

	/**
	 * A rejected host key, as SshProviders.hostKeyRejected; any other error as
	 * it is. MINA reports the rejection (unknown or changed key) as this
	 * message, during the connect or the login that follows it.
	 */
	private static IOException hostKeyError(SshSettings s, SshException e) {
		if( s.isStrictHostKeyChecking() && "Server key did not validate".equals(e.getMessage())) {
			return SshProviders.hostKeyRejected(s, e.getMessage(), e);
		}
		return e;
	}

	private static Iterable<KeyPair> loadKeys(SshSettings s) throws IOException {
		InputStream in = null;
		String name = null;
		if( s.getPrivateKey() != null ) {
			in = new ByteArrayInputStream(s.getPrivateKey());
			name = "privateKey";
		} else if( s.getPrivateKeyFile() != null && !s.getPrivateKeyFile().isEmpty()) {
			in = Files.newInputStream(Paths.get(s.getPrivateKeyFile()));
			name = s.getPrivateKeyFile();
		}
		if( in == null ) {
			return java.util.Collections.emptyList();
		}
		try (InputStream is = in) {
			Iterable<KeyPair> keys = SecurityUtils.loadKeyPairIdentities(null, NamedResource.ofName(name), is, null);
			return keys == null ? java.util.Collections.emptyList() : keys;
		} catch (GeneralSecurityException e) {
			throw new IOException("Can't load private key "+name+": "+e.getMessage(), e);
		}
	}
}
