package us.bringardner.parley.files.sftp.spi;

import java.io.Closeable;
import java.io.IOException;

/**
 * One authenticated SSH connection. It is safe to use from several threads:
 * each {@link SftpChannel} it opens is independent, and each thread should
 * use its own channel.
 */
public interface SshConnection extends Closeable {

	/** True while the connection is up. */
	boolean isConnected();

	/** Opens a new SFTP channel on this connection. Close it when done. */
	SftpChannel openSftp() throws IOException;

	/**
	 * Runs a command on the server (needs shell access) and waits for it.
	 *
	 * @param timeoutMs how long to wait for the command to finish
	 * @throws IOException if it can't run or doesn't finish in time
	 */
	ExecResult exec(String command, long timeoutMs) throws IOException;

	/**
	 * Starts a command on the server (needs shell access) and returns it while it runs, as a
	 * local program would be: write its input to {@link Process#getOutputStream()} (closing it
	 * ends the input), read {@link Process#getInputStream()} and {@link Process#getErrorStream()},
	 * and {@link Process#waitFor()} its status ({@link SshExitStatus}). {@link Process#destroy()}
	 * closes its channel. The default throws UnsupportedOperationException: a library that can
	 * only run a command to the end ({@link #exec(String, long)}).
	 *
	 * @param command the command line the user's shell on the server runs
	 * @return the running command
	 * @throws IOException if it can't be started (the server may refuse commands, as an account
	 *         limited to SFTP is)
	 * @since 2.0.0
	 */
	default Process start(String command) throws IOException {
		throw new UnsupportedOperationException("This SSH library can't stream a command");
	}

	/** Disconnects. Channels opened on it stop working. */
	@Override
	void close();

	/** Result of {@link SshConnection#exec}. */
	final class ExecResult {
		private final int exitStatus;
		private final String stdout;
		private final String stderr;

		public ExecResult(int exitStatus, String stdout, String stderr) {
			this.exitStatus = exitStatus;
			this.stdout = stdout;
			this.stderr = stderr;
		}

		/** @return the command's exit status */
		public int getExitStatus() {
			return exitStatus;
		}

		/** @return what the command wrote to standard output */
		public String getStdout() {
			return stdout;
		}

		/** @return what the command wrote to standard error */
		public String getStderr() {
			return stderr;
		}
	}
}
