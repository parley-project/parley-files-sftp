package us.bringardner.parley.files.sftp.client.parley;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.parley.files.sftp.client.SshConnection;
import us.bringardner.parley.files.sftp.client.SshProvider;
import us.bringardner.parley.files.sftp.client.SshProviders;
import us.bringardner.parley.files.sftp.client.SshSettings;
import us.bringardner.parley.ssh.SshConstants;
import us.bringardner.parley.ssh.SshException;
import us.bringardner.parley.ssh.client.ClientSession;
import us.bringardner.parley.ssh.client.HostKeyVerifiers;
import us.bringardner.parley.ssh.client.IClientAuthMethod;
import us.bringardner.parley.ssh.client.KeyboardInteractiveAuth;
import us.bringardner.parley.ssh.client.KnownHosts;
import us.bringardner.parley.ssh.client.PasswordAuth;
import us.bringardner.parley.ssh.client.PublicKeyAuth;
import us.bringardner.parley.ssh.client.SshClient;
import us.bringardner.parley.ssh.keys.SshKeyLoader;

/**
 * Parley's own SSH library (parley-ssh): no third party code.
 */
public class ParleyProvider implements SshProvider {

	@Override
	public String getName() {
		return SshProviders.PARLEY;
	}

	@Override
	public SshConnection connect(SshSettings s) throws IOException {
		// One client per connection: the host-key policy and keepalive are set on the client
		SshClient client = new SshClient();
		boolean ok = false;
		try {
			if( s.strictHostKeyChecking ) {
				client.setHostKeyVerifier(new KnownHosts(new File(SshProviders.knownHostsFile(s))));
			} else {
				client.setHostKeyVerifier(HostKeyVerifiers.acceptAll());
			}
			if( s.serverAliveIntervalMs > 0 ) {
				client.setKeepAliveInterval(s.serverAliveIntervalMs);
			}
			client.setConnectTimeout(s.connectTimeoutMs);
			ClientSession session;
			try {
				session = client.connectAndWait(s.host, s.port);
			} catch (SshException e) {
				if( s.strictHostKeyChecking && e.getReason() == SshConstants.SSH_DISCONNECT_HOST_KEY_NOT_VERIFIABLE ) {
					throw SshProviders.hostKeyRejected(s, e.getMessage(), e);
				}
				throw e;
			}
			try {
				session.setAuthTimeout(s.connectTimeoutMs);
				session.setChannelTimeout(s.connectTimeoutMs);
				List<IClientAuthMethod> methods = new ArrayList<IClientAuthMethod>();
				List<KeyPair> keys = loadKeys(s);
				if( !keys.isEmpty() ) {
					methods.add(new PublicKeyAuth(keys));
				}
				if( s.password != null ) {
					methods.add(new PasswordAuth(s.password));
					methods.add(KeyboardInteractiveAuth.password(s.password.toCharArray()));
				}
				session.authenticateAndWait(s.user, methods.toArray(new IClientAuthMethod[0]));
			} catch (IOException | RuntimeException e) {
				session.close();
				throw e;
			}
			ok = true;
			return new ParleyConnection(client, session);
		} finally {
			if( !ok ) {
				client.close();
			}
		}
	}

	private static List<KeyPair> loadKeys(SshSettings s) throws IOException {
		List<KeyPair> ret = new ArrayList<KeyPair>();
		if( s.privateKey != null ) {
			ret.add(SshKeyLoader.parse(new String(s.privateKey, StandardCharsets.US_ASCII), null));
		} else if( s.privateKeyFile != null && !s.privateKeyFile.isEmpty() ) {
			ret.add(SshKeyLoader.load(new File(s.privateKeyFile), null));
		}
		return ret;
	}
}
