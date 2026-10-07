package us.bringardner.parley.files.sftp.client;

import java.io.IOException;

/**
 * An SSH library that can open connections: JSch, Apache MINA SSHD or Parley's own (parley-ssh).
 * Pick one with {@link SshProviders#get(String)}.
 */
public interface SshProvider {

	/** Short name used to select it: "jsch", "mina" or "parley". */
	String getName();

	/**
	 * Connects and authenticates.
	 *
	 * @throws IOException if the host can't be reached, the host key is
	 *         rejected, or authentication fails
	 */
	SshConnection connect(SshSettings settings) throws IOException;
}
