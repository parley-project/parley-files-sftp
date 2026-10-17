package us.bringardner.parley.files.sftp.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/** Which SSH provider is used: found on the class path or registered, then chosen by the rules. */
public class SshProvidersTest {

	@Test
	void aConfiguredNameWins() {
		assertEquals("jsch", SshProviders.choose("jsch", Arrays.asList("jsch", "mina", "parley")));
		assertEquals("mylib", SshProviders.choose("mylib", Collections.emptyList()), "checked when connecting");
	}

	@Test
	void parleyWhenNothingIsConfigured() {
		assertEquals("parley", SshProviders.choose(null, Arrays.asList("jsch", "mina", "parley")));
	}

	@Test
	void theOnlyOneWhenParleyIsNotThere() {
		assertEquals("jsch", SshProviders.choose(null, Collections.singletonList("jsch")));
		assertEquals("mylib", SshProviders.choose(null, Collections.singletonList("mylib")));
	}

	@Test
	void severalWithoutParleyNeedAName() {
		IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> SshProviders.choose(null, Arrays.asList("jsch", "mina")));
		assertTrue(e.getMessage().contains("Several SSH providers are on the class path (jsch, mina) and none is chosen"), e.getMessage());
		assertTrue(e.getMessage().contains(SshProviders.SYSTEM_PROPERTY), e.getMessage());
	}

	@Test
	void noneSaysWhatToAdd() {
		IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> SshProviders.choose(null, Collections.emptyList()));
		assertTrue(e.getMessage().contains("parley-files-sftp-parley-ssh"), e.getMessage());
	}

	@Test
	void aRegisteredProviderIsAvailableAndCanBeChosen() {
		SshProvider mine = new SshProvider() {
			@Override
			public String getName() {
				return "MyLib";
			}

			@Override
			public SshConnection connect(SshSettings settings) throws IOException {
				throw new IOException("not really");
			}
		};
		SshProviders.register(mine);
		try {
			assertTrue(SshProviders.isAvailable("mylib"));
			assertSame(mine, SshProviders.get("MYLIB"), "names are compared without regard to case");
		} finally {
			SshProviders.unregister("mylib");
		}
		assertFalse(SshProviders.isAvailable("mylib"));
	}

	@Test
	void anUnknownNameListsWhatIsThere() {
		SshProviders.register(fake("zzlib"));
		try {
			IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SshProviders.get("telnet"));
			assertTrue(e.getMessage().contains("The SSH provider 'telnet' isn't on the class path"), e.getMessage());
			assertTrue(e.getMessage().contains("Available: ") && e.getMessage().contains("zzlib"), e.getMessage());
		} finally {
			SshProviders.unregister("zzlib");
		}
	}

	@Test
	void theOlderNameOfParleyIsAccepted() {
		assertEquals("parley", SshProviders.canonicalName(" BJL "));
		assertEquals("mina", SshProviders.canonicalName("MINA"));
	}

	private static SshProvider fake(String name) {
		return new SshProvider() {
			@Override
			public String getName() {
				return name;
			}

			@Override
			public SshConnection connect(SshSettings settings) throws IOException {
				throw new IOException("not really");
			}
		};
	}
}
