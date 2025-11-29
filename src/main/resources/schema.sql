-- Lexicon Media Files Schema
-- This creates the media_files and file_data tables in the shared mydb database

CREATE TABLE IF NOT EXISTS media_files (
    id INT PRIMARY KEY,
    filename VARCHAR(500) NOT NULL,
    original_filename VARCHAR(500),
    content_type VARCHAR(100),
    file_size BIGINT,
    file_path VARCHAR(1000) NOT NULL,
    uploaded_by INT NOT NULL,
    upload_date TIMESTAMP NOT NULL,
    title VARCHAR(500),
    description VARCHAR(2000),
    is_public BOOLEAN DEFAULT FALSE,
    media_type VARCHAR(50),
    source_url VARCHAR(1000)
);

-- Table for storing actual file binary data
CREATE TABLE IF NOT EXISTS file_data (
    media_file_id INT PRIMARY KEY,
    data VARBINARY(2147483647),
    FOREIGN KEY (media_file_id) REFERENCES media_files(id) ON DELETE CASCADE
);

-- Index for faster queries
CREATE INDEX IF NOT EXISTS idx_media_uploaded_by ON media_files(uploaded_by);
CREATE INDEX IF NOT EXISTS idx_media_is_public ON media_files(is_public);
CREATE INDEX IF NOT EXISTS idx_media_type ON media_files(media_type);
