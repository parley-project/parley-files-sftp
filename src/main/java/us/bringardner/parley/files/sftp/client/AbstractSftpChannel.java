package us.bringardner.parley.files.sftp.client;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.NoSuchFileException;
import java.util.Objects;

/**
 * What the {@link SftpChannel} implementations on a library with its own exception types share:
 * wrapping streams so their errors become the types SftpChannel promises, replacing a file where the
 * server has no atomic replace, and creating a file that must not exist yet.
 */
public abstract class AbstractSftpChannel implements SftpChannel {

	/** A call that throws IOException. */
	@FunctionalInterface
	protected interface IoCall<T> {
		T run() throws IOException;
	}

	/** Runs a call, turning the library's exceptions into the IOException types SftpChannel promises. */
	protected abstract <T> T guard(String path, IoCall<T> call) throws IOException;

	/** Make a file exist with exactly one exclusive open (SSH_FXF_EXCL); fails if it exists. */
	protected abstract void createExclusive(String path) throws IOException;

	/** {@code to} can't be replaced by a rename on this server: delete it first, then rename. */
	protected final void replaceByRemoving(String from, String to) throws IOException {
		try {
			lstat(to);
			remove(to);
		} catch (NoSuchFileException e) {
			// nothing in the way
		}
		rename(from, to);
	}

	/**
	 * One exclusive open, so the check and the create can't be split by another program.
	 *
	 * @return false if the file already exists
	 */
	@Override
	public boolean createNew(String path) throws IOException {
		try {
			createExclusive(path);
			return true;
		} catch (IOException e) {
			// SFTP v3 has no "already exists" status; OpenSSH answers SSH_FX_FAILURE
			try {
				lstat(path);
			} catch (IOException e2) {
				e.addSuppressed(e2);
				throw e;
			}
			return false;
		}
	}

	/** {@code in} with its errors turned into the types SftpChannel promises. */
	protected final InputStream guardedInput(String path, InputStream in) {
		return new FilterInputStream(in) {
			@Override
			public int read() throws IOException {
				return guard(path, () -> in.read());
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				Objects.checkFromIndexSize(off, len, b.length);
				if (len == 0) {
					return 0;
				}
				return guard(path, () -> in.read(b, off, len));
			}

			@Override
			public long skip(long n) throws IOException {
				return guard(path, () -> in.skip(n));
			}

			@Override
			public void close() throws IOException {
				guard(path, () -> {
					in.close();
					return null;
				});
			}
		};
	}

	/** {@code out} with its errors turned into the types SftpChannel promises. */
	protected final OutputStream guardedOutput(String path, OutputStream out) {
		return new FilterOutputStream(out) {
			@Override
			public void write(int b) throws IOException {
				guard(path, () -> {
					out.write(b);
					return null;
				});
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				guard(path, () -> {
					out.write(b, off, len);
					return null;
				});
			}

			@Override
			public void flush() throws IOException {
				guard(path, () -> {
					out.flush();
					return null;
				});
			}

			@Override
			public void close() throws IOException {
				guard(path, () -> {
					out.close();
					return null;
				});
			}
		};
	}
}
