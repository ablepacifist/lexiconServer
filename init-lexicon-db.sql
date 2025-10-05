-- Database initialization script for Lexicon media files
-- This should be run after the existing Alchemy database is set up

-- Create media_files table for storing media file metadata
CREATE TABLE IF NOT EXISTS media_files (
    id INTEGER PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    uploaded_by INTEGER NOT NULL,
    upload_date TIMESTAMP NOT NULL,
    title VARCHAR(200),
    description TEXT,
    is_public BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (uploaded_by) REFERENCES players(id)
);

-- Add indexes for better performance
CREATE INDEX IF NOT EXISTS idx_media_files_uploaded_by ON media_files(uploaded_by);
CREATE INDEX IF NOT EXISTS idx_media_files_public ON media_files(is_public);
CREATE INDEX IF NOT EXISTS idx_media_files_upload_date ON media_files(upload_date);

-- Add new columns to players table if they don't exist (for unified user system)
-- Note: HSQLDB syntax for adding columns safely
ALTER TABLE players ADD COLUMN email VARCHAR(100) IF NOT EXISTS;
ALTER TABLE players ADD COLUMN display_name VARCHAR(100) IF NOT EXISTS;
ALTER TABLE players ADD COLUMN registration_date TIMESTAMP IF NOT EXISTS;
ALTER TABLE players ADD COLUMN last_login_date TIMESTAMP IF NOT EXISTS;

-- Update existing players to have default values for new columns
UPDATE players SET display_name = username WHERE display_name IS NULL;
UPDATE players SET registration_date = CURRENT_TIMESTAMP WHERE registration_date IS NULL;
