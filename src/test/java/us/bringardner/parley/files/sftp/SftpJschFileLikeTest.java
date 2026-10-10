package us.bringardner.parley.files.sftp;

/** SFTP through the Jsch library acts like a java.io.File. */
public class SftpJschFileLikeTest extends AbstractSftpFileLikeTest {

	@Override
	protected String implementation() {
		return "jsch";
	}
}
