package us.bringardner.parley.files.sftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import us.bringardner.parley.files.credentials.CredentialException;
import us.bringardner.parley.files.credentials.Credentials;
import us.bringardner.parley.files.credentials.EnvCredentialProvider;

/**
 * The password (and a pasted key) may be references (@env:..., @prompt:...), resolved only to
 * log in; and auth=Agent logs in through ssh-agent.
 */
public class SftpCredentialReferenceTest {

	@AfterEach
	public void defaults() {
		Credentials.restoreDefaults();
		Credentials.setPrompter(null);
	}

	private static Properties props(String implementation, String password) throws IOException {
		Properties p = TestServer.properties(implementation, TestServer.port());
		p.setProperty(SftpFileSourceFactory.PROP_PASSWORD, password);
		return p;
	}

	@ParameterizedTest
	@MethodSource("us.bringardner.parley.files.sftp.TestServer#providers")
	public void aReferenceIsResolvedToLogInAndKeptAsIs(String implementation) throws IOException {
		Credentials.register(new EnvCredentialProvider(Map.of("SFTP_PW", TestServer.PASSWORD)::get));
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		try {
			assertTrue(f.connect(props(implementation, "@env:SFTP_PW")));
			assertEquals("@env:SFTP_PW", f.getConnectProperties().getProperty(SftpFileSourceFactory.PROP_PASSWORD));
			assertTrue(f.listRoots().length > 0);
		} finally {
			f.disConnect();
		}
	}

	@Test
	public void aWrongPromptedPasswordIsAskedForAgain() throws IOException {
		List<String> answers = new ArrayList<>(List.of("wrong", TestServer.PASSWORD));
		List<String> labels = new ArrayList<>();
		Credentials.setPrompter((label, req) -> { labels.add(label); return answers.remove(0).toCharArray(); });
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		try {
			assertThrows(IOException.class, () -> f.connect(props(TestServer.provider(), "@prompt:")), "the first answer is wrong");
			assertTrue(f.connect(props(TestServer.provider(), "@prompt:")));
			assertTrue(answers.isEmpty());
			assertEquals("password for "+TestServer.USER+"@"+TestServer.HOST+" (sftp)", labels.get(0));
		} finally {
			f.disConnect();
		}
	}

	@Test
	public void aReferenceThatCantBeResolvedFailsTheConnectNamingIt() throws IOException {
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		Properties p = props(TestServer.provider(), "@env:NO_SUCH_SFTP_PW_VAR");
		CredentialException e = assertThrows(CredentialException.class, () -> f.connect(p));
		assertTrue(e.getMessage().startsWith("@env:NO_SUCH_SFTP_PW_VAR"), e.getMessage());
	}

	@Test
	public void agentIsASettingThatRoundTrips() {
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		Properties p = new Properties();
		p.setProperty(SftpFileSourceFactory.PROP_AUTH, SftpFileSourceFactory.AUTH_AGENT);
		f.setConnectionProperties(p);
		assertTrue(f.isUseAgent());
		assertEquals(SftpFileSourceFactory.AUTH_AGENT, f.getAuth());
		assertEquals(SftpFileSourceFactory.AUTH_AGENT, f.getConnectProperties().getProperty(SftpFileSourceFactory.PROP_AUTH));
		assertTrue(f.getConnectionSettings().stream()
				.anyMatch(s -> s.key().equals(SftpFileSourceFactory.PROP_AUTH) && s.choices().contains(SftpFileSourceFactory.AUTH_AGENT)));

		p.setProperty(SftpFileSourceFactory.PROP_AUTH, SftpFileSourceFactory.AUTH_PASSWORD);
		f.setConnectionProperties(p);
		assertFalse(f.isUseAgent());
	}

	@Test
	public void withNoAgentRunningAgentLoginSaysSo() throws IOException {
		TestServer.assumeSupported(TestServer.AGENT_LOGIN);
		assumeTrue(System.getenv("SSH_AUTH_SOCK") == null && !System.getProperty("os.name").startsWith("Windows"));
		SftpFileSourceFactory f = new SftpFileSourceFactory();
		Properties p = props(TestServer.provider(), "");
		p.setProperty(SftpFileSourceFactory.PROP_AUTH, SftpFileSourceFactory.AUTH_AGENT);
		IOException e = assertThrows(IOException.class, () -> f.connect(p));
		assertTrue(e.getMessage().contains("SSH_AUTH_SOCK"), e.getMessage());
	}

}
