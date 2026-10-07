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

### Changed (no code change needed)

- The SSH library named `bjl` is now `parley`. `implementation=bjl` and `-D...=bjl` still work and
  mean `parley`; the factory reports `parley`.
- System properties: `parley.sftp.implementation` and `parley.sftp.attributeCacheTtl`. The old
  `bjl.sftp.implementation` and `bjl.sftp.attributeCacheTtl` are still read when the new ones aren't
  set. The test-only `bjl.sftp.test.server` is now `parley.sftp.test.server`.
- `SftpLegacyNamesTest` checks that the old names keep working.
