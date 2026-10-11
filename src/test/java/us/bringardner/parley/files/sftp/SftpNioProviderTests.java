package us.bringardner.parley.files.sftp;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;

import us.bringardner.parley.files.test.AbstractNioProviderTests;

/** The shared java.nio.file provider tests over SFTP. */
public class SftpNioProviderTests extends AbstractNioProviderTests {

	@BeforeAll
	public static void setUp() throws IOException {
		localTestFileDirPath = "TestFiles";
		localCacheDirPath = "target/SftpNioProviderTestsCache";
		remoteTestFileDirPath = "SftpNioProviderTests";
		factory = TestServer.connect(null);
	}

	@Override
	@org.junit.jupiter.api.Test
	@org.junit.jupiter.api.condition.DisabledOnOs(value = org.junit.jupiter.api.condition.OS.WINDOWS, disabledReason = "POSIX permission bits: the embedded server serves a Windows directory")
	public void testFileAttributes() throws Exception {
		super.testFileAttributes();
	}

	@Override
	@org.junit.jupiter.api.Test
	@org.junit.jupiter.api.condition.DisabledOnOs(value = org.junit.jupiter.api.condition.OS.WINDOWS, disabledReason = "POSIX permission bits: the embedded server serves a Windows directory")
	public void testCreateDir() throws Exception {
		super.testCreateDir();
	}
}
