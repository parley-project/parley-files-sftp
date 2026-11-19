package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.files.FileSource;

/**
 * A symbolic link keeps the exact text it was made with: createSymbolicLink(link, text) and getLinkText(). Runs
 * with the provider under test and needs OpenSSH (the embedded server turns a link's text into an absolute path of
 * the host), so it is skipped on the embedded server.
 */
public class SftpLinkTextTest {

	static final String DIR = "SftpLinkTextTest";

	/** Connected with the provider under test (TestServer.provider()). */
	static SftpFileSourceFactory connected;

	@BeforeAll
	static void setUp() throws IOException {
		TestServer.assumeOpenSsh();
		connected = TestServer.connect(TestServer.provider());
		FileSource dir = connected.createFileSource(DIR);
		if( dir.exists()) {
			SftpRandomAccessTest.deleteAll(dir);
		}
		assertTrue(dir.mkdirs(), "Can't create "+dir);
	}

	@AfterAll
	static void tearDown() throws IOException {
		if( connected != null ) {
			try {
				SftpRandomAccessTest.deleteAll(connected.createFileSource(DIR));
			} finally {
				connected.disConnect();
			}
		}
	}

	@Test
	void linksKeepTheirText() throws IOException {
		String d = connected.createFileSource(DIR).getAbsolutePath();
		SftpRegressionTest.write(connected.createFileSource(d+"/real.txt"), "data".getBytes());
		assertTrue(connected.createFileSource(d+"/folder").mkdirs());

		String[] texts = {"real.txt", "../"+DIR+"/real.txt", d+"/real.txt", "folder", "no/such/place"};
		SftpFileSourceFactory other = TestServer.connectSeparately(TestServer.provider());
		try {
			for (int i = 0; i < texts.length; i++) {
				String path = d+"/link"+i;
				FileSource link = connected.createSymbolicLink(connected.createFileSource(path), texts[i]);
				assertEquals(texts[i], link.getLinkText(), "the link just made");
				assertTrue(connected.lstat(path).isLink(), "a link on the server");
				// a connection that knows nothing of it reads the same text
				assertEquals(texts[i], other.createFileSource(path).getLinkText(), "read by another connection");
			}
			// the link follows its text: relative text is relative to the link's folder
			assertEquals(4, connected.createFileSource(d+"/link0").length());
			assertEquals(4, connected.createFileSource(d+"/link1").length());
			assertEquals(4, connected.createFileSource(d+"/link2").length());
			assertTrue(connected.createFileSource(d+"/link3").isDirectory());

			// not links
			assertNull(connected.createFileSource(d+"/real.txt").getLinkText());
			assertNull(connected.createFileSource(d+"/folder").getLinkText());
			assertNull(connected.createFileSource(d+"/missing").getLinkText());

			// a name that is taken
			assertThrows(IOException.class, () -> connected.createSymbolicLink(connected.createFileSource(d+"/link0"), "other"));
		} finally {
			other.disConnect();
		}
	}
}
