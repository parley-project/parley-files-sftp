package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import us.bringardner.parley.files.FileSource;

/**
 * delete() deletes what the listing said was at the path at once (one round trip), even when what
 * it knew is no longer fresh, and is still right when the path has changed since: another program
 * ("elsewhere") made a file into a folder, a folder into a file, or deleted it.
 */
public class SftpDeleteTest {

	static final String DIR = "SftpDeleteTest";
	/** Connected with the provider under test (TestServer.provider()). */
	static SftpFileSourceFactory connected;
	/** A connection of its own, as another program would have. */
	static SftpFileSourceFactory elsewhere;

	@BeforeAll
	static void setUp() throws IOException {
		connected = SftpRandomAccessTest.connect(TestServer.provider());
		elsewhere = TestServer.connectSeparately(TestServer.provider());
		FileSource dir = connected.createFileSource(DIR);
		if( !dir.exists()) {
			assertTrue(dir.mkdirs());
		}
	}

	@AfterAll
	static void tearDown() throws IOException {
		if( elsewhere != null ) {
			elsewhere.disConnect();
		}
		if( connected != null ) {
			SftpRandomAccessTest.deleteAll(connected.createFileSource(DIR));
			connected.disConnect();
		}
	}

	/** A fresh, empty directory for one test. */
	static String freshDir(String impl, String name) throws IOException {
		String path = DIR+"/"+impl+"-"+name;
		FileSource d = connected.createFileSource(path);
		if( d.exists()) {
			SftpRandomAccessTest.deleteAll(d);
		}
		assertTrue(d.mkdirs());
		return d.getAbsolutePath();
	}

	/** The child called name, as the listing of dir made it. */
	static FileSource listed(String dir, String name) throws IOException {
		for(FileSource f : connected.createFileSource(dir).listFiles()) {
			if( f.getName().equals(name) ) {
				return f;
			}
		}
		throw new AssertionError(name+" not listed in "+dir);
	}

	/** Makes everything known stale: any change made through the factory does. */
	static void somethingChanged(String dir) throws IOException {
		FileSource f = connected.createFileSource(dir+"/other.txt");
		SftpCacheTest.create(f, 1);
		assertTrue(f.delete());
	}

	@ParameterizedTest
	@MethodSource("us.bringardner.parley.files.sftp.TestServer#providers")
	void filesAndFoldersAsListed(String impl) throws IOException {
		String d = freshDir(impl, "listed");
		SftpCacheTest.create(connected.createFileSource(d+"/a.txt"), 10);
		assertTrue(connected.createFileSource(d+"/sub").mkdir());
		FileSource a = listed(d, "a.txt");
		FileSource sub = listed(d, "sub");
		somethingChanged(d);
		assertTrue(a.delete());
		assertTrue(sub.delete());
		assertFalse(elsewhere.createFileSource(d+"/a.txt").exists());
		assertFalse(elsewhere.createFileSource(d+"/sub").exists());
		assertEquals(0, connected.createFileSource(d).listFiles().length);
	}

	@ParameterizedTest
	@MethodSource("us.bringardner.parley.files.sftp.TestServer#providers")
	void aFileThatIsNowAFolderWithFilesInIt(String impl) throws IOException {
		String d = freshDir(impl, "now-full-folder");
		SftpCacheTest.create(connected.createFileSource(d+"/x"), 10);
		FileSource x = listed(d, "x");
		assertTrue(elsewhere.createFileSource(d+"/x").delete());
		assertTrue(elsewhere.createFileSource(d+"/x").mkdir());
		SftpCacheTest.create(elsewhere.createFileSource(d+"/x/keep.txt"), 1);
		// as java.io.File: a folder that isn't empty isn't deleted, and nothing in it is lost
		assertFalse(x.delete());
		assertTrue(elsewhere.createFileSource(d+"/x/keep.txt").exists());
	}

	@ParameterizedTest
	@MethodSource("us.bringardner.parley.files.sftp.TestServer#providers")
	void aFileThatIsNowAnEmptyFolder(String impl) throws IOException {
		String d = freshDir(impl, "now-folder");
		SftpCacheTest.create(connected.createFileSource(d+"/x"), 10);
		FileSource x = listed(d, "x");
		assertTrue(elsewhere.createFileSource(d+"/x").delete());
		assertTrue(elsewhere.createFileSource(d+"/x").mkdir());
		assertTrue(x.delete());
		assertFalse(elsewhere.createFileSource(d+"/x").exists());
	}

	@ParameterizedTest
	@MethodSource("us.bringardner.parley.files.sftp.TestServer#providers")
	void aFolderThatIsNowAFile(String impl) throws IOException {
		String d = freshDir(impl, "now-file");
		assertTrue(connected.createFileSource(d+"/x").mkdir());
		FileSource x = listed(d, "x");
		assertTrue(elsewhere.createFileSource(d+"/x").delete());
		SftpCacheTest.create(elsewhere.createFileSource(d+"/x"), 10);
		assertTrue(x.delete());
		assertFalse(elsewhere.createFileSource(d+"/x").exists());
	}

	@ParameterizedTest
	@MethodSource("us.bringardner.parley.files.sftp.TestServer#providers")
	void aFileDeletedElsewhere(String impl) throws IOException {
		String d = freshDir(impl, "gone");
		SftpCacheTest.create(connected.createFileSource(d+"/x"), 10);
		FileSource x = listed(d, "x");
		assertTrue(elsewhere.createFileSource(d+"/x").delete());
		// as java.io.File: nothing to delete is false, not an exception
		assertFalse(x.delete());
	}
}
