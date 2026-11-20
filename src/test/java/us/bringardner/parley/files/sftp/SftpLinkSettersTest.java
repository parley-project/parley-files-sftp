package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.files.FileSource;

/**
 * The times of a symbolic link are set on the link, not on what it leads to, with the server's
 * lsetstat@openssh.com (D78). Against a MINA server, which has it; a provider that can't send it
 * leaves the link alone rather than change its file.
 */
public class SftpLinkSettersTest {

	private static final long TARGET_TIME = 1_500_000_000L;
	private static final long LINK_TIME = 1_600_000_000L;
	private static final long NEW_TIME = 1_700_000_000L;

	private static Path home;
	private static SftpFileSourceFactory factory;

	@BeforeAll
	static void setUp() throws IOException {
		String provider = TestServer.provider();
		home = Files.createTempDirectory("sftp-link-setters");
		Path real = home.resolve("real.txt");
		Files.write(real, new byte[] {1, 2, 3});
		Files.setLastModifiedTime(real, FileTime.fromMillis(TARGET_TIME * 1000));
		Path link = home.resolve("ln");
		Files.createSymbolicLink(link, real.getFileName());
		BasicFileAttributeView v = Files.getFileAttributeView(link, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
		v.setTimes(FileTime.fromMillis(LINK_TIME * 1000), FileTime.fromMillis(LINK_TIME * 1000), null);
		int port = EmbeddedMinaServer.start(home.toFile(), TestServer.USER, TestServer.PASSWORD);
		Properties p = TestServer.properties(provider, port);
		factory = new SftpFileSourceFactory();
		factory.setConnectionProperties(p);
		assertTrue(factory.connect());
	}

	@AfterAll
	static void tearDown() throws IOException {
		try {
			if( factory != null ) {
				factory.disConnect();
			}
		} finally {
			if( home != null ) {
				try (Stream<Path> paths = Files.walk(home)) {
					for (Path p : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
						Files.delete(p);
					}
				}
			}
		}
	}

	private static long linkTime() throws IOException {
		return Files.getLastModifiedTime(home.resolve("ln"), LinkOption.NOFOLLOW_LINKS).toMillis() / 1000;
	}

	private static long targetTime() throws IOException {
		return Files.getLastModifiedTime(home.resolve("real.txt")).toMillis() / 1000;
	}

	private static String sameCallHere() {
		try {
			Path ln = home.resolve("ln");
			String r = "home=" + home + " real=" + home.toRealPath();
			Files.setAttribute(ln, "basic:lastModifiedTime", FileTime.fromMillis(NEW_TIME * 1000), LinkOption.NOFOLLOW_LINKS);
			Files.setAttribute(ln, "basic:lastAccessTime", FileTime.fromMillis(NEW_TIME * 1000), LinkOption.NOFOLLOW_LINKS);
			// that worked, so put the link back as the test expects it
			BasicFileAttributeView v = Files.getFileAttributeView(ln, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
			v.setTimes(FileTime.fromMillis(LINK_TIME * 1000), FileTime.fromMillis(LINK_TIME * 1000), null);
			return r + " worked";
		} catch(Exception e) {
			return e.toString();
		}
	}

	@Test
	void timesOfALinkAreSetOnTheLinkWhereTheServerCan() throws IOException {
		boolean can = factory.sftp(c -> c.canSetLinkAttributes());
		FileSource link = factory.createFileSource("/ln");
		assertEquals(LINK_TIME * 1000, link.lastModified());

		boolean modified;
		boolean accessed;
		try {
			modified = link.setLastModifiedTime(NEW_TIME * 1000);
			accessed = link.setLastAccessTime(NEW_TIME * 1000);
		} catch(IOException e) {
			// the server only answers SSH_FX_FAILURE; do here what it does there, to see what fails
			throw new AssertionError("the server failed; the same no-follow call here: " + sameCallHere(), e);
		}
		assertEquals(TARGET_TIME, targetTime(), "what the link leads to is never changed");
		if( can ) {
			assertTrue(modified);
			assertTrue(accessed);
			assertEquals(NEW_TIME, linkTime());
			assertEquals(NEW_TIME * 1000, link.lastModified());
			assertEquals(NEW_TIME * 1000, link.lastAccessTime());
		} else {
			assertFalse(modified);
			assertFalse(accessed);
			assertEquals(LINK_TIME, linkTime(), "left as it was");
		}
	}

	@Test
	void aFileIsStillSetWithSetstat() throws IOException {
		FileSource file = factory.createFileSource("/real.txt");
		assertTrue(file.setLastModifiedTime(TARGET_TIME * 1000 + 5000));
		assertNotEquals(TARGET_TIME, targetTime());
		assertTrue(file.setLastModifiedTime(TARGET_TIME * 1000));
		assertEquals(TARGET_TIME, targetTime());
	}
}
