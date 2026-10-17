# parley-files-sftp

> parley-files-sftp is part of **Parley**, a family of Java libraries for implementing internet protocols.
> It was previously `us.bringardner:bjl_file_system_sftp` (BjlFileSystemSftp), with packages under
> `us.bringardner.io.filesource.sftp`; they are now `us.bringardner.parley.files.sftp`. The SSH library
> named `bjl` is now `parley`, and the `bjl.sftp.*` system properties are now `parley.sftp.*`; the old
> names still work.

An SSH/SFTP implementation of the parley-files `FileSource` interface. The SSH library it uses is a
**provider** found on the class path, so it is chosen when the program is deployed, not when it is
built.

## Getting started

Add parley-files-sftp and a provider. parley-ssh's is the usual one:

```xml
<dependency>
    <groupId>us.bringardner.parley</groupId>
    <artifactId>parley-files-sftp-parley</artifactId>
    <version>1.0.0</version>
</dependency>
```

Each provider depends on `parley-files-sftp`, so that one dependency brings both.

## SSH providers

| Artifact | Name | Library |
|---|---|---|
| `parley-files-sftp-parley` | `parley` | [parley-ssh](https://github.com/parley-project/parley-ssh), Parley's own SSH library: no third party code |
| `parley-files-sftp-jsch` | `jsch` | [JSch, maintained fork](https://github.com/mwiede/jsch) (`com.github.mwiede:jsch`) |
| `parley-files-sftp-mina` | `mina` | [Apache MINA SSHD](https://mina.apache.org/sshd-project/) |

A provider is available when its jar is on the class path. Which one a factory uses:

1. The one it names: the `implementation` connection property, else the system property
   `parley.sftp.implementation`.
2. Nothing named: `parley`, if it is there.
3. Otherwise the only one there. With several and none named, connecting fails and asks for a name;
   with none, it fails and says which jars to add.

So a program that ships with `parley-files-sftp-parley` uses parley-ssh, and a deployment can switch to
another library, for example if parley-ssh has a bug that matters to it, without changing the program:
add `parley-files-sftp-jsch` (or `-mina`) to the class path and set

```
java -Dparley.sftp.implementation=jsch ...
```

or the `implementation` connection property of one factory:

```java
SftpFileSourceFactory factory = new SftpFileSourceFactory();
Properties p = factory.getConnectProperties();
p.setProperty("host", "example.com");
p.setProperty("user", "me");
p.setProperty("password", "secret");
p.setProperty("implementation", "jsch");
factory.setConnectionProperties(p);
```

A factory's own setting wins over the system property. All providers behave the same through
`FileSource`.

**Random access writing.** parley-ssh and MINA write at any position in one request. JSch can't do that
safely (it would put the data in the wrong place if the file changed size meanwhile), so with JSch,
random access that writes (`rw`, `rws`, `rwd`) goes through another provider on the class path that can
(parley's first), on a connection to the same account; reading stays on JSch. With JSch alone, opening a
file for random access writing is refused with a message that says what to add.

**Java 11 to 14.** parley-ssh uses the JDK's Ed25519, which arrived in Java 15, and reaches an ssh-agent on
macOS and Linux through Unix domain sockets, which arrived in Java 16. On older Java, use JSch for those
(it still needs another provider for random access writing).

## Writing a provider

A provider for another SSH library implements the interfaces in `us.bringardner.parley.files.sftp.spi`
and needs nothing but `parley-files-sftp`:

| Type | What it does |
|---|---|
| `SshProvider` | Its name, and `connect(SshSettings)`; `canWriteAtOffset()` (default true) says whether `SftpFile.write` writes at a position in one request |
| `SshConnection` | One logged-in connection: opens `SftpChannel`s, runs commands |
| `SftpChannel` | The SFTP requests (`stat`, `list`, `rename`, streams, ...); `AbstractSftpChannel` helps with the details |
| `SftpFile` | An open file read and written at positions |
| `SshSettings`, `SftpAttributes`, `SftpEntry` | What a provider is given and returns |

Register it with a file `META-INF/services/us.bringardner.parley.files.sftp.spi.SshProvider` that names
the class (it needs a public no-argument constructor), or call `SshProviders.register(provider)` at run
time. Errors are `IOException`s: `NoSuchFileException` for a missing file and `AccessDeniedException`
for a refused permission (see `SftpChannel`), so the FileSource behaves the same whatever the library. The three providers here are written the same way and
are the examples to follow; `parley-files-sftp-tests` runs each of them against the same servers.

## Other connection properties

| Property | Default | Meaning |
|---|---|---|
| `host`, `port`, `user`, `password` | port 22 | Where and how to log in |
| `identityFile` / `privateKey` | | Private key file path, or the key itself |
| `strictHostKeyChecking` | `yes` | Connect only to servers whose host key is in `knownHosts`; the error says how to add one (`ssh-keyscan`). `no` accepts any server, which lets one be impersonated |
| `knownHosts` | `~/.ssh/known_hosts` | known_hosts file used when checking is on |
| `connectTimeout` | 30000 | Milliseconds for connect, handshake and login |
| `serverAliveInterval` | 30000 | Milliseconds between keepalives; 0 turns them off |
| `attributeCacheTtl` | 2000 | Milliseconds a file's details are cached; see below |

## Links

SFTP has symbolic links and hard links, and both are supported: `createSymbolicLink` and `createLink` use the
server's `symlink` and `hardlink` requests.
SFTP is aware of links, so nothing is hidden: `getLinkedTo()` gives a symbolic link's target, a link acts
like what it leads to, a write through it changes the file it leads to (the link stays a link), and
`delete()` and `renameTo()` act on the link. (An FTP server has to hide links from its clients and handles
a write differently; that is FTP's rule, not this one.) `getCanonicalPath()` follows links (with `readlink`, up to 40 levels) and a
link that leads nowhere keeps its own path, as `java.io.File`'s does. See
[Links](https://github.com/parley-project/parley-files#links) in parley-files.

## Caching

Directory listings are never cached: every `listFiles()` asks the server, like `java.io.File`, so a listing always shows changes made through other objects or by other programs. The listing carries each child's details, so calling `isDirectory()` or `length()` on its results costs nothing more.

A file's own details (whether it exists, its size, times, type and owner) are cached for `attributeCacheTtl` milliseconds, then fetched again:

| Value | Behaviour |
|---|---|
| `2000` (default) | Trusted for 2 seconds |
| `0` | Always asks the server, exactly like `java.io.File` |
| negative, e.g. `-1` | Kept until `refresh()` |

Changes made through the same factory (writing, deleting, renaming, changing permissions, through any `FileSource` of it) are always seen at once, whatever the setting. A change made through another connection is seen when the time passes.

Set it per factory with `setAttributeCacheTtl(millis)` or the `attributeCacheTtl` connection property, or for the whole JVM with `-Dparley.sftp.attributeCacheTtl=0`. A factory's own setting wins; changing it applies at once, to files already created too.

## Testing

`mvn package` builds every module and runs all the tests. Which SSH server the tests talk to is chosen once per run with
`-Dparley.sftp.test.server`. They are in the `parley-files-sftp-tests` module, which runs every provider and
isn't published:

| Value | Server |
|---|---|
| `auto` (default) | OpenSSH on localhost:22 if the test account can log in, otherwise the embedded server |
| `local` | Always OpenSSH on localhost:22, with the test accounts `unittest1` to `unittest4` (see `TestServer`) |
| `embedded` | Always an embedded SSH server on a free port, with its home directory in the test run's folder under the temp directory (printed at the start). Needs nothing installed, so use this for CI |

Tests that need a real OpenSSH server (links, Unix permissions, the remote user, shell commands, the
channel limit) are skipped on the embedded server.

Most tests are parameterized to run with every provider; the others use the default, which can be set for
a run with `-Dparley.sftp.implementation=jsch`, `mina` or `parley`. `parley-files-sftp-jsch` has a test of
its own with JSch as the only provider.

A few tests start their own embedded servers on fixed ports (for example 2224), so they can clash on
a machine that runs several builds at once. Add `-Dsurefire.timeout=540` so a hung test fails the run
instead of stalling it.