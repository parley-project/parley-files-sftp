package us.bringardner.parley.files.sftp.client.parley;

import us.bringardner.parley.files.sftp.client.AbstractSftpChannel;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;

import us.bringardner.parley.files.sftp.client.SftpAttributes;
import us.bringardner.parley.files.sftp.client.SftpChannel;
import us.bringardner.parley.files.sftp.client.SftpEntry;
import us.bringardner.parley.files.sftp.client.SftpFile;
import us.bringardner.parley.ssh.sftp.SftpAttrs;
import us.bringardner.parley.ssh.sftp.SftpClient;
import us.bringardner.parley.ssh.sftp.SftpConstants;
import us.bringardner.parley.ssh.sftp.SftpDirEntry;
import us.bringardner.parley.ssh.sftp.SftpException;
import us.bringardner.parley.ssh.sftp.SftpHandle;

class ParleySftpChannel extends AbstractSftpChannel {

	/** How long close() waits for the server to confirm the channel is closed. */
	static final long CLOSE_WAIT_MS = 10_000;

	final SftpClient sftp;

	ParleySftpChannel(SftpClient sftp) {
		this.sftp = sftp;
	}

	interface Call<T> {
		T run() throws IOException;
	}

	/** Runs a call, turning SftpException into the types SftpChannel promises. */
	static <T> T call(String path, Call<T> c) throws IOException {
		try {
			return c.run();
		} catch (SftpException e) {
			throw translate(path, e);
		}
	}

	static IOException translate(String path, SftpException e) {
		switch (e.getStatus()) {
		case SftpConstants.SSH_FX_NO_SUCH_FILE:
			return (IOException) new NoSuchFileException(path, null, e.getMessage()).initCause(e);
		case SftpConstants.SSH_FX_PERMISSION_DENIED:
			return (IOException) new AccessDeniedException(path, null, e.getMessage()).initCause(e);
		default:
			return e;
		}
	}

	static SftpAttributes convert(SftpAttrs a) {
		return new SftpAttributes(a.getSize(), a.getUid(), a.getGid(), a.getPermissions(),
				(int) a.getAccessTime(), (int) a.getModifyTime());
	}

	@Override
	public boolean isOpen() {
		return sftp.isOpen();
	}

	@Override
	public SftpAttributes stat(String path) throws IOException {
		return call(path, () -> convert(sftp.stat(path)));
	}

	@Override
	public SftpAttributes lstat(String path) throws IOException {
		return call(path, () -> convert(sftp.lstat(path)));
	}

	@Override
	public List<SftpEntry> list(String dir) throws IOException {
		return call(dir, () -> {
			List<SftpEntry> ret = new ArrayList<>();
			for (SftpDirEntry e : sftp.list(dir)) {
				ret.add(new SftpEntry(e.getName(), e.getLongName(), convert(e.getAttrs())));
			}
			return ret;
		});
	}

	@Override
	public String readLink(String path) throws IOException {
		return call(path, () -> sftp.readlink(path));
	}

	@Override
	public void symlink(String target, String link) throws IOException {
		call(link, () -> { sftp.symlink(target, link); return null; });
	}

	@Override
	public void hardlink(String existing, String link) throws IOException {
		call(link, () -> { sftp.hardlink(existing, link); return null; });
	}

	@Override
	public void mkdir(String path) throws IOException {
		call(path, () -> { sftp.mkdir(path); return null; });
	}

	@Override
	public void rmdir(String path) throws IOException {
		call(path, () -> { sftp.rmdir(path); return null; });
	}

	@Override
	public void remove(String path) throws IOException {
		call(path, () -> { sftp.remove(path); return null; });
	}

	@Override
	public void rename(String from, String to) throws IOException {
		call(from, () -> { sftp.rename(from, to); return null; });
	}

	/**
	 * posix-rename@openssh.com when the server offers it (atomic), otherwise 'to' is
	 * removed first.
	 */
	@Override
	public void replace(String from, String to) throws IOException {
		if( sftp.hasExtension(SftpConstants.EXT_POSIX_RENAME) ) {
			call(from, () -> { sftp.posixRename(from, to); return null; });
			return;
		}
		replaceByRemoving(from, to);
	}

	@Override
	public void chmod(String path, int mode) throws IOException {
		call(path, () -> { sftp.setStat(path, SftpAttrs.NONE.withPermissions(mode & 07777)); return null; });
	}

	@Override
	public void chown(String path, int uid) throws IOException {
		// SFTP v3 sets uid and gid together; keep the group
		call(path, () -> {
			int gid = sftp.stat(path).getGid();
			sftp.setStat(path, SftpAttrs.NONE.withOwner(uid, gid));
			return null;
		});
	}

	@Override
	public void chgrp(String path, int gid) throws IOException {
		call(path, () -> {
			int uid = sftp.stat(path).getUid();
			sftp.setStat(path, SftpAttrs.NONE.withOwner(uid, gid));
			return null;
		});
	}

	@Override
	public void setModifiedTime(String path, int seconds) throws IOException {
		// SFTP v3 sets both times together; keep the access time
		call(path, () -> {
			SftpAttrs cur = sftp.stat(path);
			sftp.setStat(path, SftpAttrs.NONE.withTimes(cur.getAccessTime(), seconds & 0xffffffffL));
			return null;
		});
	}

	@Override
	public void setAccessTime(String path, int seconds) throws IOException {
		call(path, () -> {
			SftpAttrs cur = sftp.stat(path);
			sftp.setStat(path, SftpAttrs.NONE.withTimes(seconds & 0xffffffffL, cur.getModifyTime()));
			return null;
		});
	}

	@Override
	public void truncate(String path, long size) throws IOException {
		call(path, () -> { sftp.setStat(path, SftpAttrs.NONE.withSize(size)); return null; });
	}

	@Override
	public String home() throws IOException {
		return call(".", () -> sftp.realpath("."));
	}

	/** Pipelined: several read requests in flight. */
	@Override
	public InputStream read(String path, long offset) throws IOException {
		return guardedInput(path, call(path, () -> sftp.read(path, offset)));
	}

	/** Pipelined: several write requests in flight; errors come from a later write or close(). */
	@Override
	public OutputStream write(String path, boolean append) throws IOException {
		return guardedOutput(path, call(path, () -> sftp.write(path, append)));
	}

	@Override
	protected void createExclusive(String path) throws IOException {
		call(path, () -> {
			sftp.open(path, SftpConstants.SSH_FXF_WRITE | SftpConstants.SSH_FXF_CREAT | SftpConstants.SSH_FXF_EXCL, SftpAttrs.NONE).close();
			return null;
		});
	}

	@Override
	protected <T> T guard(String path, IoCall<T> c) throws IOException {
		return call(path, c::run);
	}

	@Override
	public SftpFile open(String path, boolean write) throws IOException {
		int flags = write ? SftpConstants.SSH_FXF_READ | SftpConstants.SSH_FXF_WRITE : SftpConstants.SSH_FXF_READ;
		SftpHandle h = call(path, () -> sftp.open(path, flags, SftpAttrs.NONE));
		return new ParleySftpFile(this, path, h, write);
	}

	@Override
	public void close() {
		sftp.close(CLOSE_WAIT_MS);
	}
}
