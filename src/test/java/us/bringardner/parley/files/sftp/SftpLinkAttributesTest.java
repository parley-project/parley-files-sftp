package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.files.FileSource;

/**
 * A symbolic link answers lastModified, lastAccessTime and getOwner with its own values
 * (lstat), not its target's (D78).
 */
public class SftpLinkAttributesTest {

	static final String DIR = "SftpLinkAttributesTest";
	static final long OLD = 1_500_000_000_000L;

	static SftpFileSourceFactory connected;

	@BeforeAll
	static void setUp() throws IOException {
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
				// by name: a listing doesn't show a link that points at nothing
				FileSource dir = connected.createFileSource(DIR);
				for (String name : new String[] {"ln", "dangling", "real.txt"}) {
					dir.getChild(name).delete();
				}
				dir.delete();
			} finally {
				connected.disConnect();
			}
		}
	}

	@Test
	void linkAnswersItsOwnTimesAndOwner() throws IOException {
		String d = connected.createFileSource(DIR).getAbsolutePath();
		FileSource target = connected.createFileSource(d+"/real.txt");
		SftpRegressionTest.write(target, "data".getBytes());
		assertTrue(target.setLastModifiedTime(OLD));
		assertTrue(target.setLastAccessTime(OLD));
		FileSource link = connected.createSymbolicLink(connected.createFileSource(d+"/ln"), "real.txt");

		assertEquals(OLD, target.lastModified());
		assertNotEquals(OLD, link.lastModified(), "the link's own modified time");
		assertNotEquals(OLD, link.lastAccessTime(), "the link's own access time");
		assertEquals(connected.lstat(d+"/ln").getMTime()*1000L, link.lastModified());
		assertEquals(connected.lstat(d+"/ln").getATime()*1000L, link.lastAccessTime());
		long uid = connected.lstat(d+"/ln").getUId();
		String expected = connected.userName(uid);
		assertEquals(expected == null ? ""+uid : expected, link.getOwner().getName());
	}

	@Test
	void linkToNothingHasItsOwnTime() throws IOException {
		String d = connected.createFileSource(DIR).getAbsolutePath();
		FileSource link = connected.createSymbolicLink(connected.createFileSource(d+"/dangling"), "no/such/place");
		assertEquals(connected.lstat(d+"/dangling").getMTime()*1000L, link.lastModified());
		assertTrue(link.lastModified() > OLD);
	}
}
