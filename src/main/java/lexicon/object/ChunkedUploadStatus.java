package lexicon.object;

/**
 * Status of a chunked upload session
 */
public enum ChunkedUploadStatus {
    IN_PROGRESS,    // Upload is actively receiving chunks
    PAUSED,         // Upload is temporarily paused (can be resumed)
    ASSEMBLING,     // All chunks received, assembling into final file
    COMPLETED,      // Upload completed and final file stored in database
    FAILED,         // Upload failed and needs to be restarted
    EXPIRED,        // Upload session expired due to inactivity
    CANCELLED       // Upload was cancelled by user
}