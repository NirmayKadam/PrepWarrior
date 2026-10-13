# PrepWarrior (AI Mock Interview Platform) — Complete SDE Interview Preparation Guide

> **Document Type:** Placement & Technical Interview Defense Manual  
> **Target Roles:** Backend Engineer / Software Development Engineer (SDE) Fresher  
> **Target Companies:** Service/Consulting (Capgemini, EY) & Product Startups  
> **Target Stack:** Java 21, Spring Boot 3.x, Spring AI (Gemini), Spring Data JPA, Redis, Docker  
> **Repository Location:** [SpringBootProject](file:///d:/Nirmay%20pc/ENGINEERING/projects/SpringBootProject)

---

## Table of Contents
1. [Resume Corrections & Safer Phrasing](#1-resume-corrections--safer-phrasing)
2. [60–90 Second Spoken Pitch](#2-6090-second-spoken-pitch)
3. [Tier 1 Core Answers](#3-tier-1-core-answers)
4. [Tier 2 "Prove You Built It" Technical Deep-Dive](#4-tier-2-prove-you-built-it-technical-deep-dive)
5. [Tier 3 Architecture & Production Depth](#5-tier-3-architecture--production-depth)
6. [Stack-Specific Follow-Up Q&A Bank](#6-stack-specific-follow-up-qa-bank)
7. [Follow-Up Chains](#7-follow-up-chains)
8. [High-Difficulty Curveballs](#8-high-difficulty-curveballs)
9. [Depth Calibration by Company Type](#9-depth-calibration-by-company-type)
10. [Full Self-Study Mock Interview Transcript](#10-full-self-study-mock-interview-transcript)

---

## 1. Resume Corrections & Safer Phrasing

Before citing this project in interviews or on your resume, align your terminology with real-world engineering standards:

| Codebase / Project Detail | Risk Level | Why It Is Dangerous | Defensible & Safe Phrasing |
|---|---|---|---|
| **Spring Boot 4.1.0** (listed in `pom.xml` / `README.md`) | 🔴 Critical | Spring Boot 4 is not generally available (GA). Claiming Spring Boot 4 signals to senior panelists that the candidate doesn't know the Java ecosystem. | *"Engineered with Java 21, Spring Boot 3.x, and Spring AI."* |
| **User metrics / Live scale** | 🟠 High | Claiming 10k users or 99.9% uptime when it was tested locally will collapse under basic observability/monitoring follow-ups. | *"Containerized the platform via Docker Compose and validated end-to-end functionality, token security, and rate limiting locally."* |
| **"Microservices architecture"** | 🟡 Medium | The codebase is a clean layered monolith packaged into a single runnable JAR. Calling it microservices invites questions about service discovery, Kafka, and distributed tracing. | *"Designed a clean layered monolith (Controller-Service-Repository) integrating external AI services and distributed Redis middleware."* |

---

## 2. 60–90 Second Spoken Pitch

### Spoken Script (Practice reciting at a steady pace ~75 seconds)
> "I built **PrepWarrior**, an automated mock interview platform designed to help students and job seekers practice role-specific technical interviews without needing an expensive human interviewer.
>
> In real interviews, candidates often struggle with articulating domain-specific answers under pressure. With PrepWarrior, a candidate inputs their target job title, job description, and experience level. The backend prompts Google Gemini through Spring AI to dynamically generate five targeted technical questions. Candidates can submit their answers by typing or through voice recording. The platform transcribes audio using Gemini's multimodal capabilities, scores each response out of 10, and delivers actionable, rubric-based feedback.
>
> I developed the entire backend solo using **Java 21 and Spring Boot 3**, with **Spring Data JPA, PostgreSQL, H2 for development, and Redis for distributed rate limiting**. For security, I integrated **Firebase Admin SDK** to enforce stateless JWT verification across protected routes.
>
> The module I know best and enjoyed architecting most is the **security filter pipeline and Redis rate limiter**, specifically how it intercepts incoming requests before the controller layer, enforces distinct quotas for guest IPs versus authenticated users, and protects our upstream LLM budget from runaway API consumption."

---

## 3. Tier 1 Core Answers

### Q1: What problem does this solve, and why did you choose to build it?
**Spoken Answer:**  
"Most tech job seekers practice coding on platforms like LeetCode, but fail technical interviews because they cannot articulate architectural decisions or handle dynamic conceptual questions tailored to their resume. Traditional 1-on-1 human mock interviews are either prohibitively expensive or difficult to schedule. I built PrepWarrior to democratize realistic, on-demand interview prep by generating position-specific questions directly from raw job descriptions. I chose this project because I wanted to tackle non-trivial backend challenges: handling long-latency external AI calls, enforcing rate limits to prevent denial-of-wallet attacks, and building a secure stateless JWT authentication filter in Spring Boot."

---

### Q2: Why did you choose this tech stack over alternatives?

| Component | Choice Made | Rejected Alternative | Engineering Trade-off & Justification |
|---|---|---|---|
| **Backend Framework** | **Spring Boot (Java 21)** | Node.js / Express | Node.js allows quick setup, but Spring Boot provides enterprise-grade dependency injection, robust filter chain lifecycle management (`OncePerRequestFilter`), and strict compile-time type safety for complex data models. |
| **AI Integration** | **Spring AI (`ChatModel`)** | Raw HTTP client / `WebClient` | Manually parsing REST JSON responses from Google AI Studio creates brittle parsing code. Spring AI abstracts prompt templating, multimodal binary attachments, and model interchangeability behind standard interfaces. |
| **Rate Limiting** | **Redis (Fixed Window)** | In-Memory Guava / Bucket4j | An in-memory cache lives inside a single JVM heap. If the application scales to multiple instances behind a load balancer, local caches fail to enforce global rate limits. Redis provides a centralized, shared out-of-process state store. |
| **Database** | **PostgreSQL + H2** | MongoDB (NoSQL) | While interview evaluations are text-heavy, user accounts, interview sessions, and answer history are fundamentally relational. PostgreSQL guarantees ACID transactional integrity, while H2 enables zero-setup local development. |

---

### Q3: What was your exact contribution vs the team?
**Spoken Answer:**  
"This was a 100% solo project. I designed the architecture, wrote the Maven build configuration, implemented the Spring Security filter chain with Firebase Admin SDK, built the Redis rate limiting middleware, wrote the prompt engineering and parsing logic in `GeminiService`, designed the JPA entities and repositories, and configured the multi-stage Dockerfile and Docker Compose environment."

---

### Q4: What was the hardest bug or technical challenge you faced, and how did you resolve it? (CAR Format)
* **Context:**  
  "When generating interview questions and evaluations in [GeminiService.java](file:///d:/Nirmay%20pc/ENGINEERING/projects/SpringBootProject/Project/src/main/java/com/example/mockinterview/service/GeminiService.java#L91-L102), the Gemini API occasionally wrapped its JSON output in Markdown code fences (` ```json ... ``` `) or added conversational commentary before the JSON block. When the raw string was passed to Jackson's `ObjectMapper.readTree()` in [MockInterviewController.java](file:///d:/Nirmay%20pc/ENGINEERING/projects/SpringBootProject/Project/src/main/java/com/example/mockinterview/controller/MockInterviewController.java#L71-L76), Jackson threw a deserialization exception, resulting in intermittent request failures."
* **Action:**  
  "I solved this through a two-layered defense: First, I hardened the prompt instructions with explicit output constraints. Second, I introduced a regex cleaning method `cleanJson()` that strips triple backticks and markdown identifiers case-insensitively. In the controller, I wrapped the deserialization in a defensive `try-catch` block: if the LLM output is malformed, the system intercepts it cleanly and returns an HTTP `502 Bad Gateway` with a structured error message rather than crashing or storing corrupted state in the database."
* **Result:**  
  "This eliminated JSON deserialization crashes across all local test runs and ensured that external AI formatting inconsistencies were contained at the service boundary."

---

### Q5: What are 2–3 weaknesses or things you would improve today?
**Spoken Answer:**  
1. **Schema Normalization:** "In [MockInterview.java](file:///d:/Nirmay%20pc/ENGINEERING/projects/SpringBootProject/Project/src/main/java/com/example/mockinterview/model/MockInterview.java#L24), generated questions are stored as a raw JSON string in a `TEXT` column. I would refactor this into an `InterviewQuestion` entity with an explicit `@OneToMany` relationship. This would allow individual question indexing, partial updates, and direct relational queries."
2. **Asynchronous Processing via WebSockets/SSE:** "Currently, the HTTP client blocks synchronously for 2 to 4 seconds while Gemini generates questions. I would convert this to an asynchronous background job with Server-Sent Events (SSE) or WebSockets so questions stream to the frontend in real time."
3. **Atomic Rate Limiting:** "Our Redis rate limiter currently executes `INCR` followed by `EXPIRE`. If the server fails between these calls, a key could remain without an expiry. I would replace this with an atomic Redis Lua script executing a Token Bucket or Sliding Window algorithm."

---

## 4. Tier 2 "Prove You Built It" Technical Deep-Dive

### 1. End-to-End Execution Flow (Create Mock Interview)
```
1. Client (Browser / Frontend)
   │  Sends HTTP POST /api/interviews
   │  Headers: Authorization: Bearer <Firebase_ID_Token>, Content-Type: application/json
   │  Body: { "jobPosition": "Backend Engineer", "jobDesc": "Java, Spring Boot, SQL", "jobExperience": "2" }
   ▼
2. RateLimitingFilter (OncePerRequestFilter)
   │  Path check: matches /api/*
   │  Extracts IP (via X-Forwarded-For or getRemoteAddr) and User context
   │  Checks Redis: Key "ratelimit:uid:<email>" (limit: 100/min) or "ratelimit:ip:<ip>" (limit: 20/min)
   │  If count > maxRequests -> Short-circuits with HTTP 429 Too Many Requests
   ▼
3. FirebaseTokenFilter (OncePerRequestFilter)
   │  Extracts Bearer token from Authorization header
   │  Calls FirebaseAuth.getInstance().verifyIdToken(token)
   │  Extracts UID and email -> Instantiates UsernamePasswordAuthenticationToken
   │  Injects token into SecurityContextHolder.getContext().setAuthentication(...)
   ▼
4. MockInterviewController.createInterview()
   │  Extracts Principal (authenticated user email)
   │  Validates input fields (non-empty, experience between 0 and 50)
   │  Sanitizes input strings against XSS injection
   ▼
5. GeminiService.generateQuestions()
   │  Constructs formatted prompt with job parameters and strict JSON output rules
   │  Invokes chatModel.call(prompt) via Spring AI starter
   │  Cleans response via cleanJson() regex
   ▼
6. Controller Validation & Persistence
   │  Executes objectMapper.readTree(jsonMockResp) to verify valid JSON
   │  Generates UUID mockId and sets creation timestamp
   │  Calls mockInterviewRepository.save(mockInterview)
   ▼
7. Client Response
      Returns HTTP 201 Created with JSON: { "mockId": "7b8f9e..." }
```

---

### 2. Whiteboard Architecture Diagram
```
+-----------------------------------------------------------------------------------+
|                                  CLIENT TIER                                      |
|                  (Browser UI: HTML5, CSS3, Vanilla JavaScript)                    |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          | HTTP Requests (Bearer JWT)
                                          v
+-----------------------------------------------------------------------------------+
|                        SPRING BOOT 3 APPLICATION CONTAINER                        |
|                                                                                   |
|  [Servlet Filter Chain]                                                           |
|    ├── RateLimitingFilter  ──────> [ Redis 6379 ] (Fixed-window counters)        |
|    └── FirebaseTokenFilter ──────> [ Firebase Admin SDK ] (Token Verification)    |
|                                                                                   |
|  [Controller Layer]                                                               |
|    ├── MockInterviewController  (Interview CRUD & orchestrator)                  |
|    ├── QuestionController       (Company question bank)                           |
|    ├── TranscribeController     (Audio transcription endpoint)                    |
|    └── GlobalExceptionHandler   (@RestControllerAdvice - centralized errors)      |
|                                                                                   |
|  [Service Layer]                                                                  |
|    └── GeminiService            (Spring AI ChatModel client)                      |
|                                          │                                        |
|  [Data Access Layer]                     │ Remote HTTPS Calls                     |
|    ├── MockInterviewRepository           v                                        |
|    ├── UserAnswerRepository     [ Google Gemini 2.5 Flash API ]                   |
|    └── HikariCP Connection Pool                                                   |
+-----------------------------------------+-----------------------------------------+
                                          |
                                          | JDBC
                                          v
+-----------------------------------------------------------------------------------+
|                                 DATABASE TIER                                     |
|             PostgreSQL (Production)  /  H2 In-Memory (Local Development)          |
|             Tables: mock_interview, user_answer, question, newsletter             |
+-----------------------------------------------------------------------------------+
```

---

### 3. Database Schema & Relational Design Walkthrough

#### Entity Mapping
* **`mock_interview`** ([MockInterview.java](file:///d:/Nirmay%20pc/ENGINEERING/projects/SpringBootProject/Project/src/main/java/com/example/mockinterview/model/MockInterview.java)):
  * `id`: BIGINT, Primary Key, Auto-increment.
  * `mock_id`: VARCHAR, UUID string, Indexed (`idx_mock_interview_mock_id`).
  * `json_mock_resp`: TEXT, Raw JSON holding the 5 generated questions and model answers.
  * `job_position`, `job_desc`, `job_experience`: VARCHAR, Job criteria.
  * `created_by`: VARCHAR, Candidate email, Indexed (`idx_mock_interview_created_by`).
  * `created_at`: VARCHAR, ISO Date string.
* **`user_answer`** ([UserAnswer.java](file:///d:/Nirmay%20pc/ENGINEERING/projects/SpringBootProject/Project/src/main/java/com/example/mockinterview/model/UserAnswer.java)):
  * `id`: BIGINT, Primary Key, Auto-increment.
  * `mock_id_ref`: VARCHAR, Logical reference to `mock_interview.mock_id`, Indexed (`idx_user_answer_mock_id_ref`).
  * `question`: VARCHAR, Interview question text.
  * `correct_ans`: TEXT, Reference answer.
  * `user_ans`: TEXT, Candidate's submitted text or transcribed answer.
  * `feedback`: TEXT, Qualitative AI feedback.
  * `rating`: VARCHAR, Numerical rating out of 10.
  * `user_email`: VARCHAR, Candidate identifier, Indexed (`idx_user_answer_user_email`).
  * `created_at`: VARCHAR, Timestamp.
* **`question`** ([Question.java](file:///d:/Nirmay%20pc/ENGINEERING/projects/SpringBootProject/Project/src/main/java/com/example/mockinterview/model/Question.java)):
  * Company-specific question bank table.
* **`newsletter`** ([Newsletter.java](file:///d:/Nirmay%20pc/ENGINEERING/projects/SpringBootProject/Project/src/main/java/com/example/mockinterview/model/Newsletter.java)):
  * Contact and notification subscriptions with unique email constraint.

#### The Senior Interviewer Challenge: "Why use `mock_id_ref` instead of a Foreign Key?"
**Your Spoken Defense:**  
"In the initial prototype, I decoupled the storage of interviews and answers using a logical string key `mock_id_ref` rather than an explicit `@ManyToOne` foreign key. This allowed me to persist answers flexibly without having to manage JPA entity lifecycle cascades during rapid iteration. 

However, as an architectural decision, I recognize the trade-off: this foregoes database-level referential integrity—a deleted interview could leave orphaned answer records unless handled programmatically. In a production refactor, I would replace `mock_id_ref` with an explicit `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "interview_id")` mapping with cascading constraints."

---

### 4. REST Endpoints Specification

| Method | Route | Authentication | Payload | Success Response | Error Responses |
|---|---|---|---|---|---|
| `POST` | `/api/interviews` | Optional (Guest/User) | `{ "jobPosition": "...", "jobDesc": "...", "jobExperience": "2" }` | `201 Created`<br>`{ "mockId": "uuid" }` | `400 Bad Request`<br>`429 Too Many Requests`<br>`502 Bad Gateway` |
| `GET` | `/api/interviews/{id}` | Optional | *None* | `200 OK`<br>`{ MockInterview Object }` | `404 Not Found` |
| `POST` | `/api/interviews/{id}/answers` | Optional | `{ "question": "...", "userAns": "...", "correctAns": "..." }` | `200 OK`<br>`{ "rating": 8, "feedback": "..." }` | `400 Bad Request`<br>`502 Bad Gateway` |
| `GET` | `/api/interviews/{id}/feedback` | Optional | *None* | `200 OK`<br>`[ List of UserAnswer ]` | `200 OK` (empty list) |
| `POST` | `/api/transcribe` | Optional | `multipart/form-data` with `file` (WebM/WAV audio) | `200 OK`<br>`{ "transcription": "..." }` | `400 Bad Request` |
| `POST` | `/api/newsletter` | Public | `{ "email": "candidate@example.com" }` | `200 OK`<br>`{ "message": "Subscribed" }` | `400 Bad Request` |

---

### 5. Input Validation & Exception Handling Architecture
* **Validation Layer:**
  * Guard clauses in [MockInterviewController.java](file:///d:/Nirmay%20pc/ENGINEERING/projects/SpringBootProject/Project/src/main/java/com/example/mockinterview/controller/MockInterviewController.java#L45-L66) check for non-null and non-blank fields.
  * Integer bounds checking: `jobExperience` must parse to an integer between `0` and `50`.
  * Input sanitization method strips script/HTML tags to prevent stored cross-site scripting (XSS).
* **Global Exception Handling:**
  * Centralized via `@RestControllerAdvice` in [GlobalExceptionHandler.java](file:///d:/Nirmay%20pc/ENGINEERING/projects/SpringBootProject/Project/src/main/java/com/example/mockinterview/controller/GlobalExceptionHandler.java).
  * Catches `IllegalArgumentException` and returns a `400 Bad Request` with structured JSON: `{"error": e.getMessage()}`.
  * Catches top-level `Exception` and returns a `500 Internal Server Error` with a generic message, preventing stack trace leakage to the client.

---

### 6. Git Workflow
**Spoken Answer:**  
"I used Git with a structured feature-branch workflow. Sensitive environment details (`.env`, Firebase private keys) and generated build outputs (`target/`) were strictly excluded using `.gitignore`. Each feature—such as the Redis rate limiter, the Firebase filter, and the Docker setup—was developed and committed with clear Conventional Commits format (`feat:`, `fix:`, `refactor:`)."

---

### 7. Honest AI Tool Usage
**Spoken Answer:**  
"I used Gemini within the Antigravity IDE during development as an engineering accelerator. I specifically leveraged it to experiment with prompt engineering to get reliable JSON output structures from the LLM, to generate initial DTO boilerplate, and to navigate dependency version shifts between Spring AI milestones. However, the architectural design, security filters, rate-limiting logic, and database schemas were authored, debugged, and verified by me directly."

---

## 5. Tier 3 Architecture & Production Depth

### Q1: How did you approach automated testing?
**Spoken Answer:**  
"Currently, verification relies on end-to-end API testing via automated agent runners and Postman test suites validating both valid inputs and error conditions (such as 429 rate limit triggers and invalid numeric ranges). 

To bring this to enterprise production standards, I would introduce:
1. **Unit Testing:** Using **JUnit 5 and Mockito** to mock `ChatModel` in `GeminiServiceTest` so tests execute quickly and offline without consuming external API credits.
2. **Integration Testing:** Using **`@SpringBootTest` with Testcontainers** to spin up lightweight, ephemeral Docker instances of Redis and PostgreSQL during the Maven build, testing our real database queries and rate-limiting filters."

---

### Q2: What security measures did you implement?
**Spoken Answer:**  
1. **Stateless Authentication:** Implemented via [FirebaseTokenFilter.java](file:///d:/Nirmay%20pc/ENGINEERING/projects/SpringBootProject/Project/src/main/java/com/example/mockinterview/config/FirebaseTokenFilter.java). The backend does not maintain server-side sessions; it validates cryptographically signed JWTs using Firebase Admin SDK and populates Spring's `SecurityContextHolder`.
2. **SQL Injection Defense:** All database interactions flow through Spring Data JPA repositories using parameterized SQL generated by Hibernate. No dynamic string concatenation is used in queries.
3. **Secrets Management:** Zero credentials are hardcoded. The Gemini API key and Firebase service credentials are read from environment variables (`${GEMINI_API_KEY}`) and `.env` files via Spring's `@Value` annotation.
4. **Denial-of-Wallet Protection:** Redis rate-limiting caps guest IP requests to 20/minute, preventing automated scrapers from exhausting our external AI token quotas.

---

### Q3: What happens when dependencies fail (Gemini, Database, Redis)?
**Spoken Answer:**  
* **If Gemini API fails or times out:** `GeminiService` throws an exception, and the controller returns an HTTP `502 Bad Gateway`. In a production release, I would wrap this in a **Resilience4j Circuit Breaker** with a fallback mechanism that serves pre-cached standard questions when the AI service is degraded.
* **If the Database fails:** HikariCP connection pool timeouts trigger, and the `GlobalExceptionHandler` intercepts the error, returning a clean `500 Internal Server Error` without leaking database schema details.
* **If Redis fails:** In [RateLimitingFilter.java](file:///d:/Nirmay%20pc/ENGINEERING/projects/SpringBootProject/Project/src/main/java/com/example/mockinterview/config/RateLimitingFilter.java#L75-L77), Redis calls are wrapped in a `try-catch` block that logs a warning and passes the request through (`fail-open`). This guarantees that a Redis cache outage does not take down the entire application.

---

### Q4: Where does this system break at 50,000 concurrent users, and how would you scale it?
**Spoken Answer:**  
"At 50,000 concurrent users, three distinct bottlenecks would emerge:
1. **Thread Starvation in Tomcat:** By default, Tomcat allocates 200 worker threads. Because LLM generation takes 2 to 4 seconds of blocking I/O, all 200 threads would be blocked waiting for HTTP responses, causing incoming requests to queue and time out. I would solve this by enabling **Java 21 Virtual Threads** (`spring.threads.virtual.enabled=true`), which allows millions of lightweight virtual threads to handle blocking I/O without consuming OS-level threads.
2. **Upstream LLM Rate Quotas:** Gemini's API would instantly reject requests with HTTP 429. I would decouple question generation using an asynchronous message broker like **Apache Kafka or RabbitMQ**, queuing requests and notifying users via WebSockets when generation completes.
3. **Database Connection Pool Saturation:** PostgreSQL and HikariCP would exhaust connections. I would implement read-write database replicas, introduce **PgBouncer** for connection pooling, and aggressively cache generated questions in Redis by hashing common `(jobPosition, jobExperience)` tuples."

---

### Q5: How does your database indexing work?
**Spoken Answer:**  
"I defined targeted indexes on high-cardinality lookup columns:
* `idx_mock_interview_mock_id` on `mock_interview(mock_id)`: Guarantees $O(1)$ B-Tree lookups when retrieving interview sessions by UUID.
* `idx_mock_interview_created_by` on `mock_interview(created_by)`: Optimizes dashboard queries retrieving all mock interviews created by a specific user email.
* `idx_user_answer_mock_id_ref` on `user_answer(mock_id_ref)`: Prevents full table scans when fetching the collection of submitted answers for a given interview."

---

## 6. Stack-Specific Follow-Up Q&A Bank

### Spring Boot & Core Java (Java 21)
#### Q1: What is Dependency Injection (DI) and Inversion of Control (IoC)?
**Answer:** IoC is the architectural principle where the responsibility of instantiating, configuring, and managing an object's lifecycle is handed over to the container (Spring's `ApplicationContext`). Dependency Injection is the mechanism used to fulfill IoC: instead of a class creating its dependencies using `new`, the container injects them at runtime (e.g., injecting `GeminiService` into `MockInterviewController`).

#### Q2: Why use Constructor Injection over `@Autowired` field injection?
**Answer:** Constructor injection makes dependencies immutable (`final`), prevents `NullPointerException` during standalone unit testing because dependencies must be supplied upon instantiation, and makes circular dependencies immediately detectable at application startup.

#### Q3: How does Spring Boot Auto-Configuration work under the hood?
**Answer:** It uses `@EnableAutoConfiguration` which evaluates configuration classes declared in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. It evaluates conditional annotations such as `@ConditionalOnClass`, `@ConditionalOnMissingBean`, and `@ConditionalOnProperty` to register standard beans only if the user hasn't defined custom ones.

#### Q4: What are Java 21 Virtual Threads, and how do they benefit Spring Boot?
**Answer:** Virtual threads are lightweight user-mode threads managed by the JVM rather than the operating system. In traditional Spring Boot with platform threads, 1 thread maps to 1 OS thread, consuming ~1MB of memory and blocking during I/O. Virtual threads allow the JVM to park and unpark millions of concurrent threads cheaply during blocking I/O (like external Gemini API calls), drastically increasing server throughput without rewriting code into reactive programming.

---

### Spring Data JPA & Hibernate
#### Q1: What is the N+1 select problem, and how do you resolve it?
**Answer:** The N+1 problem occurs when fetching $N$ parent entities triggers 1 initial query to get the parents, followed by $N$ separate queries to fetch lazily loaded children for each parent. It is resolved using `JOIN FETCH` in JPQL, defining an `@EntityGraph`, or configuring batch fetching via `hibernate.default_batch_fetch_size`.

#### Q2: What is the difference between `save()` and `saveAndFlush()` in Spring Data JPA?
**Answer:** `save()` attaches the entity to the Hibernate persistence context (session cache) and defers the actual SQL `INSERT`/`UPDATE` until the transaction boundary is committed (the flush). `saveAndFlush()` immediately pushes the pending SQL changes to the database within the current open transaction.

#### Q3: What is the Hibernate First-Level Cache vs Second-Level Cache?
**Answer:** The first-level cache is bound to the Hibernate `EntityManager`/Session; it exists by default and caches entities during a single transaction. The second-level cache is an optional, process-wide or distributed cache (e.g. Ehcache or Hazelcast) that survives across multiple sessions and transactions.

---

### Redis
#### Q1: Why did you choose Redis over Memcached?
**Answer:** Redis provides rich data structures (Strings, Hashes, Lists, Sets, Sorted Sets), persistence options (RDB snapshots and AOF logs), pub/sub messaging, and atomic command execution (`INCR`, `EXPIRE`). Memcached is purely an in-memory key-value cache for string/serialized objects without native atomic increment expirations.

#### Q2: What is the vulnerability with the fixed-window rate limiter in your `RateLimitingFilter`?
**Answer:** 
1. **Window Edge Burst:** A user can send 20 requests at the very end of minute 1 (e.g. second 59) and another 20 requests at the beginning of minute 2 (second 1), resulting in 40 requests within 2 seconds without violating the 20/minute rule.
2. **Atomicity Race Condition:** In [RateLimitingFilter.java](file:///d:/Nirmay%20pc/ENGINEERING/projects/SpringBootProject/Project/src/main/java/com/example/mockinterview/config/RateLimitingFilter.java#L64-L67), the code executes `increment()` and then conditionally executes `expire()`. If the server terminates between the two calls, the key persists without a TTL, permanently locking the user. In production, this is solved by running an atomic Redis Lua script.

---

### Docker & Deployment
#### Q1: Why use a multi-stage Dockerfile?
**Answer:** A multi-stage Dockerfile uses a full Maven/JDK image in the first stage (`builder`) to compile the source code and run builds. In the final stage, it copies only the compiled `.jar` artifact into a slim JRE runtime image (e.g. `eclipse-temurin:21-jre-alpine`). This reduces the final container image size from ~600MB down to ~150MB, minimizes the attack surface, and removes source code and build tools from the production container.

#### Q2: What is the purpose of Docker Compose in this project?
**Answer:** Docker Compose orchestrates multi-container environments declaratively in `docker-compose.yml`. It spins up the Spring Boot web service, PostgreSQL database, and Redis cache with isolated container networks, automated volume persistence, and environment variable injection in a single command (`docker compose up`).

---

## 7. Follow-Up Chains

### Chain 1: Following Up on the 60-90s Pitch
1. **Interviewer:** *"You mentioned Firebase authentication. How does the Spring Boot backend verify Firebase tokens without sharing a database with Firebase?"*  
   * **Answer:** "Firebase tokens are JSON Web Tokens (JWTs) cryptographically signed by Google's private keys. The backend uses the Firebase Admin SDK, which fetches Google's public JSON Web Key Set (JWKS) to verify the token's cryptographic signature, expiration (`exp`), and issuer (`iss`) locally in memory, without needing to contact a shared database."
2. **Interviewer:** *"If the tokens are stateless, how do you handle user logout or revoke compromised tokens?"*  
   * **Answer:** "In a pure stateless JWT setup, tokens remain valid until their expiration. To implement immediate revocation, Firebase provides a `revokeRefreshTokens(uid)` API. On the backend, we would check the user's `tokensValidAfterTime` timestamp or maintain a short-lived token blacklist in Redis with a TTL matching the token's lifetime."
3. **Interviewer:** *"Why do you throttle guests by IP? What happens when multiple users are on the same college Wi-Fi or office NAT?"*  
   * **Answer:** "Throttling by IP for guests is a defensive compromise: since guests have no authentication credentials, IP is the only available identifier. To prevent false positives behind shared NATs, we set the guest limit to a reasonable threshold (20 req/min) and prompt users to sign in. Once authenticated, the rate limit key switches immediately to `ratelimit:uid:<userEmail>`, giving each individual user their own private 100 req/min quota."

---

### Chain 2: Following Up on the Hardest Challenge (Gemini Output Parsing)
1. **Interviewer:** *"Why didn't you use Spring AI's built-in `BeanOutputConverter` or structured output mode instead of string regex cleaning?"*  
   * **Answer:** "Spring AI does provide a `BeanOutputConverter` that injects schema instructions into the prompt. However, during development with early Gemini Flash endpoints, unexpected markdown wrappers or formatting variations still occasionally leaked through and broke deserialization. Implementing an explicit regex sanitizer and a secondary Jackson validation guard provided defense-in-depth, ensuring the controller never crashes regardless of minor upstream model deviations."
2. **Interviewer:** *"If the JSON parsing fails and you return HTTP 502, doesn't that frustrate the user? How could you make that resilient?"*  
   * **Answer:** "Returning a 502 prevents bad data from corrupting our database, but it is an imperfect user experience. To make it truly resilient, I would implement an automatic retry mechanism with exponential backoff using Spring Retry (`@Retryable`) or Resilience4j. If generation fails once, the service automatically resubmits the prompt to Gemini once or twice before returning an error to the user."
3. **Interviewer:** *"What prompt engineering techniques did you use to improve JSON output consistency?"*  
   * **Answer:** "I used few-shot formatting examples directly in the prompt text: I explicitly specified the exact JSON schema with key names (`Question`, `Answer`), gave a 2-line JSON example, and added negative constraints instructing the model to output raw JSON with no conversational preamble or markdown formatting."

---

## 8. High-Difficulty Curveballs

### Curveball 1: "What is the single weakest part of your current implementation?"
**Spoken Answer:**  
"The weakest part is the synchronous coupling between the user's HTTP request and the external Gemini LLM call. In [MockInterviewController.java](file:///d:/Nirmay%20pc/ENGINEERING/projects/SpringBootProject/Project/src/main/java/com/example/mockinterview/controller/MockInterviewController.java#L69), the HTTP request thread blocks for 2 to 4 seconds waiting for Gemini to respond. If Google's API experiences latency spikes, client requests pile up and exhaust the Tomcat thread pool. The correct enterprise architecture is to make this asynchronous: accept the request, generate a tracking ID, publish a job to a message queue, and push the generated questions to the client via WebSockets or Server-Sent Events."

---

### Curveball 2: "What happens if a user submits a 50MB audio file to `/api/transcribe`?"
**Spoken Answer:**  
"In the current code, the controller accepts the file as raw bytes into memory. A 50MB upload from multiple users concurrently would rapidly consume the JVM heap and cause an `OutOfMemoryError`. To guard against this, I need to enforce a maximum multipart file size in `application.properties` using `spring.servlet.multipart.max-file-size=5MB` and `spring.servlet.multipart.max-request-size=5MB`. Tomcat will then reject oversized uploads at the network layer with an HTTP `413 Payload Too Large` before buffering the payload into heap memory."

---

### Curveball 3: "If I asked you to add a feature to save 'bookmarked questions' right now, what files would you touch and how would you implement it?"
**Spoken Answer:**  
"I would implement this across the four standard layers:
1. **Entity Layer:** Create a `QuestionBookmark.java` entity in `model/` containing an `id`, a `@ManyToOne` reference to `Question` or `UserAnswer`, a `userEmail` string, and a `created_at` timestamp.
2. **Repository Layer:** Create `QuestionBookmarkRepository.java` in `repository/` extending `JpaRepository`, adding a query method `List<QuestionBookmark> findByUserEmail(String userEmail)`.
3. **Controller Layer:** Add a `BookmarkController.java` with two endpoints: `POST /api/bookmarks` (taking question ID from request body) and `GET /api/bookmarks` (fetching current user's bookmarks from `Principal.getName()`).
4. **Security Layer:** Update `SecurityConfig.java` to ensure `/api/bookmarks/**` requires authentication under the Firebase token filter."

---

## 9. Depth Calibration by Company Type

| Target Company Type | What Panelists Care About | Where to Spend Your Time | Common Traps to Avoid |
|---|---|---|---|
| **Service Companies** *(Capgemini, EY, TCS, Infosys)* | Core Java fundamentals, OOP concepts, Spring annotations, MVC request flow, SQL queries. | Emphasize clean layer separation (Controller vs Service vs Repository), constructor injection, exception handling, and Java 8/17 features (Streams, Lambdas). | Avoid getting bogged down in low-level Redis concurrency primitives unless asked. Keep answers clear, structured, and confident. |
| **Product Startups** *(FinTech, E-Commerce, SaaS)* | Low-level mechanics, scalability bottlenecks, race conditions, caching strategies, failure modes, trade-off defense. | Dive deep into the Redis filter atomicity, Virtual Threads, circuit breakers, schema normalization trade-offs, and how you would scale to 50k users. | Never claim a feature exists if you didn't build it. Admit gaps upfront and walk through how you would architect the production fix. |
| **AI/ML Roles** | LLM latency, prompt injection defense, token usage optimization, multimodal audio streaming, evaluation rubrics. | Focus on Spring AI integration, Gemini prompt design, regex cleaning, and WebM audio transcription via multimodal attachments. | Don't claim you trained or fine-tuned the model. State clearly that you engineered the integration and prompt pipelines using Gemini 2.5 Flash. |

---

## 10. Full Self-Study Mock Interview Transcript

Read through this complete mock interview simulation to internalize how an expert candidate navigates questioning:

### Question 1: The Pitch
* **Interviewer:** *"Walk me through the architecture and purpose of PrepWarrior."*
* **Candidate:** *"PrepWarrior is an automated mock interview platform that simulates role-specific technical interviews. Candidates input their target role, description, and seniority, and our backend prompts Google Gemini via Spring AI to generate tailored questions. Candidates answer via voice or text, receiving score ratings and rubric feedback. I built it solo using Java 21, Spring Boot 3, Spring Data JPA, PostgreSQL, and Redis for distributed rate limiting. The core module I focused on is our security and rate-limiting pipeline, which differentiates quotas between guest IPs and Firebase-authenticated users to safeguard our upstream AI budget."*
* **Interviewer Assessment:** **Clarity: 9/10 | Depth: 9/10.** Clear hook, concise stack summary, immediately sets up a defensible technical discussion.

### Question 2: Technical Deep-Dive
* **Interviewer:** *"I see you're using Redis for rate limiting. How does your filter distinguish between authenticated and unauthenticated traffic?"*
* **Candidate:** *"In [RateLimitingFilter.java](file:///d:/Nirmay%20pc/ENGINEERING/projects/SpringBootProject/Project/src/main/java/com/example/mockinterview/config/RateLimitingFilter.java#L50-L61), the filter inspects the Spring `SecurityContextHolder`. If the context contains an authenticated user token from Firebase, we extract their email/UID and construct the Redis key `ratelimit:uid:<userEmail>` with a limit of 100 requests per minute. If the context is unauthenticated or anonymous, we extract the client IP via the `X-Forwarded-For` header or `getRemoteAddr()` and use `ratelimit:ip:<ip>` with a stricter limit of 20 requests per minute."*
* **Interviewer Assessment:** **Clarity: 10/10 | Technical Accuracy: 10/10.** Directly references code mechanics and exact key formatting.

### Question 3: The Edge Case
* **Interviewer:** *"What happens if your Redis cluster crashes during traffic peak? Does the application go down?"*
* **Candidate:** *"No, the application stays up. In [RateLimitingFilter.java](file:///d:/Nirmay%20pc/ENGINEERING/projects/SpringBootProject/Project/src/main/java/com/example/mockinterview/config/RateLimitingFilter.java#L75-L77), I wrapped the Redis increment and expiration calls in a `try-catch` block. If Redis throws a connection exception, the filter logs a warning and invokes `filterChain.doFilter()`, allowing the request to proceed. This is a deliberate 'fail-open' design decision: we prioritize platform availability over rate limiting enforcement during cache infrastructure failures."*
* **Interviewer Assessment:** **Clarity: 10/10 | Depth: 10/10.** Demonstrates real architectural maturity by articulating the deliberate trade-off between availability and quota enforcement.
