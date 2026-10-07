package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.files.sftp.client.SshProviders;
import us.bringardner.parley.files.sftp.client.parley.ParleyProvider;

/**
 * The move to Parley renamed the "bjl" SSH library to "parley" and the bjl.sftp.* system
 * properties to parley.sftp.*. The old names must keep working. No server needed.
 */
public class SftpLegacyNamesTest {

	@AfterEach
	public void clearProperties() {
		System.clearProperty(SshProviders.SYSTEM_PROPERTY);
		System.clearProperty(SshProviders.LEGACY_SYSTEM_PROPERTY);
		System.clearProperty(SftpFileSourceFactory.SYSTEM_PROPERTY_ATTRIBUTE_CACHE_TTL);
		System.clearProperty(SftpFileSourceFactory.LEGACY_SYSTEM_PROPERTY_ATTRIBUTE_CACHE_TTL);
	}

	@Test
	public void bjlIsAnotherNameForParley() {
		assertTrue(SshProviders.get("bjl") instanceof ParleyProvider);
		assertTrue(SshProviders.get("BJL") instanceof ParleyProvider);
		assertTrue(SshProviders.get("parley") instanceof ParleyProvider);
		assertEquals(SshProviders.PARLEY, SshProviders.get("bjl").getName());
		assertEquals(SshProviders.PARLEY, SshProviders.canonicalName(" Bjl "));
		assertEquals("mina", SshProviders.canonicalName("MINA"));
	}

	@Test
	public void factoryStoresTheNewName() {
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		f.setImplementation("bjl");
		assertEquals(SshProviders.PARLEY, f.getImplementation());
		assertEquals(SshProviders.PARLEY, f.getEffectiveImplementation());
	}

	@Test
	public void oldImplementationPropertyStillWorks() {
		System.setProperty(SshProviders.LEGACY_SYSTEM_PROPERTY, "bjl");
		assertEquals(SshProviders.PARLEY, SshProviders.defaultName());
		// the new property wins when both are set
		System.setProperty(SshProviders.SYSTEM_PROPERTY, "mina");
		assertEquals(SshProviders.MINA, SshProviders.defaultName());
	}

	@Test
	public void oldAttributeCacheTtlPropertyStillWorks() {
		System.setProperty(SftpFileSourceFactory.LEGACY_SYSTEM_PROPERTY_ATTRIBUTE_CACHE_TTL, "123");
		assertEquals(123, SftpFileSourceFactory.defaultAttributeCacheTtl());
		System.setProperty(SftpFileSourceFactory.SYSTEM_PROPERTY_ATTRIBUTE_CACHE_TTL, "456");
		assertEquals(456, SftpFileSourceFactory.defaultAttributeCacheTtl());
	}
}
