package lexicon.object;

import java.io.InputStream;

/**
 * Result object for streaming operations with Range request support
 * Contains file data chunk and metadata for HTTP 206 Partial Content responses
 */
public class StreamResult {
    private final byte[] data;
    /** Set instead of {@link #data} for files on disk: the range, read lazily as it is sent. */
    private final InputStream stream;
    private final long start;
    private final long end;
    private final long totalSize;
    private final boolean isPartialContent;
    private final String contentType;

    public StreamResult(byte[] data, long start, long end, long totalSize,
                      boolean isPartialContent, String contentType) {
        this(data, null, start, end, totalSize, isPartialContent, contentType);
    }

    /** A range streamed from disk. {@code stream} yields exactly {@code end - start + 1} bytes. */
    public StreamResult(InputStream stream, long start, long end, long totalSize,
                      boolean isPartialContent, String contentType) {
        this(null, stream, start, end, totalSize, isPartialContent, contentType);
    }

    private StreamResult(byte[] data, InputStream stream, long start, long end, long totalSize,
                      boolean isPartialContent, String contentType) {
        this.data = data;
        this.stream = stream;
        this.start = start;
        this.end = end;
        this.totalSize = totalSize;
        this.isPartialContent = isPartialContent;
        this.contentType = contentType;
    }

    public byte[] getData() { return data; }
    /** Non-null when the bytes come from disk; the caller must send (and so close) it. */
    public InputStream getStream() { return stream; }
    public long getStart() { return start; }
    public long getEnd() { return end; }
    public long getTotalSize() { return totalSize; }
    public boolean isPartialContent() { return isPartialContent; }
    public String getContentType() { return contentType; }
    public long getContentLength() { return end - start + 1; }
}
