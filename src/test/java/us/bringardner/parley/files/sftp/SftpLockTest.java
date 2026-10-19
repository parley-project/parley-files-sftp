package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.lock.LockScope;
import us.bringardner.parley.files.test.AbstractLockTests;

/**
 * The lock contract over SFTP, with the provider under test. SFTP (version 3, what servers offer) has
 * no locks, so SftpFileSource keeps the default: locks within this JVM (PROCESS); the cross-process
 * cases are skipped.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SftpLockTest extends AbstractLockTests {

	private static final AtomicInteger DIRS = new AtomicInteger();
	private SftpFileSourceFactory factory;
	private String base;

	@BeforeAll
	void connect() throws Exception {
		factory = TestServer.connect(TestServer.provider());
		base = "SftpLockTest-" + TestServer.provider() + "-" + System.nanoTime();
		assertTrue(factory.createFileSource(base).mkdirs(), "Can't create " + base);
	}

	@AfterAll
	void disconnect() throws Exception {
		if( factory != null ) {
			removeAll(factory.createFileSource(base));
			factory.disConnect();
		}
	}

	private static void removeAll(FileSource f) throws java.io.IOException {
		FileSource[] kids = f.isDirectory() ? f.listFiles() : null;
		if( kids != null ) {
			for (FileSource k : kids) {
				removeAll(k);
			}
		}
		f.delete();
	}

	@Override
	protected FileSource newLockDirectory() throws Exception {
		FileSource d = factory.createFileSource(base + "/locks" + DIRS.incrementAndGet());
		assertTrue(d.mkdirs(), "Can't create " + d);
		return d;
	}

	@Test
	public void sftpLocksStayInThisJvm() {
		assertEquals(LockScope.PROCESS, dir.getLockScope());
	}
}
