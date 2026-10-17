package us.bringardner.parley.files.sftp.spi;

/**
 * File attributes as SFTP version 3 reports them. The accessor names match
 * JSch's SftpATTRS, so code written against it reads the same.
 */
public final class SftpAttributes {

	private static final int S_IFMT = 0170000;
	private static final int S_IFDIR = 0040000;
	private static final int S_IFLNK = 0120000;
	private static final int S_IFREG = 0100000;

	private final long size;
	private final long uid;
	private final long gid;
	private final int permissions;
	private final long atime;
	private final long mtime;

	/**
	 * @param uid the owner's user id (SFTP sends an unsigned 32-bit number; pass it unsigned)
	 * @param gid the group id (unsigned, as uid)
	 * @param permissions full st_mode: file type bits and permission bits
	 * @param atime seconds since 1970 (unsigned: a library that gives an int should pass
	 *        Integer.toUnsignedLong(t), so times after 2038 stay right)
	 * @param mtime seconds since 1970 (unsigned, as atime)
	 */
	public SftpAttributes(long size, long uid, long gid, int permissions, long atime, long mtime) {
		this.size = size;
		this.uid = uid;
		this.gid = gid;
		this.permissions = permissions;
		this.atime = atime;
		this.mtime = mtime;
	}

	public long getSize() {
		return size;
	}

	public long getUId() {
		return uid;
	}

	public long getGId() {
		return gid;
	}

	/** Full st_mode, including the file type bits. */
	public int getPermissions() {
		return permissions;
	}

	/** Seconds since 1970. */
	public long getATime() {
		return atime;
	}

	/** Seconds since 1970. */
	public long getMTime() {
		return mtime;
	}

	public boolean isDir() {
		return (permissions & S_IFMT) == S_IFDIR;
	}

	public boolean isLink() {
		return (permissions & S_IFMT) == S_IFLNK;
	}

	public boolean isReg() {
		return (permissions & S_IFMT) == S_IFREG;
	}

	/** Like ls -l: "drwxr-xr-x", with s/S and t/T for the special bits. */
	public String getPermissionsString() {
		StringBuilder sb = new StringBuilder(10);
		sb.append(isDir() ? 'd' : isLink() ? 'l' : '-');
		sb.append((permissions & 0400) != 0 ? 'r' : '-');
		sb.append((permissions & 0200) != 0 ? 'w' : '-');
		sb.append(exec(permissions & 0100, permissions & 04000, 's'));
		sb.append((permissions & 0040) != 0 ? 'r' : '-');
		sb.append((permissions & 0020) != 0 ? 'w' : '-');
		sb.append(exec(permissions & 0010, permissions & 02000, 's'));
		sb.append((permissions & 0004) != 0 ? 'r' : '-');
		sb.append((permissions & 0002) != 0 ? 'w' : '-');
		sb.append(exec(permissions & 0001, permissions & 01000, 't'));
		return sb.toString();
	}

	private static char exec(int x, int special, char letter) {
		if( special != 0 ) {
			return x != 0 ? letter : Character.toUpperCase(letter);
		}
		return x != 0 ? 'x' : '-';
	}

	@Override
	public String toString() {
		return getPermissionsString()+" "+uid+" "+gid+" "+size+" "+mtime;
	}
}
