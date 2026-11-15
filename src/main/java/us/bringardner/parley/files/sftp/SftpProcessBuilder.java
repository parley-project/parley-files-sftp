package us.bringardner.parley.files.sftp;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceProcessBuilder;

/**
 * Starts programs on the SFTP server, over an SSH exec channel, when the connection allows it
 * (SftpFileSourceFactory.PROP_RUN_COMMANDS). The command, directory and environment become one
 * command line for the user's shell on the server:
 * <pre>cd 'DIRECTORY' &amp;&amp; exec env 'NAME=VALUE' 'PROGRAM' 'ARG' 2&gt;&amp;1</pre>
 * with every word quoted, so it needs a POSIX-style shell there (sh, bash, zsh, ksh); Windows'
 * cmd.exe doesn't read it. The program is found on the server's PATH. The
 * environment starts empty: its variables are set on top of the one the server gives the
 * program. A null directory is the user's home directory.
 * @since 2.0.0
 */
final class SftpProcessBuilder extends FileSourceProcessBuilder {

	private final SftpFileSourceFactory factory;

	SftpProcessBuilder(SftpFileSourceFactory factory, List<String> command) {
		super(factory, command, new HashMap<>());
		this.factory = factory;
	}

	@Override
	protected Process launch(List<String> command, FileSource directory, Map<String, String> environment,
			boolean redirectErrorStream) throws IOException {
		return factory.startCommand(commandLine(command, directory == null ? null : directory.getAbsolutePath(),
				environment, redirectErrorStream));
	}

	/** The command line the server's shell runs (see the class description). */
	static String commandLine(List<String> command, String directory, Map<String, String> environment,
			boolean redirectErrorStream) {
		StringBuilder line = new StringBuilder();
		if( directory != null ) {
			line.append("cd ").append(quote(directory)).append(" && ");
		}
		line.append("exec ");
		if( !environment.isEmpty()) {
			line.append("env ");
			// (sorted: the same builder gives the same line)
			for (Map.Entry<String, String> e : new TreeMap<>(environment).entrySet()) {
				line.append(quote(e.getKey()+"="+e.getValue())).append(' ');
			}
		}
		for (int i = 0; i < command.size(); i++) {
			if( i > 0 ) {
				line.append(' ');
			}
			line.append(quote(command.get(i)));
		}
		if( redirectErrorStream ) {
			line.append(" 2>&1");
		}
		return line.toString();
	}

	/** s as one word for a POSIX shell: in single quotes, each ' written as '\'' */
	static String quote(String s) {
		return "'"+s.replace("'", "'\\''")+"'";
	}
}
