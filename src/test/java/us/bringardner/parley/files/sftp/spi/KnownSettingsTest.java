package us.bringardner.parley.files.sftp.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.files.sftp.SftpFileSourceFactory;
import us.bringardner.parley.net.server.KnownSettings;

/**
 * To-Do 2.16: parley-files-sftp's list of settings (META-INF/parley/settings.list) has every name its
 * code spells out, so a server's start-up check never warns about a real setting.
 */
public class KnownSettingsTest {

	@Test
	public void everyConstantIsListed() throws Exception {
		List<String> found = KnownSettings.settingConstants(SftpFileSourceFactory.class);
		assertFalse(found.isEmpty());
		assertEquals(List.of(), KnownSettings.load().undeclared(found));
	}
}
