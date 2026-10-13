# API Reference — PrepWarrior (AI Mock Interview Platform)

> **Purpose of this document**: Serve as the definitive, exhaustive reference for every REST endpoint exposed by the PrepWarrior backend. For each endpoint, this document explains *what it does*, *why it exists*, *how it connects to the broader architecture*, *the exact request/response contracts*, *validation rules*, *error scenarios*, and *the code path the request follows*. After reading this, you should be able to integrate with, debug, or explain any API endpoint.

---

## Table of Contents

1. [API Design Philosophy](#1-api-design-philosophy)
2. [Authentication & Authorization](#2-authentication--authorization)
3. [Request/Response Conventions](#3-requestresponse-conventions)
4. [Interview Management API](#4-interview-management-api)
   - [4.1 Create Mock Interview](#41-create-mock-interview)
   - [4.2 Get Interview Details](#42-get-interview-details)
   - [4.3 List User Interviews](#43-list-user-interviews)
   - [4.4 Submit & Evaluate Answer](#44-submit--evaluate-answer)
   - [4.5 Get Interview Feedback](#45-get-interview-feedback)
5. [Custom Question Bank API](#5-custom-question-bank-api)
   - [5.1 Generate Company Questions](#51-generate-company-questions)
   - [5.2 Get Question Bank Details](#52-get-question-bank-details)
   - [5.3 List User Question Banks](#53-list-user-question-banks)
6. [Audio Transcription API](#6-audio-transcription-api)
7. [Newsletter / Contact Form API](#7-newsletter--contact-form-api)
8. [Firebase Client Configuration API](#8-firebase-client-configuration-api)
9. [Error Handling — Global Exception Handler](#9-error-handling--global-exception-handler)
10. [Rate Limiting](#10-rate-limiting)
11. [API Summary Matrix](#11-api-summary-matrix)

---

## 1. API Design Philosophy

The API follows these design principles:

| Principle | Implementation |
|---|---|
| **RESTful resource naming** | Endpoints are nouns (`/api/interviews`, `/api/questions`), not verbs. HTTP methods convey the action (`POST` = create, `GET` = read). |
| **Stateless requests** | Every request carries its own authentication (JWT in `Authorization` header). No server-side sessions. |
| **Consistent error format** | All errors return `{"error": "message"}` regardless of the exception type (enforced by `GlobalExceptionHandler`). |
| **Input validation at the edge** | Controllers validate and sanitize all inputs before passing them to services. Invalid input never reaches the AI or database. |
| **AI response validation** | Every AI response is validated as parseable JSON before being stored or returned. Unparseable AI output returns `502 Bad Gateway`. |
| **User scoping** | All data-returning endpoints are scoped to the authenticated user. You can only see your own interviews, questions, and feedback. |

### How Controllers Map to the MVC Pattern

In MVC (Model-View-Controller), controllers are the "C" — they receive HTTP requests (from the View/client), orchestrate business logic, and return responses. In this REST API:

- **Input**: HTTP request → deserialized into a DTO (`@RequestBody` annotation)
- **Processing**: Controller calls `GeminiService` (Service Layer) and `*Repository` (Data Access Layer)
- **Output**: Java object → serialized to JSON by Jackson (`@RestController` auto-applies `@ResponseBody`)

Each `@RestController` is a Spring-managed **singleton bean** — one instance handles all concurrent requests. This is safe because the controllers are **stateless** (no instance variables that change per-request).

---

## 2. Authentication & Authorization

### Which Endpoints Require Authentication?

| Endpoint Pattern | Auth Required? | Reason |
|---|---|---|
| `/api/interviews/**` | ✅ Yes | User's interview data — must be scoped per-user |
| `/api/questions/**` | ✅ Yes | User's question banks — must be scoped per-user |
| `/api/transcribe` | ✅ Yes | AI transcription call — costs money, must track user |
| `/api/newsletter` | ❌ No | Public contact form — anyone should be able to submit |
| `/api/config/firebase.js` | ❌ No | Client-side SDK config — needed before login |
| `/`, `/dashboard/**`, static assets | ❌ No | Public HTML pages |

### How to Authenticate

1. **Frontend**: Sign in via Firebase Authentication (Google, email/password, etc.)
2. **Get ID Token**: Call `firebase.auth().currentUser.getIdToken()` — returns a JWT string
3. **Include in request**: Set the `Authorization` header:

```
Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6IjEyMzQ1Njc4OTAifQ.eyJpc3MiOiJodHRwczovL3NlY3VyZXRva2VuLmdvb2dsZS5jb20vcHJlcHdhcnJpb3ItNDY3YzQiLCJhdWQiOiJwcmVwd2FycmlvciIsImlhdCI6MTYyMTg2NjY2Nn0...
```

### Server-Side Verification (Code Path)

```
Request arrives
  → SecurityConfig.filterChain() route matching
  → If /api/** (not newsletter or firebase.js):
    → FirebaseTokenFilter.doFilterInternal()
      → Extracts "Bearer <token>" from Authorization header
      → Calls FirebaseAuth.getInstance().verifyIdToken(token)
      → On success: sets SecurityContext with email as Principal
      → On failure: clears SecurityContext → Spring returns 401
```

The `Principal` object (specifically `principal.getName()`) is then available in every controller method that declares a `java.security.Principal` parameter. Controllers use it to scope queries: `findByCreatedByOrderByIdDesc(principal.getName())`.

---

## 3. Request/Response Conventions

### Request Headers

| Header | Value | Required For |
|---|---|---|
| `Authorization` | `Bearer <Firebase_ID_Token>` | All `/api/**` except newsletter and firebase.js |
| `Content-Type` | `application/json` | All `POST` endpoints except transcription |
| `Content-Type` | `multipart/form-data` | `POST /api/transcribe` only |

### Response Format

**Success responses** vary by endpoint — they may return a JSON object, a JSON array, or a simple map.

**Error responses** always follow this format:
```json
{
  "error": "Human-readable error description"
}
```

### HTTP Status Codes Used

| Status | Meaning | When Used |
|---|---|---|
| `200 OK` | Request succeeded | GET responses, successful answer evaluation, newsletter submission |
| `201 Created` | Resource created | Interview creation (`POST /api/interviews`) |
| `400 Bad Request` | Client sent invalid data | Missing required fields, experience out of range, invalid email |
| `401 Unauthorized` | Missing or invalid auth token | No Bearer token, expired token, invalid token signature |
| `404 Not Found` | Requested resource doesn't exist | Interview or question bank with given ID not found |
| `413 Payload Too Large` | Upload exceeds size limit | Audio file > 15MB |
| `429 Too Many Requests` | Rate limit exceeded | More than 100 requests/min (authenticated) or 20/min (anonymous) |
| `502 Bad Gateway` | AI returned unparseable response | Gemini returned invalid JSON or was unavailable |
| `500 Internal Server Error` | Unexpected server error | Unhandled exceptions |

---

## 4. Interview Management API

**Controller**: `MockInterviewController` (`@RestController`, base path: `/api/interviews`)

**Responsibility in the architecture**: This is the core controller that manages the full interview lifecycle — from creating AI-generated interviews to evaluating user answers. It coordinates between the Service Layer (`GeminiService`) and the Data Access Layer (`MockInterviewRepository`, `UserAnswerRepository`).

### 4.1 Create Mock Interview

Generates 5 tailored interview questions using AI based on the user's job details.

| | |
|---|---|
| **URL** | `POST /api/interviews` |
| **Auth** | Required (Firebase JWT) |
| **Content-Type** | `application/json` |
| **Code Location** | `MockInterviewController.createInterview()` |

#### Request Body

```json
{
  "jobPosition": "Backend Developer",
  "jobDesc": "Design and build REST APIs with Spring Boot, work with PostgreSQL and Redis",
  "jobExperience": "3"
}
```

| Field | Type | Required | Validation Rules |
|---|---|---|---|
| `jobPosition` | String | ✅ | Must not be null/empty. Sanitized: `< > { }` stripped, truncated to 500 chars. |
| `jobDesc` | String | ✅ | Must not be null/empty. Same sanitization as above. |
| `jobExperience` | String | ✅ | Must be a valid integer between 0 and 50 (inclusive). |

#### Code Path (Step-by-Step)

1. **Deserialization**: Jackson deserializes the JSON body into `CreateInterviewRequest` (inner `@Data` class with `jobPosition`, `jobDesc`, `jobExperience` fields).
2. **User identification**: `principal.getName()` extracts the authenticated user's email from the Spring Security context (set by `FirebaseTokenFilter`).
3. **Validation**: Checks all three fields are non-null and non-empty. Parses `jobExperience` as integer and validates range 0-50.
4. **Sanitization**: The `sanitize()` private method removes `< > { }` characters (defense against XSS and prompt injection) and truncates to 500 characters.
5. **AI generation**: Calls `geminiService.generateQuestions(cleanPosition, cleanDesc, cleanExperience)`.
   - This builds a prompt like: *"Generate 5 interview questions and answers for: Job Position: Backend Developer, Job Description: ..., Years of Experience: 3. Please provide a valid JSON array with this exact format: [{"Question":"...","Answer":"..."}]"*
   - Gemini responds with a JSON array of 5 Q&A pairs.
   - The `cleanJson()` helper strips markdown code fences (` ```json ... ``` `).
6. **JSON validation**: `objectMapper.readTree(jsonMockResp)` verifies the AI response is valid JSON. If parsing fails → `502 Bad Gateway`.
7. **Persistence**: A `MockInterview` entity is built using Lombok's `@Builder`:
   - `mockId` = `UUID.randomUUID().toString()` (globally unique identifier for this interview)
   - `jsonMockResp` = the validated AI response (raw JSON string stored as `TEXT`)
   - `createdBy` = user's email
   - `createdAt` = today's date (`LocalDate.now().toString()`)
8. **Response**: Returns `201 Created` with the `mockId`.

#### Response

**`201 Created`**:
```json
{
  "mockId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**`400 Bad Request`** (validation failure):
```json
{
  "error": "All fields are required"
}
```
```json
{
  "error": "Years of experience must be a number"
}
```
```json
{
  "error": "Years of experience must be between 0 and 50"
}
```

**`502 Bad Gateway`** (AI response unparseable):
```json
{
  "error": "Failed to parse AI response. Please try again."
}
```

#### cURL Example

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

---

### 4.2 Get Interview Details

Fetch the generated questions and all metadata for a specific interview.

| | |
|---|---|
| **URL** | `GET /api/interviews/{id}` |
| **Auth** | Required |
| **Path Parameter** | `id` — the `mockId` (UUID string) of the interview |
| **Code Location** | `MockInterviewController.getInterview()` |

#### Code Path

1. `mockInterviewRepository.findByMockId(id)` — Spring Data JPA generates: `SELECT * FROM mock_interview WHERE mock_id = ?`
2. If found → returns the full `MockInterview` entity as JSON (`200 OK`)
3. If not found → returns `404 Not Found` with error message

> **Note**: This endpoint does **not** verify that the authenticated user owns the interview. Any authenticated user can fetch any interview by UUID. The UUID's randomness (128-bit) provides security through obscurity. If strict access control is needed, add `AND created_by = ?` to the query.

#### Response

**`200 OK`**:
```json
{
  "id": 1,
  "mockId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "jsonMockResp": "[{\"Question\":\"Explain dependency injection in Spring.\",\"Answer\":\"DI is a design pattern...\"}]",
  "jobPosition": "Backend Developer",
  "jobDesc": "Design and build REST APIs with Spring Boot",
  "jobExperience": "3",
  "createdBy": "john@example.com",
  "createdAt": "2026-10-13"
}
```

**`404 Not Found`**:
```json
{
  "error": "Interview not found"
}
```

---

### 4.3 List User Interviews

Fetch all mock interviews created by the currently authenticated user, ordered newest first.

| | |
|---|---|
| **URL** | `GET /api/interviews/list` |
| **Auth** | Required |
| **Code Location** | `MockInterviewController.listInterviews()` |

#### Code Path

1. Extracts user email from `principal.getName()`
2. Calls `mockInterviewRepository.findByCreatedByOrderByIdDesc(userEmail)`
   - Generated SQL: `SELECT * FROM mock_interview WHERE created_by = ? ORDER BY id DESC`
   - The `idx_mock_interview_created_by` database index ensures this query is fast even with many interviews
3. Returns the list as JSON (may be empty `[]` if the user has no interviews)

#### Response

**`200 OK`**:
```json
[
  {
    "id": 3,
    "mockId": "c3d4e5f6-a1b2-7890-abcd-ef1234567890",
    "jobPosition": "Frontend Engineer",
    "jobDesc": "React, TypeScript, CSS...",
    "jobExperience": "2",
    "createdBy": "john@example.com",
    "createdAt": "2026-10-13",
    "jsonMockResp": "[...]"
  },
  {
    "id": 1,
    "mockId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "jobPosition": "Backend Developer",
    "jobDesc": "Spring Boot APIs...",
    "jobExperience": "3",
    "createdBy": "john@example.com",
    "createdAt": "2026-10-12",
    "jsonMockResp": "[...]"
  }
]
```

---

### 4.4 Submit & Evaluate Answer

Submit a user's answer to an interview question. The AI evaluates it and returns a rating (out of 10) with constructive feedback. Both the answer and feedback are persisted.

| | |
|---|---|
| **URL** | `POST /api/interviews/{id}/answer` |
| **Auth** | Required |
| **Content-Type** | `application/json` |
| **Path Parameter** | `id` — the `mockId` of the interview this answer belongs to |
| **Code Location** | `MockInterviewController.recordAnswer()` |

#### Request Body

```json
{
  "question": "Explain dependency injection in Spring.",
  "correctAns": "DI is a design pattern where objects receive their dependencies from external sources rather than creating them internally...",
  "userAns": "Dependency injection lets Spring manage object creation. Instead of using 'new', Spring creates and injects dependencies."
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `question` | String | ✅ | The interview question being answered |
| `correctAns` | String | ❌ | The "model answer" generated by AI (for reference). Stored but not sent to AI for evaluation. |
| `userAns` | String | ✅ | The user's actual answer to be evaluated |

#### Code Path

1. **Validation**: Checks `question` and `userAns` are non-null/non-empty.
2. **AI evaluation**: Calls `geminiService.evaluateAnswer(question, userAns)`.
   - Prompt: *"Question: {question}. User Answer: {userAns}. Evaluate the user's answer. Provide a rating out of 10 and feedback (3-5 lines). Respond in this exact JSON format: {"rating": 7, "feedback": "Your feedback here."}"*
3. **Parse AI response**: Deserializes the JSON into a `Map` to extract `rating` and `feedback` fields.
4. **Persistence**: Saves a `UserAnswer` entity with:
   - `mockIdRef` = the interview's `mockId` (from URL path)
   - All answer data + AI feedback + rating
   - `userEmail` from the authenticated Principal
5. **Response**: Returns the `rating` and `feedback`.

#### Response

**`200 OK`**:
```json
{
  "rating": "8",
  "feedback": "Good understanding of the core concept. Consider mentioning constructor vs setter injection and the @Autowired annotation for a more complete answer."
}
```

**`400 Bad Request`**:
```json
{
  "error": "question and userAns are required"
}
```

**`502 Bad Gateway`**:
```json
{
  "error": "Failed to parse AI feedback. Please try again."
}
```

#### cURL Example

```bash
curl -X POST http://localhost:8080/api/interviews/a1b2c3d4-e5f6/answer \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <Firebase_ID_Token>" \
  -d '{
    "question": "Explain dependency injection in Spring.",
    "correctAns": "DI is a design pattern...",
    "userAns": "Spring manages object creation and injects dependencies."
  }'
```

---

### 4.5 Get Interview Feedback

Retrieve all recorded answers, AI ratings, and feedback for a given interview. Used by the feedback review page.

| | |
|---|---|
| **URL** | `GET /api/interviews/{id}/feedback` |
| **Auth** | Required |
| **Path Parameter** | `id` — the `mockId` of the interview |
| **Code Location** | `MockInterviewController.getFeedback()` |

#### Code Path

1. Calls `userAnswerRepository.findByMockIdRefOrderByIdAsc(id)`
   - Generated SQL: `SELECT * FROM user_answer WHERE mock_id_ref = ? ORDER BY id ASC`
   - Returns answers in submission order (oldest first)
2. Returns the list (may be empty if no answers submitted yet)

#### Response

**`200 OK`**:
```json
[
  {
    "id": 1,
    "mockIdRef": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "question": "Explain dependency injection in Spring.",
    "correctAns": "DI is a design pattern...",
    "userAns": "Spring manages object creation...",
    "feedback": "Good understanding. Consider mentioning...",
    "rating": "8",
    "userEmail": "john@example.com",
    "createdAt": "2026-10-13"
  },
  {
    "id": 2,
    "mockIdRef": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "question": "What is the difference between @Bean and @Component?",
    "correctAns": "...",
    "userAns": "...",
    "feedback": "...",
    "rating": "6",
    "userEmail": "john@example.com",
    "createdAt": "2026-10-13"
  }
]
```

---

## 5. Custom Question Bank API

**Controller**: `QuestionController` (`@RestController`, base path: `/api/questions`)

**Purpose**: Allows users to generate question banks tailored to a specific company and question type (behavioral, technical, system design, etc.). This is separate from the interview flow — it's a standalone study tool.

### 5.1 Generate Company Questions

| | |
|---|---|
| **URL** | `POST /api/questions` |
| **Auth** | Required |
| **Content-Type** | `application/json` |
| **Code Location** | `QuestionController.createQuestions()` |

#### Request Body

```json
{
  "jobPosition": "Software Engineer",
  "jobDesc": "Full-stack development with React and Node.js",
  "jobExperience": "2",
  "typeQuestion": "Behavioral",
  "company": "Google"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `jobPosition` | String | ✅ | Target job title |
| `jobDesc` | String | ✅ | Job description or responsibilities |
| `jobExperience` | String | ❌ | Years of experience (defaults to "0") |
| `typeQuestion` | String | ✅ | Category: "Behavioral", "Technical", "System Design", etc. |
| `company` | String | ✅ | Target company name: "Google", "Amazon", "Meta", etc. |

#### Code Path

1. Validates required fields (position, desc, type, company)
2. Calls `geminiService.generateCompanyQuestions(position, desc, experience, typeQuestion, company)`
   - Prompt includes company context and question type for targeted generation
3. Validates AI response is parseable JSON
4. Saves a `Question` entity with a UUID `mockId`
5. Returns the `mockId`

#### Response

**`200 OK`**:
```json
{
  "mockId": "f9e8d7c6-b5a4-3210-9fed-cba987654321"
}
```

---

### 5.2 Get Question Bank Details

| | |
|---|---|
| **URL** | `GET /api/questions/{id}` |
| **Auth** | Required |
| **Path Parameter** | `id` — the `mockId` of the question bank |
| **Code Location** | `QuestionController.getQuestions()` |

#### Response

**`200 OK`**:
```json
{
  "id": 1,
  "mockId": "f9e8d7c6-b5a4-3210-9fed-cba987654321",
  "mockQuestionJsonResp": "[{\"Question\":\"Tell me about a time you resolved a technical conflict.\",\"Answer\":\"Use the STAR method...\"}]",
  "jobPosition": "Software Engineer",
  "jobDesc": "Full-stack development with React and Node.js",
  "jobExperience": "2",
  "typeQuestion": "Behavioral",
  "company": "Google",
  "createdBy": "john@example.com",
  "createdAt": "2026-10-13"
}
```

**`404 Not Found`**:
```json
{
  "error": "Questions not found"
}
```

---

### 5.3 List User Question Banks

| | |
|---|---|
| **URL** | `GET /api/questions/list` |
| **Auth** | Required |
| **Code Location** | `QuestionController.listQuestions()` |

Returns all question banks created by the authenticated user, ordered newest first.

#### Response

**`200 OK`**:
```json
[
  {
    "id": 2,
    "mockId": "f9e8d7c6-b5a4-3210-9fed-cba987654321",
    "jobPosition": "Software Engineer",
    "company": "Google",
    "typeQuestion": "Behavioral",
    "createdAt": "2026-10-13",
    "mockQuestionJsonResp": "[...]",
    "jobDesc": "...",
    "jobExperience": "2",
    "createdBy": "john@example.com"
  }
]
```

---

## 6. Audio Transcription API

**Controller**: `TranscribeController` (`@RestController`, base path: `/api/transcribe`)

**Purpose**: Converts audio recordings to text using Gemini's multimodal AI. This enables users to practice answering questions verbally — they record their answer, upload the audio, and get a text transcription that can then be submitted for AI evaluation.

### Transcribe Audio

| | |
|---|---|
| **URL** | `POST /api/transcribe` |
| **Auth** | Required |
| **Content-Type** | `multipart/form-data` |
| **Code Location** | `TranscribeController.transcribeAudio()` |

#### Request

- **Form field**: `audio` — the audio file (binary upload)
- **Max size**: 15MB (enforced in controller code)
- **Allowed MIME types**: Must start with `audio/` (e.g., `audio/webm`, `audio/mp3`, `audio/wav`)

#### Code Path

1. **Validation**: Checks file is present, not empty, ≤15MB, and has an `audio/*` content type.
2. **Reads bytes**: `file.getBytes()` loads the audio into memory.
3. **AI transcription**: Calls `geminiService.transcribe(bytes, contentType)`.
   - Creates a `ByteArrayResource` wrapping the audio bytes.
   - Builds a multimodal `UserMessage` with a text prompt ("Transcribe the following audio accurately. Return only the transcribed text with no additional commentary.") and the audio as a `Media` attachment.
   - Calls `chatModel.call(new Prompt(userMessage))` — sends both text and audio to Gemini in one request.
4. **Returns transcription**: The AI's text response is returned directly.

#### Response

**`200 OK`**:
```json
{
  "transcription": "In my previous role, I designed a microservices architecture using Spring Boot and Kafka for event-driven communication between services."
}
```

**`400 Bad Request`**:
```json
{
  "error": "No audio file provided"
}
```
```json
{
  "error": "File size exceeds limit of 15MB"
}
```
```json
{
  "error": "Invalid file type. Only audio files are allowed."
}
```

#### cURL Example

```bash
curl -X POST http://localhost:8080/api/transcribe \
  -H "Authorization: Bearer <Firebase_ID_Token>" \
  -F "audio=@recording.webm"
```

---

## 7. Newsletter / Contact Form API

**Controller**: `NewsletterController` (`@RestController`, base path: `/api/newsletter`)

**Purpose**: Accepts contact form submissions from the landing page. This is a **public endpoint** — no authentication required — because it's meant for potential users who haven't signed up yet.

### Submit Contact Form

| | |
|---|---|
| **URL** | `POST /api/newsletter` |
| **Auth** | ❌ Not required |
| **Content-Type** | `application/json` |
| **CORS** | `@CrossOrigin(origins = "*")` — allows requests from any origin |
| **Code Location** | `NewsletterController.saveSubmission()` |

#### Request Body

```json
{
  "name": "Jane Doe",
  "email": "jane@example.com",
  "message": "Interested in using PrepWarrior for our bootcamp. Do you offer bulk pricing?"
}
```

| Field | Type | Required | Validation | Max Length |
|---|---|---|---|---|
| `name` | String | ✅ | Not null/empty | 100 chars |
| `email` | String | ✅ | Must match RFC-compliant email regex | 200 chars |
| `message` | String | ✅ | Not null/empty | 2000 chars |

#### Email Validation

The controller uses a regex pattern for email validation:
```
^[\w!#$%&'*+/=?`{|}~^-]+(?:\.[\w!#$%&'*+/=?`{|}~^-]+)*@(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,6}$
```
This validates the general structure of email addresses without being overly restrictive. It accepts most valid email formats while rejecting obviously malformed inputs.

#### Response

**`200 OK`**:
```json
{
  "success": true
}
```

**`400 Bad Request`**:
```json
{
  "error": "All fields are required"
}
```
```json
{
  "error": "Invalid email address"
}
```

---

## 8. Firebase Client Configuration API

**Controller**: `FirebaseClientConfigController` (`@RestController`)

**Purpose**: Dynamically generates a JavaScript file that contains the Firebase client SDK configuration. The frontend loads this script to initialize Firebase Authentication without hardcoding Firebase credentials in static HTML files.

### Get Firebase Config

| | |
|---|---|
| **URL** | `GET /api/config/firebase.js` |
| **Auth** | ❌ Not required (needed before login) |
| **Content-Type (Response)** | `application/javascript` |
| **Code Location** | `FirebaseClientConfigController.getFirebaseConfigJs()` |

#### How It Works

1. The controller reads Firebase client config values from `application.properties` (injected via `@Value`):
   - `firebase.client.apiKey`, `firebase.client.authDomain`, `firebase.client.projectId`, etc.
2. Generates a JavaScript string containing a `firebaseConfig` object and an `initializeApp()` call.
3. Returns it with `Content-Type: application/javascript`.

#### Response

```javascript
const firebaseConfig = {
  apiKey: "AIzaSyAqYPfeBuHJdxdU5PXxXyYrBO46WvILOkg",
  authDomain: "prepwarrior-467c4.firebaseapp.com",
  projectId: "prepwarrior-467c4",
  storageBucket: "prepwarrior-467c4.firebasestorage.app",
  messagingSenderId: "525786522065",
  appId: "1:525786522065:web:8fc2e89708ee575beac0f0",
  measurementId: "G-TMQQLPW67T"
};
if (typeof firebase !== 'undefined' && !firebase.apps.length) {
    firebase.initializeApp(firebaseConfig);
}
```

#### Why This Pattern?

Instead of hardcoding Firebase config in each HTML file, this pattern:
- **Centralizes configuration** — change it in one place (`application.properties`) and all pages get the update.
- **Supports environment-specific values** — different Firebase projects for dev/staging/prod via environment variables.
- **Keeps secrets out of source code** — values come from environment variables, not committed files.

---

## 9. Error Handling — Global Exception Handler

**Class**: `GlobalExceptionHandler` (`@RestControllerAdvice`)

This is a **cross-cutting concern** that intercepts exceptions thrown by any controller and converts them to consistent JSON error responses. It uses Spring's `@ExceptionHandler` mechanism.

### How It Works in the MVC Pipeline

```
Controller throws exception
  → Spring's DispatcherServlet catches it
  → Looks for matching @ExceptionHandler in @RestControllerAdvice classes
  → Calls the handler method → returns ResponseEntity with error body
  → Client receives consistent {"error": "..."} JSON
```

### Exception Mapping

| Exception Type | HTTP Status | When Triggered | Example Error Message |
|---|---|---|---|
| `IllegalArgumentException` | `400 Bad Request` | Validation failures in controllers (null fields, out-of-range values, invalid MIME types) | `"All fields are required"` |
| `MaxUploadSizeExceededException` | `413 Content Too Large` | Audio file exceeds Spring's multipart upload limit | `"File too large. Maximum upload size exceeded."` |
| `Exception` (catch-all) | `500 Internal Server Error` | Any unhandled exception (database errors, NPEs, etc.) | `"An unexpected error occurred: Connection refused"` |

### Additional Error Sources (Not in GlobalExceptionHandler)

| Source | HTTP Status | Trigger |
|---|---|---|
| `RateLimitingFilter` | `429 Too Many Requests` | Rate limit exceeded (handled directly in the filter, not via exception) |
| Spring Security | `401 Unauthorized` | Missing/invalid Firebase token |
| Controller-level handling | `502 Bad Gateway` | AI response JSON parsing fails (handled via `try-catch` in controller, returned as `ResponseEntity`) |

---

## 10. Rate Limiting

**Class**: `RateLimitingFilter` (runs after `FirebaseTokenFilter` in the security filter chain)

### Rate Limit Rules

| User Type | Identification | Limit | Window | Redis Key Pattern |
|---|---|---|---|---|
| Authenticated user | User email from Spring Security Principal | **100 requests** | 60 seconds | `ratelimit:uid:<email>` |
| Anonymous/guest | Client IP (from `X-Forwarded-For` header or `remoteAddr`) | **20 requests** | 60 seconds | `ratelimit:ip:<ip>` |

### Rate Limit Response

When the limit is exceeded:

**`429 Too Many Requests`**:
```json
{
  "error": "Too many requests. Please try again later."
}
```

### Scope

Rate limiting only applies to paths starting with `/api/`. Static page requests (`/`, `/dashboard/**`, CSS/JS files) are never rate-limited.

### Failure Mode

If Redis is unavailable, the filter logs a warning and **allows the request through** (fail-open). This ensures the application remains functional even without Redis — just without rate limiting protection.

---

## 11. API Summary Matrix

| Method | Endpoint | Auth | Description | Success Status |
|---|---|---|---|---|
| `POST` | `/api/interviews` | ✅ | Create interview with AI-generated questions | `201` |
| `GET` | `/api/interviews/{id}` | ✅ | Get interview by mockId | `200` |
| `GET` | `/api/interviews/list` | ✅ | List all interviews for current user | `200` |
| `POST` | `/api/interviews/{id}/answer` | ✅ | Submit answer for AI evaluation | `200` |
| `GET` | `/api/interviews/{id}/feedback` | ✅ | Get all answers + feedback for interview | `200` |
| `POST` | `/api/questions` | ✅ | Generate company-specific question bank | `200` |
| `GET` | `/api/questions/{id}` | ✅ | Get question bank by mockId | `200` |
| `GET` | `/api/questions/list` | ✅ | List all question banks for current user | `200` |
| `POST` | `/api/transcribe` | ✅ | Transcribe audio file via AI | `200` |
| `POST` | `/api/newsletter` | ❌ | Submit contact form | `200` |
| `GET` | `/api/config/firebase.js` | ❌ | Get Firebase client SDK config as JS | `200` |
