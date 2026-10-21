# Changelog

## parley-files-sftp 1.0.0 (2026-10-21)

BjlFileSystemSftp (`us.bringardner:bjl_file_system_sftp` 1.0.0-SNAPSHOT) is now **parley-files-sftp**,
part of the Parley library family.

### Changed (needs a code change)

- Maven coordinates: `us.bringardner:bjl_file_system_sftp` is now `us.bringardner.parley:parley-files-sftp`.
- Packages: `us.bringardner.io.filesource.sftp` is now `us.bringardner.parley.files.sftp`.
- The client for Parley's own SSH library moved from `sftp.client.bjl` (`BjlProvider`, `BjlConnection`,
  `BjlSftpChannel`, `BjlSftpFile`) to `sftp.client.parley` (`ParleyProvider`, `ParleyConnection`,
  `ParleySftpChannel`, `ParleySftpFile`).
- `SshProviders.BJL` is now `SshProviders.PARLEY`.
- `ServiceLoader` registration: `META-INF/services/us.bringardner.parley.files.FileSourceFactory`.
- Module name (`Automatic-Module-Name`): `us.bringardner.parley.files.sftp`.
- Dependencies: `bjl_net_ssh`, `bjl_core`, `bjl_io` and `bjl_file_system` are now `parley-ssh`,
  `parley-core`, `parley-io` and `parley-files`.
- `SftpPropertyEditPanel` is gone: the factory describes its settings with `getConnectionSettings()`
  (see parley-files), and the module no longer uses Swing. The new `auth` property ("Password",
  "Private Key File" or "Private Key") says which credential a form asks for; it's optional when
  connecting (worked out from the credentials set) and `getConnectProperties()` includes it.
- `listFiles(ProgressMonitor)` is now `listFiles(FileSourceProgress)`.
- **The SSH library is a provider on the class path.** `parley-files-sftp` no longer includes any SSH
  library; add a provider: `parley-files-sftp-parley-ssh` (parley-ssh), `parley-files-sftp-jsch` or
  `parley-files-sftp-mina`, each a project of its own. Each brings `parley-files-sftp` with it. Providers are found with
  `ServiceLoader` (or `SshProviders.register`), and anyone can write one against the new
  `us.bringardner.parley.files.sftp.spi` package (it was `sftp.client`).
- **parley is the default.** With no `implementation` set, parley-ssh's provider is used if it is on the
  class path, else the only provider there; with several and none named, connecting fails and asks for a
  name. (The default was JSch.)
- The providers moved: `sftp.client.parley`, `.jsch` and `.mina` are now `sftp.parley`, `sftp.jsch` and
  `sftp.mina`, each in its own project and artifact (see their CHANGELOGs).
- The provider API: `SshSettings` and `SshConnection.ExecResult` have getters instead of public fields;
  `SftpAttributes` gives user and group ids and times as `long` (SFTP sends them unsigned, so times
  after 2038 stay right), and `SftpChannel.chown`, `chgrp`, `setModifiedTime` and `setAccessTime` take
  `long`; `SshProvider` has `getDescription()`.
- `setImplementation` accepts any name; connecting fails, listing the providers there, if none has it.

### Changed (no code change needed)

- The SSH library named `bjl` is now `parley`. `implementation=bjl` and `-D...=bjl` still work and
  mean `parley`; the factory reports `parley`.
- System properties: `parley.sftp.implementation` and `parley.sftp.attributeCacheTtl`. The old
  `bjl.sftp.implementation` and `bjl.sftp.attributeCacheTtl` are still read when the new ones aren't
  set. The test-only `bjl.sftp.test.server` is now `parley.sftp.test.server`.
- With JSch, random access writing no longer goes through a MINA connection: a provider's limits are its
  own, and the jsch provider now refuses a write at a position (see parley-files-sftp-jsch).
- A random access stream asks its provider before the first write (`SftpFile.write` with no bytes), so a
  file opened read-only, or a provider that can't write at a position, fails that write rather than a
  later save.

### Added

- **The provider test suite**, as parley-files-sftp's tests jar: a provider project runs it with
  surefire's `dependenciesToScan` and names what its library can't do in `parley.sftp.test.unsupported`.
  See the README, "Testing a provider".

- `bufferSize` (also `parley.sftp.bufferSize`; within 1 KB and 16 MB) is another name for `chunkSize`, and a
  stream can be opened with its own (`StreamOption.BUFFER_SIZE` or `CHUNK_SIZE`). A random access stream
  keeps the size it was opened with.
- A FileSource that was kept sees a change made through any other FileSource of the same factory at once
  (see parley-files' `getChangeCount()`); it used to be seen after the attribute cache time (2 s). A change
  made through another connection is still seen when that time passes (`attributeCacheTtl`).

### Changed (no code change needed)

- In the way of `java.io.File`: `mkdirs` (false for what is there), `list`/`listFiles` of a missing
  directory (null), `delete` of a directory that isn't empty (false), `renameTo` (itself, onto something, a
  missing directory), `setReadOnly` (write bits), `setLastModified` of a missing path (false), opening a
  directory for reading (`FileNotFoundException`; the server let it open).
- `getCanonicalPath()` of a link that leads nowhere is its own path (the longest leading part that resolves
  is resolved and the rest is left as written), as `java.io.File`'s is.
- `MinaSftpChannel` reports an unchecked IO error as an `IOException`; `JschSftpChannel` buffers writes
  (32 KB).
