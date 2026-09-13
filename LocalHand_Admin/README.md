# LocalHand Admin

Web dashboard for moderating the LocalHand app — view every registered user
and listing, delete either. React + TypeScript + Vite, no router (two tabs,
conditional auth screen).

Talks to `LocalHand_Backend`'s `/admin/*` routes, authenticated with a
single hardcoded admin credential (`ADMIN_EMAIL`/`ADMIN_PASSWORD` on the
backend) — not a regular user account.

## Develop

```bash
npm install
npm run dev
```

Reads the backend URL from `VITE_API_BASE_URL` (see `.env.example`).
`.env.local` points at `http://localhost:3000` for local backend development.

## Build

```bash
npm run build
```

## Deploy

Deployed on Vercel. Set `VITE_API_BASE_URL` to the deployed backend's URL
(`https://localhandbackend.onrender.com`) in Vercel's project environment
variables.
