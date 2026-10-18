# Lexicon - Personal Media Sharing Platform

Lexicon is a personal video and audio sharing backend service built with Spring Boot.

## Architecture

Lexicon follows a clean 3-layer architecture:

```
api/        - REST API controllers (only calls logic layer)
logic/      - Business logic services 
data/       - Database access layer
object/     - Domain objects (User, MediaFile, etc.)
```

## Features

- **User Management**: Registration, authentication, profiles
- **Media Upload**: Video and audio file upload with metadata
- **Media Types**: Support for Music, Video, Audiobook, and Other media classifications
- **YouTube Downloads**: Download media directly from YouTube URLs using yt-dlp
- **Media Sharing**: Public/private media with access controls
- **Search**: Find media by title, description, or user
- **File Management**: Secure file storage and retrieval

## Technology Stack

- **Backend**: Spring Boot 3.1.4, Java 17
- **Database**: HSQLDB (embedded)
- **Security**: Spring Security with BCrypt
- **File Upload**: Multipart file handling

## Ports

- **Lexicon Port**: `36568` by default (`LEXICON_PORT` / `server.port`)
- **Database Port**: `9002` by default (parsed from `DATABASE_URL`, see `.env.example`)

## Quick Start

### Prerequisites
- Java 17+
- Gradle 8.3+
- **yt-dlp** (for YouTube download functionality)

### Installing yt-dlp

#### On Linux/Mac:
```bash
# Using pip (recommended)
pip install yt-dlp

# Or using your package manager
# Ubuntu/Debian:
sudo apt install yt-dlp
# macOS:
brew install yt-dlp
```

#### On Windows:
```bash
# Using pip
pip install yt-dlp

# Or download the executable from: https://github.com/yt-dlp/yt-dlp/releases
```

Verify installation:
```bash
yt-dlp --version
```

### Setting Up YouTube Cookies (Required for YouTube Downloads)

YouTube requires authentication to prevent bot detection. You need to export your YouTube cookies:

#### Quick Setup (Recommended)

Run the setup script:
```bash
./setup-youtube.sh
```

This interactive script will:
- Check if yt-dlp is installed
- Find your cookies file
- Add the environment variable to your shell configuration

#### Manual Setup

1. **Install a browser extension**:
   - **Chrome/Edge**: Install "[Get cookies.txt LOCALLY](https://chromewebstore.google.com/detail/get-cookiestxt-locally/cclelndahbckbenkjhflpdbgdldlbecc)"
   - **Firefox**: Install "[cookies.txt](https://addons.mozilla.org/en-US/firefox/addon/cookies-txt/)"

