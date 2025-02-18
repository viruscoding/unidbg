package com.github.unidbg.ios.file;

import com.alibaba.fastjson.util.IOUtils;
import com.github.unidbg.Emulator;
import com.github.unidbg.Utils;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.file.FileIO;
import com.github.unidbg.file.ios.BaseDarwinFileIO;
import com.github.unidbg.file.ios.IOConstants;
import com.github.unidbg.file.ios.StatStructure;
import com.github.unidbg.unix.IO;
import com.github.unidbg.utils.Inspector;
import com.sun.jna.Pointer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;

public class SimpleFileIO extends BaseDarwinFileIO implements FileIO {

    private static final Log log = LogFactory.getLog(SimpleFileIO.class);

    protected final File file;
    protected final String path;
    private RandomAccessFile _randomAccessFile;

    private synchronized RandomAccessFile checkOpenFile() {
        try {
            if (_randomAccessFile == null) {
                FileUtils.forceMkdir(file.getParentFile());
                if (!file.exists() && !file.createNewFile()) {
                    throw new IOException("createNewFile failed: " + file);
                }
                _randomAccessFile = new RandomAccessFile(file, "rws");
            }
            return _randomAccessFile;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public SimpleFileIO(int oflags, File file, String path) {
        super(oflags);
        this.file = file;
        this.path = path;

        if (file.isDirectory()) {
            throw new IllegalArgumentException("file is directory: " + file);
        }
        if (!file.exists()) {
            throw new IllegalArgumentException("file not exists: " + file);
        }
    }

    @Override
    public void close() {
        IOUtils.close(_randomAccessFile);

        if (debugStream != null) {
            try {
                debugStream.flush();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public int write(byte[] data) {
        try {
            if (debugStream != null) {
                debugStream.write(data);
                debugStream.flush();
            }

            if (log.isDebugEnabled() && data.length < 0x3000) {
                Inspector.inspect(data, "write");
            }

            RandomAccessFile randomAccessFile = checkOpenFile();
            if ((oflags & IOConstants.O_APPEND) != 0) {
                randomAccessFile.seek(randomAccessFile.length());
            }
            randomAccessFile.write(data);
            return data.length;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    OutputStream debugStream;

    void setDebugStream(OutputStream stream) {
        this.debugStream = new BufferedOutputStream(stream);
    }

    @Override
    public int read(Backend backend, Pointer pointer, final int _count) {
        RandomAccessFile randomAccessFile = checkOpenFile();
        return Utils.readFile(randomAccessFile, pointer, _count);
    }

    @Override
    protected byte[] getMmapData(long addr, int offset, int length) throws IOException {
        RandomAccessFile randomAccessFile = checkOpenFile();
        randomAccessFile.seek(offset);
        int remaining = (int) (randomAccessFile.length() - randomAccessFile.getFilePointer());
        ByteArrayOutputStream baos = remaining <= 0 ? new ByteArrayOutputStream() : new ByteArrayOutputStream(Math.min(length, remaining));
        byte[] buf = new byte[1024];
        do {
            int count = length - baos.size();
            if (count == 0) {
                break;
            }

            if (count > buf.length) {
                count = buf.length;
            }

            int read = randomAccessFile.read(buf, 0, count);
            if (read == -1) {
                break;
            }

            baos.write(buf, 0, read);
        } while (true);
        return baos.toByteArray();
    }

    @Override
    public String toString() {
        return path;
    }

    @Override
    public int ioctl(Emulator<?> emulator, long request, long argp) {
        if (IO.STDOUT.equals(path) || IO.STDERR.equals(path)) {
            return 0;
        }

        return super.ioctl(emulator, request, argp);
    }

    @Override
    public FileIO dup2() {
        SimpleFileIO dup = new SimpleFileIO(oflags, file, path);
        dup.debugStream = debugStream;
        dup.op = op;
        dup.oflags = oflags;
        return dup;
    }

    @Override
    public int lseek(int offset, int whence) {
        try {
            RandomAccessFile randomAccessFile = checkOpenFile();
            switch (whence) {
                case SEEK_SET:
                    randomAccessFile.seek(offset);
                    return (int) randomAccessFile.getFilePointer();
                case SEEK_CUR:
                    randomAccessFile.seek(randomAccessFile.getFilePointer() + offset);
                    return (int) randomAccessFile.getFilePointer();
                case SEEK_END:
                    randomAccessFile.seek(randomAccessFile.length() + offset);
                    return (int) randomAccessFile.getFilePointer();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return super.lseek(offset, whence);
    }

    @Override
    public int llseek(long offset, Pointer result, int whence) {
        try {
            RandomAccessFile randomAccessFile = checkOpenFile();
            switch (whence) {
                case SEEK_SET:
                    randomAccessFile.seek(offset);
                    result.setLong(0, randomAccessFile.getFilePointer());
                    return 0;
                case SEEK_END:
                    randomAccessFile.seek(randomAccessFile.length() - offset);
                    result.setLong(0, randomAccessFile.getFilePointer());
                    return 0;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return super.llseek(offset, result, whence);
    }

    @Override
    public int fstat(Emulator<?> emulator, StatStructure stat) {
        int blockSize = emulator.getPageAlign();
        int st_mode;
        if (IO.STDOUT.equals(file.getName())) {
            st_mode = IO.S_IFCHR | 0x777;
        } else if(Files.isSymbolicLink(file.toPath())) {
            st_mode = IO.S_IFLNK;
        } else {
            st_mode = IO.S_IFREG;
        }
        stat.st_dev = 1;
        stat.st_mode = (short) (st_mode);
        stat.setSize(file.length());
        stat.setBlockCount((file.length() + blockSize - 1) / blockSize);
        stat.st_blksize = blockSize;
        stat.st_ino = 7;
        stat.st_uid = 0;
        stat.st_gid = 0;
        stat.setLastModification(file.lastModified());
        stat.pack();
        return 0;
    }

    @Override
    public int ftruncate(int length) {
        try (FileChannel channel = new FileOutputStream(file, true).getChannel()) {
            channel.truncate(length);
            return 0;
        } catch (IOException e) {
            log.debug("ftruncate failed", e);
            return -1;
        }
    }

    @Override
    public int listxattr(Pointer namebuf, int size, int options) {
        return listxattr(file, namebuf, size);
    }

    @Override
    public int setxattr(String name, byte[] data) {
        return setxattr(file, name, data);
    }

    @Override
    public int chown(int uid, int gid) {
        return chown(file, uid, gid);
    }

    @Override
    public int chmod(int mode) {
        return chmod(file, mode);
    }

    @Override
    public int chflags(int flags) {
        return chflags(file, flags);
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public int getxattr(Emulator<?> emulator, String name, Pointer value, int size) {
        return getxattr(emulator, file, name, value, size);
    }
}
