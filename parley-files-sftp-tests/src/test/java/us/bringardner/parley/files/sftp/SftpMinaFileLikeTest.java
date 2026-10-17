package us.bringardner.parley.files.sftp;

/** SFTP through the Mina library acts like a java.io.File. */
public class SftpMinaFileLikeTest extends AbstractSftpFileLikeTest {

	@Override
	protected String implementation() {
		return "mina";
	}
}
