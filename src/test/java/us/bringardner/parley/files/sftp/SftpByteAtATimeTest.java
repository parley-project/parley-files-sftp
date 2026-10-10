package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import us.bringardner.parley.files.FileSource;

/**
 * Streams used a byte at a time (DataInputStream, readLine, text parsers):
 * the data must be right with every SSH library, and a byte must not cost a
 * request. JSch's own streams send one SFTP request per written byte and move
 * the rest of a received block down on every single-byte read, so the JSch
 * channel puts a buffer in front of them.
 * <p>
 * Connects to the test server: OpenSSH on localhost:22 or the embedded
 * server (see TestServer).
 */
public class SftpByteAtATimeTest {

	static final String DIR = "SftpByteAtATimeTest";
	/** Large enough that a 32 KB block is read byte by byte, and several blocks. */
	static final int SIZE = 160 * 1024;

	static SftpFileSourceFactory jsch;
	static SftpFileSourceFactory mina;
	static SftpFileSourceFactory bjl;

	@BeforeAll
	static void setUp() throws IOException {
		jsch = TestServer.connect("jsch");
		mina = TestServer.connect("mina");
		bjl = TestServer.connect("parley");
		FileSource dir = jsch.createFileSource(DIR);
		if( !dir.exists()) {
			assertTrue(dir.mkdirs(), "Can't create "+dir);
		}
	}

	@AfterAll
	static void tearDown() throws IOException {
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

	@ParameterizedTest
	@ValueSource(strings = {"jsch", "mina", "parley"})
	public void writeAndReadAByteAtATime(String implementation) throws IOException {
		byte[] data = SftpReadAheadTest.random(SIZE, 42);
		FileSource f = factory(implementation).createFileSource(DIR).getChild(implementation+"-bytes");

		long t0 = System.nanoTime();
		try (OutputStream out = f.getOutputStream()) {
			for(byte b : data) {
				out.write(b & 0xff);
			}
		}
		long wrote = System.nanoTime() - t0;
		assertEquals(SIZE, f.length());

		byte[] got = new byte[SIZE];
		t0 = System.nanoTime();
		try (InputStream in = f.getInputStream()) {
			for(int i = 0; i < SIZE; i++) {
				int b = in.read();
				if( b < 0 ) {
					throw new IOException("end of file at "+i);
				}
				got[i] = (byte) b;
			}
			assertEquals(-1, in.read());
		}
		long read = System.nanoTime() - t0;
		assertArrayEquals(data, got);

		System.out.printf("[sftp bytes] %-6s %d KB: write %.0f ms, read %.0f ms%n",
				implementation, SIZE/1024, wrote/1e6, read/1e6);
	}

	@ParameterizedTest
	@ValueSource(strings = {"jsch", "mina", "parley"})
	public void mixedByteAndBlockReadsAndSkip(String implementation) throws IOException {
		byte[] data = SftpReadAheadTest.random(SIZE, 7);
		FileSource f = factory(implementation).createFileSource(DIR).getChild(implementation+"-mixed");
		try (OutputStream out = f.getOutputStream()) {
			out.write(data, 0, 1000);
			out.write(data[1000] & 0xff);
			out.write(data, 1001, SIZE - 1001);
		}
		try (InputStream in = f.getInputStream()) {
			int pos = 0;
			byte[] b = new byte[5000];
			for(int round = 0; pos < SIZE - 40000; round++) {
				assertEquals(data[pos] & 0xff, in.read(), "byte at "+pos);
				pos++;
				int n = in.read(b, 0, 5000);
				assertTrue(n > 0);
				for(int i = 0; i < n; i++) {
					assertEquals(data[pos + i], b[i], "block byte at "+(pos + i));
				}
				pos += n;
				long s = in.skip(3000);
				pos += (int) s;
			}
			assertEquals(data[pos] & 0xff, in.read());
		}
	}
}
