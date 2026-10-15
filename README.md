# parley-files-sftp

> parley-files-sftp is part of **Parley**, a family of Java libraries for implementing internet protocols.
> It was previously `us.bringardner:bjl_file_system_sftp` (BjlFileSystemSftp), with packages under
> `us.bringardner.io.filesource.sftp`; they are now `us.bringardner.parley.files.sftp`. The SSH library
> named `bjl` is now `parley`, and the `bjl.sftp.*` system properties are now `parley.sftp.*`; the old
> names still work.

```xml
<dependency>
    <groupId>us.bringardner.parley</groupId>
    <artifactId>parley-files-sftp</artifactId>
    <version>1.0.0</version>
</dependency>
```
 SSH/SFTP implementation of the FileSource interface

## Choosing the SSH library

Three SSH libraries are included, and which one is used is decided at run time:

| Value | Library |
|---|---|
| `jsch` (default) | [JSch, maintained fork](https://github.com/mwiede/jsch) (`com.github.mwiede:jsch`) |
| `mina` | [Apache MINA SSHD](https://mina.apache.org/sshd-project/) |
| `parley` | [parley-ssh](https://github.com/parley-project/parley-ssh) (`us.bringardner.parley:parley-ssh`), Parley's own SSH library: no third party code |

Set it per factory with the `implementation` connection property:

```java
SftpFileSourceFactory factory = new SftpFileSourceFactory();
Properties p = factory.getConnectProperties();
p.setProperty("host", "example.com");
p.setProperty("user", "me");
p.setProperty("password", "secret");
p.setProperty("implementation", "mina");   // or "jsch", "parley"
factory.setConnectionProperties(p);
```

or for the whole JVM with a system property:

```
java -Dparley.sftp.implementation=mina ...   (or jsch, parley)
```

A factory's own setting wins over the system property. All three behave the same through `FileSource`. With MINA and BJL, random access reads and writes at any position in one request each. JSch has no safe way to write at a position, so random access that can write (`rw`, `rws`, `rwd`) goes through a MINA connection to the same account even when JSch is chosen; reading stays on JSch.

Code that needs to talk to the server directly can use the library-neutral interface in `us.bringardner.io.filesource.sftp.client` (`SshProviders`, `SshConnection`, `SftpChannel`, `SftpFile`).

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

`mvn package` runs the whole suite. Which SSH server the tests talk to is chosen once per run with
`-Dparley.sftp.test.server`:

| Value | Server |
|---|---|
| `auto` (default) | OpenSSH on localhost:22 if the test account can log in, otherwise the embedded server |
| `local` | Always OpenSSH on localhost:22, with the test accounts `unittest1` to `unittest4` (see `TestServer`) |
| `embedded` | Always an embedded SSH server on a free port, with its home directory in `target/embedded-sftp/home`. Needs nothing installed, so use this for CI |

Tests that need a real OpenSSH server (links, Unix permissions, the remote user, shell commands, the
channel limit) are skipped on the embedded server.

Most tests are parameterized to run with every SSH library. Pick the library for a run with
`-Dparley.sftp.implementation=jsch` (the default), `mina` or `parley`.

A few tests start their own embedded servers on fixed ports (for example 2224), so they can clash on
a machine that runs several builds at once. Add `-Dsurefire.timeout=540` so a hung test fails the run
instead of stalling it.