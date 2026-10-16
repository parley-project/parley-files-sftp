package us.bringardner.parley.files.sftp;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.files.test.AbstractTestClass;

/** The shared FileSource tests over SFTP (see TestServer for the server). */
public class SftpFileSourceTest extends AbstractTestClass {

	@BeforeAll
	public static void setUp() throws IOException {
		localTestFileDirPath = testFilesDir(SftpFileSourceTest.class);
		localCacheDirPath = scratchDir("SftpFileSourceTestCache");
		remoteTestFileDirPath = "SftpFileSourceTest";
		factory = TestServer.connect(null);
	}

	/**
	 * getCanonicalPath() looks at every directory from the root down, which
	 * the embedded server's sandbox refuses above its home directory.
	 */
	@Override
	@Test
	public void testIsChildOfMineContract() throws IOException {
		TestServer.assumeOpenSsh();
		super.testIsChildOfMineContract();
	}

	@Override
	@org.junit.jupiter.api.Test
	@org.junit.jupiter.api.condition.DisabledOnOs(value = org.junit.jupiter.api.condition.OS.WINDOWS, disabledReason = "POSIX permission bits: the embedded server serves a Windows directory")
	public void testPermissions() throws java.io.IOException {
		super.testPermissions();
	}
}
