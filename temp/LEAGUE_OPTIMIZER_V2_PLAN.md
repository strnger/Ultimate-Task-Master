# 🐶 League Task Optimizer — V2 Design Plan

> Building for **League 6** (upcoming). Raging Echoes (League 5) data is our prototype sandbox.

---

## Two Core Features

### Feature 1: Route Builder 🗺️
Player manually adds tasks to an ordered route. Each task shows:
- Task name + description
- **Location** (WorldPoint — from wiki, shown on world map)
- **Items required** (from wiki)
- **Requirements** (skills, quests)
- Current completion status

Player builds their play session: "I'm going to do these 8 tasks in Misthalin today."
Plugin shows them on the map, numbered, with item checklists.

### Feature 2: Task Suggestions 💡
Player presses a button → plugin:
1. Gets player's current `WorldPoint`
2. Gathers all tasks within configurable radius X
3. Filters out: completed tasks, tasks missing level/quest requirements
4. Presents remaining tasks as a sorted list
5. Player can one-click add any suggestion to their Route

**No pathfinding. No time estimates. No AI.** Just clean spatial filtering + requirement checking.
Simple is beautiful. Zen of Python: "Simple is better than complex."

---

## Data Architecture

### Where Data Comes From

```
┌─────────────────────────┐     ┌──────────────────────────┐
│     OSRS Wiki (Day 1)   │     │   Crowdsourced Backend   │
│                         │     │                          │
│ • Task list (name,      │     │ • Completion % (scraped  │
│   desc, area, points)   │     │   from wiki periodically)│
│ • Task locations         │     │ • AFK/Focus ratings      │
│   (WorldPoint coords)   │     │   (1-5, user submitted)  │
│ • Item requirements     │     │                          │
│ • Skill/quest reqs      │     │                          │
└───────────┬─────────────┘     └────────────┬─────────────┘
            │                                │
            ▼                                ▼
┌──────────────────────────────────────────────────────────┐
│                Plugin JSON Data Bundle                    │
│                                                          │
│  tasks.json — fetched from our API (updatable post-      │
│               launch without plugin rebuild)              │
│                                                          │
│  Each task:                                              │
│  {                                                       │
│    "id": 1234,                                           │
│    "name": "Complete the Draynor Agility Course",        │
│    "description": "Complete a lap of the Draynor...",    │
│    "area": "Misthalin",                                  │
│    "points": 10,                                         │
│    "tier": "Easy",                                       │
│    "location": { "x": 3104, "y": 3279, "plane": 0 },   │
│    "items_required": [],                                 │
│    "skill_reqs": [{ "skill": "Agility", "level": 1 }],  │
│    "quest_reqs": [],                                     │
│    "completion_pct": 87.3,   ← from wiki, updated       │
│    "avg_focus_rating": 2.1,  ← crowdsourced             │
│    "focus_vote_count": 347   ← how many ratings          │
│  }                                                       │
└──────────────────────────────────────────────────────────┘
```

### Where Data Lives at Runtime

```
┌─────────────────────────────────────────────────┐
│                RuneLite Plugin                   │
│                                                  │
│  Remote API (our server)                         │
│  ├── GET  /tasks           → all task data       │
│  ├── GET  /tasks/version   → data version hash   │
│  ├── POST /focus-rating    → submit AFK rating   │
│  └── GET  /focus-ratings   → aggregated ratings  │
│                                                  │
│  Local State (ConfigManager / RuneLite profile)  │
│  ├── Completed tasks     ← from game varps       │
│  ├── Player's route      ← user-built list       │
│  ├── Player's ratings    ← what they've rated    │
│  └── Filter preferences  ← tier, area, etc.      │
│                                                  │
│  Game State (live from client)                   │
│  ├── Current WorldPoint  ← client.getLocalPlayer │
│  ├── Skill levels        ← client.getRealSkill   │
│  ├── Quest completion    ← varbits               │
│  └── Task completion     ← varp bits             │
└─────────────────────────────────────────────────┘
```

