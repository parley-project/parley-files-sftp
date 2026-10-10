package us.bringardner.parley.files.sftp;

/** SFTP through the Parley library acts like a java.io.File. */
public class SftpParleyFileLikeTest extends AbstractSftpFileLikeTest {

	@Override
	protected String implementation() {
		return "parley";
	}
}
