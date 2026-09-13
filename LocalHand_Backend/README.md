# LocalHand Backend

Express + Postgres (via Prisma) + Cloudflare R2 API backing the LocalHand
Android app: real accounts (JWT auth), listings CRUD with per-user ownership,
per-user favourites, and photo uploads.

## Stack

- **API**: Node.js + Express + TypeScript
- **Database**: Postgres, accessed via Prisma
- **Photo storage**: Cloudflare R2 (S3-compatible)
- **Auth**: JWT bearer tokens, passwords hashed with bcrypt

## Local development

Requires Docker (for a local Postgres instance) and Node 20+.

```bash
npm install
docker compose up -d          # starts Postgres on localhost:15432
cp .env.example .env          # fill in DATABASE_URL (see below), JWT_SECRET
npx prisma migrate dev        # creates tables
npm run dev                   # starts the API on http://localhost:3000
```

For local Docker Postgres, `.env`'s `DATABASE_URL` is:
```
postgresql://localhand:localhand@localhost:15432/localhand
```

Generate a `JWT_SECRET` with:
```bash
node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"
```

R2 credentials (`R2_*` in `.env`) are only needed to test photo uploads —
everything else works with the placeholder values already in `.env.example`.

### Verifying it works

```bash
curl http://localhost:3000/health
# {"status":"ok"}

curl -X POST http://localhost:3000/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name":"Test User","email":"test@example.com","password":"password123","locality":"Malabe"}'
# {"token":"...","user":{...}}
```

## API reference

All authenticated routes take `Authorization: Bearer <token>`.

| Method | Path                        | Auth      | Description                                  |
|--------|-----------------------------|-----------|-----------------------------------------------|
| POST   | `/auth/signup`               | —         | Create an account, returns token + user       |
| POST   | `/auth/login`                 | —         | Returns token + user                          |
| GET    | `/auth/me`                    | required  | Current user profile                          |
| GET    | `/listings`                   | optional  | Browse/search (`?type=`, `?category=`, `?q=`) |
| GET    | `/listings/mine`              | required  | The caller's own listings                     |
| GET    | `/listings/favourites`        | required  | The caller's favourited listings              |
| GET    | `/listings/best?category=X`   | optional  | Top-rated listing in a category               |
| GET    | `/listings/:id`               | optional  | Single listing                                |
| POST   | `/listings`                   | required  | Create (multipart form; `photo` field optional) |
| PUT    | `/listings/:id`               | required  | Edit (owner only)                             |
| DELETE | `/listings/:id`               | required  | Delete (owner only)                           |
| POST   | `/listings/:id/favourite`     | required  | Favourite                                     |
| DELETE | `/listings/:id/favourite`     | required  | Unfavourite                                   |
| PUT    | `/users/me/location`          | required  | Re-anchor the user's pin                      |

`POST`/`PUT /listings` body fields (multipart or JSON depending on whether a
photo is attached): `type` (`SERVICE`|`MARKETPLACE`), `title`, `category`
(one of `REPAIRS`|`TUTORING`|`CLEANING`|`GARDEN`|`ERRANDS`|`ELECTRONICS`|
`FURNITURE`|`OTHER`), `price`, `locality`, `latitude`, `longitude`,
`condition` (`LIKE_NEW`|`GOOD`|`FAIR`, marketplace only), `description`.

A listing response looks like:

```json
{
  "id": "uuid",
  "type": "SERVICE",
  "title": "Electrical Repairs",
  "category": "REPAIRS",
  "providerName": "Chamara Silva",
  "rating": 5,
  "price": "Rs 800 / hour",
  "locality": "Malabe",
  "latitude": 6.9061,
  "longitude": 79.9701,
  "condition": null,
  "description": "...",
  "imageUrl": null,
  "isMine": true,
  "isFavourite": false,
  "createdAt": 1789021783470
}
```

`providerName`/`rating` come from the listing owner's account, not stored
per-listing — editing your profile name updates it everywhere your listings
appear. `isMine`/`isFavourite` are computed relative to whoever's calling
(or `false` for both when the request is unauthenticated).

## Deploying

### 1. Database — Supabase

1. Create a free project at [supabase.com](https://supabase.com).
2. Project Settings → Database → Connection string → **URI**, using the
   **Transaction pooler** (port 6543) — Render's serverless-style connections
   need pooled connections, not Postgres's direct port 5432.
3. Set that as `DATABASE_URL` in Render's environment variables (below).
4. Run migrations against it once, from your machine:
   ```bash
   DATABASE_URL="<supabase-connection-string>" npx prisma migrate deploy
   ```

### 2. Photo storage — Cloudflare R2

1. Cloudflare dashboard → R2 → Create bucket (e.g. `localhand-photos`).
2. Bucket → Settings → Public Access → enable the `r2.dev` subdomain (or
   connect a custom domain) — this becomes `R2_PUBLIC_URL`.
3. R2 → Manage R2 API Tokens → Create API Token → Object Read & Write,
   scoped to the bucket. This gives you `R2_ACCESS_KEY_ID` and
   `R2_SECRET_ACCESS_KEY`.
4. `R2_ACCOUNT_ID` is in the R2 dashboard's URL / overview page.

### 3. API — Render

1. Push this repo to GitHub.
2. Render dashboard → New → Web Service → connect the repo.
3. Build command: `npm install && npm run build`
4. Start command: `npm start`
5. Add environment variables: `DATABASE_URL`, `JWT_SECRET`, `JWT_EXPIRES_IN`,
   `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`,
   `R2_BUCKET_NAME`, `R2_PUBLIC_URL`. Render sets `PORT` itself — don't
   override it.
6. Deploy. `/health` should respond once it's up.

Render's free tier spins the service down after 15 minutes idle; the first
request after that takes ~30-60s to cold-start. That's Render's own free-tier
behavior, not something this app does — fine for development, worth knowing
if you demo it live.

## Project structure

```
src/
  app.ts              Express app wiring (middleware, routes, error handler)
  server.ts            Entry point — loads .env, starts listening
  lib/
    prisma.ts          Shared Prisma client
    jwt.ts              Sign/verify access tokens
    r2.ts                Upload/delete listing photos in R2
  middleware/
    auth.ts             requireAuth / optionalAuth
  routes/
    auth.ts              signup, login, me
    listings.ts           listings CRUD, favourites, search, best-match
    users.ts               location update
prisma/
  schema.prisma          User, Listing, Favourite models
  migrations/             generated by `prisma migrate dev`
```
