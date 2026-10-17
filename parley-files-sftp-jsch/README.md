# parley-files-sftp-jsch

The [JSch](https://github.com/mwiede/jsch) provider for
[parley-files-sftp](../README.md): SFTP through the maintained JSch fork (`com.github.mwiede:jsch`).

```xml
<dependency>
    <groupId>us.bringardner.parley</groupId>
    <artifactId>parley-files-sftp-jsch</artifactId>
    <version>1.0.0</version>
</dependency>
```

On the class path it is used when it is the only provider there, or when it is chosen: the
`implementation` connection property or `-Dparley.sftp.implementation=jsch`.

## Limitation: no writing at a position

**JSch can't write at a position in a file.** Its API has no call for it, and the only way to emulate
one (ask the file's size, then append at "position - size") puts the data in the wrong place, silently,
if the file changes size in between. This provider doesn't emulate it. Instead, a write at a position
throws an `IOException` that says so:

- writing to a random access stream (`FileSource.getRandomAccessStream("rw")`, `"rws"`, `"rwd"`, or a
  `SeekableByteChannel` opened for writing) throws at the first write;
- growing a file with `setLength` throws.

Everything else works: reading, writing and appending whole files with streams, listing, links,
permissions, and random access *reading* (mode `"r"`), including at any position. Shrinking a file
with `setLength` works too.

If your application writes at positions, use another provider, such as parley-ssh's
(`parley-files-sftp-parley`) or Apache MINA SSHD's (`parley-files-sftp-mina`).

## Other notes

- JSch logs in with a password, a private key (file or contents) or an ssh-agent (`auth=Agent`).
- Ed25519 keys work on Java 11 and later.
