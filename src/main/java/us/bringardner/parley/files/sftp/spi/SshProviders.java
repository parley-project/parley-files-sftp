package us.bringardner.parley.files.sftp.spi;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The SSH providers this program can use, and which one is used.
 * <p>
 * A provider is available when its jar is on the class path (it is found with
 * {@link ServiceLoader}), or when it was {@link #register(SshProvider) registered}. Which one a
 * factory uses:
 * <ol>
 * <li>the one it names: the factory's "implementation" connection property, else the system
 * property {@value #SYSTEM_PROPERTY};</li>
 * <li>with no name: parley-ssh's ({@value #PARLEY}) if it is available;</li>
 * <li>otherwise the only one available. With several and none named, connecting fails and asks
 * for a name; with none, it fails and says which jars to add.</li>
 * </ol>
 */
public final class SshProviders {

	/** System property naming the provider to use when a factory doesn't name one. */
	public static final String SYSTEM_PROPERTY = "parley.sftp.implementation";
	/** The older name of {@link #SYSTEM_PROPERTY}, still read when the new one isn't set. */
	public static final String LEGACY_SYSTEM_PROPERTY = "bjl.sftp.implementation";
	/** parley-ssh's provider (parley-files-sftp-parley-ssh), used when several are available and none is named. */
	public static final String PARLEY = "parley";
	/** The older name of {@link #PARLEY}, still accepted. */
	public static final String LEGACY_PARLEY = "bjl";
	/** What to add when there is no provider. */
	static final String ADD_ONE = "add one to the class path: us.bringardner.parley:parley-files-sftp-parley-ssh"
			+" (parley-ssh), parley-files-sftp-jsch (JSch) or parley-files-sftp-mina (Apache MINA SSHD)";

	private static final Map<String, SshProvider> REGISTERED = new ConcurrentHashMap<>();
	private static volatile Map<String, SshProvider> found;

	private SshProviders() {
	}

	/**
	 * Makes a provider available without a service file, for example one loaded from a plugin
	 * folder. It replaces one of the same name found on the class path.
	 */
	public static void register(SshProvider provider) {
		REGISTERED.put(canonicalName(provider.getName()), provider);
	}

	/** Undoes {@link #register(SshProvider)}. */
	public static void unregister(String name) {
		REGISTERED.remove(canonicalName(name));
	}

	/** Looks on the class path again (it is looked at once, when first needed). */
	public static void reload() {
		found = null;
	}

	/** @return the available providers by name, in name order */
	public static Map<String, SshProvider> available() {
		Map<String, SshProvider> ret = new TreeMap<>(found());
		ret.putAll(REGISTERED);
		return Collections.unmodifiableMap(ret);
	}

	/** @return true if a provider of that name is available */
	public static boolean isAvailable(String name) {
		return name != null && available().containsKey(canonicalName(name));
	}

	/** The name in lower case, with the older "bjl" turned into "parley". Null or empty stays as it is. */
	public static String canonicalName(String name) {
		if( name == null || name.trim().isEmpty()) {
			return name;
		}
		String n = name.trim().toLowerCase(Locale.ROOT);
		return LEGACY_PARLEY.equals(n) ? PARLEY : n;
	}

	/** @return the provider the system property names, or null if it isn't set */
	public static String configuredName() {
		String name = System.getProperty(SYSTEM_PROPERTY);
		if( name == null || name.trim().isEmpty()) {
			name = System.getProperty(LEGACY_SYSTEM_PROPERTY);
		}
		return name == null || name.trim().isEmpty() ? null : canonicalName(name);
	}

	/**
	 * The provider used when a factory names none: the system property's, else parley's if it is
	 * available, else the only one available.
	 *
	 * @throws IllegalStateException if there is none, or several and none is named
	 */
	public static String defaultName() {
		return choose(configuredName(), available().keySet());
	}

	/** The rules of defaultName(): 'configured' if set, else parley if available, else the only one. */
	static String choose(String configured, java.util.Collection<String> available) {
		if( configured != null ) {
			return configured;
		}
		if( available.contains(PARLEY)) {
			return PARLEY;
		}
		if( available.size() == 1 ) {
			return available.iterator().next();
		}
		if( available.isEmpty()) {
			throw new IllegalStateException("There is no SSH provider for SFTP: "+ADD_ONE+".");
		}
		throw new IllegalStateException("Several SSH providers are on the class path ("+String.join(", ", available)
				+") and none is chosen: set the \"implementation\" connection property, or -D"+SYSTEM_PROPERTY+"=<name>.");
	}

	/**
	 * @param name a provider's name (case-insensitive; "bjl" is the older name of parley);
	 *        null or empty for {@link #defaultName()}
	 * @throws IllegalArgumentException if no provider of that name is available
	 * @throws IllegalStateException if none is named and none can be chosen (see defaultName())
	 */
	public static SshProvider get(String name) {
		String n = name == null || name.trim().isEmpty() ? defaultName() : canonicalName(name);
		Map<String, SshProvider> all = available();
		SshProvider p = all.get(n);
		if( p == null ) {
			throw new IllegalArgumentException("The SSH provider '"+n+"' isn't on the class path. "
					+(all.isEmpty() ? "There is none: "+ADD_ONE : "Available: "+String.join(", ", all.keySet())
					+"; or add its jar")+".");
		}
		return p;
	}

	/** The known_hosts file used for host key checking: the setting, else ~/.ssh/known_hosts. */
	public static String knownHostsFile(SshSettings s) {
		return s.getKnownHosts() != null && !s.getKnownHosts().isEmpty()
				? s.getKnownHosts()
				: System.getProperty("user.home")+"/.ssh/known_hosts";
	}

	/**
	 * The error for a server whose host key the known_hosts file doesn't
	 * vouch for, saying what to do about it. The libraries' own messages
	 * ("reject HostKey: host", "Server key did not validate") don't. For providers.
	 */
	public static IOException hostKeyRejected(SshSettings s, String detail, Throwable cause) {
		String file = knownHostsFile(s);
		return new IOException("The host key of "+s.getHost()+":"+s.getPort()+" isn't in "+file
				+", or doesn't match the one there ("+detail+"). Host keys are checked by default"
				+" (strictHostKeyChecking=yes). If you trust this server, add its key to "+file
				+", for example: ssh-keyscan -p "+s.getPort()+" "+s.getHost()+" >> "+file
				+" (compare the fingerprint with the server's first). If the key was there and"
				+" changed without a reason, the server may be impersonated. Setting"
				+" strictHostKeyChecking=no accepts any server.", cause);
	}

	private static Map<String, SshProvider> found() {
		Map<String, SshProvider> f = found;
		if( f == null ) {
			synchronized (SshProviders.class) {
				f = found;
				if( f == null ) {
					Map<String, SshProvider> m = new TreeMap<>();
					ClassLoader own = SshProviders.class.getClassLoader();
					load(ServiceLoader.load(SshProvider.class, own), m);
					ClassLoader context = Thread.currentThread().getContextClassLoader();
					if( context != null && context != own ) {
						load(ServiceLoader.load(SshProvider.class, context), m);
					}
					f = Collections.unmodifiableMap(m);
					found = f;
				}
			}
		}
		return f;
	}

	private static void load(ServiceLoader<SshProvider> loader, Map<String, SshProvider> into) {
		Iterator<SshProvider> it = loader.iterator();
		while (true) {
			try {
				if( !it.hasNext()) {
					return;
				}
				SshProvider p = it.next();
				into.putIfAbsent(canonicalName(p.getName()), p);
			} catch (ServiceConfigurationError | LinkageError e) {
				// a provider that can't be loaded (its SSH library is missing): it isn't available
			}
		}
	}
}
