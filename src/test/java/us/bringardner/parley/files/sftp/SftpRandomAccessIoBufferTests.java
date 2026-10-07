package us.bringardner.parley.files.sftp;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.IRandomAccessIoController;
import us.bringardner.parley.files.test.FileSourceRandomAccessIoBufferTests;

/** The shared random access I/O controller tests over SFTP, with small chunks. */
public class SftpRandomAccessIoBufferTests extends FileSourceRandomAccessIoBufferTests {

	@BeforeAll
	public static void setUp() throws IOException {
		remoteTestFileDirPath = "SftpRandomAccessIoBufferTests";
		SftpFileSourceFactory sftp = TestServer.connect(null);
		//  use a small chunk size to generate lot's of activity
		sftp.setChunkSize(100);
		factory = sftp;
	}

	@Override
	protected IRandomAccessIoController getRandomAccessFileStream(FileSource file) throws IOException {
		return new SftpRandomAccessIoController(file);
	}
}
