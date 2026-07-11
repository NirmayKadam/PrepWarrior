# AI Mock Interview Platform

An AI-powered mock interview application built with **Spring Boot 4.1** and **Google Gemini AI**. Users create mock interviews by specifying a job position, description, and experience level — the app generates tailored interview questions, evaluates spoken/typed answers in real-time, and provides detailed feedback with ratings.

---

## Detailed Documentation

Find comprehensive project documentation under the [documents/](./documents) directory:
- [System Architecture](./documents/architecture.md) — High-level architecture, components, and data flow.
- [API Reference](./documents/api.md) — Exhaustive REST endpoint specifications with payload contracts.
- [Database & Caching](./documents/database.md) — JPA entity schemas, database layout, indexing, and Redis rate limit cache.
- [Setup & Installation](./documents/setup.md) — Prerequisites, environment variables, local run scripts, and Docker Compose guidance.

---

## Index

1. [Features](#features)
2. [Tech Stack](#tech-stack)
3. [Project Structure](#project-structure)
4. [Getting Started](#getting-started)
5. [API Reference](#api-reference)
6. [API Request/Response Examples](#api-requestresponse-examples)
7. [Database](#database)
8. [Docker](#docker)
9. [Architecture Overview](#architecture-overview)
10. [Configuration](#configuration)
11. [Frontend Pages](#frontend-pages)
12. [Error Handling](#error-handling)
13. [License](#license)

---

## Features

- **AI-Generated Interview Questions** — Gemini generates 5 role-specific questions based on job position, description, and experience level
- **Answer Evaluation & Feedback** — AI rates user answers (out of 10) with constructive feedback for improvement
- **Audio Transcription** — Upload audio recordings and get them transcribed via Gemini's multimodal capabilities
- **Company-Specific Question Bank** — Generate questions tailored to specific companies and question types
- **Contact/Newsletter Form** — Built-in contact form with email validation and persistence
- **H2 In-Memory Database** — Zero-config database with web console for development
- **Docker Support** — Multi-stage Dockerfile and Docker Compose for containerized deployment
- **Global Exception Handling** — Centralized error handling with meaningful JSON error responses

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Framework** | Spring Boot 4.1.0 |
| **Language** | Java 21 |
| **AI Integration** | Spring AI 2.0.0 + Google Gemini (`gemini-2.5-flash`) |
| **Database** | H2 (in-memory, default) / PostgreSQL (production-ready) |
| **ORM** | Spring Data JPA + Hibernate |
| **Build Tool** | Maven |
| **Code Generation** | Lombok |
| **Containerization** | Docker + Docker Compose |
| **Frontend** | Static HTML/CSS served from Spring Boot |

---

## Project Structure

```
SpringBootProject/
├── docker-compose.yml              # Docker Compose orchestration
├── Project/
│   ├── Dockerfile                   # Multi-stage Docker build
│   ├── pom.xml                      # Maven dependencies & build config
│   ├── src/main/
│   │   ├── java/com/example/mockinterview/
│   │   │   ├── MockinterviewApplication.java   # Entry point
│   │   │   ├── controller/
│   │   │   │   ├── MockInterviewController.java  # Interview CRUD + AI generation
│   │   │   │   ├── QuestionController.java       # Company question bank
│   │   │   │   ├── TranscribeController.java     # Audio transcription
│   │   │   │   ├── NewsletterController.java     # Contact form
│   │   │   │   ├── ViewController.java           # HTML page routing
│   │   │   │   └── GlobalExceptionHandler.java   # Centralized error handling
│   │   │   ├── model/
│   │   │   │   ├── MockInterview.java    # Interview entity
│   │   │   │   ├── Question.java         # Question bank entity
│   │   │   │   ├── UserAnswer.java       # User answer + feedback entity
│   │   │   │   └── Newsletter.java       # Contact form entity
│   │   │   ├── repository/
│   │   │   │   ├── MockInterviewRepository.java
│   │   │   │   ├── QuestionRepository.java
│   │   │   │   ├── UserAnswerRepository.java
│   │   │   │   └── NewsletterRepository.java
│   │   │   └── service/
│   │   │       └── GeminiService.java    # All Gemini AI interactions
│   │   └── resources/
│   │       ├── application.properties    # App configuration
│   │       └── static/                   # Frontend HTML/CSS
│   │           ├── index.html
│   │           ├── dashboard.html
│   │           ├── interview.html
│   │           ├── start.html
│   │           ├── feedback.html
│   │           ├── questions.html
│   │           └── styles.css
│   └── target/                           # Build output
```

---

## Getting Started

### Prerequisites

- **Java 21** or higher
- **Maven 3.9+** (or use included `mvnw` wrapper)
- **Gemini API Key** — get one from [Google AI Studio](https://aistudio.google.com/apikey)

### 1. Clone & Configure

```bash
git clone <repository-url>
cd SpringBootProject
```

Set your Gemini API key as an environment variable:

```bash
# Linux/macOS
export GEMINI_API_KEY=your_api_key_here

# Windows (PowerShell)
$env:GEMINI_API_KEY="your_api_key_here"

# Windows (CMD)
set GEMINI_API_KEY=your_api_key_here
```

### 2. Run Locally

```bash
cd Project

# Using Maven wrapper
./mvnw spring-boot:run        # Linux/macOS
.\mvnw.cmd spring-boot:run    # Windows
```

App starts at **http://localhost:8080**

### 3. Run with Docker

```bash
# From root directory (SpringBootProject/)
docker compose up --build
```

This builds a multi-stage Docker image (Maven build → JRE runtime) and runs on port **8080**.

---

## API Reference

### Interviews

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/interviews` | Create interview — generates 5 AI questions |
| `GET` | `/api/interviews/{id}` | Get interview by mock ID |
| `GET` | `/api/interviews/list` | List all interviews for current user |
| `POST` | `/api/interviews/{id}/answer` | Submit answer — get AI rating & feedback |
| `GET` | `/api/interviews/{id}/feedback` | Get all answers + feedback for interview |

### Questions (Company-Specific)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/questions` | Generate company-specific question bank |
| `GET` | `/api/questions/{id}` | Get question bank by ID |
| `GET` | `/api/questions/list` | List all question banks for current user |

### Transcription

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/transcribe` | Transcribe audio file (max 15MB) |

### Newsletter / Contact

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/newsletter` | Submit contact form |
### User Authentication

All user-scoped endpoints under `/api/**` require authentication. Pass the Firebase ID Token in the Authorization header:
- **Header**: `Authorization: Bearer <Firebase_ID_Token>`

---

## API Request/Response Examples

### Create Interview

```bash
curl -X POST http://localhost:8080/api/interviews \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <Firebase_ID_Token>" \
  -d '{
    "jobPosition": "Backend Developer",
    "jobDesc": "Design and build REST APIs with Spring Boot",
    "jobExperience": "3"
  }'
```

**Response** `201 Created`:
```json
{ "mockId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890" }
```

### Submit Answer

```bash
curl -X POST http://localhost:8080/api/interviews/{mockId}/answer \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <Firebase_ID_Token>" \
  -d '{
    "question": "Explain dependency injection in Spring.",
    "correctAns": "DI is a design pattern...",
    "userAns": "Dependency injection lets Spring manage object creation..."
  }'
```

**Response** `200 OK`:
```json
{
  "rating": "8",
  "feedback": "Good understanding of the core concept. Consider mentioning constructor vs setter injection and the @Autowired annotation for a more complete answer."
}
```

### Transcribe Audio

```bash
curl -X POST http://localhost:8080/api/transcribe \
  -F "audio=@recording.webm"
```

**Response** `200 OK`:
```json
{ "transcription": "My approach to system design involves..." }
```

---

## Database

### Default: H2 In-Memory

- Auto-configured, no setup needed
- **H2 Console**: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:interviewdb`
  - Username: `sa`
  - Password: *(empty)*
- Data resets on restart

### Production: PostgreSQL

PostgreSQL driver included. Switch by updating `application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/interviewdb
spring.datasource.driverClassName=org.postgresql.Driver
spring.datasource.username=your_user
spring.datasource.password=your_password
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

---

## Docker

### Dockerfile (Multi-Stage)

- **Build stage**: `maven:3.9-eclipse-temurin-21-alpine` — compiles the JAR
- **Run stage**: `eclipse-temurin:21-jre-alpine` — minimal JRE runtime (~200MB image)

### Docker Compose

```bash
# Build and run
docker compose up --build

# Run in background
docker compose up -d --build

# Stop
docker compose down
```

Environment variable `GEMINI_API_KEY` passed through to container. Set it in your shell or create a `.env` file:

```env
GEMINI_API_KEY=your_api_key_here
```

---

## Architecture Overview

```
┌─────────────┐     HTTP      ┌──────────────────┐     Spring AI     ┌─────────────┐
│   Browser    │ ◄──────────► │   Spring Boot    │ ◄──────────────► │  Gemini AI   │
│  (Static UI) │              │   REST API       │                   │  (2.5-flash) │
└─────────────┘              └──────┬───────────┘                   └─────────────┘
                                    │
                                    │ JPA
                                    ▼
                             ┌──────────────┐
                             │  H2 / Postgres│
                             │   Database    │
                             └──────────────┘
```

**Flow**: User fills interview form → Controller validates & sanitizes → GeminiService prompts AI → JSON response saved to DB → User answers questions → AI evaluates & rates → Feedback persisted & returned.

---

## Configuration

Key properties in `application.properties`:

| Property | Description | Default |
|---|---|---|
| `spring.ai.google.genai.api-key` | Gemini API key | `${GEMINI_API_KEY}` |
| `spring.ai.google.genai.chat.options.model` | AI model | `gemini-2.5-flash` |
| `spring.datasource.url` | Database JDBC URL | `jdbc:h2:mem:interviewdb` |
| `spring.h2.console.enabled` | H2 web console | `true` |
| `spring.jpa.hibernate.ddl-auto` | Schema management | `update` |

---

## Frontend Pages

| Route | Page | Description |
|---|---|---|
| `/` | `index.html` | Landing page |
| `/dashboard` | `dashboard.html` | Interview list & creation |
| `/dashboard/interview/{id}` | `interview.html` | Interview detail |
| `/dashboard/interview/{id}/start` | `start.html` | Take the interview |
| `/dashboard/interview/{id}/feedback` | `feedback.html` | Review answers & AI feedback |
| `/dashboard/questions` | `questions.html` | Company question bank |

---

## Error Handling

Global exception handler returns consistent JSON:

```json
{ "error": "Description of what went wrong" }
```

| Exception | HTTP Status |
|---|---|
| `IllegalArgumentException` | `400 Bad Request` |
| `MaxUploadSizeExceededException` | `413 Payload Too Large` |
| AI response parse failure | `502 Bad Gateway` |
| All other exceptions | `500 Internal Server Error` |

---

## License

This project is for educational and personal use.
