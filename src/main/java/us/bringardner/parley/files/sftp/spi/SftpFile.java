package us.bringardner.parley.files.sftp.spi;

import java.io.Closeable;
import java.io.IOException;

/** An open remote file, read and written at explicit positions. */
public interface SftpFile extends Closeable {

	/**
	 * Reads up to 'len' bytes at 'position'. May return fewer bytes than
	 * asked for even before the end of the file.
	 *
	 * @return bytes read, or -1 at the end of the file
	 */
	int read(long position, byte[] b, int off, int len) throws IOException;

	/**
	 * Writes all 'len' bytes at 'position', extending the file if needed. A provider whose
	 * library can't write at a position must throw an IOException that says so, never write
	 * the data anywhere else.
	 * <p>
	 * 'len' may be 0: nothing is written, but a file opened read-only, or a provider that can't
	 * write at a position, throws as it would for a real write. A random access stream calls it
	 * so on its first write, so that write is the one that fails, not a later save.
	 */
	void write(long position, byte[] b, int off, int len) throws IOException;

	/** Sets the file's size. */
	void truncate(long size) throws IOException;

	/** False if it was opened read-only. */
	boolean isWritable();

	@Override
	void close() throws IOException;
}
