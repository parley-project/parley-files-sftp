package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import us.bringardner.parley.files.ConnectionSetting;
import us.bringardner.parley.files.ConnectionSettings;

/**
 * SFTP's connection settings, described for any UI (they used to be a Swing panel).
 * A new connection starts empty: it used to be filled in with the test account
 * ("unittest1" on "localhost"), which a real user then had to notice and replace.
 */
public class SftpConnectionSettingsTest {

	private static Properties start(SftpFileSourceFactory f) {
		return ConnectionSettings.initialValues(f.getConnectionSettings(), f.getConnectProperties());
	}

	@Test
	void newConnectionHasNoUserHostOrCredentials() {
		Properties p = start(new SftpFileSourceFactory());
		assertEquals("", p.getProperty(SftpFileSourceFactory.PROP_USER), "user");
		assertEquals("", p.getProperty(SftpFileSourceFactory.PROP_HOST), "host");
		assertEquals("", p.getProperty(SftpFileSourceFactory.PROP_PASSWORD), "password");
		assertEquals("", p.getProperty(SftpFileSourceFactory.PROP_PRIVATE_KEY), "private key");
		assertEquals("", p.getProperty(SftpFileSourceFactory.PROP_PRIVATE_KEY_FILE_NAME), "identity file");
		assertEquals(""+SftpFileSourceFactory.DEFAULT_PORT, p.getProperty(SftpFileSourceFactory.PROP_PORT),
				"the standard port is still filled in");
		assertEquals(SftpFileSourceFactory.AUTH_PASSWORD, p.getProperty(SftpFileSourceFactory.PROP_AUTH));
	}

	@Test
	void everyPropertyIsDescribed() {
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		List<ConnectionSetting> settings = f.getConnectionSettings();
		for(String key : f.getConnectProperties().stringPropertyNames()) {
			assertTrue(ConnectionSettings.find(settings, key) != null, key+" isn't described");
		}
		// secrets are never shown in the clear
		for(ConnectionSetting s : settings) {
			if( f.isSecretProperty(s.key())) {
				assertTrue(s.isSecret() || s.kind() == ConnectionSetting.Kind.HIDDEN, s.key());
			}
		}
	}

	@Test
	void theCredentialsAskedForFollowTheChoice() {
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		List<ConnectionSetting> settings = f.getConnectionSettings();
		Properties values = start(f);
		values.setProperty(SftpFileSourceFactory.PROP_HOST, "example.org");
		values.setProperty(SftpFileSourceFactory.PROP_USER, "someone");
		values.setProperty(SftpFileSourceFactory.PROP_PASSWORD, "pw");
		assertEquals(List.of(), f.validateConnection(values));

		values.setProperty(SftpFileSourceFactory.PROP_AUTH, SftpFileSourceFactory.AUTH_KEY_FILE);
		assertEquals(List.of("Private key file is required"), f.validateConnection(values));
		values.setProperty(SftpFileSourceFactory.PROP_PRIVATE_KEY_FILE_NAME, "/home/someone/.ssh/id_ed25519");
		assertEquals(List.of(), f.validateConnection(values));

		// the password isn't sent with a key file
		f.setConnectionProperties(ConnectionSettings.forConnect(settings, values));
		assertEquals("", f.getPassword());
		assertEquals(SftpFileSourceFactory.AUTH_KEY_FILE, f.getAuth());
		assertEquals(SftpFileSourceFactory.AUTH_KEY_FILE, f.getConnectProperties().getProperty(SftpFileSourceFactory.PROP_AUTH));
	}

	@Test
	void badNumbersAreCaughtBeforeConnecting() {
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		Properties values = start(f);
		values.setProperty(SftpFileSourceFactory.PROP_HOST, "h");
		values.setProperty(SftpFileSourceFactory.PROP_USER, "u");
		values.setProperty(SftpFileSourceFactory.PROP_PORT, "");
		values.setProperty(SftpFileSourceFactory.PROP_MAX_CHANNELS, "0");
		assertEquals(List.of("Port is required", "Most channels at once must be at least 1"), f.validateConnection(values));
	}

	@Test
	void settingsSurviveARoundTrip() {
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		Properties in = new Properties();
		in.setProperty(SftpFileSourceFactory.PROP_USER, "someone");
		in.setProperty(SftpFileSourceFactory.PROP_HOST, "example.org");
		in.setProperty(SftpFileSourceFactory.PROP_PORT, "2222");
		in.setProperty(SftpFileSourceFactory.PROP_STRICT_HOST_KEY_CHECKING, "no");
		in.setProperty(SftpFileSourceFactory.PROP_SAFE_OVERWRITE, "true");
		f.setConnectionProperties(in);
		Properties out = start(f);
		assertEquals("someone", out.getProperty(SftpFileSourceFactory.PROP_USER));
		assertEquals("example.org", out.getProperty(SftpFileSourceFactory.PROP_HOST));
		assertEquals("2222", out.getProperty(SftpFileSourceFactory.PROP_PORT));
		assertEquals("no", out.getProperty(SftpFileSourceFactory.PROP_STRICT_HOST_KEY_CHECKING));
		assertEquals("true", out.getProperty(SftpFileSourceFactory.PROP_SAFE_OVERWRITE));
		assertFalse(f.getConnectionSettings().isEmpty());
	}
}
