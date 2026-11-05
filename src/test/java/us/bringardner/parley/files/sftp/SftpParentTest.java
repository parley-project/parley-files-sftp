package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.files.FileSource;

/** A path's parent, as java.io.File's: no server needed, a path is only taken apart. */
public class SftpParentTest {

	private static FileSource file(String path) {
		return new SftpFileSource(new SftpFileSourceFactory(), path);
	}

	@Test
	public void topLevelFileHasTheRootAsParent() throws Exception {
		FileSource f = file("/a");
		assertEquals("/", f.getParent());
		assertEquals("/", f.getParentFile().getAbsolutePath());
		assertNull(f.getParentFile().getParent(), "the root has no parent");
	}

	@Test
	public void walkingUpReachesTheRoot() throws Exception {
		FileSource f = file("/a/b/c");
		assertEquals("/a/b", f.getParent());
		FileSource p = f;
		int steps = 0;
		while( p.getParentFile() != null ) {
			p = p.getParentFile();
			steps++;
		}
		assertEquals("/", p.getAbsolutePath());
		assertEquals(3, steps);
	}

	@Test
	public void rootAndRelativePathsHaveNone() throws Exception {
		assertNull(file("/").getParent());
		assertNull(file("/").getParentFile());
		assertNull(file("a").getParent());
	}
}
