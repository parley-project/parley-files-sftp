package us.bringardner.parley.files.sftp.client;

import java.util.Locale;

import us.bringardner.parley.files.sftp.client.jsch.JschProvider;
import us.bringardner.parley.files.sftp.client.mina.MinaProvider;

/**
 * Chooses the SSH library at run time.
 * <p>
 * The name comes from, in order: the value passed in (the factory's
 * "implementation" connection property), the system property
 * {@value #SYSTEM_PROPERTY}, then {@value #DEFAULT}.
 */
public final class SshProviders {

	/** System property naming the default implementation: jsch, mina or parley. */
	public static final String SYSTEM_PROPERTY = "parley.sftp.implementation";
	/** The older name of {@link #SYSTEM_PROPERTY}, still read when the new one isn't set. */
	public static final String LEGACY_SYSTEM_PROPERTY = "bjl.sftp.implementation";
	public static final String JSCH = "jsch";
	public static final String MINA = "mina";
	/** Parley's own SSH library (parley-ssh), no third party code */
	public static final String PARLEY = "parley";
	/** The older name of {@link #PARLEY}, still accepted. */
	public static final String LEGACY_PARLEY = "bjl";
	public static final String DEFAULT = JSCH;

	private SshProviders() {
	}

	/**
	 * The implementation to use when none is configured on the factory:
	 * the system property if set, otherwise jsch.
	 */
	public static String defaultName() {
		String name = System.getProperty(SYSTEM_PROPERTY);
		if( name == null || name.trim().isEmpty()) {
			name = System.getProperty(LEGACY_SYSTEM_PROPERTY);
		}
		return name == null || name.trim().isEmpty() ? DEFAULT : canonicalName(name);
	}

	/**
	 * The name in lower case, with the older "bjl" turned into "parley".
	 * Null or empty stays as it is.
	 */
	public static String canonicalName(String name) {
		if( name == null || name.trim().isEmpty()) {
			return name;
		}
		String n = name.trim().toLowerCase(Locale.ROOT);
		return LEGACY_PARLEY.equals(n) ? PARLEY : n;
	}

	/** The known_hosts file used for host key checking: the setting, else ~/.ssh/known_hosts. */
	public static String knownHostsFile(SshSettings s) {
		return s.knownHosts != null && !s.knownHosts.isEmpty()
				? s.knownHosts
				: System.getProperty("user.home")+"/.ssh/known_hosts";
	}

	/**
	 * The error for a server whose host key the known_hosts file doesn't
	 * vouch for, saying what to do about it. The libraries' own messages
	 * ("reject HostKey: host", "Server key did not validate") don't.
	 */
	public static java.io.IOException hostKeyRejected(SshSettings s, String detail, Throwable cause) {
		String file = knownHostsFile(s);
		return new java.io.IOException("The host key of "+s.host+":"+s.port+" isn't in "+file
				+", or doesn't match the one there ("+detail+"). Host keys are checked by default"
				+" (strictHostKeyChecking=yes). If you trust this server, add its key to "+file
				+", for example: ssh-keyscan -p "+s.port+" "+s.host+" >> "+file
				+" (compare the fingerprint with the server's first). If the key was there and"
				+" changed without a reason, the server may be impersonated. Setting"
				+" strictHostKeyChecking=no accepts any server.", cause);
	}

	/**
	 * @param name "jsch", "mina" or "parley" ("bjl" is the older name of parley; case-insensitive);
	 *        null or empty for the default
	 * @throws IllegalArgumentException for any other name
	 */
	public static SshProvider get(String name) {
		if( name == null || name.trim().isEmpty()) {
			name = defaultName();
		}
		switch (canonicalName(name)) {
		case JSCH: return new JschProvider();
		case MINA: return new MinaProvider();
		case PARLEY: return new us.bringardner.parley.files.sftp.client.parley.ParleyProvider();
		default:
			throw new IllegalArgumentException("Unknown SFTP implementation '"+name+"'; use "+JSCH+", "+MINA+" or "+PARLEY);
		}
	}
}