---

## Crowdsourcing Design

### 1. Wiki Completion % (Passive — We Scrape It)
- Our **backend server** runs the wiki scraper periodically (every few hours)
- Plugin fetches updated percentages from our API
- Players don't do anything — this is free data
- Already proven: we scraped 1,589 tasks with completion % in seconds

### 2. Focus/AFK Ratings (Active — Players Submit)
The novel crowdsourced metric. Scale:

| Rating | Meaning | Example |
|--------|---------|---------|
| 1 | **Sweaty** — constant clicking, full attention | Inferno, complex boss |
| 2 | **Active** — clicking every few seconds | Agility course laps |
| 3 | **Semi-AFK** — check every 30s-1min | Fishing, woodcutting |
| 4 | **Chill** — check every few minutes | NMZ, some farming tasks |
| 5 | **Full AFK** — set and forget 20min | Splashing, some gathering |

#### How it works:
1. Player completes a task (detected via varp change)
2. Plugin pops a small, non-intrusive prompt: "How AFK was that? ⭐⭐⭐⭐⭐"
3. Rating is POSTed to our API: `{ task_id, rating, player_hash }`
4. Backend aggregates: mean rating + vote count
5. Ratings included in task data on next fetch

#### Anti-gaming:
- One rating per player per task (update allowed, not duplicate)
- Use `client.getAccountHash()` for anonymous dedup (no usernames stored)
- Require task to actually be completed before rating (varp check)
- Outlier detection: ignore if < 3 or > N ratings, use trimmed mean

#### Inspiration: RuneLite Crowdsourcing Plugin
RuneLite's built-in crowdsourcing plugin POSTs data to wiki servers on events
(cooking success/fail, thieving rates, etc.). Same pattern — just different data.

WikiSync model: `ScheduledExecutorService` → periodic batch POST.
We do the same, but with focus ratings instead of varbit dumps.

---

## Day-1 Launch Pipeline

This is CRITICAL. League launches → wiki has tasks → we need data FAST.

```
Hour 0:  League 6 announced / wiki task page goes live
         ↓
Hour 0:  Run updated scraper (adapted from our Raging Echoes scraper)
         - Scrape task list: name, description, area, points, tier
         - Scrape requirements: skills, quests, items
         - Scrape/infer locations from wiki page content
         ↓
Hour 1:  Push tasks.json to our API server
         ↓
Hour 1:  Plugin auto-fetches new data on startup
         (version check → download if newer)
         ↓
Hour 2+: Crowdsourced focus ratings start flowing in
         Completion % scraped from wiki as people play
```

### Handling New/Changed/Deleted Tasks Between Leagues
- Tasks keyed by **name + area** (not numeric ID, since IDs change)
- Scraper diffs against previous league data
- Focus ratings from previous league can carry over for repeated tasks
  (with a "from previous league" flag, decayed weight)

---

## Plugin Architecture

