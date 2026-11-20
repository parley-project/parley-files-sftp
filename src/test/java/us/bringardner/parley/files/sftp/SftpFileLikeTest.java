package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.test.FileLikeBehaviorTests;

/**
 * An SFTP server, reached through SftpFileSource, acts like a java.io.File. One subclass per SSH
 * library, since each has its own way of doing every call.
 * <p>
 * Connects to the test server: OpenSSH on localhost:22 or the embedded server (see TestServer).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SftpFileLikeTest extends FileLikeBehaviorTests {

	private static final AtomicInteger TREES = new AtomicInteger();

	private SftpFileSourceFactory factory;
	private String base;
	private String tree;

	/** The provider under test. */
	protected String implementation() {
		return TestServer.provider();
	}

	/** Random access writes at positions: not with a provider that says its library can't. */
	@Override
	protected boolean supportsRandomAccess() {
		return TestServer.supports(TestServer.WRITE_AT_POSITION);
	}

	@BeforeAll
	void connect() throws Exception {
		factory = TestServer.connect(implementation());
		base = "SftpFileLikeTest-" + implementation();
		FileSource dir = factory.createFileSource(base);
		if( dir.exists() ) {
			removeAll(dir);
		}
		assertTrue(dir.mkdirs(), "Can't create " + dir);
	}

	@AfterAll
	void disconnect() throws Exception {
		if( factory != null ) {
			removeAll(factory.createFileSource(base));
			factory.disConnect();
		}
	}

	/**
	 * Deletes a tree, making directories writable first: a tree left by an earlier run may hold a
	 * directory that a permission case made read-only, which can't have its children deleted.
	 */
	private static void removeAll(FileSource f) throws java.io.IOException {
		if( f.isDirectory() ) {
			f.setExecutable(true);   // without it nothing in the directory can be reached
			f.setWritable(true);
			FileSource[] kids = f.listFiles();
			if( kids != null ) {
				for(FileSource k : kids) {
					removeAll(k);
				}
			}
		}
		f.delete();
	}

	@Override
	protected void newTree() throws Exception {
		tree = base + "/tree" + TREES.incrementAndGet();
		assertTrue(factory.createFileSource(tree).mkdirs(), "Can't create " + tree);
	}

	@Override
	protected FileSource sourceFor(String relative) throws Exception {
		return factory.createFileSource(tree + "/" + relative);
	}

	/** Not on the embedded Maverick server: it logs in as another user than the files' owner. */
	@Override
	protected boolean permissionsOfExistingPathsAreComparable() {
		return !TestServer.isEmbeddedMaverick();
	}

	/** Not on the embedded Maverick server: its links lead outside its sandbox. */
	@Override
	protected boolean supportsSymbolicLinks() {
		return !TestServer.isEmbeddedMaverick();
	}

	/** Not on the embedded Maverick server: it has no hardlink@openssh.com. */
	@Override
	protected boolean supportsHardLinks() {
		return !TestServer.isEmbeddedMaverick();
	}

	/** A link's own bits are the server's: rwxrwxrwx on Linux, the umask's on macOS (D78). */
	@Override
	protected java.util.Set<java.nio.file.attribute.PosixFilePermission> ownPermissionsOfALink(FileSource link) throws Exception {
		String own = factory.lstat(link.getAbsolutePath()).getPermissionsString();
		java.util.Set<java.nio.file.attribute.PosixFilePermission> bits = new java.util.HashSet<>();
		java.nio.file.attribute.PosixFilePermission[] all = java.nio.file.attribute.PosixFilePermission.values();   // OWNER_READ ... OTHERS_EXECUTE
		for (int i = 0; i < all.length; i++) {
			if( own.charAt(i+1) != '-' ) {
				bits.add(all[i]);
			}
		}
		return bits;
	}

	/** SFTP version 3 stores a modified time as whole seconds. */
	@Override
	protected long modifiedTimeResolutionMillis() {
		return 1000;
	}
}
