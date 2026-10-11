# Changelog

## parley-files-sftp 1.0.0 (unreleased)

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

### Changed (no code change needed)

- The SSH library named `bjl` is now `parley`. `implementation=bjl` and `-D...=bjl` still work and
  mean `parley`; the factory reports `parley`.
- System properties: `parley.sftp.implementation` and `parley.sftp.attributeCacheTtl`. The old
  `bjl.sftp.implementation` and `bjl.sftp.attributeCacheTtl` are still read when the new ones aren't
  set. The test-only `bjl.sftp.test.server` is now `parley.sftp.test.server`.
- `SftpLegacyNamesTest` checks that the old names keep working.

### Added

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