```
com.fatcat.leaguetasks/
│
├── LeagueTasksPlugin.java          — Main: lifecycle, events, coordination
├── LeagueTasksConfig.java          — Settings: radius, tier filter, etc.
│
├── data/
│   ├── TaskDefinition.java         — POJO: the task + all its metadata
│   ├── TaskLocation.java           — WorldPoint wrapper with area context
│   ├── TaskDataService.java        — Fetches/caches task data from API
│   └── TaskDataVersion.java        — Version tracking for cache invalidation
│
├── tracking/
│   ├── CompletionTracker.java      — Reads varp bits for task completion
│   ├── RequirementChecker.java     — Can player do this task? (skills/quests)
│   └── PlayerContext.java          — Current location, stats, equipment
│
├── route/
│   ├── TaskRoute.java              — Ordered list of tasks (the user's plan)
│   ├── RouteManager.java           — Add/remove/reorder tasks, persist route
│   └── RouteExporter.java          — Export/import route as shareable JSON
│
├── suggestions/
│   ├── TaskSuggestionEngine.java   — The radius-based suggestion logic
│   └── SuggestionFilters.java      — Composable filter predicates
│
├── crowdsource/
│   ├── FocusRatingService.java     — Submit/fetch AFK ratings from API
│   ├── FocusRatingPrompt.java      — The rating UI after task completion
│   └── CrowdsourceApiClient.java   — HTTP client for our backend
│
├── overlay/
│   ├── RouteWorldMapOverlay.java   — Numbered task markers on world map
│   ├── RouteMinimapOverlay.java    — Next task indicator on minimap
│   ├── TaskInfoOverlay.java        — In-game panel: current route progress
│   └── SuggestionOverlay.java      — Suggestion list overlay
│
└── panel/
    ├── LeagueTasksPanel.java       — Main side panel
    ├── TaskListPanel.java          — Scrollable filtered task list
    ├── RoutePanel.java             — Current route with drag-reorder
    └── TaskCard.java               — Individual task display widget
```

**~20 files.** Medium complexity. No pathfinding engine needed.

---

## Feature Details

### Route Builder UX

```
┌─────────────────────────────────────┐
│ 🗺️ My Route              [Clear All]│
├─────────────────────────────────────┤
│ 1. ☐ Complete Draynor Agility      │
│    📍 Draynor (10 pts, ⭐⭐ Active) │
│    🎒 None                          │
│    [▲] [▼] [✕]                      │
├─────────────────────────────────────┤
│ 2. ☐ Have Ned make you some rope   │
│    📍 Draynor (10 pts, ⭐⭐⭐ Semi) │
│    🎒 None                          │
│    [▲] [▼] [✕]                      │
├─────────────────────────────────────┤
│ 3. ☐ Insult Aggie the Witch        │
│    📍 Draynor (10 pts, ⭐⭐⭐⭐ Chill│
│    🎒 None                          │
│    [▲] [▼] [✕]                      │
├─────────────────────────────────────┤
│ Route: 3 tasks, 30 pts total       │
│ [📤 Export] [📥 Import]             │
└─────────────────────────────────────┘
```

### Task Suggestions UX

```
┌─────────────────────────────────────┐
│ 💡 Suggestions    Radius: [50] tiles│
│ Standing at: Draynor (3104, 3279)   │
│ [🔄 Refresh]                        │
├─────────────────────────────────────┤
│ Filter: [All▼] [Easy+▼] [Any Skill]│
├─────────────────────────────────────┤
│ Complete Draynor Agility Course     │
│ 📍 42 tiles away · 10 pts · 87% done│
│ ⭐⭐ Active · Agility 1             │
│ [+ Add to Route]                    │
├─────────────────────────────────────┤
│ Have Ned make you some rope         │
│ 📍 67 tiles away · 10 pts · 72% done│
│ ⭐⭐⭐ Semi-AFK · No reqs           │
│ [+ Add to Route]                    │
├─────────────────────────────────────┤
│ Showing 8 of 23 eligible tasks      │
└─────────────────────────────────────┘
```

### Sorting Options for Suggestions
- **Distance** (nearest first) — default
- **Points** (highest first)
- **Points/Distance** (best value)
- **Most AFK first** (highest focus rating)
- **Completion %** (easiest first = highest %)
- **Completion %** (rarest first = lowest %)

---

## Backend API (Simple)

Lightweight server — could be a free-tier Cloudflare Worker, Vercel Edge Function,
or even a static JSON on GitHub Pages with a separate ratings microservice.

### Endpoints

```
GET  /api/v1/tasks
     → Full task list JSON (cached, versioned)
     → ETag / If-None-Match for efficient polling

GET  /api/v1/tasks/version
     → { "version": "abc123", "updated_at": "2025-06-15T12:00:00Z" }

GET  /api/v1/focus-ratings
     → { "task_id_1": { "avg": 2.3, "count": 150 }, ... }

POST /api/v1/focus-ratings
     → { "task_id": 1234, "rating": 3, "player_hash": "anon_hash" }
     ← { "ok": true }
```

