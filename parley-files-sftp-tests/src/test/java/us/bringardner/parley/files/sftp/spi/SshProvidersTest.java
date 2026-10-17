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
		assertTrue(e.getMessage().contains("parley-files-sftp-parley"), e.getMessage());
	}

	@Test
	void providersOnTheClassPathAreFound() {
		assertEquals(Arrays.asList("jsch", "mina", "parley"), new java.util.ArrayList<>(SshProviders.available().keySet()));
		assertTrue(SshProviders.isAvailable("BJL"), "the older name of parley");
		assertFalse(SshProviders.isAvailable("nope"));
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
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SshProviders.get("telnet"));
		assertTrue(e.getMessage().contains("Available: jsch, mina, parley"), e.getMessage());
	}
}
