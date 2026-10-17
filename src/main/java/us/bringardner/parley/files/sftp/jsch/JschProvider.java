package us.bringardner.parley.files.sftp.jsch;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Properties;

import com.jcraft.jsch.AgentConnector;
import com.jcraft.jsch.AgentIdentityRepository;
import com.jcraft.jsch.AgentProxyException;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.SSHAgentConnector;
import com.jcraft.jsch.UnixDomainSocketFactory;
import com.jcraft.jsch.WindowsSSHAgentConnector;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import us.bringardner.parley.files.sftp.spi.SshConnection;
import us.bringardner.parley.files.sftp.spi.SshProvider;
import us.bringardner.parley.files.sftp.spi.SshProviders;
import us.bringardner.parley.files.sftp.spi.SshSettings;

/** SSH through JSch (the maintained com.github.mwiede fork, or the original 0.1.55). */
public class JschProvider implements SshProvider {

	@Override
	public String getName() {
		return SshProviders.JSCH;
	}

	@Override
	public SshConnection connect(SshSettings s) throws IOException {
		try {
			// A new JSch per connection, so identities don't pile up across connects
			JSch jsch = new JSch();
			if( s.strictHostKeyChecking ) {
				jsch.setKnownHosts(SshProviders.knownHostsFile(s));
			}
			if( s.useAgent ) {
				jsch.setIdentityRepository(new AgentIdentityRepository(agentConnector()));
			} else if( s.privateKey != null ) {
				jsch.addIdentity(null, s.privateKey, null, null);
			} else if( s.privateKeyFile != null && !s.privateKeyFile.isEmpty()) {
				jsch.addIdentity(s.privateKeyFile);
			}

			Session session = jsch.getSession(s.user, s.host, s.port);
			Properties config = new Properties();
			config.put("StrictHostKeyChecking", s.strictHostKeyChecking ? "yes" : "no");
			session.setConfig(config);
			if( s.useAgent ) {
				// only the agent's keys: don't fall back to asking for a password
				config.put("PreferredAuthentications", "publickey");
				session.setConfig(config);
			} else if( s.password != null ) {
				session.setPassword(s.password.getBytes(StandardCharsets.UTF_8));
			}
			if( s.serverAliveIntervalMs > 0 ) {
				session.setServerAliveInterval(s.serverAliveIntervalMs);
			}
			// JSch's connection thread isn't a daemon by default, so a program that
			// forgot disConnect() never exited. MINA's threads already are.
			session.setDaemonThread(true);
			session.connect(s.connectTimeoutMs);
			return new JschConnection(session, s.connectTimeoutMs);
		} catch (JSchException e) {
			String m = e.getMessage();
			// by message, so the original JSch 0.1.55 is covered too (it has no exception types for these)
			if( s.strictHostKeyChecking && m != null
					&& (m.startsWith("reject HostKey") || m.startsWith("UnknownHostKey") || m.startsWith("HostKey has been changed"))) {
				throw SshProviders.hostKeyRejected(s, m, e);
			}
			throw new IOException(m, e);
		}
	}

	/**
	 * The running ssh-agent: the Windows OpenSSH agent on Windows, otherwise the one
	 * SSH_AUTH_SOCK names (needs Java 16 or later for its Unix domain socket).
	 */
	static AgentConnector agentConnector() throws IOException {
		try {
			AgentConnector c;
			if( System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows")) {
				c = new WindowsSSHAgentConnector();
			} else {
				String sock = System.getenv("SSH_AUTH_SOCK");
				if( sock == null || sock.isEmpty()) {
					throw new IOException("No ssh-agent: SSH_AUTH_SOCK isn't set");
				}
				c = new SSHAgentConnector(new UnixDomainSocketFactory(), java.nio.file.Paths.get(sock));
			}
			if( !c.isAvailable()) {
				throw new IOException("The ssh-agent isn't running or can't be reached");
			}
			return c;
		} catch (AgentProxyException e) {
			throw new IOException("Can't use the ssh-agent: "+e.getMessage(), e);
		}
	}
}
