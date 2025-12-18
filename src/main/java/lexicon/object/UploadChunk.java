package lexicon.object;

/**
 * Represents a single chunk of data in a chunked upload
 */
public class UploadChunk {
    private String uploadId;
    private int chunkNumber;
    private byte[] data;
    private int size;
    private String checksum; // MD5 or SHA256 hash of chunk data
    private long uploadTimestamp;
    
    public UploadChunk() {
        this.uploadTimestamp = System.currentTimeMillis();
    }
    
    public UploadChunk(String uploadId, int chunkNumber, byte[] data) {
        this();
        this.uploadId = uploadId;
        this.chunkNumber = chunkNumber;
        this.data = data;
        this.size = data != null ? data.length : 0;
    }
    
    public UploadChunk(String uploadId, int chunkNumber, byte[] data, String checksum) {
        this(uploadId, chunkNumber, data);
        this.checksum = checksum;
    }
    
    // Getters and Setters
    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }
    
    public int getChunkNumber() { return chunkNumber; }
    public void setChunkNumber(int chunkNumber) { this.chunkNumber = chunkNumber; }
    
    public byte[] getData() { return data; }
    public void setData(byte[] data) { 
        this.data = data;
        this.size = data != null ? data.length : 0;
    }
    
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    
    public long getUploadTimestamp() { return uploadTimestamp; }
    public void setUploadTimestamp(long uploadTimestamp) { this.uploadTimestamp = uploadTimestamp; }
}