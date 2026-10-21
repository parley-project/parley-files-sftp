# parley-files-sftp

> parley-files-sftp is part of **Parley**, a family of Java libraries for implementing internet protocols.
> It was previously `us.bringardner:bjl_file_system_sftp` (BjlFileSystemSftp), with packages under
> `us.bringardner.io.filesource.sftp`; they are now `us.bringardner.parley.files.sftp`. The SSH library
> named `bjl` is now `parley` (`bjl` still works as an implementation name). The `bjl.sftp.*` system
> properties became `parley.sftp.*`, and in 2.0.0 `parley.files.sftp.*`; the old names are no longer read.

An SSH/SFTP implementation of the parley-files `FileSource` interface. The SSH library it uses is a
**provider** found on the class path, so it is chosen when the program is deployed, not when it is
built.

## Getting started

Add parley-files-sftp and a provider. parley-ssh's is the usual one:

```xml
<dependency>
    <groupId>us.bringardner.parley</groupId>
    <artifactId>parley-files-sftp-parley-ssh</artifactId>
    <version>1.0.0</version>
</dependency>
```

Each provider depends on `parley-files-sftp`, so that one dependency brings both.

## SSH providers

| Artifact | Name | Library |
|---|---|---|
| [`parley-files-sftp-parley-ssh`](https://github.com/parley-project/parley-files-sftp-parley-ssh) | `parley` | [parley-ssh](https://github.com/parley-project/parley-ssh), Parley's own SSH library: no third party code |
| [`parley-files-sftp-jsch`](https://github.com/parley-project/parley-files-sftp-jsch) | `jsch` | [JSch, maintained fork](https://github.com/mwiede/jsch) (`com.github.mwiede:jsch`). **Can't write at a position** (see its README) |
| [`parley-files-sftp-mina`](https://github.com/parley-project/parley-files-sftp-mina) | `mina` | [Apache MINA SSHD](https://mina.apache.org/sshd-project/). **Can't log in with an ssh-agent** (see its README) |

Each is a project of its own, released on its own.

A provider is available when its jar is on the class path. Which one a factory uses:

1. The one it names: the `implementation` connection property, else the system property
   `parley.files.sftp.implementation`.
2. Nothing named: `parley`, if it is there.
3. Otherwise the only one there. With several and none named, connecting fails and asks for a name;
   with none, it fails and says which jars to add.

So a program that ships with `parley-files-sftp-parley-ssh` uses parley-ssh, and a deployment can switch to
another library, for example if parley-ssh has a bug that matters to it, without changing the program:
add `parley-files-sftp-jsch` (or `-mina`) to the class path and set

```
java -Dparley.files.sftp.implementation=jsch ...
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

**A provider's limits are its own.** A provider documents what its library can't do and throws an
`IOException` when an application tries it. JSch, for example, can't write at a position, so with the
`jsch` provider writing to a random access stream throws; MINA's provider can't log in with an
ssh-agent. Their READMEs say so.

**Java 11 to 14.** parley-ssh uses the JDK's Ed25519, which arrived in Java 15, and reaches an ssh-agent on
macOS and Linux through Unix domain sockets, which arrived in Java 16. On older Java, use JSch for those
(if the application doesn't write at positions) or MINA.

## Writing a provider

A provider for another SSH library implements the interfaces in `us.bringardner.parley.files.sftp.spi`
and needs nothing but `parley-files-sftp`:

| Type | What it does |
|---|---|
| `SshProvider` | Its name, and `connect(SshSettings)` |
| `SshConnection` | One logged-in connection: opens `SftpChannel`s, runs commands |
| `SftpChannel` | The SFTP requests (`stat`, `list`, `rename`, streams, ...); `AbstractSftpChannel` helps with the details |
| `SftpFile` | An open file read and written at positions |
| `SshSettings`, `SftpAttributes`, `SftpEntry` | What a provider is given and returns |

Register it with a file `META-INF/services/us.bringardner.parley.files.sftp.spi.SshProvider` that names
the class (it needs a public no-argument constructor), or call `SshProviders.register(provider)` at run
time. Something the library can't do is the provider's to document and to refuse with an
`IOException` when it is tried (as the JSch provider does for writing at a position), not something to
work around. Errors are `IOException`s: `NoSuchFileException` for a missing file and `AccessDeniedException`
for a refused permission (see `SftpChannel`), so the FileSource behaves the same whatever the library.
parley-files-sftp-parley-ssh, -jsch and -mina are written the same way and are the examples to follow.

## Testing a provider

parley-files-sftp's tests jar is the provider test suite: about 230 tests of the whole FileSource
through the provider on the class path, against an embedded SSH server (or OpenSSH, see below). A
provider project runs it with its own tests:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <dependenciesToScan>
                    <dependency>us.bringardner.parley:parley-files-sftp</dependency>
                </dependenciesToScan>
                <!-- only if the library can't do something: see below -->
                <systemPropertyVariables>
                    <parley.files.sftp.test.unsupported>writeAtPosition</parley.files.sftp.test.unsupported>
                </systemPropertyVariables>
            </configuration>
        </plugin>
    </plugins>
</build>

<dependencies>
    <dependency>
        <groupId>us.bringardner.parley</groupId>
        <artifactId>parley-files-sftp</artifactId>
        <version>1.0.0</version>
        <type>test-jar</type>
        <scope>test</scope>
    </dependency>
    <!-- and the suite's own test dependencies, which a test-jar doesn't pass on: parley-files'
         test-jar, junit-jupiter, the Maverick server (maverick-synergy-server,
         maverick-virtual-session, maverick-virtual-filesystem), parley-ssh (for the ssh-agent
         test's server) and, for Windows, org.apache.sshd:sshd-sftp. See parley-files-sftp-jsch's pom. -->
</dependencies>
```

The provider must be the only one on the test class path. A library that can't do something names it
in `parley.files.sftp.test.unsupported` (comma separated): `writeAtPosition`, `agentLogin`. The suite skips
those tests, and the provider's own tests should check that it refuses them with a clear `IOException`.

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

## Locking

In short, for `SftpFileSourceFactory`, whichever provider:

| | |
|---|---|
| Locks | `PROCESS`: this JVM only (SFTP has no locks). Range locks work within the JVM. |
| Atomic create | `createNewFile()` and `mkdir()` are atomic (see the provider for how) |
| Capabilities (`getCapabilities()`) | `PROCESS` locks; atomic mkdir; atomic create where the provider can make it so with the server (`SftpChannel.createNewIsAtomic()`: JSch can't with a server that offers `posix-rename@openssh.com` but no hard links); replace (`renameTo(dest, true)`) not atomic: dest moved aside, then the rename; durable sync not promised (it depends on the server and provider); operations in one order |
| FileChannel | through the file's streams and random access |
| `map()` | `READ_ONLY` and `PRIVATE` map a local copy of the region, read when it is mapped; `READ_WRITE` throws `UnsupportedOperationException` |
| `force()` | sends what is held, then `fsync@openssh.com` where the provider and the server can |

| Provider | Atomic `createNewFile()` | `force()` |
|---|---|---|
| parley-ssh | exclusive open (`SSH_FXF_EXCL`) | `fsync@openssh.com` when the server offers it |
| MINA | exclusive open (`SSH_FXF_EXCL`) | `fsync@openssh.com` when the server offers it |
| JSch | an empty file under its own name, then a hard link (`hardlink@openssh.com`) or a plain rename onto the path, which the server refuses if it is taken; check-then-create only on a server with the replacing rename and no hard links | only sends the data (JSch can't send `fsync@openssh.com`) |

SFTP (version 3, what servers offer) has no file locks, so `lock()` and `tryLock()` (see parley-files) keep the
default: locks within this JVM (`getLockScope()` is `PROCESS`). They keep threads and code in one process apart,
not other clients of the server. Applications that need more build it on top (parley-net's locking). The suite's
`SftpLockTest` runs parley-files' lock contract with each provider.

## Caching

Directory listings are never cached: every `listFiles()` asks the server, like `java.io.File`, so a listing always shows changes made through other objects or by other programs. The listing carries each child's details, so calling `isDirectory()` or `length()` on its results costs nothing more.

A file's own details (whether it exists, its size, times, type and owner) are cached for `attributeCacheTtl` milliseconds, then fetched again:

| Value | Behaviour |
|---|---|
| `2000` (default) | Trusted for 2 seconds |
| `0` | Always asks the server, exactly like `java.io.File` |
| negative, e.g. `-1` | Kept until `refresh()` |

Changes made through the same factory (writing, deleting, renaming, changing permissions, through any `FileSource` of it) are always seen at once, whatever the setting. A change made through another connection is seen when the time passes.

Set it per factory with `setAttributeCacheTtl(millis)` or the `attributeCacheTtl` connection property, or for the whole JVM with `-Dparley.files.sftp.attributeCacheTtl=0`. A factory's own setting wins; changing it applies at once, to files already created too.

## Testing

`mvn package` in a provider project runs the suite and that project's own tests. Which SSH server the tests talk to is chosen once per run with
`-Dparley.files.sftp.test.server`, in any project that runs the suite:

| Value | Server |
|---|---|
| `auto` (default) | OpenSSH on localhost:22 if the test account can log in, otherwise the embedded server |
| `local` | Always OpenSSH on localhost:22, with the test accounts `unittest1` to `unittest4` (see `TestServer`) |
| `embedded` | Always an embedded SSH server on a free port, with its home directory in the test run's folder under the temp directory (printed at the start). Needs nothing installed, so use this for CI |

Tests that need a real OpenSSH server (links, Unix permissions, the remote user, shell commands, the
channel limit) are skipped on the embedded server.

The suite tests the one provider on the class path. parley-files-sftp itself has none, so here only the
provider registry's tests run; the suite runs in each provider project.

A few tests start their own embedded servers on fixed ports (for example 2224), so they can clash on
a machine that runs several builds at once. Add `-Dsurefire.timeout=540` so a hung test fails the run
instead of stalling it.