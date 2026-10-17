package us.bringardner.parley.files.sftp.spi;

import java.io.IOException;

/**
 * An SSH library that SftpFileSourceFactory can use. An implementation is found at run time: put
 * its jar on the class path with a {@code META-INF/services/us.bringardner.parley.files.sftp.spi.SshProvider}
 * file naming the class (it needs a public no-argument constructor), or call
 * {@link SshProviders#register(SshProvider)}. parley-files-sftp-parley, parley-files-sftp-jsch and
 * parley-files-sftp-mina are implementations; others can be written against this package alone.
 * <p>
 * Methods added to this interface later get default implementations, so a provider written
 * now keeps working.
 */
public interface SshProvider {

	/**
	 * The name that selects this provider (the "implementation" connection property, or the
	 * system property {@value SshProviders#SYSTEM_PROPERTY}). Compared without regard to case.
	 */
	String getName();

	/** A line for people choosing a provider, such as the library and its version. */
	default String getDescription() {
		return getName();
	}

	/**
	 * Connects and authenticates.
	 *
	 * @throws IOException if the host can't be reached, the host key is
	 *         rejected, or authentication fails
	 */
	SshConnection connect(SshSettings settings) throws IOException;
}
