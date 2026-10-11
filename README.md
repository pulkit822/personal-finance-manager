# Personal Finance Manager

Spring Boot 3 / Java 17 REST API for tracking income, expenses, savings goals and reports.
Session-based authentication (secure `JSESSIONID` cookie), full per-user data isolation.

## Tech stack
Java 17 · Spring Boot 3.3 · Spring Security · Spring Data JPA · H2 (default) / PostgreSQL · JUnit 5 + Mockito · JaCoCo · Maven

## Run locally
```bash
mvn spring-boot:run              # API at http://localhost:8080/api
mvn verify                       # tests + JaCoCo report (target/site/jacoco) + 80% line-coverage gate
bash financial_manager_tests.sh http://localhost:8080/api   # provided E2E script (needs curl, bc)
```

## Configuration (environment variables)
| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `8080` | HTTP port (Render sets this) |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | in-memory H2 | Point at PostgreSQL for persistent data, e.g. `jdbc:postgresql://host:5432/db` |
| `COOKIE_SECURE` | `false` (`true` in Docker image) | Adds the `Secure` flag to the session cookie |
| `APP_TIMEZONE` | `UTC` | Zone used for "today" (future-date checks, default goal start) |

> The default H2 database is in-memory, so data resets on every restart/redeploy (free Render instances also sleep). That is fine for the assignment test script; set `DB_URL` to a free PostgreSQL (Render/Neon/Supabase) for persistence.

## Deploy on Render
1. Push this repo to GitHub (public).
2. Render → **New → Web Service** → pick the repo → Runtime **Docker** (the `Dockerfile` is included; `render.yaml` also works as a Blueprint). Health check path: `/api/health`.
3. Your base URL is `https://<service>.onrender.com/api`. Then run  
   `bash financial_manager_tests.sh https://<service>.onrender.com/api` and screenshot the summary.

## API summary
All endpoints except register/login require the session cookie. Errors return
`{"status":400,"error":"Bad Request","message":"…","timestamp":"…"}`.

| Method & path | Success | Errors |
|---|---|---|
| `POST /api/auth/register` | 201 `{message,userId}` | 400, 409 |
| `POST /api/auth/login` | 200 + `Set-Cookie` | 400, 401 |
| `POST /api/auth/logout` | 200 | 401 |
| `POST /api/transactions` | 201 | 400, 401 |
| `GET /api/transactions?startDate&endDate&category&categoryId&type` | 200 `{transactions:[…]}` newest first | 400, 401 |
| `PUT /api/transactions/{id}` (amount/category/description; `date` ignored) | 200 | 400, 401, 404 |
| `DELETE /api/transactions/{id}` | 200 | 401, 404 |
| `GET /api/categories` | 200 | 401 |
| `POST /api/categories` `{name,type}` | 201 | 400, 401, 409 |
| `DELETE /api/categories/{name}` | 200 | 400 (in use), 401, 403 (default), 404 |
| `POST/GET /api/goals`, `GET/PUT/DELETE /api/goals/{id}` | 201/200 | 400, 401, 403 (not yours), 404 |
| `GET /api/reports/monthly/{year}/{month}` | 200 | 400, 401 |
| `GET /api/reports/yearly/{year}` | 200 | 400, 401 |
| `GET /api/health` | 200 (public) | – |

Default categories: **INCOME** Salary · **EXPENSE** Food, Rent, Transportation, Entertainment, Healthcare, Utilities.

## Design decisions
- **Layers**: `controller → service → repository`; request/response **DTOs** (`dto/Dto.java`, Java records) keep entities private.
- **Global error handling**: `@RestControllerAdvice` extending `ResponseEntityExceptionHandler`; unknown failures map to a generic 500, known ones never do.
- **Auth**: `AuthenticationManager` + BCrypt; login stores the `SecurityContext` in the HTTP session (session id rotated to prevent fixation); logout invalidates it. `401` JSON entry point for missing/expired sessions.
- **Data isolation**: every query is scoped by the authenticated user's id (taken from the principal, never from the request). Other users' transactions → `404` (no existence leak); other users' goals → `403`; other users' categories are invisible (`404`).
- **Goal progress is derived**, not stored: `(income − expenses)` of the user's transactions dated on/after the goal's start date, so deleting/editing a transaction automatically updates goals and reports. Percentage is rounded to 2 dp and clamped to 0–100; remaining amount never goes below 0.
- **Money** is `BigDecimal` (`DECIMAL(19,2)`), serialised with two decimals (`5000.00`); a zero total serialises as `0`.
- **Category JSON** contains both `isCustom` (spec) and `custom` (used by the provided test script).
- **Validation**: Bean Validation on all DTOs (positive amount ≤ 2 dp, `YYYY-MM-DD` dates, no future transaction dates, future goal target date, start ≤ target, valid email/phone, password ≥ 8 chars).
- **Time**: one injectable `Clock` (UTC by default) so date rules are deterministic and testable.

## Tests
- `service/*Test` – Mockito unit tests for every service (business rules, error statuses).
- `integration/ApiIntegrationTest` – MockMvc through the real security chain + H2: auth, CRUD, filters, goals, reports, isolation, error codes.
- JaCoCo enforces ≥ 80% line coverage on `mvn verify`.
