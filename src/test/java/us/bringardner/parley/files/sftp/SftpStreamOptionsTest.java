package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import us.bringardner.parley.files.FileSource;
import us.bringardner.parley.files.IRandomAccessStream;
import us.bringardner.parley.files.ISeekableInputStream;
import us.bringardner.parley.files.StreamOption;
import us.bringardner.parley.files.StreamOptions;

/**
 * Streams opened with their own sizes, with every SSH library: BUFFER_SIZE for the sequential
 * streams, CHUNK_SIZE for seekable and random access. The data is the same whatever the sizes
 * are, a stream that was opened without options behaves as it did, and one stream's chunk size
 * doesn't move when the factory's does.
 * <p>
 * Connects to the test server: OpenSSH on localhost:22 or the embedded server (see TestServer).
 */
public class SftpStreamOptionsTest {

	static final String DIR = "SftpStreamOptionsTest";
	/** Several chunks of the sizes used below, and not a multiple of any. */
	static final int SIZE = 300_007;

	static SftpFileSourceFactory jsch;
	static SftpFileSourceFactory mina;
	static SftpFileSourceFactory bjl;

	@BeforeAll
	static void setUp() throws Exception {
		jsch = TestServer.connect("jsch");
		mina = TestServer.connect("mina");
		bjl = TestServer.connect("parley");
		FileSource dir = jsch.createFileSource(DIR);
		if( !dir.exists()) {
			assertTrue(dir.mkdirs(), "Can't create "+dir);
		}
	}

	@AfterAll
	static void tearDown() throws Exception {
		if( mina != null ) {
			mina.disConnect();
		}
		if( bjl != null ) {
			bjl.disConnect();
		}
		if( jsch != null ) {
			SftpRandomAccessTest.deleteAll(jsch.createFileSource(DIR));
			jsch.disConnect();
		}
	}

	private static SftpFileSourceFactory factory(String implementation) {
		return implementation.equals("jsch") ? jsch : implementation.equals("mina") ? mina : bjl;
	}

	private static FileSource written(String implementation, String name, byte[] data) throws Exception {
		FileSource f = factory(implementation).createFileSource(DIR).getChild(implementation + "-" + name);
		try (OutputStream out = f.getOutputStream()) {
			out.write(data);
		}
		return f;
	}

	@ParameterizedTest
	@ValueSource(strings = {"jsch", "mina", "parley"})
	void saysWhatItUnderstandsAndWhatItUsesByDefault(String implementation) throws Exception {
		SftpFileSourceFactory f = factory(implementation);
		FileSource file = f.createFileSource(DIR).getChild(implementation + "-defaults");
		assertEquals(Set.of(StreamOption.BUFFER_SIZE, StreamOption.CHUNK_SIZE), file.supportedStreamOptions());
		StreamOptions defaults = file.getStreamDefaults();
		assertEquals(f.getBufferSize(), defaults.bufferSize());
		assertEquals(f.getBufferSize(), defaults.chunkSize());
	}

