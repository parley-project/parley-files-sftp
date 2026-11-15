package us.bringardner.parley.files.sftp.spi;

import java.util.HashMap;
import java.util.Map;

/**
 * The exit status of a command that ran over SSH, as a local program's would be: the status the
 * server sent, or 128 plus the signal's number when it was ended by a signal (as a POSIX shell
 * reports it), or 255 when the server sent neither (as the ssh command does).
 * @since 2.0.0
 */
public final class SshExitStatus {

	/** The signals SSH names (RFC 4254 6.10), by their POSIX numbers. */
	private static final Map<String, Integer> SIGNALS = new HashMap<>();
	static {
		SIGNALS.put("HUP", 1);
		SIGNALS.put("INT", 2);
		SIGNALS.put("QUIT", 3);
		SIGNALS.put("ILL", 4);
		SIGNALS.put("ABRT", 6);
		SIGNALS.put("FPE", 8);
		SIGNALS.put("KILL", 9);
		SIGNALS.put("USR1", 10);
		SIGNALS.put("SEGV", 11);
		SIGNALS.put("USR2", 12);
		SIGNALS.put("PIPE", 13);
		SIGNALS.put("ALRM", 14);
		SIGNALS.put("TERM", 15);
	}

	/** What a command that ended without a status or a signal the server named reports. */
	public static final int UNKNOWN = 255;

	private SshExitStatus() {
	}

	/**
	 * @param status the exit status the server sent, or null (or negative) if it sent none
	 * @param signal the signal that ended the command, as SSH names it ("TERM"), or null
	 * @return the status a local program would have
	 */
	public static int of(Integer status, String signal) {
		if( status != null && status >= 0 ) {
			return status;
		}
		if( signal != null ) {
			Integer n = SIGNALS.get(signal.startsWith("SIG") ? signal.substring(3) : signal);
			if( n != null ) {
				return 128 + n;
			}
		}
		return UNKNOWN;
	}
}
