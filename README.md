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
- **Media Sharing**: Public/private media with access controls
- **Search**: Find media by title, description, or user
- **File Management**: Secure file storage and retrieval

## Technology Stack

- **Backend**: Spring Boot 3.1.4, Java 17
- **Database**: HSQLDB (embedded)
- **Security**: Spring Security with BCrypt
- **File Upload**: Multipart file handling

## API Gateway Integration

Lexicon is designed to work with an API gateway alongside other services:

- **Gateway Port**: 8080 (nginx)
- **Lexicon Port**: 36568 (internal)
- **Database Port**: 9003 (HSQLDB)
- **API Prefix**: `/api/lexicon/`

## Quick Start

### Prerequisites
- Java 17+
- Gradle 8.3+

### Running the Application

1. **Start the database server** (separate terminal):
   ```bash
   # TODO: Create database startup script
   ```

2. **Start Lexicon backend**:
   ```bash
   ./gradlew bootRun
   ```

3. **Test the API**:
   ```bash
   curl http://localhost:36568/api/health
   # or through gateway:
   curl http://localhost:8080/api/lexicon/health
   ```

## API Endpoints

### Health & Info
- `GET /api/health` - Service health check
- `GET /api/info` - Service information

### Authentication (TODO)
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - User login

### Media Management (TODO)
- `POST /api/media/upload` - Upload media file
- `GET /api/media/public` - Get public media files
- `GET /api/media/user/{userId}` - Get user's media files
- `GET /api/media/{id}` - Get specific media file
- `DELETE /api/media/{id}` - Delete media file

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
    FOREIGN KEY (uploaded_by) REFERENCES users(id)
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
│   │   └── LexiconTestController.java
│   ├── logic/                     # Business logic services
│   │   ├── UserManagerService.java
│   │   └── MediaManagerService.java
│   ├── data/                      # Database access
│   │   ├── ILexiconDatabase.java
│   │   └── HSQLLexiconDatabase.java
│   └── object/                    # Domain objects
│       ├── User.java
│       └── MediaFile.java
├── src/main/resources/
│   └── application.properties
├── lib/                          # HSQLDB jar files
├── build.gradle
└── README.md
```

## TODO

- [ ] Implement UserManagerService
- [ ] Implement MediaManagerService  
- [ ] Create database initialization scripts
- [ ] Implement authentication controllers
- [ ] Implement media upload/download controllers
- [ ] Add file validation and security
- [ ] Add media thumbnails/previews
- [ ] Add user permissions and roles
- [ ] Add media categories/tags
- [ ] Add API documentation (Swagger)

## Related Projects

- **alchemyServer**: Alchemy game backend
- **API Gateway**: Nginx routing between services
- **Frontend**: React applications for both services