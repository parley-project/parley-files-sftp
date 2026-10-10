package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.files.ConnectionSettings;

/**
 * The buffer size is for programmers: a per-connection value (the connect property
 * "bufferSize", or setBufferSize), else a JVM default, else the factory's own default.
 * No dialog shows it, it isn't written into connections that didn't set it, and a
 * value that can't be used never stops a connection. Offline: nothing connects.
 */
public class SftpBufferSizeTest {

	private static final String KEY = SftpFileSourceFactory.PROP_BUFFER_SIZE;

	@AfterEach
	void clearSystemProperty() {
		System.clearProperty(SftpFileSourceFactory.SYSTEM_PROPERTY_BUFFER_SIZE);
	}

	@Test
	void defaultIsTheFactorysOwn() {
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		assertEquals(SftpFileSourceFactory.DEFAULT_CHUNK_SIZE, f.getBufferSize());
		assertEquals(f.getChunkSize(), f.getBufferSize(), "the same value");
	}

	@Test
	void notShownToUsersAndNotWrittenUnlessSet() {
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		assertNull(f.getConnectProperties().getProperty(KEY), "unset: saved connections follow the default");
		assertNull(ConnectionSettings.find(f.getConnectionSettings(), KEY), "never a setting in a dialog");

		f.setBufferSize(64 * 1024);
		assertEquals("65536", f.getConnectProperties().getProperty(KEY));
		assertNull(ConnectionSettings.find(f.getConnectionSettings(), KEY), "still not a setting");
	}

	@Test
	void setBufferSizeKeepsItInRange() {
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		f.setBufferSize(1);
		assertEquals(SftpFileSourceFactory.MIN_BUFFER_SIZE, f.getBufferSize());
		f.setBufferSize(Integer.MAX_VALUE);
		assertEquals(SftpFileSourceFactory.MAX_BUFFER_SIZE, f.getBufferSize());
		f.setBufferSize(-5);
		assertEquals(SftpFileSourceFactory.MIN_BUFFER_SIZE, f.getBufferSize());
		// the older setter is unchanged: the tests use a chunk of 100 bytes to force many requests
		f.setChunkSize(100);
		assertEquals(100, f.getBufferSize());
	}

	@Test
	void roundTripsThroughConnectPropertiesAndCopies() {
		SftpFileSourceFactory a = new SftpFileSourceFactory();
		a.setBufferSize(256 * 1024);

		SftpFileSourceFactory b = new SftpFileSourceFactory();
		b.setConnectionProperties(a.getConnectProperties());
		assertEquals(256 * 1024, b.getBufferSize());
		assertEquals("262144", b.getConnectProperties().getProperty(KEY), "still explicit after loading");

		SftpFileSourceFactory c = (SftpFileSourceFactory) a.createThreadSafeCopy();
		assertEquals(256 * 1024, c.getBufferSize());
		assertEquals("262144", c.getConnectProperties().getProperty(KEY));

		// another factory is untouched
		assertEquals(SftpFileSourceFactory.DEFAULT_CHUNK_SIZE, new SftpFileSourceFactory().getBufferSize());
	}

	@Test
	void aDialogCarriesItThrough() {
		// what a settings form does with a value it doesn't describe: keep it and pass it on
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		f.setBufferSize(512 * 1024);
		Properties shown = f.getConnectProperties();
		assertTrue(ConnectionSettings.validate(f.getConnectionSettings(), shown).stream().noneMatch(p -> p.contains(KEY)));
		Properties saved = ConnectionSettings.forConnect(f.getConnectionSettings(), shown);
		assertEquals("524288", saved.getProperty(KEY));

		SftpFileSourceFactory g = new SftpFileSourceFactory();
		g.setConnectionProperties(saved);
		assertEquals(512 * 1024, g.getBufferSize());
	}

	@Test
	void anEmptyOrBadValueKeepsTheDefaultAndDoesNotThrow() {
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		Properties p = f.getConnectProperties();
		p.setProperty(KEY, "");
		f.setConnectionProperties(p);
		assertEquals(SftpFileSourceFactory.DEFAULT_CHUNK_SIZE, f.getBufferSize());
		assertNull(f.getConnectProperties().getProperty(KEY), "empty is unset");

		p.setProperty(KEY, "lots");
		f.setConnectionProperties(p);
		assertEquals(SftpFileSourceFactory.DEFAULT_CHUNK_SIZE, f.getBufferSize());
		assertNull(f.getConnectProperties().getProperty(KEY));

		p.setProperty(KEY, "0");
		f.setConnectionProperties(p);
		assertEquals(SftpFileSourceFactory.MIN_BUFFER_SIZE, f.getBufferSize(), "kept within range");
	}

	@Test
	void aMissingValueDoesNotResetAnExplicitOne() {
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		f.setBufferSize(32 * 1024);
		Properties p = f.getConnectProperties();
		p.remove(KEY);
		f.setConnectionProperties(p);
		assertEquals(32 * 1024, f.getBufferSize());
	}

	@Test
	void jvmDefaultAppliesToNewFactoriesAndIsNotWrittenOut() {
		System.setProperty(SftpFileSourceFactory.SYSTEM_PROPERTY_BUFFER_SIZE, "65536");
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		assertEquals(65536, f.getBufferSize());
		assertNull(f.getConnectProperties().getProperty(KEY), "a JVM default isn't frozen into the connection");
		assertNotNull(f.getConnectProperties());

		// an explicit value wins
		f.setBufferSize(8192);
		assertEquals(8192, f.getBufferSize());

		System.setProperty(SftpFileSourceFactory.SYSTEM_PROPERTY_BUFFER_SIZE, "nonsense");
		assertEquals(SftpFileSourceFactory.DEFAULT_CHUNK_SIZE, new SftpFileSourceFactory().getBufferSize());
		System.setProperty(SftpFileSourceFactory.SYSTEM_PROPERTY_BUFFER_SIZE, "10");
		assertEquals(SftpFileSourceFactory.MIN_BUFFER_SIZE, new SftpFileSourceFactory().getBufferSize());
		assertFalse(new SftpFileSourceFactory().getConnectProperties().containsKey(KEY));
	}
}
