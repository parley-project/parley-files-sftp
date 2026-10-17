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

/**
 * SSH through JSch (the maintained com.github.mwiede fork, or the original 0.1.55).
 * <p>
 * <b>Limitation: no writing at a position.</b> JSch's API has no positional write, so random
 * access writing (a random access stream opened "rw", "rws" or "rwd" that writes) throws an
 * IOException at the write. Everything else works, including reading at a position. Use another
 * provider, such as parley's (parley-files-sftp-parley), if the application writes at positions.
 */
public class JschProvider implements SshProvider {

	/** The name that selects it: "jsch". */
	public static final String NAME = "jsch";

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getDescription() {
		return "JSch (com.github.mwiede:jsch)";
	}

	@Override
	public SshConnection connect(SshSettings s) throws IOException {
		try {
			// A new JSch per connection, so identities don't pile up across connects
			JSch jsch = new JSch();
			if( s.isStrictHostKeyChecking() ) {
				jsch.setKnownHosts(SshProviders.knownHostsFile(s));
			}
			if( s.isUseAgent() ) {
				jsch.setIdentityRepository(new AgentIdentityRepository(agentConnector()));
			} else if( s.getPrivateKey() != null ) {
				jsch.addIdentity(null, s.getPrivateKey(), null, null);
			} else if( s.getPrivateKeyFile() != null && !s.getPrivateKeyFile().isEmpty()) {
				jsch.addIdentity(s.getPrivateKeyFile());
			}

			Session session = jsch.getSession(s.getUser(), s.getHost(), s.getPort());
			Properties config = new Properties();
			config.put("StrictHostKeyChecking", s.isStrictHostKeyChecking() ? "yes" : "no");
			session.setConfig(config);
			if( s.isUseAgent() ) {
				// only the agent's keys: don't fall back to asking for a password
				config.put("PreferredAuthentications", "publickey");
				session.setConfig(config);
			} else if( s.getPassword() != null ) {
				session.setPassword(s.getPassword().getBytes(StandardCharsets.UTF_8));
			}
			if( s.getServerAliveIntervalMs() > 0 ) {
				session.setServerAliveInterval(s.getServerAliveIntervalMs());
			}
			// JSch's connection thread isn't a daemon by default, so a program that
			// forgot disConnect() never exited. MINA's threads already are.
			session.setDaemonThread(true);
			session.connect(s.getConnectTimeoutMs());
			return new JschConnection(session, s.getConnectTimeoutMs());
		} catch (JSchException e) {
			String m = e.getMessage();
			// by message, so the original JSch 0.1.55 is covered too (it has no exception types for these)
			if( s.isStrictHostKeyChecking() && m != null
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
