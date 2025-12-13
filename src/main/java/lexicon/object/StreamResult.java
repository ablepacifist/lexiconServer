package lexicon.object;

/**
 * Result object for streaming operations with Range request support
 * Contains file data chunk and metadata for HTTP 206 Partial Content responses
 */
public class StreamResult {
    private final byte[] data;
    private final long start;
    private final long end;
    private final long totalSize;
    private final boolean isPartialContent;
    private final String contentType;
    
    public StreamResult(byte[] data, long start, long end, long totalSize, 
                      boolean isPartialContent, String contentType) {
        this.data = data;
        this.start = start;
        this.end = end;
        this.totalSize = totalSize;
        this.isPartialContent = isPartialContent;
        this.contentType = contentType;
    }
    
    public byte[] getData() { return data; }
    public long getStart() { return start; }
    public long getEnd() { return end; }
    public long getTotalSize() { return totalSize; }
    public boolean isPartialContent() { return isPartialContent; }
    public String getContentType() { return contentType; }
    public long getContentLength() { return end - start + 1; }
}
