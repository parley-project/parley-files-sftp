package us.bringardner.parley.files.sftp.spi;

/**
 * Everything a provider needs to open an SSH connection. Providers read these
 * values and nothing else, so every SSH library connects the same way. The
 * factory fills it in; settings may be added in later versions, so providers
 * should read only through the getters.
 */
public final class SshSettings {

	private String host;
	private int port = 22;
	private String user;
	private String password;
	private String privateKeyFile;
	private byte[] privateKey;
	private boolean useAgent;
	private String knownHosts;
	private boolean strictHostKeyChecking;
	private int connectTimeoutMs = 30_000;
	private int serverAliveIntervalMs = 30_000;

	/** @return the server's host name or address */
	public String getHost() {
		return host;
	}

	public SshSettings setHost(String host) {
		this.host = host;
		return this;
	}

	/** @return the server's port (22 by default) */
	public int getPort() {
		return port;
	}

	public SshSettings setPort(int port) {
		this.port = port;
		return this;
	}

	/** @return the user to log in as */
	public String getUser() {
		return user;
	}

	public SshSettings setUser(String user) {
		this.user = user;
		return this;
	}

	/** @return the password, or null for key authentication only */
	public String getPassword() {
		return password;
	}

	public SshSettings setPassword(String password) {
		this.password = password;
		return this;
	}

	/** @return the path of a private key file, or null */
	public String getPrivateKeyFile() {
		return privateKeyFile;
	}

	public SshSettings setPrivateKeyFile(String privateKeyFile) {
		this.privateKeyFile = privateKeyFile;
		return this;
	}

	/**
	 * @return the private key itself (PEM or OpenSSH format), or null. When set it is used
	 *         instead of {@link #getPrivateKeyFile()}. The array is the factory's: the factory
	 *         clears it after connecting, so a provider must not keep it.
	 */
	public byte[] getPrivateKey() {
		return privateKey;
	}

	public SshSettings setPrivateKey(byte[] privateKey) {
		this.privateKey = privateKey;
		return this;
	}

	/** @return true to log in with the keys an ssh-agent holds; the password and keys are then not used */
	public boolean isUseAgent() {
		return useAgent;
	}

	public SshSettings setUseAgent(boolean useAgent) {
		this.useAgent = useAgent;
		return this;
	}

	/** @return the known_hosts file used when host keys are checked; null means ~/.ssh/known_hosts */
	public String getKnownHosts() {
		return knownHosts;
	}

	public SshSettings setKnownHosts(String knownHosts) {
		this.knownHosts = knownHosts;
		return this;
	}

	/** @return true to reject host keys that aren't in known_hosts; false to accept any host key */
	public boolean isStrictHostKeyChecking() {
		return strictHostKeyChecking;
	}

	public SshSettings setStrictHostKeyChecking(boolean strictHostKeyChecking) {
		this.strictHostKeyChecking = strictHostKeyChecking;
		return this;
	}

	/** @return milliseconds for the TCP connect, handshake, authentication and channel opens; 0 = no limit */
	public int getConnectTimeoutMs() {
		return connectTimeoutMs;
	}

	public SshSettings setConnectTimeoutMs(int connectTimeoutMs) {
		this.connectTimeoutMs = connectTimeoutMs;
		return this;
	}

	/** @return milliseconds between keepalive messages on an idle connection; 0 = none */
	public int getServerAliveIntervalMs() {
		return serverAliveIntervalMs;
	}

	public SshSettings setServerAliveIntervalMs(int serverAliveIntervalMs) {
		this.serverAliveIntervalMs = serverAliveIntervalMs;
		return this;
	}

	@Override
	public String toString() {
		return user+"@"+host+":"+port;
	}
}
