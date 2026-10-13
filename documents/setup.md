# Setup & Installation Guide — PrepWarrior (AI Mock Interview Platform)

> **Purpose of this document**: Walk any developer — from a first-time contributor to a senior engineer — through every step needed to configure, build, and run the PrepWarrior application. This guide covers local development, Docker Compose deployment, all required environment variables, external service setup (Firebase, Redis, PostgreSQL), and troubleshooting. After reading this, you should be able to go from a fresh clone to a running application.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Project Directory Structure](#2-project-directory-structure)
3. [Environment Variables — Complete Reference](#3-environment-variables--complete-reference)
4. [External Service Setup](#4-external-service-setup)
   - [4.1 Google Gemini API Key](#41-google-gemini-api-key)
   - [4.2 Firebase Authentication Setup](#42-firebase-authentication-setup)
   - [4.3 Redis Setup](#43-redis-setup)
5. [Running Locally (Without Docker)](#5-running-locally-without-docker)
   - [5.1 Quick Start — Minimal Setup](#51-quick-start--minimal-setup)
   - [5.2 Full Setup — With All Services](#52-full-setup--with-all-services)
6. [Running with Docker Compose](#6-running-with-docker-compose)
7. [Build Process — How Maven Compiles the Project](#7-build-process--how-maven-compiles-the-project)
8. [Docker Build — Multi-Stage Dockerfile Explained](#8-docker-build--multi-stage-dockerfile-explained)
9. [Configuration Reference — application.properties](#9-configuration-reference--applicationproperties)
10. [Frontend Assets — Static File Serving](#10-frontend-assets--static-file-serving)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. Prerequisites

### Required Software

| Software | Minimum Version | Purpose | Installation |
|---|---|---|---|
| **Java Development Kit (JDK)** | 21 | Compiles and runs the Spring Boot application | [Adoptium Temurin](https://adoptium.net/) or `winget install EclipseAdoptium.Temurin.21.JDK` |
| **Maven** | 3.9+ | Build tool (or use the included `mvnw` / `mvnw.cmd` wrapper) | Included in project as `mvnw.cmd` |
| **Git** | Any | Clone the repository | [git-scm.com](https://git-scm.com/) |

### Optional Software (for full functionality)

| Software | Purpose | When Needed |
|---|---|---|
| **Docker Desktop** | Run the full stack (app + PostgreSQL + Redis) in containers | When using Docker Compose |
| **Redis** | Rate limiting cache | Only if running locally without Docker and you want rate limiting |
| **PostgreSQL** | Production-grade database | Only if running locally without Docker and you don't want H2 in-memory |

### Verify Installations

```powershell
# Check Java version (must be 21+)
java -version

# Check Maven version (if installed globally — optional if using mvnw)
mvn -version

# Check Docker (optional)
docker --version
docker compose version
```

---

## 2. Project Directory Structure

Understanding where things live is essential before configuration:

```
SpringBootProject/                      ← Root directory (where you clone)
├── .env                                ← Environment variables (secrets — DO NOT COMMIT)
├── docker-compose.yml                  ← Docker Compose orchestration (3 services)
├── README.md                           ← Project overview
├── documents/                          ← This documentation directory
│   ├── architecture.md
│   ├── api.md
│   ├── database.md
│   └── setup.md                        ← You are here
│
└── Project/                            ← Spring Boot application root
    ├── Dockerfile                      ← Multi-stage Docker build
    ├── pom.xml                         ← Maven build configuration & dependencies
    ├── mvnw / mvnw.cmd                 ← Maven wrapper (no global Maven needed)
    ├── src/main/
    │   ├── java/com/example/mockinterview/
    │   │   ├── MockinterviewApplication.java   ← @SpringBootApplication entry point
    │   │   ├── config/                         ← Security, Firebase, rate limiting config
    │   │   ├── controller/                     ← REST API endpoints
    │   │   ├── model/                          ← JPA entities (database tables)
    │   │   ├── repository/                     ← Data access interfaces
    │   │   └── service/                        ← Business logic (AI integration)
    │   └── resources/
    │       ├── application.properties          ← App configuration
    │       └── static/                         ← Frontend HTML/CSS/JS files
    └── target/                                 ← Build output (generated, gitignored)
```

### Key Paths

| Purpose | Path |
|---|---|
| Set environment variables | `SpringBootProject/.env` |
| Run Maven commands | `SpringBootProject/Project/` |
| Run Docker Compose | `SpringBootProject/` (root) |
| Edit app configuration | `SpringBootProject/Project/src/main/resources/application.properties` |
| Edit frontend pages | `SpringBootProject/Project/src/main/resources/static/` |

---

## 3. Environment Variables — Complete Reference

The application requires environment variables for secrets and service configuration. **Never hardcode these in source code.**

### How to Set Environment Variables

**Option A: `.env` file** (recommended for Docker Compose)
Create a file at `SpringBootProject/.env`:

```env
# ──────────────────────────────────────────
# REQUIRED — Application will not function without these
# ──────────────────────────────────────────

# Google Gemini API key for AI question generation and evaluation
# Get from: https://aistudio.google.com/apikey
GEMINI_API_KEY=your_gemini_api_key_here

# Base64-encoded Firebase Service Account JSON
# Required for API authentication (verifying user JWT tokens)
# Get from: Firebase Console → Project Settings → Service Accounts → Generate New Private Key
# Encode it: base64 -w0 path/to/serviceAccountKey.json (Linux/Mac)
#            certutil -encode serviceAccountKey.json temp.b64 (Windows, then copy content)
FIREBASE_CONFIG_B64=your_base64_encoded_json_here

# ──────────────────────────────────────────
# OPTIONAL — Have sensible defaults
# ──────────────────────────────────────────

# PostgreSQL configuration (only needed if NOT using H2)
DB_HOST=localhost
DB_PORT=5432
DB_NAME=interviewdb
DB_USER=postgres
DB_PASSWORD=postgres

# Firebase client SDK configuration (defaults are set in application.properties)
# Override these if using a different Firebase project
FIREBASE_CLIENT_API_KEY=AIzaSy...
FIREBASE_CLIENT_AUTH_DOMAIN=your-project.firebaseapp.com
FIREBASE_CLIENT_PROJECT_ID=your-project-id
FIREBASE_CLIENT_STORAGE_BUCKET=your-project.firebasestorage.app
FIREBASE_CLIENT_MESSAGING_SENDER_ID=123456789
FIREBASE_CLIENT_APP_ID=1:123:web:abc
FIREBASE_CLIENT_MEASUREMENT_ID=G-XXXXXXXXXX
```

**Option B: Shell environment variables** (for local development without Docker)

```powershell
# PowerShell (Windows)
$env:GEMINI_API_KEY="your_api_key_here"
$env:FIREBASE_CONFIG_B64="your_base64_key_here"
```

```bash
# Bash (Linux/macOS)
export GEMINI_API_KEY="your_api_key_here"
export FIREBASE_CONFIG_B64="your_base64_key_here"
```

### Variable Reference Table

| Variable | Required | Default | Used By | Description |
|---|---|---|---|---|
| `GEMINI_API_KEY` | ✅ **Yes** | (none) | `application.properties` → Spring AI | Authenticates requests to the Google Gemini API |
| `FIREBASE_CONFIG_B64` | ✅ **Yes** (for auth) | (none) | `FirebaseConfig.java` | Base64-encoded Firebase service account JSON. Used to initialize Firebase Admin SDK for JWT verification. |
| `DB_HOST` | ❌ | `localhost` | `application.properties` | PostgreSQL hostname |
| `DB_PORT` | ❌ | `5432` | `application.properties` | PostgreSQL port |
| `DB_NAME` | ❌ | `interviewdb` | `application.properties` | PostgreSQL database name |
| `DB_USER` | ❌ | `postgres` | `application.properties` | PostgreSQL username |
| `DB_PASSWORD` | ❌ | `postgres` | `application.properties` | PostgreSQL password |
| `SPRING_DATA_REDIS_HOST` | ❌ | `localhost` | Spring auto-config | Redis server hostname (set in Docker Compose) |
| `SPRING_DATA_REDIS_PORT` | ❌ | `6379` | Spring auto-config | Redis server port |

### What Happens When Variables Are Missing?

| Missing Variable | Behavior |
|---|---|
| `GEMINI_API_KEY` | Application starts but **all AI features fail** — interview creation, answer evaluation, transcription, and question generation all throw errors. |
| `FIREBASE_CONFIG_B64` | Firebase Admin SDK **does not initialize**. `FirebaseTokenFilter` skips JWT verification. Spring Security's `authenticated()` rule on `/api/**` will reject all requests with `401 Unauthorized` since no Principal is ever set. |
| `DB_*` variables | Falls back to defaults (`localhost:5432/interviewdb/postgres/postgres`). If no PostgreSQL is running, the app **fails to start** unless you switch to H2. |
| Redis variables | App starts fine. Rate limiting is disabled (filter catches connection errors and allows requests through). |

---

## 4. External Service Setup

### 4.1 Google Gemini API Key

The Gemini API key authenticates requests to Google's AI model. Every AI feature (question generation, answer evaluation, audio transcription) uses this.

**Steps to obtain**:
1. Go to [Google AI Studio](https://aistudio.google.com/apikey)
2. Sign in with a Google account
3. Click **"Create API Key"**
4. Copy the key string (starts with `AI...`)
5. Set it as `GEMINI_API_KEY` in your `.env` or shell

**Cost**: The Gemini 2.5 Flash model has a generous free tier. For development and small-scale usage, you're unlikely to incur charges. Check [Google AI pricing](https://ai.google.dev/pricing) for current limits.

### 4.2 Firebase Authentication Setup

Firebase Authentication handles user login on the frontend (Google Sign-In, email/password). The backend verifies Firebase-issued JWT tokens.

#### Step 1: Create a Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click **"Add Project"** → name it (e.g., `prepwarrior`) → create
3. Enable **Authentication**: Go to Build → Authentication → Get Started
4. Enable sign-in providers: Google, Email/Password, or others as needed

#### Step 2: Get the Service Account Key (for Backend)

1. Firebase Console → **Project Settings** (gear icon) → **Service Accounts** tab
2. Click **"Generate New Private Key"** → downloads a JSON file
3. **Base64-encode** the file:

```powershell
# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("path\to\serviceAccountKey.json"))
```

```bash
# Linux/macOS
base64 -w0 path/to/serviceAccountKey.json
```

4. Set the encoded string as `FIREBASE_CONFIG_B64`

> **⚠️ SECURITY**: The service account key grants full admin access to your Firebase project. Never commit it to source control. Always use environment variables.

#### Step 3: Get Client Config (for Frontend)

1. Firebase Console → **Project Settings** → **General** tab → scroll to "Your apps"
2. Add a **Web app** if you haven't already
3. Copy the `firebaseConfig` object values
4. These are set in `application.properties` as `firebase.client.*` properties (with environment variable overrides)

### 4.3 Redis Setup

Redis is used for API rate limiting. It's optional — the app works without it (just no rate limiting).

#### Option A: Via Docker (Recommended)

```powershell
docker run -d --name local-redis -p 6379:6379 redis:alpine
```

#### Option B: Native Installation

```powershell
# Windows (via Chocolatey)
choco install redis-64

# Start Redis server
redis-server
```

#### Verify Redis is Running

```powershell
redis-cli ping
# Expected response: PONG
```

---

## 5. Running Locally (Without Docker)

### 5.1 Quick Start — Minimal Setup

This gets you running with the bare minimum — H2 database (in-memory), no Redis, no Firebase auth.

#### Step 1: Set the API Key

```powershell
$env:GEMINI_API_KEY="your_gemini_api_key_here"
```

#### Step 2: Override Database to H2

You can either edit `application.properties` temporarily or set environment variables to override:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:h2:mem:interviewdb"
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME="org.h2.Driver"
$env:SPRING_DATASOURCE_USERNAME="sa"
$env:SPRING_DATASOURCE_PASSWORD=""
$env:SPRING_JPA_DATABASE_PLATFORM="org.hibernate.dialect.H2Dialect"
$env:SPRING_H2_CONSOLE_ENABLED="true"
```

#### Step 3: Run

```powershell
cd "d:\Nirmay pc\ENGINEERING\projects\SpringBootProject\Project"
.\mvnw.cmd spring-boot:run
```

#### Step 4: Access

- **Application**: http://localhost:8080
- **H2 Console**: http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:interviewdb`, user: `sa`, no password)

> **Note**: Without `FIREBASE_CONFIG_B64`, all `/api/**` endpoints (except newsletter and firebase.js) will return `401 Unauthorized` because the security filter chain requires authentication. To test API endpoints directly (e.g., with cURL/Postman), you'll need Firebase auth set up.

### 5.2 Full Setup — With All Services

#### Step 1: Set All Environment Variables

```powershell
$env:GEMINI_API_KEY="your_gemini_api_key"
$env:FIREBASE_CONFIG_B64="your_base64_encoded_firebase_key"
```

#### Step 2: Start Redis

```powershell
docker run -d --name local-redis -p 6379:6379 redis:alpine
```

#### Step 3: Start PostgreSQL (or use H2)

```powershell
# If using Docker for PostgreSQL:
docker run -d --name local-postgres -p 5432:5432 -e POSTGRES_DB=interviewdb -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:alpine
```

#### Step 4: Run the Application

```powershell
cd "d:\Nirmay pc\ENGINEERING\projects\SpringBootProject\Project"
.\mvnw.cmd spring-boot:run
```

The application starts at **http://localhost:8080**.

---

## 6. Running with Docker Compose

Docker Compose starts the entire stack — Spring Boot app + PostgreSQL + Redis — with a single command. This is the closest to a production environment.

### Prerequisites

- Docker Desktop installed and running
- `.env` file in the root directory (`SpringBootProject/.env`) with `GEMINI_API_KEY` and `FIREBASE_CONFIG_B64` set

### Commands

```powershell
# Navigate to root directory (where docker-compose.yml is)
cd "d:\Nirmay pc\ENGINEERING\projects\SpringBootProject"

# Build and start all services
docker compose up --build

# Or run in background (detached mode)
docker compose up -d --build

# View real-time logs
docker compose logs -f

# View logs for a specific service
docker compose logs -f app

# Stop all services
docker compose down

# Stop and remove all data (including PostgreSQL data volume)
docker compose down -v

# Rebuild only the app container (after code changes)
docker compose build app
docker compose up -d app
```

### What Docker Compose Does

The `docker-compose.yml` defines three services:

```mermaid
graph LR
    subgraph "Docker Compose Stack"
        A["mock-interview-app<br/>Port 8080<br/>Spring Boot"] -->|"JDBC over<br/>Docker network"| B["mock-interview-db<br/>Port 5432<br/>PostgreSQL Alpine"]
        A -->|"Redis protocol<br/>Docker network"| C["mock-interview-redis<br/>Port 6379<br/>Redis Alpine"]
    end
    D["Host Machine<br/>Browser"] -->|"http://localhost:8080"| A
    E["Host Machine<br/>DB client"| -->|"localhost:5432"| B
```

| Service | Container Name | Image | Ports | Data Persistence |
|---|---|---|---|---|
| `app` | `mock-interview-app` | Custom (built from `Project/Dockerfile`) | 8080:8080 | Stateless — connects to DB |
| `postgres` | `mock-interview-db` | `postgres:alpine` | 5432:5432 | `pgdata` Docker volume (survives `docker compose down`) |
| `redis` | `mock-interview-redis` | `redis:alpine` | 6379:6379 | Ephemeral (resets on container restart) |

### Environment Variables in Docker Compose

The `docker-compose.yml` passes these to the Spring Boot container:

| Variable | Value | Purpose |
|---|---|---|
| `GEMINI_API_KEY` | From `.env` | AI API key |
| `FIREBASE_CONFIG_B64` | From `.env` | Firebase service account |
| `SPRING_DATA_REDIS_HOST` | `redis` (Docker service name) | Redis hostname |
| `DB_HOST` | `postgres` (Docker service name) | PostgreSQL hostname |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `interviewdb` | Database name |
| `DB_USER` | `postgres` | Database user |
| `DB_PASSWORD` | From `.env` or `postgres` (default) | Database password |

### Static File Hot-Reload

The Docker Compose config mounts the local static files directory into the container:

```yaml
volumes:
  - ./Project/src/main/resources/static:/app/static
```

Combined with:
```yaml
environment:
  - SPRING_WEB_RESOURCES_STATIC_LOCATIONS=file:/app/static/,classpath:/static/
```

This means you can edit `*.html`, `*.css`, and `*.js` files locally and see changes in the browser **without rebuilding the Docker image** — just refresh the page. Only Java code changes require a rebuild.

---

## 7. Build Process — How Maven Compiles the Project

### What Maven Does

Maven is the build tool that:
1. Downloads all dependencies from Maven Central (listed in `pom.xml`)
2. Compiles Java source code (with Lombok annotation processing)
3. Runs tests (if not skipped)
4. Packages everything into a single executable **fat JAR** (includes all dependencies + static resources)

### Key Maven Commands

```powershell
cd "d:\Nirmay pc\ENGINEERING\projects\SpringBootProject\Project"

# Build the project (compile + test + package)
.\mvnw.cmd clean package

# Build without running tests (faster)
.\mvnw.cmd clean package -DskipTests

# Run directly (compile + start Spring Boot)
.\mvnw.cmd spring-boot:run

# Just compile (no package/test)
.\mvnw.cmd compile
```

### Maven Wrapper (`mvnw.cmd`)

The project includes a **Maven Wrapper** — a small script that automatically downloads the correct Maven version. You don't need Maven installed globally. Just use `.\mvnw.cmd` instead of `mvn`.

### Dependencies (from `pom.xml`)

| Dependency | Version | Purpose |
|---|---|---|
| `spring-boot-starter-webmvc` | (from parent 4.1.0) | Spring MVC for REST controllers and static resource serving |
| `spring-boot-starter-data-jpa` | (from parent) | JPA + Hibernate for database access |
| `spring-boot-starter-validation` | (from parent) | Bean validation (`@Valid`, `@NotNull`, etc.) |
| `spring-boot-starter-security` | (from parent) | Spring Security for filter chains and auth |
| `spring-boot-starter-data-redis` | (from parent) | Redis client (`StringRedisTemplate`) for rate limiting |
| `spring-ai-starter-model-google-genai` | 2.0.0 (via BOM) | Spring AI integration with Google Gemini |
| `firebase-admin` | 9.3.0 | Firebase Admin SDK for JWT verification |
| `spring-boot-h2console` | (from parent) | H2 database web console |
| `h2` | (runtime) | H2 in-memory database driver |
| `postgresql` | (runtime) | PostgreSQL database driver |
| `lombok` | (compile-only) | Code generation (getters, setters, builders) |

### Annotation Processing (Lombok)

Lombok generates code at compile time. The `pom.xml` explicitly configures the Maven Compiler Plugin with Lombok as an annotation processor:

```xml
<annotationProcessorPaths>
    <path>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </path>
</annotationProcessorPaths>
```

This ensures Lombok processes `@Data`, `@Builder`, `@NoArgsConstructor`, etc. during both main compilation and test compilation.

---

## 8. Docker Build — Multi-Stage Dockerfile Explained

The `Project/Dockerfile` uses a **multi-stage build** to create a minimal production image:

```dockerfile
# ──── Stage 1: BUILD ────
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# ──── Stage 2: RUN ────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Why Multi-Stage?

| | Single Stage | Multi-Stage (Our Approach) |
|---|---|---|
| **Final image contains** | JDK + Maven + source code + dependencies + JAR | JRE + JAR only |
| **Image size** | ~800MB | **~200MB** |
| **Security** | Source code and build tools exposed | Minimal attack surface |
| **Build tools in prod** | Yes (unnecessary) | No |

### How It Works

1. **Build stage**: Uses the full Maven + JDK image to compile the project. Runs `mvn clean package -DskipTests` which produces a fat JAR in `target/`.
2. **Run stage**: Uses the minimal JRE-only image (no compiler, no Maven). Copies just the JAR from the build stage. The intermediate build stage is discarded.
3. **`EXPOSE 8080`**: Documents that the app listens on port 8080 (Docker metadata, doesn't actually publish the port — `docker-compose.yml` handles that with `ports: "8080:8080"`).
4. **`ENTRYPOINT`**: Runs the application with `java -jar app.jar`.

---

## 9. Configuration Reference — `application.properties`

Complete reference for all properties in `src/main/resources/application.properties`:

### Application

| Property | Value | Description |
|---|---|---|
| `spring.application.name` | `mockinterview` | Application name (used in logging and actuator) |

### Database

| Property | Value | Description |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:interviewdb}?sslmode=require` | JDBC connection URL with SSL |
| `spring.datasource.driverClassName` | `org.postgresql.Driver` | PostgreSQL JDBC driver class |
| `spring.datasource.username` | `${DB_USER:postgres}` | Database username |
| `spring.datasource.password` | `${DB_PASSWORD:postgres}` | Database password |
| `spring.h2.console.enabled` | `false` | H2 web console (disabled for PostgreSQL) |

### Connection Pool (HikariCP)

| Property | Value | Description |
|---|---|---|
| `spring.datasource.hikari.maximum-pool-size` | `10` | Max concurrent DB connections |
| `spring.datasource.hikari.minimum-idle` | `2` | Min idle connections kept open |
| `spring.datasource.hikari.idle-timeout` | `30000` (30s) | Close idle connections after this |
| `spring.datasource.hikari.connection-timeout` | `20000` (20s) | Max wait time for a connection |
| `spring.datasource.hikari.max-lifetime` | `1800000` (30min) | Recycle connections after this |

### JPA / Hibernate

| Property | Value | Description |
|---|---|---|
| `spring.jpa.database-platform` | `org.hibernate.dialect.PostgreSQLDialect` | SQL dialect for query generation |
| `spring.jpa.hibernate.ddl-auto` | `update` | Auto-create/update tables at startup |
| `spring.jpa.show-sql` | `false` | Don't log SQL queries (set to `true` for debugging) |

### Spring AI (Gemini)

| Property | Value | Description |
|---|---|---|
| `spring.ai.google.genai.api-key` | `${GEMINI_API_KEY}` | Gemini API key from environment |
| `spring.ai.google.genai.chat.options.model` | `gemini-2.5-flash` | AI model to use |

### Logging

| Property | Value | Description |
|---|---|---|
| `logging.level.org.springframework.ai` | `DEBUG` | Verbose logging for Spring AI (useful for debugging prompts/responses) |

### Firebase Client Config

| Property | Default Value | Description |
|---|---|---|
| `firebase.client.apiKey` | `AIzaSy...` | Firebase Web API key |
| `firebase.client.authDomain` | `prepwarrior-467c4.firebaseapp.com` | Firebase auth domain |
| `firebase.client.projectId` | `prepwarrior-467c4` | Firebase project ID |
| `firebase.client.storageBucket` | `prepwarrior-467c4.firebasestorage.app` | Firebase storage bucket |
| `firebase.client.messagingSenderId` | `525786522065` | FCM sender ID |
| `firebase.client.appId` | `1:525786522065:web:...` | Firebase app ID |
| `firebase.client.measurementId` | `G-TMQQLPW67T` | Google Analytics measurement ID |

> **Note**: All Firebase client config values can be overridden with environment variables following the naming convention `FIREBASE_CLIENT_API_KEY`, `FIREBASE_CLIENT_AUTH_DOMAIN`, etc.

---

## 10. Frontend Assets — Static File Serving

### How Spring Boot Serves Static Files

Spring Boot automatically serves files from `src/main/resources/static/` at the root URL path. For example:
- `static/index.html` → `http://localhost:8080/index.html`
- `static/styles.css` → `http://localhost:8080/styles.css`

### Page Routing (Clean URLs)

The `ViewController` maps clean URL paths to static HTML files:

| Browser URL | Served File | How |
|---|---|---|
| `/` | `index.html` | `forward:/index.html` |
| `/dashboard` | `dashboard.html` | `forward:/dashboard.html` |
| `/dashboard/interview/{id}` | `interview.html` | `forward:/interview.html` |
| `/dashboard/interview/{id}/start` | `start.html` | `forward:/start.html` |
| `/dashboard/interview/{id}/feedback` | `feedback.html` | `forward:/feedback.html` |
| `/dashboard/questions` | `questions.html` | `forward:/questions.html` |

The `forward:/` mechanism is an internal server-side forward (not a redirect) — the browser URL stays as the clean path.

### Frontend Files

| File | Purpose |
|---|---|
| `index.html` | Landing page — project introduction, features, contact form |
| `dashboard.html` | User dashboard — lists interviews, create new interview |
| `interview.html` | Interview detail — shows generated questions |
| `start.html` | Take the interview — answer questions, record audio |
| `feedback.html` | Review results — see ratings and AI feedback |
| `questions.html` | Company question bank — generate and browse question sets |
| `styles.css` | Global stylesheet for all pages |

---

## 11. Troubleshooting

### Application Won't Start

| Error | Cause | Fix |
|---|---|---|
| `Connection refused` to PostgreSQL | PostgreSQL not running or wrong host/port | Start PostgreSQL, or switch to H2 (see [Quick Start](#51-quick-start--minimal-setup)) |
| `GEMINI_API_KEY` is null | Environment variable not set | Set `$env:GEMINI_API_KEY` in PowerShell or add to `.env` |
| `Port 8080 already in use` | Another process on port 8080 | Kill the process: `netstat -aon | findstr 8080`, then `taskkill /PID <pid> /F` |
| Lombok compilation errors | Annotation processor not configured | Ensure your IDE has Lombok plugin installed; Maven build should work regardless |

### API Returns 401 Unauthorized

| Cause | Fix |
|---|---|
| `FIREBASE_CONFIG_B64` not set | Set the environment variable with your base64-encoded service account key |
| Expired Firebase token | Token expires after ~1 hour; refresh it via `getIdToken(true)` in frontend |
| Invalid token | Ensure you're using the correct Firebase project's tokens |

### AI Features Return 502 Bad Gateway

| Cause | Fix |
|---|---|
| Invalid `GEMINI_API_KEY` | Verify the key is correct and active in [AI Studio](https://aistudio.google.com/apikey) |
| Gemini rate limit exceeded | Wait and retry; consider upgrading to a paid plan |
| AI returned malformed JSON | Retry the request; Gemini occasionally produces imperfect formatting |

### Redis Connection Issues

| Symptom | Cause | Fix |
|---|---|---|
| Warning log: "Redis rate limiter unavailable" | Redis not running | Start Redis: `docker run -d -p 6379:6379 redis:alpine` |
| Rate limiting not working | Above warning means fail-open mode | This is by design — rate limiting is disabled when Redis is unavailable |

### Docker Compose Issues

| Issue | Fix |
|---|---|
| `app` container exits immediately | Check logs: `docker compose logs app` — usually a missing env variable |
| Database connection refused in Docker | Ensure `DB_HOST=postgres` (the Docker service name, not `localhost`) |
| Changes to Java code not reflected | Rebuild: `docker compose build app && docker compose up -d app` |
| Changes to HTML/CSS not reflected | Should be instant (volume mount). If not, try `docker compose restart app` |
| Disk space issues | Clean old images: `docker system prune -af` |
