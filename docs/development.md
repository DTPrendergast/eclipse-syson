# Building and Running Locally

Instructions for building and running this fork from source. This file is specific to
this fork — see `CLAUDE.md` for the broader architectural context and `plan.md` for the
feature roadmap.

## Prerequisites

- Java 21 **JDK** (not just a JRE — the Maven build needs `javac`).
- Maven.
- Node 22.16.0 / npm 10.9.2 (the versions pinned in the root `package.json`; installing via
  [nvm](https://github.com/nvm-sh/nvm) is the easiest way to match them exactly).
- Docker, for a local PostgreSQL instance.
- A GitHub personal access token with `read:packages` scope. Upstream Sirius Web, Sirius
  EMF JSON, and SysON artifacts are published to GitHub Packages, not Maven Central, so
  Maven needs to authenticate to fetch them.

## 1. Start the database

```
./scripts/restart-postgresql.sh
```

This starts PostgreSQL in Docker on port `5433` with database `syson-db`, user `dbuser` /
password `dbpwd` — matching the `dev` Spring profile
(`backend/application/syson-application/src/main/resources/application-dev.properties`).

## 2. Configure Maven credentials

The repo-root `settings.xml` declares the GitHub Packages repositories and expects
`${env.USERNAME}` / `${env.PASSWORD}` for authentication:

```
export USERNAME=<your-github-username>
export PASSWORD=<your-github-personal-access-token>
```

## 3. Build and run the backend

```
mvn -s settings.xml clean install
cd backend/application/syson-application
mvn -s ../../../settings.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

The backend serves on `:8080`.

## 4. Build and run the frontend

From the repository root:

```
npm install
npm run start
```

This builds `frontend/syson-components` in dev mode and starts a Vite dev server for
`frontend/syson`, by default at `http://localhost:5173/`. It talks to the backend on
`:8080` per `frontend/syson/.env.development` — no proxy configuration is needed.

Open `http://localhost:5173/` once both processes are up.
