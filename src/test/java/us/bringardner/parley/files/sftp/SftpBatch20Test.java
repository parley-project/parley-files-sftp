package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.FileSourceRandomAccessStream;
import us.bringardner.parley.files.IRandomAccessStream;
import us.bringardner.parley.files.sftp.spi.SftpChannel;

/**
 * Batch 20: random access that can write goes through MINA even when the
 * factory uses JSch, because JSch can only write at "the file's size + n"
 * and a size change by anyone else in between put the data in the wrong
 * place. Read-only random access stays on JSch.
 * <p>
 * Connects to the test server: OpenSSH on localhost:22 or the embedded
 * server (see TestServer).
 */
public class SftpBatch20Test {

	static final String DIR = "SftpBatch20Test";
	static final AtomicInteger KEYS = new AtomicInteger();

	/** Connected with the provider under test (TestServer.provider()). */
	static SftpFileSourceFactory connected;

	@BeforeAll
	static void setUp() throws IOException {
		connected = TestServer.connect(TestServer.provider());
		FileSource dir = connected.createFileSource(DIR);
		if( !dir.exists()) {
			assertTrue(dir.mkdirs(), "Can't create "+dir);
		}
	}

	@AfterAll
	static void tearDown() throws IOException {
		if( connected != null ) {
			SftpRandomAccessTest.deleteAll(connected.createFileSource(DIR));
			connected.disConnect();
		}
	}

	static SftpFileSourceFactory factory(String impl) {
		return connected;
	}

	/** A connected factory with a connection of its own; 'key' null makes one up. */
	static SftpFileSourceFactory own(String impl, String key) throws IOException {
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		Properties p = TestServer.properties(impl, TestServer.port());
		p.setProperty(SftpFileSourceFactory.PROP_SESSION_KEY,
				key != null ? key : "batch20-"+impl+"-"+KEYS.incrementAndGet()+"-"+System.nanoTime());
		f.setConnectionProperties(p);
		assertTrue(f.connect());
		return f;
	}

	/** Writes a file of 'size' zeros and returns its absolute path. */
	static String zeros(String impl, String name, int size) throws IOException {
		return SftpRegressionTest.write(connected.createFileSource(DIR+"/"+impl+"-"+name), new byte[size]).getAbsolutePath();
	}

	/**
	 * The bug: while another program keeps changing the file's size, JSch
	 * random-access writes landed size-change bytes away from where they were
	 * asked to go (or failed). Every write here is read back from the server.
	 */
	@ParameterizedTest
	@MethodSource("us.bringardner.parley.files.sftp.TestServer#providers")
	void writesLandWhereAskedWhileTheSizeChanges(String impl) throws Exception {
		TestServer.assumeSupported(TestServer.WRITE_AT_POSITION);
		String path = zeros(impl, "race.bin", 4096);
		AtomicBoolean stop = new AtomicBoolean();
		AtomicReference<Throwable> otherFailed = new AtomicReference<>();
		// the other program: grows the file to 8 KB and cuts it back to 4 KB, over and over
		Thread other = new Thread(() -> {
			try (SftpChannel c = connected.openSftp()) {
				while( !stop.get()) {
					c.truncate(path, 8192);
					c.truncate(path, 4096);
				}
			} catch (Throwable e) {
				otherFailed.set(e);
			}
		});
		other.setDaemon(true);
		other.start();

		FileSource f = factory(impl).createFileSource(path);
		SftpRandomAccessIoController io = new SftpRandomAccessIoController(f, "rw");
		try (IRandomAccessStream r = new FileSourceRandomAccessStream(io, "rw")) {
			for (int i = 0; i < 200; i++) {
				byte[] pattern = new byte[16];
				Arrays.fill(pattern, (byte) (i % 200 + 1));
				r.seek(100);
				r.write(pattern);
				io.save();   // to the server now
				byte[] back;
				try (InputStream in = connected.createFileSource(path).getInputStream(100)) {
					back = in.readNBytes(16);
				}
				assertArrayEquals(pattern, back, "write "+i+" isn't at position 100");
			}
		} finally {
			stop.set(true);
			other.join(10_000);
		}
		assertNull(otherFailed.get());
	}

	static int readAt(String path, long pos) throws IOException {
		try (InputStream in = connected.createFileSource(path).getInputStream(pos)) {
			return in.read();
		}
	}

}
