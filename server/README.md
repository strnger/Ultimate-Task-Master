# UTM Crowdsource Server

Local development server for Ultimate Task Master crowdsourcing.

## Quick Start

```bash
cd server
npm install
npm start
```

Server runs on `http://localhost:3847`

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/submit` | Submit task completion `{ task_name, struct_id, x, y, plane }` |
| GET | `/api/locations` | Get all crowdsourced locations |
| GET | `/api/locations/:structId` | Get locations for specific task |
| GET | `/api/stats` | Server statistics |
| POST | `/api/admin/blacklist` | Blacklist IP + revoke contributions |

## Database

SQLite file: `utm_crowdsource.db` (auto-created on first run, gitignored)

Tables:
- `task_completions` — aggregated location data (struct_id, x, y, plane, hits)
- `ip_submissions` — audit trail of who submitted what
- `ip_blacklist` — blocked IPs whose contributions are revoked

## Production Architecture (Future)

Cloudflare → Nginx → This server → SQLite
AWS Lightsail $5-24/month + Cloudflare free tier
