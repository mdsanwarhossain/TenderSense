# TenderSense

TenderSense finds government and international tenders that actually match a company's
capabilities — instead of a procurement team scrolling through hundreds of listings by
hand every day.

It continuously crawls multiple tender sources (Bangladesh's e-GP portal, World Bank,
UNGM, IsDB, BRAC e-Tender), scores every open tender against a company's capability
profile using AI embeddings, and surfaces a ranked, explainable shortlist — with the
evidence for *why* a tender matched, gaps in eligibility, deadlines, and one-click
tracking through bid decisions.

## Table of contents

- [What it does](#what-it-does)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Getting started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Option A — Docker Compose (recommended)](#option-a--docker-compose-recommended)
  - [Option B — Run locally](#option-b--run-locally)
- [Configuration](#configuration)
- [Running profiles](#running-profiles)
- [User manual](#user-manual)
- [Project structure](#project-structure)
- [Development commands](#development-commands)
- [Troubleshooting](#troubleshooting)

## What it does

- **Multi-source ingestion** — pulls tenders from Bangladesh's e-GP portal (Selenium,
  since listings are JS-rendered), the World Bank procurement notices API, UNGM, IsDB,
  and BRAC e-Tender (PDF parsing via PDFBox).
- **AI matching** — each company builds a capability profile (sectors, past projects,
  certifications). Tenders and profiles are embedded locally (all-MiniLM-L6-v2 via
  ONNX, no API key, no network call) and compared with a pgvector similarity search, so
  matching works fully offline.
- **Explainable results** — every match comes with a grade, the evidence chunks that
  drove the score, and any eligibility gaps, instead of a black-box number.
- **Optional LLM enrichment** — a local Ollama model can generate plain-language tender
  summaries and pre-screen eligibility before a tender reaches a company's dashboard;
  the whole pipeline runs fine without it, falling back to rule/template-based logic.
- **Scheduling & tracking** — configurable cron jobs keep the tender pool fresh; a
  morning digest highlights new matches; each tender can be tracked through a bid
  decision workflow (interested / bidding / won / lost / skipped).
- **Admin console** — manage the schedule, trigger ingestion pipelines manually, and
  inspect run history.

## Tech stack

| Layer          | Technology                                                        |
|----------------|--------------------------------------------------------------------|
| Backend        | Java 21, Spring Boot, Spring Security (session auth), Spring Data JPA |
| AI / Matching  | Spring AI, local ONNX embeddings (all-MiniLM-L6-v2), pgvector      |
| Database       | PostgreSQL + [pgvector](https://github.com/pgvector/pgvector) extension |
| Ingestion      | Selenium (e-GP), Jsoup, Apache PDFBox (BRAC e-Tender PDFs)         |
| Frontend       | Angular 21, TypeScript, RxJS                                       |
| LLM (optional) | [Ollama](https://ollama.com) running `qwen2.5:7b` locally           |
| Packaging      | Gradle, Docker / Docker Compose, Nginx (frontend container)        |

## Architecture

```
┌─────────────┐     ┌──────────────────────────────────────────┐
│  Sources    │     │              TenderSense backend           │
│  e-GP       │────▶│  Ingestion → Staging → Matching → Tender   │
│  World Bank │     │       (Spring Boot + Spring AI + pgvector)  │
│  UNGM       │     │                                            │
│  IsDB       │     │  optional: local LLM (Ollama) enrichment    │
│  BRAC       │     └──────────────────────────────────────────┘
└─────────────┘                     │
                                     ▼
                          ┌────────────────────┐
                          │ Angular SPA (built  │
                          │ into Spring static) │
                          └────────────────────┘
```

Ingested tenders land in a staging table first. With the LLM worker off (the default),
they are promoted to live tenders by rule-based logic; with it on (`ai` profile),
a local model screens and enriches them before promotion.

## Getting started

### Prerequisites

- **Docker & Docker Compose** (easiest path), **or**
- For running locally without Docker:
  - JDK 21
  - Node.js `20.19.6` (see `frontend/.nvmrc`) and npm
  - PostgreSQL with the [pgvector](https://github.com/pgvector/pgvector) extension
  - Chromium + chromedriver (only needed for e-GP crawling)
  - (Optional) [Ollama](https://ollama.com) for AI summaries/eligibility screening

### Option A — Docker Compose (recommended)

This spins up Postgres (with pgvector), the Spring Boot backend, and the Angular
frontend behind Nginx.

```bash
docker compose up --build
```

- Frontend: http://localhost:4200
- Backend API: http://localhost:8080
- Postgres: `localhost:5434` (mapped from the container's `5432`)
- (Optional) Portainer, for inspecting the containers: http://localhost:9000

The default admin login is `admin@tendersense.local` / `tendersense-admin` (see
[Configuration](#configuration) to change it). Demo company accounts, if seeded, use
the password `tendersense`.

### Option B — Run locally

1. **Start Postgres with pgvector** (or reuse Docker just for the database):

   ```bash
   docker compose up postgres
   ```

   By default the backend expects it on `localhost:5433` (see
   `src/main/resources/application.properties`) — adjust `spring.datasource.url` if
   your Postgres runs elsewhere.

2. **Run the backend** (this also builds and copies in the Angular app, so the whole
   app is served from one origin with no CORS/proxy needed):

   ```bash
   ./gradlew ngBuild bootRun
   ```

   Then open http://localhost:8080.

3. **Or run the frontend separately** in dev mode (hot reload), against a backend
   started with plain `./gradlew bootRun`:

   ```bash
   cd frontend
   npm ci
   npm start   # ng serve, http://localhost:4200
   ```

### First run

On first start, the local embedding model (~90 MB) is downloaded once and cached under
`~/.cache/tendersense/onnx-model`. The first e-GP crawl and matching pass can take a
few minutes; subsequent runs are incremental.

## Configuration

Main settings live in `src/main/resources/application.properties`. Notable ones:

| Property | Purpose |
|---|---|
| `spring.datasource.*` | Database connection |
| `tendersense.egp.*`, `tendersense.worldbank.*`, `tendersense.ungm.*`, `tendersense.isdb.*`, `tendersense.brac.*` | Per-source ingestion settings |
| `tendersense.schedule.*` | Cron expressions for each ingestion/digest job (Asia/Dhaka). Editable at runtime via the admin Scheduler page after first boot |
| `tendersense.matching.*` | Chunk size/overlap and evidence count for AI matching |
| `tendersense.summary.enabled` / `tendersense.summary.api-key` | Enable an LLM-based summary writer instead of the built-in template |
| `tendersense.llm.*`, `tendersense.processing.*` | Local Ollama model for staging → tender enrichment (see [Running profiles](#running-profiles)) |
| `tendersense.auth.admin-email` / `tendersense.auth.admin-password` | First platform admin, created only if none exists — **change before deploying anywhere but a dev machine** |
| `tendersense.auth.demo-password` | Password for demo company accounts seeded from `data/capability-profile*.json` |

Never commit real credentials — override sensitive values with environment variables
or a local, git-ignored properties file instead of editing the checked-in defaults.

## Running profiles

Spring profiles layer on top of the base config (`-Dspring.profiles.active=<name>`, or
`SPRING_PROFILES_ACTIVE` env var):

| Profile | Effect |
|---|---|
| *(none)* | Full live ingestion, no LLM — rule-based staging → tender promotion |
| `ai` | Turns on the local LLM worker (needs Ollama running with `qwen2.5:7b` pulled: `ollama pull qwen2.5:7b`) |
| `demo` | Disables all scheduled jobs — ingestion only runs when manually triggered from the admin console; verbose logging |
| `cached` | Replays the bundled Day-0 disk snapshot instead of calling live portals — works with networking disabled, useful for offline demos |

## User manual

### For a company user

1. **Sign up / sign in** at the app's root URL.
2. **Build your capability profile** (Profile page): sectors you operate in, past
   projects, and certifications. The richer this is, the better the AI matching.
3. **Check the Dashboard** for your ranked shortlist of matching tenders — each entry
   shows a match grade, why it matched (evidence), and any eligibility gaps.
4. **Open a tender's detail page** for the full notice, deadline, and AI-generated
   summary.
5. **Track it** — mark a tender as interested / bidding / won / lost / skipped from the
   Tracking page to keep your pipeline organized.
6. **Benchmark** — compare your win/participation stats over time on the Benchmark
   page.
7. Leave **feedback** on match quality — it helps tune future matching.

### For an admin

1. Sign in with the admin account.
2. **Admin → Scheduler**: view/edit the cron schedule for each ingestion source, or
   restore a job to its default. Turning `tendersense.schedule.enabled=false` off
   disables all scheduling regardless of what's saved here.
3. **Admin → Pipeline**: trigger any ingestion source manually and watch run history
   (useful right after a fresh deploy, or when debugging a source).
4. **Admin → Tenders**: inspect/manage ingested tenders directly.

## Project structure

```
TenderSense/
├── src/main/java/com/bracit/tendersense/
│   ├── controller/       REST controllers (tenders, dashboard, profile, tracking, admin...)
│   ├── service/          Business logic (ingestion, matching, summaries, scheduling)
│   ├── entity/           JPA entities (Tender, Organisation, CapabilityProfile, MatchResult...)
│   ├── repository/       Spring Data repositories
│   ├── security/         Spring Security config, session auth
│   ├── scheduler/        Cron-triggered ingestion/digest jobs
│   └── config/           App-wide configuration
├── src/main/resources/   application*.properties, seed data
├── frontend/             Angular 21 SPA
│   └── src/app/
│       ├── core/         Services, guards, interceptors, models
│       ├── features/     auth, dashboard, tender-detail, profile, shortlist, pipeline,
│       │                 benchmark, admin
│       └── shared/       Shared UI components
├── docker/               Dockerfiles (backend/frontend), nginx config, pgvector init SQL
├── docker-compose.yml    Postgres + backend + frontend (+ optional Portainer)
├── data/                 Seed capability profiles, e-GP snapshot cache
└── docs/                 Project documentation
```

## Development commands

```bash
# Backend
./gradlew bootRun                 # run backend only (serves last-built frontend if any)
./gradlew ngBuild bootRun         # build Angular and run backend serving it together
./gradlew test                    # run backend tests
./gradlew ngClean                 # remove the copied-in frontend build

# Frontend (from frontend/)
npm ci                            # install dependencies
npm start                         # ng serve, dev mode with hot reload
npm run build                     # production build
npm test                          # unit tests (vitest)
```

## Troubleshooting

- **`JAVA_HOME` errors during Gradle build** — the project targets JDK 21 via
  toolchains; if your `JAVA_HOME` points elsewhere, set
  `org.gradle.java.home=<path-to-jdk-21>` in `gradle.properties` or export a correct
  `JAVA_HOME`.
- **`ngBuild`/`ngCompile` fails to find the right Node version** — install Node
  `20.19.6` (matching `frontend/.nvmrc`) via `nvm install`; Gradle auto-detects it from
  `~/.nvm` if present.
- **e-GP crawl fails / Chromium errors** — the crawler needs a local Chromium +
  chromedriver install when running outside Docker (the Docker image already includes
  both).
- **Embedding model re-downloads on every start** — check that
  `spring.ai.embedding.transformer.cache.directory` is writable; it should persist
  under `~/.cache/tendersense/onnx-model`.
- **AI summaries/eligibility screening not running** — confirm Ollama is running and
  `qwen2.5:7b` is pulled, the `ai` profile is active, and (if the backend itself runs
  in a container) `tendersense.llm.base-url` points at
  `http://host.docker.internal:11434`.