2. **Export YouTube cookies**:
   - Go to [youtube.com](https://youtube.com) and make sure you're logged in
   - Click the browser extension icon
   - Click "Export" to download the cookies file
   - Save it as `youtube_cookies.txt` in a secure location

3. **Set the environment variable**:
   ```bash
   export YTDLP_COOKIES_PATH="/path/to/your/youtube_cookies.txt"
   ```

   **For production/permanent setup**, add to your shell profile:
   ```bash
   # Add to ~/.bashrc or ~/.zshrc
   echo 'export YTDLP_COOKIES_PATH="/path/to/your/youtube_cookies.txt"' >> ~/.bashrc
   source ~/.bashrc
   ```

   **Note**: Keep your cookies file secure and never commit it to version control!

### Running the Application

1. **Start the HSQLDB server** (if not already running):
   ```bash
   # Start on port 9002
   java -cp lib/hsqldb.jar org.hsqldb.server.Server --database.0 file:mydb --dbname.0 mydb --port 9002
   ```

2. **Configure environment variables**:
   
   **Option A: Using .env file (Recommended for development)**
   ```bash
   # Copy the example file
   cp .env.example .env
   
   # Edit .env and set your values
   nano .env  # or use your preferred editor
   
   # Make sure to set YTDLP_COOKIES_PATH to your cookies file location
   ```

   **Option B: Export directly (Good for testing)**
   ```bash
   # Required for YouTube downloads
   export YTDLP_COOKIES_PATH="/path/to/your/youtube_cookies.txt"
   
   # Optional overrides (defaults shown)
   export LEXICON_PORT=36568
   export DATABASE_URL="jdbc:hsqldb:hsql://localhost:9002/mydb"
   export MAX_FILE_SIZE=100MB
   export UPLOAD_DIR="./uploads"
   ```

3. **Start Lexicon backend**:
   ```bash
   ./gradlew bootRun
   ```

   The server will log:
   ```
   Using YouTube cookies from: /path/to/your/youtube_cookies.txt
   Tomcat started on port(s): 36568
   ```

4. **Test the API**:
   ```bash
   curl http://localhost:36568/api/health
   ```

## API Endpoints

### Health & Info
- `GET /api/health` - Service health check
- `GET /api/info` - Service information

### Authentication (TODO)
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - User login

### Media Management
- `POST /api/media/upload` - Upload media file (multipart form-data)
  - Parameters: `file`, `userId`, `title`, `description`, `isPublic`, `mediaType` (MUSIC/VIDEO/AUDIOBOOK/OTHER)
- `POST /api/media/upload-from-url` - Download and upload from YouTube URL
  - Parameters: `url`, `userId`, `title`, `description`, `isPublic`, `mediaType`, `downloadType` (AUDIO_ONLY/VIDEO)
- `GET /api/media/public` - Get all public media files
- `GET /api/media/user/{userId}` - Get user's media files
- `GET /api/media/{id}` - Get specific media file metadata
- `GET /api/media/{id}/download` - Download media file
- `DELETE /api/media/{id}` - Delete media file
- `GET /api/media/search?q={query}` - Search media files
- `GET /api/media/recent?limit={n}` - Get recent media files

## Database Schema

### Users Table
```sql
CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    display_name VARCHAR(100) NOT NULL
);
```

### Media Files Table
```sql
CREATE TABLE media_files (
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
    media_type VARCHAR(20) DEFAULT 'OTHER',
    source_url VARCHAR(1000),
    FOREIGN KEY (uploaded_by) REFERENCES users(id)
);

CREATE TABLE file_data (
    media_file_id INTEGER PRIMARY KEY,
    data BLOB,
    FOREIGN KEY (media_file_id) REFERENCES media_files(id)
);
```

## Development

### Project Structure
```
lexiconServer/
├── src/main/java/lexicon/
│   ├── LexiconApplication.java     # Main Spring Boot application
│   ├── api/                       # REST controllers
│   │   ├── LexiconSecurityConfig.java
│   │   ├── MediaController.java
│   │   └── LexiconTestController.java
│   ├── logic/                     # Business logic services
│   │   ├── UserManagerService.java
│   │   ├── MediaManagerService.java
│   │   └── MediaManager.java      # Implementation
│   ├── service/                   # External service integrations
│   │   └── YtDlpService.java     # YouTube download service
│   ├── data/                      # Database access
│   │   ├── ILexiconDatabase.java
│   │   ├── IMediaDatabase.java
│   │   ├── HSQLLexiconDatabase.java
│   │   └── HSQLMediaDatabase.java
│   └── object/                    # Domain objects
│       ├── User.java
│       ├── MediaFile.java
│       └── MediaType.java         # Enum: MUSIC, VIDEO, AUDIOBOOK, OTHER
├── src/main/resources/
│   └── application.properties
├── src/test/java/lexicon/
│   ├── logic/MediaManagerTest.java
│   └── data/HSQLMediaDatabaseTest.java
├── lib/                          # HSQLDB jar files
├── build.gradle
└── README.md
```

## Configuration

### Environment Variables

Spring Boot loads `.env` files automatically via `spring.config.import` in
`application.properties`: this module's own `.env` (git-ignored; copy
`.env.example` to `.env` to run standalone) is overridden by the monorepo
root `.env` (same key names; found via `$MASTER_ENV_FILE` or `../.env`),
which is overridden by real process environment variables. Neither file is
required - the app still starts on the `${KEY:default}` values below if
both are absent. See `.env.example` for the full list this module reads.