### Tech Stack Options
| Option | Pros | Cons |
|--------|------|------|
| **Cloudflare Workers + KV** | Free tier, global edge, fast | KV is eventually consistent |
| **Vercel + Postgres** | Free tier, easy deploy | Cold starts |
| **GitHub Pages + Supabase** | Static JSON + real DB for ratings | Two services |
| **Railway + SQLite** | Dead simple, cheap | Single region |

Recommendation: **Cloudflare Workers + D1 (SQLite)**. Free, fast, scales, dead simple.

---

## Data Pipeline: Wiki → Plugin

### The Scraper (Python — already prototyped!)

We already built `scrape_tasks.py` for Raging Echoes. For League 6 we extend it:

```python
# What we already scrape:
# ✅ name, description, area, points, tier, completion_pct

# What we need to ADD:
# 🔲 Location (WorldPoint) — from wiki {{Map}} templates or task page links
# 🔲 Items required — from task page requirements column
# 🔲 Quest requirements — from task page requirements column  
# 🔲 Skill requirements — ✅ already have this!
```

### Location Extraction Strategy

The wiki has coordinates! Discovered in Draynor Village's page:
```
|map = {{Map|x=3103|y=3259}}
```

**Approach:**
1. Parse task descriptions for location names ("Draynor", "Lumbridge Castle", etc.)
2. Look up each location name via wiki API → extract `{{Map|x=...|y=...}}`
3. Cache the location→WorldPoint mapping
4. For tasks with no specific location, use area centroid

We could also use the wiki's Semantic MediaWiki or Cargo tables if available.

---

## Route Sharing (Bonus Feature)

Routes are just ordered task ID lists. Trivially shareable:

```json
{
  "name": "Misthalin Speedrun",
  "author": "FattestCat",
  "tasks": [1234, 1235, 1240, 1242, 1250],
  "notes": "Start at Lumbridge, work north to Varrock"
}
```

- Export as JSON string (copy to clipboard)
- Import by pasting
- Could integrate with Pastebin-style sharing later
- Community could share optimized routes on Reddit/Discord

---

## Open Questions

1. **Backend hosting** — what's your preference? I'd go Cloudflare Workers + D1.
2. **Route persistence** — RuneLite ConfigManager (per-profile), or our backend?
   (ConfigManager is simpler but limited storage. Backend enables cross-device sync.)
3. **Focus rating prompt timing** — immediately on task completion? Or batch at
   end of session? (Immediate is more accurate but potentially annoying.)
4. **Plugin name?** "League Task Optimizer"? "Task Planner"? "League Routes"?
5. **Do we want to support non-league use?** Achievement diary tracker? Combat
   achievement tracker? Same architecture could work for all of these.

---

## What We Build FIRST (MVP for League 6 Launch)

### Must-Have (Week 1)
- [ ] Task data pipeline: scraper → JSON → API endpoint
- [ ] Plugin: fetch task data, display in side panel
- [ ] Plugin: completion tracking from game varps
- [ ] Plugin: requirement checking (skills + quests)
- [ ] Plugin: task suggestions (radius-based spatial filter)
- [ ] Plugin: route builder (add/remove/reorder tasks)
- [ ] Plugin: world map markers for route tasks

### Nice-to-Have (Week 2+)
- [ ] Focus rating crowdsourcing
- [ ] Route import/export
- [ ] Minimap overlay
- [ ] Wiki completion % auto-refresh
- [ ] Filter by focus rating / AFK-ness

### Future
- [ ] Route sharing platform
- [ ] Community-voted optimal routes
- [ ] Party integration (share routes with party members)
- [ ] Previous league rating migration

---

*Generated by FatCat 🐶 — your loyal code puppy*
*May 2025*
