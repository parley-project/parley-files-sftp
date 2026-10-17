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

	/** SFTP version 3 stores a modified time as whole seconds. */
	@Override
	protected long modifiedTimeResolutionMillis() {
		return 1000;
	}
}
