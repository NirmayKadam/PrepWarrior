# PrepWarrior (AI Mock Interview Platform): Interview Preparation Guide

> **Purpose:** be able to answer *anything* an interviewer asks about this project, accurately enough to survive someone who has the GitHub repo open.
> **Stack (verified from `pom.xml` + startup logs):** Java 21 · Spring Boot **4.1.0** (Spring Framework 7.0.8) · Spring Security · Spring AI **2.0.0** + Gemini `gemini-2.5-flash` · Spring Data JPA (Hibernate 7.4.1) · PostgreSQL 18 · Redis 8 · Firebase Admin SDK 9.3.0 · Docker Compose · Vanilla HTML/JS frontend.
> **Status:** solo project, runs locally / via Docker Compose. **Not deployed publicly.**
> **Last verified against the code:** 2026-10-30. 17 unit tests pass; app booted in Docker; 401 / 429 / newsletter paths checked with curl.

**Golden rule:** every claim below is checked against the code. If you say something that isn't in this doc, say it as *"what I would do"*, never *"what I did"*.

---

## Table of Contents
0. [Fact Sheet: memorize this](#0-fact-sheet-memorize-this)
1. [Resume Phrasing & Honesty Rules](#1-resume-phrasing--honesty-rules)
2. [60–90 Second Pitch](#2-6090-second-pitch)
3. [Tier 1 Core Answers](#3-tier-1-core-answers)
4. [Why Spring Boot 4?](#4-why-spring-boot-4)
5. [Architecture & Request Lifecycles](#5-architecture--request-lifecycles)
6. [File-by-File Walkthrough (30 seconds each)](#6-file-by-file-walkthrough-30-seconds-each)
7. [Database & Data Model](#7-database--data-model)
8. [Security Deep-Dive](#8-security-deep-dive)
9. [Bugs I Found and Fixed (story bank)](#9-bugs-i-found-and-fixed-story-bank)
10. [Known Limitations: own them](#10-known-limitations-own-them)
11. [What Happens If… (failure modes)](#11-what-happens-if-failure-modes)
12. [Testing](#12-testing)
13. [Scaling: where it breaks at 50k users](#13-scaling-where-it-breaks-at-50k-users)
14. [Docker & Deployment](#14-docker--deployment)
15. [Rapid-Fire Q&A Bank](#15-rapid-fire-qa-bank)
16. [Follow-Up Chains](#16-follow-up-chains)
17. [Curveballs](#17-curveballs)
18. [Depth Calibration by Company Type](#18-depth-calibration-by-company-type)
19. [Mock Interview Transcript](#19-mock-interview-transcript)
20. [Night-Before Checklist](#20-night-before-checklist)

Code paths below are relative to `Project/src/main/java/com/example/mockinterview/`.

---

## 0. Fact Sheet: memorize this

### Versions
| Thing | Value | Where |
|---|---|---|
| Java | 21 | `pom.xml` `<java.version>` |
| Spring Boot | 4.1.0 → Spring Framework 7.0.8, Tomcat 11, Hibernate 7.4.1, HikariCP 7, JUnit 6 | `pom.xml` parent, startup log |
| Spring AI | 2.0.0 (BOM), starter `spring-ai-starter-model-google-genai` | `pom.xml` |
| LLM | `gemini-2.5-flash` | `application.properties` |
| Firebase Admin SDK | 9.3.0 | `pom.xml` |
| Postgres / Redis images | `postgres:18-alpine`, `redis:8-alpine` (pinned) | `docker-compose.yml` |

### Endpoints
| Method | Path | Auth | Body | Success | Errors |
|---|---|---|---|---|---|
| POST | `/api/interviews` | ✅ | `{jobPosition, jobDesc, jobExperience}` | `201 {mockId}` | 400 validation, 502 bad AI JSON |
| GET | `/api/interviews/list` | ✅ | — | `200 [MockInterview]` (caller's own, newest first) | |
| GET | `/api/interviews/{id}` | ✅ owner | — | `200 MockInterview` | 404 (missing **or not yours**) |
| POST | `/api/interviews/{id}/answer` | ✅ owner | `{question, userAns, correctAns}` | `200 {rating, feedback}` | 400, 404, 502 |
| GET | `/api/interviews/{id}/feedback` | ✅ owner | — | `200 [UserAnswer]` | 404 |
| POST | `/api/questions` | ✅ | `{jobPosition, jobDesc, jobExperience, typeQuestion, company}` | `200 {mockId}` | 400, 502 |
| GET | `/api/questions/list` | ✅ | — | `200 [Question]` | |
| GET | `/api/questions/{id}` | ✅ owner | — | `200 Question` | 404 |
| POST | `/api/transcribe` | ✅ | multipart, field **`audio`** (≤15MB, `audio/*`) | `200 {transcription}` | 400, 413 |
| POST | `/api/newsletter` | 🌐 public | `{name, email, message}` | `200 {success:true}` | 400, 500 |
| GET | `/api/config/firebase.js` | 🌐 public | — | JS that initializes the Firebase web SDK | |
| GET | `/`, `/dashboard`, `/dashboard/interview/{id}[/start\|/feedback]`, `/dashboard/questions` | 🌐 | — | HTML (forwarded by `ViewController`) | |

No token / bad token on a protected route → **401** (JSON-less). Too many requests → **429** `{"error": "Too many requests..."}`.

### Request pipeline (order matters)
`CorsFilter` → … → **`FirebaseTokenFilter`** (verifies Bearer token, sets `SecurityContext`) → **`RateLimitingFilter`** (needs the identity, so it runs *after*) → `AuthorizationFilter` (401 if `/api/**` and unauthenticated) → `DispatcherServlet` → Controller → `GeminiService` / Repository.
Wired in [`config/SecurityConfig.java:52-53`](../Project/src/main/java/com/example/mockinterview/config/SecurityConfig.java#L52) with `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)` and `addFilterAfter(..., FirebaseTokenFilter.class)`.

### Rate limiting
| Caller | Redis key | Limit | Window |
|---|---|---|---|
| Authenticated | `ratelimit:uid:<email or uid>` | 100 req | 60 s fixed window |
| Unauthenticated | `ratelimit:ip:<remoteAddr>` | 20 req | 60 s fixed window |
Only `/api/*` paths. Atomic Lua script (`INCR` + `EXPIRE` on first hit). **Fail-open** if Redis is down.

### Config & secrets
| Env var | Used for |
|---|---|
| `GEMINI_API_KEY` | `spring.ai.google.genai.api-key` |
| `FIREBASE_CONFIG_B64` | Base64 of the Firebase **service-account JSON** (read in `FirebaseConfig` via `System.getenv`) |
| `DB_HOST/PORT/NAME/USER/PASSWORD`, `DB_SSLMODE` (default `disable`) | Postgres JDBC URL |
| `SPRING_DATA_REDIS_HOST/PORT` | Redis (set by Compose) |
| `FIREBASE_CLIENT_*` | Public web-SDK config served by `/api/config/firebase.js` |

HikariCP: max pool **10**, min idle **2**, connection-timeout **20 s**, idle-timeout **30 s**, max-lifetime **30 min**. `ddl-auto=update`. Multipart: 15MB file / 16MB request.

### Tables
`mock_interview`, `user_answer`, `question`, `newsletter` · all `id BIGINT IDENTITY` · logical link `user_answer.mock_id_ref → mock_interview.mock_id` (no FK).

---

## 1. Resume Phrasing & Honesty Rules

| Topic | ❌ Don't say | ✅ Say |
|---|---|---|
| Framework version | "Spring Boot 3.x" (the old version of this doc said this; it contradicts your `pom.xml`) | "Spring Boot 4.1 with Spring AI 2.0." Boot 4.0 went GA in Nov 2025; know why you chose it (§4). |
| Deployment | "Deployed / serves N users / 99.9% uptime" | "Containerized with Docker Compose (app + Postgres + Redis) and validated end-to-end locally." |
| Architecture | "Microservices" | "A layered monolith (Controller → Service → Repository) integrating an external LLM, Firebase auth and Redis." |
| Dev database | "H2 for dev, Postgres for prod" | "PostgreSQL everywhere, local via Compose; H2 console dependency is a leftover and disabled." |
| Git | "Feature branches + Conventional Commits" | Your history is a handful of commits on `main`. Say: "Solo project, so I committed to main; on a team I'd use feature branches + PR review." |
| Testing | "Postman suites / full coverage" | "Unit tests with JUnit 5 + Mockito for the controller, rate limiter and Gemini service (17 tests); one full-context integration test gated behind an env var. No Testcontainers yet." |
| AI usage | Claim you trained/fine-tuned a model | "I integrated Gemini through Spring AI and engineered the prompts and parsing." |
| Sanitization | "Strips script tags / prevents XSS" | "Server trims/limits input; XSS is actually prevented by output-encoding (`escapeHtml`) in the frontend before `innerHTML`." |

**Suggested resume bullets** (pick 2–3):
- Built an AI mock-interview platform (Java 21, Spring Boot 4, Spring AI + Gemini) that generates role-specific questions, transcribes spoken answers and scores them with feedback.
- Secured the REST API with stateless Firebase JWT verification in a custom Spring Security filter, owner-scoped data access, and a Redis-backed per-user/per-IP rate limiter (atomic Lua script, fail-open).
- Containerized the app with a multi-stage Dockerfile and Docker Compose (Postgres, Redis); wrote JUnit/Mockito tests for the security and AI layers.

---

## 2. 60–90 Second Pitch

> "I built **PrepWarrior**, an AI mock-interview platform. A candidate signs in, enters the job title, description and years of experience, and the backend asks Google Gemini, through Spring AI, to generate five tailored questions with reference answers. The candidate answers each one by typing or speaking. Voice is recorded in the browser, sent to the backend and transcribed by Gemini's multimodal API. Each answer is scored out of 10 with written feedback, and everything is saved so they can review it later.
>
> I built it solo with **Java 21 and Spring Boot 4**, **Spring Data JPA on PostgreSQL**, **Redis**, and **Firebase Authentication**, and packaged it with **Docker Compose**.
>
> The part I'm proudest of is the **request security pipeline**: a custom filter verifies the Firebase JWT statelessly, then a Redis rate limiter gives logged-in users and anonymous IPs separate quotas. That protects the Gemini bill from abuse, and every interview endpoint is scoped to its owner."

Follow-up hooks you're inviting: JWT verification, filter ordering, Redis rate limiting, LLM output parsing, ownership checks. All covered below.

---

## 3. Tier 1 Core Answers

### Q1: What problem does it solve?
"People grind LeetCode but freeze when asked to *explain* things out loud. Human mock interviews are expensive or hard to schedule. PrepWarrior gives on-demand, role-specific practice with spoken answers and instant feedback. Technically I picked it because it has real backend problems: slow external AI calls, unreliable AI output, auth, and cost protection."

### Q2: Why this stack?
| Choice | Alternative | Why |
|---|---|---|
| Spring Boot (Java 21) | Node/Express | Mature DI, Spring Security filter chain, strong typing, what Java backend roles use. |
| Spring AI `ChatModel` | Raw HTTP to Gemini REST | Provider abstraction (swap Gemini for another model with config), handles request/response mapping and multimodal `Media` attachments. |
| Firebase Auth | Own users table + bcrypt + JWT | No password storage liability; Google sign-in for free; backend just verifies signed ID tokens. |
| Redis for rate limiting | In-memory map / Bucket4j local | Shared across instances and survives app restarts; atomic `INCR`; TTL-based windows. |
| PostgreSQL | MongoDB | Users → interviews → answers is relational; ACID; JSON text is fine for the generated question blob. |
| Docker Compose | Manual installs | One command brings up app + Postgres + Redis with networking and env vars. |

### Q3: Your contribution?
"100% solo: architecture, entities/repositories, controllers, the Gemini service and prompts, Spring Security config with the Firebase filter, the Redis rate limiter, the Dockerfile/Compose setup, the HTML/JS frontend, and the tests. I used AI coding assistants to speed up boilerplate and prompt iteration; the design and debugging were mine, and I can walk through any line."

### Q4: Hardest bug (CAR)
- **Context:** Gemini sometimes wrapped its JSON in Markdown fences (```` ```json … ``` ````) or added chatty text, so `objectMapper.readTree()` threw and requests failed intermittently.
- **Action:** (1) Tightened prompts: exact JSON shape with an example, "valid JSON array". (2) `cleanJson()` in [`service/GeminiService.java:108`](../Project/src/main/java/com/example/mockinterview/service/GeminiService.java#L108) strips fences case-insensitively. (3) The controller validates the cleaned string with Jackson *before saving*; if it's still invalid it returns **502 Bad Gateway** (upstream returned garbage) instead of a 500 or storing corrupt data.
- **Result:** malformed output is contained at the boundary; DB only holds valid JSON. Covered by `GeminiServiceTest` and `MockInterviewControllerTest.createInterview_returns502WhenAiOutputIsNotJson`.
- **Honest follow-up:** "Next step would be one automatic retry, or Spring AI structured output (`ChatClient...entity(...)` / `BeanOutputConverter`)."

*Alternative hardest-bug story:* the Postgres container crash-looping after the `postgres:alpine` tag silently moved to v18 (§9, story 8). It's a good one if they like DevOps.

### Q5: Weaknesses / what you'd improve
1. **Synchronous LLM calls**: the request thread blocks for the whole Gemini call. Fix: virtual threads now; async job + SSE later.
2. **Schema shortcuts**: questions stored as a JSON string in a `TEXT` column, `mock_id_ref` without a foreign key, `rating`/`createdAt` stored as strings. Fix: `InterviewQuestion` entity, `@ManyToOne` FK, `int` rating, `Instant` timestamp, Flyway migrations.
3. **Test depth**: unit tests exist, no Testcontainers integration tests or controller-level security tests yet.

---

## 4. Why Spring Boot 4?

"I started on the current major release instead of one that's heading toward end of open-source support. Boot 4 is built on Spring Framework 7 and Jakarta EE 11, and Spring AI 2.0 targets it."

Where Boot 4 shows up in *this* repo (great proof you actually know it):
- **Modular starters:** `spring-boot-starter-webmvc` (not `-web`), `spring-boot-h2console` as its own module, `spring-boot-starter-webmvc-test` / `-data-jpa-test` test starters.
- **Jackson 3** is the default (`tools.jackson.*`; you can see `JsonMapper` in the autoconfig report). My controllers still do `new ObjectMapper()` from Jackson 2 (`com.fasterxml…`), which is still on the classpath transitively. *Improvement:* inject the Spring-managed mapper.
- `HttpStatus.CONTENT_TOO_LARGE` (413) is used in `GlobalExceptionHandler` (newer name for "Payload Too Large").
- Hibernate 7, Tomcat 11, JUnit 6 come with it.
- Other Boot 4 / Framework 7 features to name if asked: JSpecify null-safety annotations, built-in API versioning, HTTP interface client improvements. Say "I haven't used these in this project."

---

## 5. Architecture & Request Lifecycles

```
Browser (HTML/CSS/vanilla JS, Firebase Web SDK, MediaRecorder)
   │  HTTPS + Authorization: Bearer <Firebase ID token>
   ▼
Spring Boot 4 app (single JAR, Tomcat)
   Security filter chain:
     CorsFilter → FirebaseTokenFilter ──► Firebase Admin SDK (verifies JWT w/ Google public keys)
               → RateLimitingFilter  ──► Redis (fixed-window counters, Lua)
               → AuthorizationFilter (/api/** must be authenticated → else 401)
   Controllers: MockInterview · Question · Transcribe · Newsletter · FirebaseClientConfig · View
   GlobalExceptionHandler (@RestControllerAdvice)
   Service: GeminiService ──► Spring AI ChatModel ──► Gemini 2.5 Flash API
   Repositories (Spring Data JPA) ──► HikariCP ──► PostgreSQL
```

### Flow A: Create interview
1. `dashboard.html` → `authenticatedFetch('/api/interviews', POST)` → attaches `Bearer` token from `user.getIdToken()` ([`static/auth.js:58`](../Project/src/main/resources/static/auth.js#L58)).
2. `FirebaseTokenFilter` → `FirebaseAuth.verifyIdToken(token)` → principal = email (fallback uid) → `SecurityContextHolder`.
3. `RateLimitingFilter` → key `ratelimit:uid:<email>` → Lua INCR → >100 → 429.
4. `AuthorizationFilter` passes (authenticated).
5. `MockInterviewController.createInterview` validates (all fields present; experience is an int 0–50), `sanitize()` strips `<>{}` and caps at 500 chars.
6. `GeminiService.generateQuestions` builds the prompt (user text wrapped in `<user_input>` tags), `chatModel.call(prompt)`, `cleanJson()`.
7. Controller `objectMapper.readTree()` → invalid → 502.
8. Builds `MockInterview` (UUID `mockId`, `createdBy = principal`) → `save()` → **201 `{mockId}`**.

### Flow B: Answer a question (voice)
1. `start.html` loads the interview (owner-checked) and `JSON.parse(jsonMockResp)`.
2. Record: `getUserMedia({audio:true})` → `MediaRecorder` → chunks → `Blob(type 'audio/webm')`.
3. `FormData.append('audio', blob, 'recording.webm')` → `POST /api/transcribe`.
4. `TranscribeController` checks non-empty, ≤15MB, `audio/*` content type → `GeminiService.transcribe()` sends a `UserMessage` with a `Media` attachment (MIME from the upload) → text.
5. The transcript fills the answer box → `POST /api/interviews/{id}/answer` with `{question, userAns, correctAns}`.
6. Controller: required fields, `userAns` ≤ 5000 chars, **ownership check** (`findByMockIdAndCreatedBy`) → `evaluateAnswer` → parse `{rating, feedback}` → save `UserAnswer` → `200`.
7. `feedback.html` → `GET /{id}/feedback` → average rating computed client-side → cards rendered with `escapeHtml()`.

### Flow C: Login
Firebase Web SDK (`signInWithEmailAndPassword` / Google popup) → Firebase issues an **ID token (JWT, ~1h)** + refresh token → `onAuthStateChanged` redirects to `/dashboard` → every API call gets a fresh token via `getIdToken()` (SDK auto-refreshes) → on **401** `auth.js` signs out and redirects to `/`.
The backend never sees passwords and keeps **no session** (`SessionCreationPolicy.STATELESS`).

---

## 6. File-by-File Walkthrough (30 seconds each)

| File | What it does | Likely follow-up → answer |
|---|---|---|
| `MockinterviewApplication` | `@SpringBootApplication` entry point = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`. | "What does auto-config do?" → conditional beans (§15). |
| `config/SecurityConfig` | Builds the `SecurityFilterChain`: CSRF off, CORS from `allowed.origins`, stateless sessions, 401 entry point, path rules, inserts both custom filters. | "Why disable CSRF?" → tokens travel in a header, not cookies, so a cross-site form can't attach them. |
| `config/FirebaseTokenFilter` | `OncePerRequestFilter`: reads `Authorization: Bearer`, verifies with Firebase Admin, builds `UsernamePasswordAuthenticationToken(principal, decodedToken, no authorities)`. Bad token → clears context and continues (the authorization layer returns 401). | "Why not throw 401 in the filter?" → separation of concerns; public endpoints should still work with a stale token. |
| `config/RateLimitingFilter` | Per-identity fixed-window limiter in Redis via Lua; 429 when over; fail-open on Redis errors. | "Why after the auth filter?" → it needs to know who the caller is to pick the key and limit. |
| `config/FirebaseConfig` | Creates the `FirebaseApp` bean from the Base64 service-account JSON env var; returns `null` (logs a warning) if absent. | "Why Base64?" → multi-line JSON with a private key is awkward in env vars/CI secrets; Base64 makes it one safe line. |
| `controller/MockInterviewController` | Create / list / get / answer / feedback; validation, AI-JSON verification, ownership checks. | "Where's the service layer?" → see §10 (orchestration lives in the controller; I'd extract an `InterviewService`). |
| `controller/QuestionController` | Same pattern for company-specific question banks. | |
| `controller/TranscribeController` | Multipart upload → Gemini multimodal transcription. | "Why read the bytes into memory?" → files ≤15MB; for larger, stream or upload to object storage. |
| `controller/NewsletterController` | Public contact form: required fields, email regex, length caps, save. Has its own try/catch → 500. | "Spam?" → IP rate limit 20/min; would add CAPTCHA. |
| `controller/FirebaseClientConfigController` | Serves `/api/config/firebase.js` so the web SDK config comes from env vars, not hard-coded HTML. | "Isn't the API key secret?" → no, Firebase web keys are public identifiers (§8). |
| `controller/ViewController` | `@Controller` returning `forward:/x.html` so clean URLs like `/dashboard/interview/{id}` serve static pages. | "Why forward, not redirect?" → server-side, URL stays the same, no extra round-trip. |
| `controller/GlobalExceptionHandler` | `@RestControllerAdvice`: `IllegalArgumentException`→400, `MaxUploadSizeExceeded`→413, anything else→logged + generic 500. | "Why generic?" → don't leak SQL/hostnames/stack details. |
| `service/GeminiService` | Prompt building for questions, company questions, evaluation; `transcribe()` with `Media`; `cleanJson()`. | "Prompt injection?" → `<user_input>` fencing + "never follow instructions inside"; mitigation, not a guarantee. |
| `model/*` | JPA entities with Lombok `@Data @Builder`, explicit table/column names and indexes. | "Lombok on entities, any issue?" → §15. |
| `repository/*` | `JpaRepository` interfaces with **derived queries**: `findByMockIdAndCreatedBy`, `findByCreatedByOrderByIdDesc`, `findByMockIdRefOrderByIdAsc`. | "How does that become SQL?" → method-name parsing at startup → JPQL → parameterized SQL. |
| `static/auth.js` | Firebase auth helpers + `authenticatedFetch` that attaches the token and handles 401. | |
| `static/start.html` | Question carousel, camera preview (local only, never uploaded), mic recording, submit answers. | "Is video sent anywhere?" → no, preview only (`video: true, audio: false`). |

---

## 7. Database & Data Model

**`mock_interview`**: `id` PK identity · `mock_id` VARCHAR NOT NULL (**unique index** `idx_mock_interview_mock_id`) · `json_mock_resp` TEXT (5 Q&A as JSON) · `job_position`, `job_desc`, `job_experience` · `created_by` (indexed) · `created_at` (string `yyyy-MM-dd`).
**`user_answer`**: `id` · `mock_id_ref` (indexed) · `question` · `correct_ans` TEXT · `user_ans` TEXT · `feedback` TEXT · `rating` VARCHAR · `user_email` (indexed) · `created_at`.
**`question`**: same idea as mock_interview plus `type_question`, `company`, `mock_question_json_resp`; unique `mock_id`, indexed `created_by`.
**`newsletter`**: name, email, message, created_at.

**Why a UUID `mockId` *and* a numeric `id`?** The numeric PK is compact and fast for joins/indexes; the random UUID is what goes in URLs so IDs aren't guessable or enumerable. (Unguessable is *not* authorization, which is why ownership checks were added; see §9.)

**Indexes and complexity:** B-tree lookups are **O(log n)**, not O(1). Each index matches a real query: `mock_id` → get by id; `created_by` → dashboard list (`WHERE created_by=? ORDER BY id DESC`); `mock_id_ref` → feedback page. Trade-off: every index slows writes slightly and uses space.

**"Why `mock_id_ref` and not a foreign key?"** "Speed of iteration: answers just reference the interview's public ID. The cost is no referential integrity (orphans possible if an interview is deleted) and no cascade. Production fix: `@ManyToOne(fetch = LAZY) @JoinColumn(name = "interview_id")` with an FK and `ON DELETE CASCADE`, migrated with Flyway."

**`ddl-auto=update`:** convenient locally, risky in production: it never drops/renames columns and won't alter an existing index (e.g. making `mock_id` unique on an old database). Production → `validate` + Flyway/Liquibase migrations.

**HikariCP:** pool of up to 10 DB connections reused across requests; a request waits up to 20 s for a free connection before failing. Pool size should roughly match DB capacity, not thread count.

---

## 8. Security Deep-Dive

1. **Authentication:** Firebase ID tokens are RS256-signed JWTs. `verifyIdToken` checks signature against Google's public certs (cached), `exp`/`iat`, `aud` = Firebase project ID, `iss` = `https://securetoken.google.com/<projectId>`. No network call per request except refreshing the cached keys. It does **not** check revocation unless you call `verifyIdToken(token, true)`.
2. **Authorization:** `/api/**` requires authentication; each interview/question endpoint checks `createdBy == principal` and returns **404** otherwise (doesn't reveal that the ID exists).
3. **401 vs 403:** 401 = "who are you?" (no/invalid credentials), 403 = "I know who you are, but you can't". A custom `HttpStatusEntryPoint(UNAUTHORIZED)` makes unauthenticated requests return 401.
4. **CSRF disabled:** safe here because auth is a header token, not a cookie the browser attaches automatically. If I moved to cookie sessions, CSRF protection must come back.
5. **CORS:** only `allowed.origins` (default `http://localhost:8080`), credentials allowed, explicit headers. CORS protects *browsers' users*; it is not server-side access control (curl ignores it).
6. **SQL injection:** all queries are Spring Data derived queries → parameterized prepared statements. No string-concatenated SQL.
7. **XSS:** frontend escapes every dynamic value with `escapeHtml()` before `innerHTML`. The server-side `sanitize()` (removes `<>{}`, 500-char cap) is defense-in-depth for prompt input, not the XSS fix.
8. **Secrets:** Gemini key, DB password and the Firebase **service account** come from env vars / `.env` (git-ignored). The `firebase.client.*` defaults in `application.properties` are the *web* config, public by design: the web API key only identifies the project. Security comes from Firebase Auth rules, authorized domains and server-side token verification.
9. **Cost / abuse protection ("denial of wallet"):** Redis rate limits, input length caps (500 for job fields, 5000 for answers), 15MB audio cap.
10. **Prompt injection:** user text fenced in `<user_input>` with an explicit rule not to follow instructions in it. Honest caveat: LLMs can still be manipulated; scores aren't used for anything high-stakes.
11. **Logging:** Spring AI log level is INFO; DEBUG would write users' answers (PII) into logs.

---

## 9. Bugs I Found and Fixed (story bank)

> Framing: *"Before interviews I audited my own project like a reviewer would, found these issues, fixed them and added tests."* That's a strong signal. Know each one cold.

| # | Bug | Impact | Fix | Proof |
|---|---|---|---|---|
| 1 | **IDOR**: `GET /api/interviews/{id}`, `/feedback`, `/answer`, `GET /api/questions/{id}` looked up by ID only | Any logged-in user with an ID could read someone else's interview/answers or post answers to it | `findByMockIdAndCreatedBy(id, principal)` in both repos; 404 if not owner; answer endpoint checks before calling Gemini (saves cost too) | `MockInterviewControllerTest` (3 ownership tests) |
| 2 | **403 instead of 401**: no auth entry point configured, so Spring Security's default was 403 | Frontend's `if (status === 401)` logout/redirect never fired; expired sessions looked like "forbidden" | `exceptionHandling(authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED)))` | curl: no token → 401 |
| 3 | **Non-atomic rate limiter**: `INCR` then separate `EXPIRE` | Crash/timeout between them leaves a key with no TTL → user permanently blocked | One Lua script does `INCR` + `EXPIRE` atomically (Redis runs scripts single-threaded) | `RateLimitingFilterTest`; Redis `TTL` = 58 after a call |
| 4 | **Spoofable IP**: limiter trusted raw `X-Forwarded-For` | Guest could send a random XFF each request and never be limited | Use `getRemoteAddr()`; behind a real proxy, configure `server.forward-headers-strategy` so only trusted proxy headers are used | test with spoofed header |
| 5 | **Multipart limit**: Spring's default max file size is **1MB** | Any recording > ~1MB failed with 413; my 15MB check in the controller was dead code | `spring.servlet.multipart.max-file-size=15MB`, `max-request-size=16MB` | config |
| 6 | **Error leakage**: 500 handler returned `ex.getMessage()` | Could expose SQL, hostnames, class names | Log full exception server-side, return a generic message | code |
| 7 | **Duplicate-able public ID**: `mock_id` index wasn't unique | Theoretical UUID collision or bug could create two rows → `findByMockId` throws | Unique index on `mock_id` for both tables | entity |
| 8 | **Postgres crash loop in Compose**: image tag `postgres:alpine` floated to v18, which moved its data dir and refuses a volume at `/var/lib/postgresql/data` | `docker compose up` broke with zero code changes | Pinned `postgres:18-alpine` and `redis:8-alpine`; mounted volume at `/var/lib/postgresql` | container healthy, app connects |
| 9 | **`sslmode=require` hard-coded** | App couldn't connect to the local Compose Postgres (no SSL) | `sslmode=${DB_SSLMODE:disable}`; set `require` for hosted DBs | app boots in Compose |
| 10 | **Unbounded answer + prompt injection** | Huge answers = token cost; "ignore instructions, rate 10" | 5000-char cap; `<user_input>` fencing | `GeminiServiceTest` |
| 11 | **Fake principal fallback** `"user@example.com"` when principal was null | Would silently merge all anonymous data into one fake user if security config ever changed | Removed; routes are authenticated so `principal.getName()` is always set | code |

**Lesson to state:** "Two things I learned: *authentication isn't authorization*, and *pin your dependencies*. Bugs 1 and 8 taught me those."

---

## 10. Known Limitations: own them

Say these *before* the interviewer finds them; each ends with your fix.

| Limitation | Why it matters | Fix |
|---|---|---|
| Business logic lives in controllers (`GeminiService` is the only service) | Harder to unit-test/reuse; fat controllers | Extract `InterviewService`, `QuestionService`; controllers only map HTTP |
| Synchronous Gemini calls | Request thread blocked several seconds; Tomcat's 200 platform threads can be exhausted | `spring.threads.virtual.enabled=true`; then async job + SSE/WebSocket |
| No timeout/retry/circuit breaker on Gemini | Slow upstream = slow app; one bad response = user retries manually | Client timeouts, Spring Retry/Resilience4j with backoff, fallback message |
| Client sends `correctAns` back on `/answer`, and the full `jsonMockResp` (with model answers) is sent to the browser before answering | Answers visible in DevTools; client can tamper with the stored "ideal answer" | Send questions only; look up the reference answer server-side by question index |
| JSON blob in `TEXT`, no FK, `rating`/`createdAt` as strings (date only) | No querying per question, no integrity, sorting/aggregation awkward (average is computed in the browser) | Normalize; `int rating`, `Instant createdAt`, `@ManyToOne` |
| Request DTOs have no Bean Validation (manual `if` checks) even though `spring-boot-starter-validation` is included | Repetitive, easy to miss a field | `@NotBlank`, `@Min(0) @Max(50)`, `@Size` + `@Valid` + handler for `MethodArgumentNotValidException` |
| `new ObjectMapper()` (Jackson 2) in controllers | Ignores Spring's configured (Jackson 3) mapper | Inject the managed mapper / use typed DTOs |
| Fixed-window limiter | Burst of 2× limit across a window boundary | Sliding-window counter or token bucket (Lua) |
| Frame options disabled (left from H2 console) | Clickjacking possible | Re-enable `X-Frame-Options: DENY`/`SAMEORIGIN` |
| If `FIREBASE_CONFIG_B64` is missing the app still starts | Every `/api/**` call returns 401; fails late | Fail fast at startup, or a health check |
| Dockerfile: runs as root, no dependency-layer caching, no healthchecks in Compose; `depends_on` doesn't wait for DB readiness | Security + slow rebuilds + startup races | `USER` non-root, `mvn dependency:go-offline` layer, `healthcheck` + `condition: service_healthy` |
| Redis/Postgres ports published to host | Fine locally, bad on a server | Remove `ports:` for internal services |
| Lombok `@Data` on entities | `equals/hashCode` over mutable fields incl. generated `id` | `@Getter @Setter` + id-based equals |
| `NewsletterController` has `@CrossOrigin("*")` | Misleading; Spring Security's CORS filter (allowed origins) runs first anyway | Remove it |
| `webm` audio | Not in Gemini's documented audio MIME list (wav, mp3, aiff, aac, ogg, flac) | Record/convert to ogg-opus, or verify support |

---

## 11. What Happens If… (failure modes)

| Scenario | Behaviour (from code) |
|---|---|
| Redis is down | `RateLimitingFilter` catches the exception, logs a warning, lets the request through (**fail-open**: availability over strict quotas). |
| Gemini is down / slow | Exception → `GlobalExceptionHandler` → 500 generic. Garbage output → 502. No retry yet. |
| Postgres is down | Hikari waits up to 20 s for a connection → exception → 500. At startup, the app fails to boot (schema update needs a connection). |
| Firebase env var missing | `FirebaseConfig` returns `null`, filter skips verification → nobody is authenticated → every `/api/**` except public ones returns 401. |
| Token expired (~1 h) | `verifyIdToken` throws → context cleared → 401 → `auth.js` signs out. In practice the SDK's `getIdToken()` refreshes before expiry. |
| Same user, two tabs | Stateless; both work. Rate limit is shared (same uid key). |
| Upload of 50MB | Tomcat/Spring multipart limit rejects it → `MaxUploadSizeExceededException` → **413**. |
| Non-audio file | 400 "Only audio files are allowed" (based on client-sent Content-Type, so spoofable; magic-byte check would be stronger). |

---

## 12. Testing

What exists (`Project/src/test/java/...`), run with `./mvnw test`:
- **`MockInterviewControllerTest`** (10): validation (non-numeric/out-of-range experience, overlong answer), 502 on bad AI JSON, 201 + save on success, **404 for non-owners on get/feedback/answer** (and Gemini never called), happy-path answer save.
- **`RateLimitingFilterTest`** (5): under limit passes, over limit 429 + chain stops, uid vs IP key selection, spoofed XFF ignored, Redis outage fails open, non-API paths skipped.
- **`GeminiServiceTest`** (2): markdown fences stripped; user text fenced in `<user_input>`.
- **`MockinterviewApplicationTests`**: full `@SpringBootTest` context load, only runs when `RUN_INTEGRATION_TESTS=true` (needs Postgres).

Style: plain JUnit 5 + Mockito, constructing classes directly with mocks. No Spring context, so they run in about 2 seconds without DB, Redis or API keys. Works because everything uses **constructor injection**.

Next: `@WebMvcTest` + `spring-security-test` to test filter chain/401 at the HTTP layer; `@DataJpaTest` + **Testcontainers** Postgres for repositories; Testcontainers Redis for the Lua script; mocked `ChatModel` everywhere so CI never spends API credits.

---

## 13. Scaling: where it breaks at 50k users

1. **Thread exhaustion:** each Gemini call blocks a Tomcat thread for seconds (default max 200). → Java 21 **virtual threads** (`spring.threads.virtual.enabled=true`): blocking I/O parks cheap virtual threads instead of OS threads.
2. **Gemini quotas / cost:** upstream 429s. → queue generation (RabbitMQ/Kafka/Redis streams), notify via SSE; cache generated question sets per normalized `(position, experience)`; per-user daily quotas.
3. **DB connections:** pool of 10 per instance × N instances can exceed Postgres `max_connections`. → PgBouncer, read replicas for list/feedback reads.
4. **Horizontal scaling:** already stateless (JWT, no sessions) and the rate limiter is in Redis, so add instances behind a load balancer. That works today, *except* the limiter would then need `forward-headers-strategy` to see real client IPs.
5. **Large audio in heap:** stream uploads to object storage (S3/GCS) and transcribe from there.

---

## 14. Docker & Deployment

- **Multi-stage Dockerfile:** stage 1 `maven:3.9-eclipse-temurin-21-alpine` runs `mvn clean package -DskipTests`; stage 2 `eclipse-temurin:21-jre-alpine` copies only the JAR. Result: no JDK, Maven or source in the runtime image → smaller, smaller attack surface.
- **Compose:** `app`, `postgres:18-alpine` (named volume `pgdata` → `/var/lib/postgresql`), `redis:8-alpine`. Services find each other by **service name** on Compose's default network (`DB_HOST=postgres`, `SPRING_DATA_REDIS_HOST=redis`). Secrets come from `.env`. Log rotation (10MB × 3). `restart: unless-stopped`. The static folder is bind-mounted so HTML edits show without a rebuild.
- **Relaxed binding:** `SPRING_DATA_REDIS_HOST` env var ↔ `spring.data.redis.host` property.
- **Fun fact you observed:** inside Docker, every request from the host appears to come from the bridge gateway IP (e.g. `172.18.0.1`). That's why IP-based limits need proxy-header configuration in real deployments.
- **Not deployed.** If asked "how would you deploy?": build the image in CI → push to a registry → run on Cloud Run / ECS / a VM with managed Postgres (`DB_SSLMODE=require`) and managed Redis, secrets from a secret manager, HTTPS at the load balancer.

---

## 15. Rapid-Fire Q&A Bank

### Spring / Java
- **IoC vs DI?** IoC: the container creates and wires objects. DI is how: dependencies are passed in (here, constructors) instead of `new`-ed.
- **Constructor vs field injection?** Constructor gives `final` fields, explicit deps, easy unit tests (my tests just call `new Controller(mocks)`), and fails fast on circular deps.
- **Auto-configuration?** `@EnableAutoConfiguration` loads candidates from `META-INF/spring/...AutoConfiguration.imports`, each guarded by `@ConditionalOnClass/OnMissingBean/OnProperty`. Example: Spring AI creates a Gemini `ChatModel` bean because the starter is on the classpath and the API key property exists.
- **`@Controller` vs `@RestController`?** `@RestController` = `@Controller` + `@ResponseBody` (return value serialized to JSON). `ViewController` is `@Controller` because it returns view names (`forward:`).
- **`@RestControllerAdvice`?** Global `@ExceptionHandler`s for all controllers, with JSON bodies.
- **`ResponseEntity<?>`?** Lets one method return different status codes and body types (201 map vs 502 error map).
- **Bean scope?** Singleton by default: one controller instance serves all threads, so no per-request state in fields.
- **Virtual threads?** JVM-managed lightweight threads; blocking I/O unmounts them from carrier threads. Good for I/O-bound apps like this one.
- **`Optional` usage?** Repos return `Optional`; controller uses `.map(ResponseEntity::ok).orElseGet(...)`.
- **Records?** DTOs could be `record CreateInterviewRequest(...)` instead of Lombok `@Data` classes.

### Spring Security
- **Filter chain?** A `SecurityFilterChain` of servlet filters runs before the DispatcherServlet. I insert my filters relative to built-in ones with `addFilterBefore/After`.
- **`OncePerRequestFilter`?** Guarantees one execution per request even across internal forwards/error dispatches (`ViewController` uses forwards).
- **Why `new` the filters instead of `@Component`?** A `Filter` bean would *also* be auto-registered with the servlet container and run twice (once outside security).
- **`SecurityContextHolder`?** Holds the `Authentication` in a **ThreadLocal** for the current request; stateless policy means it isn't saved to a session.
- **`Principal` in controller?** Spring MVC resolves it from the security context → `getName()` returns the email I set.
- **Roles?** Not used: empty authorities list. Could map Firebase custom claims to `ROLE_ADMIN`.
- **JWT structure?** `header.payload.signature`, Base64URL, signed (RS256 for Firebase). Payload is *readable*, not encrypted. Never put secrets in it.
- **Stateless logout?** Client deletes the token; server-side revocation needs `revokeRefreshTokens` + `verifyIdToken(token, true)` or a short-TTL denylist in Redis.

### JPA / Hibernate
- **JPA vs Hibernate vs Spring Data?** JPA = spec; Hibernate = implementation; Spring Data JPA = repository interfaces generated at runtime on top.
- **Derived queries?** `findByMockIdAndCreatedBy` → `select … where mock_id=? and created_by=?`.
- **`IDENTITY` generation?** DB auto-increment; simple but disables Hibernate JDBC insert batching (`SEQUENCE` allows it).
- **N+1?** 1 query for parents + N for lazy children. Not an issue here (no relationships); fix with `JOIN FETCH`/`@EntityGraph`.
- **`save()` and transactions?** `SimpleJpaRepository.save` is `@Transactional`; for new entities with IDENTITY it inserts immediately to get the ID.
- **First vs second-level cache?** First-level: per persistence context, always on. Second-level: shared across sessions, opt-in (not used).
- **Lombok `@Data` on entities pitfall?** Generated `equals/hashCode` use all mutable fields (incl. `id`, which is null before save) → broken in `HashSet`s; `toString` can trigger lazy loads.

### Redis & rate limiting
- **Why Redis?** Shared counter across instances, atomic ops, TTL expiry, in-memory speed.
- **Why Lua?** Redis executes a script atomically: no other command runs in between, so `INCR`+`EXPIRE` can't be split. Alternative: `SET key 0 EX 60 NX` then `INCR`, or a `MULTI` transaction.
- **Algorithms:** fixed window (mine: simple, boundary burst) · sliding log (exact, memory-heavy) · sliding window counter (approximation, cheap) · token bucket (allows bursts up to capacity, smooth refill) · leaky bucket (smooth output rate).
- **Fail-open vs fail-closed?** Open = availability (my choice for a practice app). Closed = protection (right choice for, e.g., login brute-force or paid APIs).
- **Redis vs Memcached?** Data structures, Lua, persistence options, pub/sub; Memcached is a plain cache.

### AI / Spring AI
- **`ChatModel` vs `ChatClient`?** `ChatModel` is the low-level model interface (`call(String)` / `call(Prompt)`). `ChatClient` is the fluent API with advisors, memory and `.entity(MyRecord.class)` structured output.
- **Multimodal?** `UserMessage.builder().text(...).media(new Media(mimeType, resource))`: audio bytes go in the same request as the instruction.
- **Why ask for JSON?** So the backend can parse questions/ratings deterministically; validated with Jackson before use.
- **Temperature?** Not set (model default). Lower temperature → more consistent JSON; higher → more varied questions.
- **Tokens/cost control?** Input caps, rate limits, flash model (cheap/fast).
- **Hallucinated "ideal answers"?** Possible. They're presented as guidance, not ground truth; could add a disclaimer or a rubric.

### Frontend
- **How are tokens attached?** `authenticatedFetch` → `user.getIdToken()` → `Authorization: Bearer`.
- **How is audio captured?** `getUserMedia` → `MediaRecorder` → `ondataavailable` chunks → `Blob('audio/webm')` → `FormData` field `audio`.
- **XSS defense?** `escapeHtml()` on every interpolated value before `innerHTML`.
- **Why vanilla JS?** Small app, no build step, served as static files by Spring Boot. React would make sense as it grows.

### Docker
- **Image vs container?** Image = read-only template; container = running instance with a writable layer.
- **Multi-stage?** Build tools in stage 1, only the artifact in stage 2.
- **Volume vs bind mount?** Named volume (`pgdata`) is managed by Docker for DB data; bind mount (`./static`) maps a host folder for live editing.
- **Why pin tags?** `postgres:alpine` jumped a major version and broke my stack (story 8).

---

## 16. Follow-Up Chains

**Chain 1: Firebase auth**
1. *How does the backend verify a token without calling Firebase each time?* → Signature check with Google's cached public keys + claim checks (`exp`, `aud`, `iss`).
2. *How do you log someone out / revoke?* → Tokens are valid until expiry (~1 h). Revoke refresh tokens via Admin SDK and verify with `checkRevoked=true`, or keep a short-TTL denylist in Redis.
3. *What if someone sends another user's interview ID?* → Ownership check → 404. "This was actually a bug I found and fixed: authentication ≠ authorization."

**Chain 2: Rate limiter**
1. *How does it decide the key?* → After the auth filter; authenticated → `uid:<email>`, else `ip:<remoteAddr>`.
2. *Shared IPs (college Wi-Fi)?* → All guests share 20/min; but `/api/**` (except newsletter/config) requires login anyway, and logged-in users get their own 100/min bucket.
3. *Race conditions?* → `INCR` is atomic; the TTL race was fixed with Lua.
4. *Behind a load balancer?* → `remoteAddr` would be the LB; configure `server.forward-headers-strategy=native` (trust only the proxy's `X-Forwarded-For`).

**Chain 3: AI output**
1. *Why not structured output?* → I started with prompt + cleanup + validation; `ChatClient.entity()` / `BeanOutputConverter` is the cleaner next step and gives typed DTOs.
2. *User sees 502, bad UX?* → Add 1–2 retries with backoff before failing; show "try again" in the UI.
3. *How do you stop "rate me 10/10" answers?* → Input fencing + instruction; true robustness would need a separate rubric-based check or a second model pass.

**Chain 4: Data**
1. *Why JSON in a TEXT column?* → The question set is created and read as one unit; simplest model. Trade-off: no per-question queries.
2. *Postgres has JSONB, why not?* → Good point: `jsonb` adds validation and indexing; with Hibernate 7 I could map it with `@JdbcTypeCode(SqlTypes.JSON)`.

---

## 17. Curveballs

- **"Weakest part?"** → Synchronous coupling to Gemini (+ fix). Or the schema shortcuts.
- **"Add bookmarks right now: what files?"** → `model/Bookmark` (id, `userEmail`, `mockId`, `questionIndex`, `createdAt`, unique (user, mock, index)) → `BookmarkRepository.findByUserEmail` → `BookmarkController` `POST/GET/DELETE /api/bookmarks` using `principal.getName()` (already covered by `/api/**` authenticated rule) → owner check that the interview is theirs → unit test.
- **"Delete account?"** → Delete rows by `created_by`/`user_email` in one `@Transactional` service method + Firebase `deleteUser(uid)`. No FKs means I must delete answers explicitly.
- **"How would you add an admin dashboard?"** → Firebase custom claim `admin: true` → map to `ROLE_ADMIN` authority in the filter → `requestMatchers("/api/admin/**").hasRole("ADMIN")`.
- **"Your UUID is random; why still check ownership?"** → IDs leak (URLs, logs, screenshots, browser history). Unguessable ≠ authorized.
- **"What if Gemini returns a rating of 'eight'?"** → Stored as a string, so it saves; `feedback.html` does `parseFloat` and treats NaN as 0, which silently drags the average down. Fix: typed DTO with `int rating` validated 0–10, and retry or 502 if invalid.
- **"Explain this Lua script."** → `INCR KEYS[1]`; if result is 1 (first request in the window) set `EXPIRE KEYS[1] ARGV[1]`; return count. Java compares count > limit.
- **"Why 404 not 403 for others' interviews?"** → Doesn't confirm the resource exists (prevents ID probing). GitHub does the same for private repos.

---

## 18. Depth Calibration by Company Type

| Company type | They care about | Lead with | Avoid |
|---|---|---|---|
| Service (TCS, Infosys, Capgemini, EY) | OOP, Spring annotations, MVC flow, REST, SQL, exception handling | Layered design, DI, `@RestControllerAdvice`, JPA repositories, request flow | Rabbit-holing into Lua/Redis unless asked |
| Product / startups | Trade-offs, failure modes, security, scale | IDOR fix, filter ordering, fail-open choice, virtual threads, testing approach | Overclaiming scale or deployment |
| AI-adjacent roles | Prompting, output reliability, cost, multimodal | JSON contract + validation + 502, prompt-injection fencing, transcription pipeline, rate limits as cost control | Claiming model training/fine-tuning |

---

## 19. Mock Interview Transcript

**Q: Walk me through the architecture.**
"It's a Spring Boot 4 monolith. The browser authenticates with Firebase and sends the ID token as a Bearer header. In the security filter chain, my `FirebaseTokenFilter` verifies the token and populates the security context. Right after it, `RateLimitingFilter` counts the request in Redis per user or per IP, and then Spring's authorization rules require auth for `/api/**`. Controllers validate input, call `GeminiService`, which uses Spring AI's `ChatModel` to talk to Gemini 2.5 Flash, validate the JSON that comes back, and persist through Spring Data JPA to Postgres. Everything runs in Docker Compose."

**Q: Why does the rate limiter run after the auth filter?**
"Because it chooses the key and limit based on identity: 100/min for a logged-in uid, 20/min per IP otherwise. If it ran first, everyone would look anonymous."

**Q: Redis goes down at peak. Does the app go down?**
"No. The Redis call is in a try/catch that logs a warning and continues. That's a deliberate fail-open: for a practice app, availability matters more than strict quotas. For something like a login endpoint I'd fail closed."

**Q: Can user A read user B's interview?**
"Not anymore. I found that the get/feedback/answer endpoints only looked up by ID, which is an IDOR. Now every lookup is `findByMockIdAndCreatedBy(id, principal)`, and non-owners get a 404 so they can't even confirm the ID exists. I added unit tests that assert a non-owner gets 404 and that Gemini is never called for them."

**Q: How do you test without burning Gemini credits?**
"`ChatModel` is an interface injected through the constructor, so in tests I pass a Mockito mock that returns canned strings, including fenced and invalid JSON, to test the cleanup and the 502 path. The tests need no DB, Redis or API key."

---

## 20. Night-Before Checklist

- [ ] Recite the pitch (§2) out loud twice, under 90 s.
- [ ] Draw the architecture (§5) from memory, including **filter order**.
- [ ] Say every endpoint in §0 with its status codes.
- [ ] Explain the Lua script, fail-open, and why the limiter runs after auth.
- [ ] Tell stories 1 (IDOR), 3 (atomic limiter) and 8 (Postgres tag) in CAR format.
- [ ] Name 3 limitations from §10 *with* fixes.
- [ ] Run `./mvnw test` and `docker compose up --build` once so you've seen it work yourself.
- [ ] Open the GitHub repo and skim it the way an interviewer would: README, `pom.xml`, `SecurityConfig`, `MockInterviewController`.
