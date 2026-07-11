# Setup and Installation Guide - AI Mock Interview Platform

This document describes how to configure, build, and run the AI Mock Interview application locally and via Docker.

---

## 1. Prerequisites

Ensure you have the following software installed:
- **Java Development Kit (JDK) 21** or higher.
- **Apache Maven 3.9+** (or use the provided `mvnw` wrapper).
- **Docker & Docker Compose** (required for containerized setups).
- **Google Gemini API Key**: Get a key from [Google AI Studio](https://aistudio.google.com/apikey).

---

## 2. Environment Variables

The project utilizes environment variables to inject secrets and settings. Create a `.env` file in the root workspace directory with the following contents:

```env
# Google Gemini API key
GEMINI_API_KEY=your_gemini_api_key_here

# Base64-encoded Firebase Service Account JSON key (optional in dev, required for API auth)
FIREBASE_CONFIG_B64=your_base64_encoded_json_key_here

# Redis host & port (defaults to localhost:6379 if not set)
REDIS_HOST=localhost
REDIS_PORT=6379

# PostgreSQL database settings (optional, defaults to H2 in-memory in dev)
DB_HOST=localhost
DB_PORT=5432
DB_NAME=interviewdb
DB_USER=postgres
DB_PASSWORD=password
```

> [!NOTE]
> If `FIREBASE_CONFIG_B64` is omitted, the Firebase Admin SDK will not initialize. In this state, requests calling `/api/**` endpoints will fail with a `401 Unauthorized` error since the Security Filter Chain mandates authentication.

---

## 3. Running Locally

### Step 1: Set Environment Variables
Set your environment variables in your terminal session before launching:

```bash
# Linux / macOS
export GEMINI_API_KEY="your_api_key_here"
export FIREBASE_CONFIG_B64="your_base64_key_here"

# Windows (PowerShell)
$env:GEMINI_API_KEY="your_api_key_here"
$env:FIREBASE_CONFIG_B64="your_base64_key_here"

# Windows (Command Prompt)
set GEMINI_API_KEY=your_api_key_here
set FIREBASE_CONFIG_B64=your_base64_key_here
```

### Step 2: Start Redis (for Rate Limiting)
Make sure you have a local Redis server running on port `6379`, or run it via Docker:
```bash
docker run -d --name local-redis -p 6379:6379 redis:alpine
```

### Step 3: Run the Application
Navigate to the `Project/` folder and boot the Spring application:

```bash
cd Project

# Linux / macOS
./mvnw spring-boot:run

# Windows
.\mvnw.cmd spring-boot:run
```

The application will start on **http://localhost:8080**.

---

## 4. Running with Docker Compose

To boot the entire application stack including the Spring Boot service, PostgreSQL, and Redis automatically:

```bash
# Verify .env exists in the root directory (where docker-compose.yml resides)
# Start the containers
docker compose up -d --build
```

### Useful Commands:
- **View logs**: `docker compose logs -f`
- **Stop services**: `docker compose down`
- **Rebuild app container**: `docker compose build`

---

## 5. Directory Mapping

The application serves static web assets directly from the Java classpath:
- **Backend Code**: `Project/src/main/java/`
- **Frontend Assets**: `Project/src/main/resources/static/`
- **App Configuration**: `Project/src/main/resources/application.properties`
