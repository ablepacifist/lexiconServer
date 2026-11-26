package lexicon.logic;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads at most {@code limit} bytes from the wrapped stream, then reports end of stream.
 * Lets a byte range be streamed straight from disk instead of being loaded into memory.
 * Closing it closes the wrapped stream (the open file).
 */
final class BoundedInputStream extends FilterInputStream {
    private long remaining;

    BoundedInputStream(InputStream in, long limit) {
        super(in);
        this.remaining = Math.max(0, limit);
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) return -1;
        int b = super.read();
        if (b >= 0) remaining--;
        return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (remaining <= 0) return -1;
        int n = super.read(buffer, offset, (int) Math.min(length, remaining));
        if (n > 0) remaining -= n;
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(Math.min(n, remaining));
        remaining -= skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min(super.available(), remaining);
    }

    @Override
    public boolean markSupported() {
        return false;
    }
}
