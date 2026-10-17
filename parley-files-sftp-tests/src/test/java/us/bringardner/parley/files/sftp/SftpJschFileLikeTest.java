package us.bringardner.parley.files.sftp;

/** SFTP through the Jsch library acts like a java.io.File. */
public class SftpJschFileLikeTest extends AbstractSftpFileLikeTest {

	@Override
	protected String implementation() {
		return "jsch";
	}

	/** The shared random access tests write at positions, which the jsch provider can't (it throws). */
	@Override
	protected boolean supportsRandomAccess() {
		return false;
	}
}
