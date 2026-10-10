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
public abstract class AbstractSftpFileLikeTest extends FileLikeBehaviorTests {

	private static final AtomicInteger TREES = new AtomicInteger();

	private SftpFileSourceFactory factory;
	private String base;
	private String tree;

	/** "jsch", "mina" or "parley" */
	protected abstract String implementation();

	@BeforeAll
	void connect() throws Exception {
		factory = TestServer.connect(implementation());
		base = "SftpFileLikeTest-" + implementation();
		FileSource dir = factory.createFileSource(base);
		if( dir.exists() ) {
			SftpRandomAccessTest.deleteAll(dir);
		}
		assertTrue(dir.mkdirs(), "Can't create " + dir);
	}

	@AfterAll
	void disconnect() throws Exception {
		if( factory != null ) {
			SftpRandomAccessTest.deleteAll(factory.createFileSource(base));
			factory.disConnect();
		}
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
}