| Variable | Default | Description |
|----------|---------|-------------|
| `YTDLP_COOKIES_PATH` | `cookies.txt` | Path to your YouTube cookies file (required for YouTube downloads) |
| `LEXICON_PORT` | `36568` | Port for the Lexicon server |
| `SERVER_ADDRESS` | `0.0.0.0` | Server bind address |
| `DATABASE_URL` | `jdbc:hsqldb:hsql://localhost:9002/mydb` | HSQLDB connection URL |
| `MAX_FILE_SIZE` | `100MB` (`2GB` in the default profile) | Maximum file upload size |
| `MAX_REQUEST_SIZE` | `100MB` (`2GB` in the default profile) | Maximum request size |
| `UPLOAD_DIR` | `./uploads` | Directory for file uploads |
| `CORS_ALLOWED_ORIGINS` / `CORS_ALLOWED_ORIGIN_PATTERNS` | see `.env.example` | Allowed browser origins (exact / wildcard) |
| `LAN_HOST` / `PLAYIT_HOST` | _(unset)_ | When set, `http://<host>:*` is added to the CORS origin patterns automatically |
| `LEXI_PORT` / `LEXI_BASE_URL` / `LEXI_TOOL_TOKEN` | `8765` / derived / _(none)_ | Voice relay to Lexi (`/api/voice`) |
| `PUBLIC_BRIDGE_URL` | `https://voice.alex-dyakin.com` | Mumble Bridge base URL (avatar proxy) |
| `PUBLIC_LEXICON_URL` | `https://api.alex-dyakin.com` | Used to compose the app-update download URL |

### application.properties

The application uses Spring Boot properties with environment variable overrides:

```properties
# Server Configuration
server.port=${LEXICON_PORT:36568}
server.address=${SERVER_ADDRESS:0.0.0.0}

# File upload configuration
spring.servlet.multipart.max-file-size=${MAX_FILE_SIZE:2GB}
spring.servlet.multipart.max-request-size=${MAX_REQUEST_SIZE:2GB}

# File storage path
lexicon.file.upload-dir=${UPLOAD_DIR:./uploads}

# yt-dlp configuration
ytdlp.cookies.path=${YTDLP_COOKIES_PATH:cookies.txt}
```

`DATABASE_URL` is not a Spring property (there is no `database.url` key) -
it is read directly by `lexicon.utils.DatabaseConfig`, used by every
`HSQL*Database` class. See that class's Javadoc for why.

## TODO

- [x] Implement MediaManagerService
- [x] Implement MediaManager with media type support
- [x] Implement YouTube download functionality with yt-dlp
- [x] Create database schema with media_type and source_url
- [x] Implement media upload/download controllers
- [x] Add comprehensive unit tests for business and data layers
- [ ] Implement UserManagerService
- [ ] Implement authentication controllers
- [ ] Add file validation and security
- [ ] Add media thumbnails/previews
- [ ] Add user permissions and roles
- [ ] Add media categories/tags
- [ ] Add API documentation (Swagger)

## Troubleshooting

### YouTube Download Issues

**Error**: "Sign in to confirm you're not a bot"
- **Solution**: You need to set up YouTube cookies (see "Setting Up YouTube Cookies" section above)

**Error**: "yt-dlp is not installed"
- **Solution**: Install yt-dlp using `pip install yt-dlp` or your package manager

**Error**: "Cookies file not found"
- **Solution**: Check that `YTDLP_COOKIES_PATH` points to the correct file location
- Verify the file exists: `ls -la $YTDLP_COOKIES_PATH`

### Database Issues

**Error**: "Connection refused to localhost:9002"
- **Solution**: Start the HSQLDB server first (see "Running the Application" section)

### File Upload Issues

**Error**: "Maximum upload size exceeded"
- **Solution**: Increase `MAX_FILE_SIZE` environment variable before starting the server

## Related Projects

- **alchemyServer**: Alchemy game backend
- **API Gateway**: Nginx routing between services
- **Frontend**: React applications for both services