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
	 * Whether {@link SftpFile#write(long, byte[], int, int)} writes at the position in one
	 * request, so a file that changes size meanwhile can't put the data in the wrong place.
	 * A provider that can't (JSch) says false: random access that writes then goes through
	 * another provider that can, and is refused if there is none.
	 */
	default boolean canWriteAtOffset() {
		return true;
	}

	/**
	 * Connects and authenticates.
	 *
	 * @throws IOException if the host can't be reached, the host key is
	 *         rejected, or authentication fails
	 */
	SshConnection connect(SshSettings settings) throws IOException;
}
