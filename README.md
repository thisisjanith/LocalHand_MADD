# LocalHand

A hyper-local neighbourhood marketplace and services app. Users drop a pin
for their area at sign-up, and everything — nearby listings, search results,
and the in-app assistant's recommendations — is ranked by real distance from
that pin.

This repo contains all three pieces of the product:

| Folder | What it is | Stack |
|---|---|---|
| [`LocalHand_App`](LocalHand_App) | Android client | Kotlin, XML Views + Fragments, Navigation Component, MVVM |
| [`LocalHand_Backend`](LocalHand_Backend) | REST API | Node.js, Express, TypeScript, Prisma + Postgres, Cloudflare R2 |
| [`LocalHand_Admin`](LocalHand_Admin) | Moderation dashboard | React, TypeScript, Vite |

Each folder is a standalone project with its own dependencies and its own
`README.md` with full setup instructions — this file is just the map.

## How it fits together

```
LocalHand_App  ──HTTPS──▶  LocalHand_Backend  ──▶  Postgres (via Prisma)
(Android)                  (Express API)       ──▶  Cloudflare R2 (photos)
                                  ▲
                                  │ /admin/* routes
LocalHand_Admin ─────────────────┘
(web dashboard)
```

- The **Android app** is what end users install: sign up, browse/search
  services and second-hand goods near them, post their own listings, chat
  with an on-device assistant, and manage favourites.
- The **backend** owns the data: accounts (JWT auth), listings CRUD with
  per-user ownership, favourites, and photo uploads.
- The **admin dashboard** is an internal tool for moderating users and
  listings — authenticated separately from regular user accounts.

## Getting started

Each project is independent — you generally only need to run the pieces
you're actively working on.

**Backend** (needed by both the app and the admin dashboard for anything
beyond static UI):
```bash
cd LocalHand_Backend
npm install
docker compose up -d
cp .env.example .env   # fill in DATABASE_URL, JWT_SECRET
npx prisma migrate dev
npm run dev             # http://localhost:3000
```

**Android app** — open `LocalHand_App` in Android Studio and run the `app`
configuration. Requires JDK 21.

**Admin dashboard**:
```bash
cd LocalHand_Admin
npm install
npm run dev
```

See each subproject's README for environment variables, the full API
reference, and deployment notes.

## Repo layout

```
LocalHand/
├── LocalHand_App/        Android client (Kotlin)
├── LocalHand_Backend/    REST API (Node.js + Express + Prisma)
└── LocalHand_Admin/      Moderation dashboard (React)
```
