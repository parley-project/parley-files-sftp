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
}