	@ParameterizedTest
	@ValueSource(strings = {"jsch", "mina", "parley"})
	void sequentialStreamsWithABufferOfTheirOwn(String implementation) throws Exception {
		byte[] data = SftpReadAheadTest.random(SIZE, 11);
		FileSource f = factory(implementation).createFileSource(DIR).getChild(implementation + "-sequential");

		try (OutputStream out = f.getOutputStream(false, StreamOptions.buffer(100_000))) {
			// bytes one at a time for the first part, then blocks: all of it goes through the buffer
			for(int i = 0; i < 50_000; i++) {
				out.write(data[i] & 0xff);
			}
			out.write(data, 50_000, SIZE - 50_000);
		}
		assertEquals(SIZE, f.length());

		try (InputStream in = f.getInputStream(StreamOptions.buffer(64 * 1024))) {
			assertTrue(in.markSupported(), "a buffered stream: " + in.getClass().getName());
			assertArrayEquals(data, in.readAllBytes());
		}
		try (InputStream in = f.getInputStream(250_000, StreamOptions.buffer(8 * 1024))) {
			assertArrayEquals(Arrays.copyOfRange(data, 250_000, SIZE), in.readAllBytes());
		}
		// append, buffered
		byte[] more = SftpReadAheadTest.random(40_000, 12);
		try (OutputStream out = f.getOutputStream(true, StreamOptions.buffer(16 * 1024))) {
			out.write(more);
		}
		byte[] all = Arrays.copyOf(data, SIZE + more.length);
		System.arraycopy(more, 0, all, SIZE, more.length);
		try (InputStream in = f.getInputStream()) {
			assertArrayEquals(all, in.readAllBytes());
		}
		// a size that is too small is kept to the minimum, not refused
		try (InputStream in = f.getInputStream(StreamOptions.buffer(100))) {
			assertArrayEquals(all, in.readAllBytes());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"jsch", "mina", "parley"})
	void noOptionsIsTheStreamItAlwaysWas(String implementation) throws Exception {
		byte[] data = SftpReadAheadTest.random(70_000, 13);
		FileSource f = written(implementation, "plain", data);
		try (InputStream in = f.getInputStream(StreamOptions.NONE)) {
			assertArrayEquals(data, in.readAllBytes());
		}
		try (InputStream in = f.getInputStream((StreamOptions) null)) {
			assertArrayEquals(data, in.readAllBytes());
		}
		// an option that says nothing this source uses for a sequential stream changes nothing
		try (InputStream in = f.getInputStream(StreamOptions.chunk(4096))) {
			assertArrayEquals(data, in.readAllBytes());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"jsch", "mina", "parley"})
	void seekableAndRandomAccessWithAChunkOfTheirOwn(String implementation) throws Exception {
		byte[] data = SftpReadAheadTest.random(SIZE, 14);
		FileSource f = written(implementation, "chunked", data);
		int[] positions = {0, 1, 4095, 4096, 4097, 100_000, 150_001, SIZE - 100, SIZE - 1};

		for(int chunk : new int[] {4096, 10_000, 65_536, 1_000_000}) {
			StreamOptions o = StreamOptions.chunk(chunk);
			ISeekableInputStream seekable = f.getSeekableInputStream(o);
			try {
				for(int pos : positions) {
					seekable.seek(pos);
					byte[] got = new byte[50];
					int n = seekable.read(got, 0, 50);
					assertArrayEquals(Arrays.copyOfRange(data, pos, pos + n), Arrays.copyOf(got, n), "seekable, chunk " + chunk + " at " + pos);
				}
			} finally {
				seekable.close();
			}
			try (IRandomAccessStream r = f.getRandomAccessStream("r", o)) {
				for(int pos : positions) {
					r.seek(pos);
					byte[] got = new byte[50];
					int n = r.read(got);
					assertArrayEquals(Arrays.copyOfRange(data, pos, pos + n), Arrays.copyOf(got, n), "random access, chunk " + chunk + " at " + pos);
				}
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"jsch", "mina", "parley"})
	void aChunkSizeIsFixedWhenTheStreamIsOpened(String implementation) throws Exception {
		byte[] data = SftpReadAheadTest.random(SIZE, 15);
		FileSource f = written(implementation, "fixed", data);
		SftpFileSourceFactory factory = factory(implementation);
		int before = factory.getChunkSize();
		try {
			// the controller itself says what it was built with
			SftpRandomAccessIoController own = new SftpRandomAccessIoController(f, "r", 12_000);
			try {
				assertEquals(12_000, own.getChunkSize());
			} finally {
				own.close();
			}
			SftpRandomAccessIoController plain = new SftpRandomAccessIoController(f, "r");
			try {
				assertEquals(before, plain.getChunkSize(), "0 means the factory's");
			} finally {
				plain.close();
			}

			// and a stream keeps its chunk when the factory's changes under it
			try (IRandomAccessStream r = f.getRandomAccessStream("r", StreamOptions.chunk(8192))) {
				factory.setChunkSize(5000);
				for(int pos : new int[] {0, 8191, 8192, 70_000, 123_456, SIZE - 60}) {
					r.seek(pos);
					byte[] got = new byte[50];
					int n = r.read(got);
					assertArrayEquals(Arrays.copyOfRange(data, pos, pos + n), Arrays.copyOf(got, n), "at " + pos);
				}
			}
		} finally {
			factory.setChunkSize(before);
		}
	}
}
