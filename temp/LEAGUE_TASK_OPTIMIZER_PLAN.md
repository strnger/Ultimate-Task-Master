# 🐶 Raging Echoes League Task Optimizer — Design Plan

## The Idea

A RuneLite plugin that says: _"Hey, you're standing in Draynor — here are 8 tasks you can knock out
within walking distance, sorted by efficiency. Go do them in THIS order."_

Basically a **task-aware GPS** — a traveling salesman solver that clusters nearby tasks
and suggests optimal routes through them.

---

## What Already Exists (Prior Art)

From analyzing the RuneLite ecosystem, three existing plugins are VERY relevant:

| Plugin | What It Does | What We Steal |
|--------|-------------|---------------|
| **[Tasks Tracker](https://github.com/osrs-reldo/tasks-tracker-plugin)** (66 files) | Reads league task completion from game varps (bitpacked), remote JSON task definitions, filterable side panel | Varp-based completion detection, struct composition reading, async data loading |
| **[Leagues Planner](https://github.com/JacobNelsonGames/Leagues_Planner_And_Pather)** (46 files) | A* pathfinder with collision maps, world map overlays, region boundaries, task management | The entire pathfinding engine, collision maps, transport data, world map rendering |
| **[Shortest Path](https://github.com/Skretzo/shortest-path)** (#30 most popular) | Background thread A* pathfinding, world map + minimap path rendering | Pathfinding architecture, cancellable background computation |

### Key Insight
Tasks Tracker already reads completion status. Leagues Planner already does pathfinding + task display.
**Nobody combines them into an optimizer that says "here's the most efficient ORDER to do things."**

---

## The Data We Have

### Scraped: 1,589 tasks
```
By Tier:  Easy=224, Medium=536, Hard=437, Elite=345, Master=47
By Area:  General=488, Kourend=119, Misthalin=114, Desert=112, Fremennik=112,
          Varlamore=111, Kandarin=103, Morytania=101, Asgarnia=95,
          Wilderness=95, Tirannwn=85, Karamja=54
```

### What Each Task Has
- **Area** (region-level: Misthalin, Desert, etc.)
- **Name** + **Description** (often mentions specific locations like "Draynor", "Lumbridge")
- **Requirements** (skill + level, or quest completion)
- **Points** (10/30/80/200/400)
- **Completion %** (how many players have done it — proxy for difficulty)

### What We NEED To Add
- **Specific WorldPoint coordinates** for each task (or at least the sub-region/town)
- **Task dependencies** (some tasks chain: "Complete Easy diary" before "Complete Medium diary")
- **Task categories** (skilling, combat, questing, exploration, equipment)
- **Estimated time** to complete each task

---

## Architecture

### Core Algorithm: Greedy Nearest-Neighbor with Task Clustering

This isn't a pure TSP (that's NP-hard for 1,589 nodes). Instead:

```
1. DETECT player's current WorldPoint
2. FILTER tasks to only show: incomplete + requirements met
3. CLUSTER nearby tasks by sub-region (e.g., "Draynor area", "Lumbridge area")
4. SCORE each cluster by: (total_points / estimated_travel_time)
5. Within each cluster, ORDER tasks by: proximity (nearest-neighbor greedy)
6. PRESENT top 3-5 clusters as "suggested routes"
```

### Why Clustering > Pure TSP
- Player has limited inventory/gear — can't do ALL tasks in one trip
- Tasks have different TYPES (need fishing rod, need combat gear, need quest items)
- Player attention span — suggest 5-10 tasks per "route", not 1,589

### The Scoring Formula
```
cluster_score = sum(task.points for task in cluster) / estimated_total_travel_time
bonus: +weight if tasks share same skill (fewer gear swaps)
bonus: +weight if tasks are in same building/area
penalty: -weight if tasks require items player doesn't have
```

---

## Plugin Structure (Java)

```
com.fatcat.leagueoptimizer/
├── LeagueOptimizerPlugin.java        — Main plugin, lifecycle, event handling
├── LeagueOptimizerConfig.java        — User settings (filter by tier, area, etc.)
│
├── data/
│   ├── TaskDefinition.java           — POJO: task name, description, location, requirements
│   ├── TaskLocation.java             — WorldPoint + sub-region for a task
│   ├── TaskState.java                — Completion status (from varps)
│   └── TaskDataLoader.java           — Loads task definitions (bundled JSON or remote)
│
├── tracker/
│   ├── CompletionTracker.java        — Reads varp bits to track which tasks are done
│   ├── RequirementChecker.java       — Checks if player meets task requirements
│   └── PlayerStateService.java       — Current stats, inventory, equipment, location
│
├── optimizer/
│   ├── TaskClusterer.java            — Groups tasks by geographic proximity
│   ├── RouteOptimizer.java           — Nearest-neighbor + scoring within clusters
│   ├── TaskRoute.java                — Ordered list of tasks = a "suggested route"
│   └── ScoringConfig.java            — Weights for the scoring formula
│
├── pathfinding/
│   ├── PathfindingService.java       — Wraps Shortest Path / Leagues Planner pathfinder
│   └── DistanceEstimator.java        — Quick distance estimates (Chebyshev or pathfinder)
│
├── overlay/
│   ├── RouteOverlay.java             — In-game overlay showing current suggested route
│   ├── RouteWorldMapOverlay.java     — World map markers for route tasks
│   ├── RouteMinimapOverlay.java      — Minimap path to next task
│   └── TaskInfoOverlay.java          — Panel showing task details + progress
│
└── panel/
    └── LeagueOptimizerPanel.java     — Side panel: route list, filters, settings
```

### ~15-18 files, targeting medium complexity (like GOTR Helper tier, not Quest Helper tier)

---

## Implementation Phases

### Phase 1: Data Foundation 🗃️
**Goal:** Get task data with locations into the plugin

1. Enhance our scraper to extract/infer specific locations from task descriptions
   - NLP-lite: parse place names like "Draynor", "Lumbridge", "Wizards' Tower"
   - Map place names → WorldPoint coordinates (wiki has these)
   - For generic tasks ("Reach level 10"), assign to nearest relevant training spot
2. Build `TaskDefinition` with `WorldPoint` locations
3. Bundle as JSON resource in the plugin

### Phase 2: Completion Tracking ✅
**Goal:** Know which tasks the player has/hasn't done

1. Use the Tasks Tracker varp-reading pattern (bitpacked varps)
2. Read player stats via `client.getRealSkillLevel(Skill.X)`
3. Track equipment via `ItemContainerChanged` on `InventoryID.WORN`

### Phase 3: The Optimizer Brain 🧠
**Goal:** Suggest efficient task routes

1. Implement geographic task clustering (simple grid-based or k-means)
2. Implement nearest-neighbor route ordering within clusters
3. Scoring formula: points / distance, with skill-affinity bonuses
4. Filter by: player can actually do it (requirements met)

### Phase 4: Visual Overlays 🗺️
**Goal:** Show the route in-game

1. Side panel with task list + checkboxes
2. World map markers for route tasks (numbered 1, 2, 3...)
3. Minimap path line to next task
4. In-game tile highlight at task location
5. Info overlay: "Next: Complete Draynor Agility Course (30 pts, ~2 min)"

### Phase 5: Polish 🐾
- Config: tier filter, area filter, max route length
- "Recalculate" button when player finishes tasks
- Auto-detect task completion and update route
- Points-per-hour tracking

---

## The Location Mapping Challenge

This is the HARDEST part. We have 1,589 tasks and need WorldPoint coordinates for each.

### Strategy: Tiered Approach

**Tier 1 — Specific location in description (~40% of tasks)**
Many tasks literally say WHERE: "Complete a lap of the Draynor Rooftop Agility Course"
→ We know exactly where Draynor agility course is: `WorldPoint(3104, 3279, 0)`

**Tier 2 — Area-level location (~30% of tasks)**
"Equip a Rune Scimitar" — could be done anywhere, but we know the AREA is General.
→ Assign to a central point in the area, or skip for route optimization

**Tier 3 — Context-dependent (~20% of tasks)**
"Reach level 50 in any skill" — depends on what the player is training.
→ Don't include in geographic routing; show in side panel only

**Tier 4 — Boss/dungeon specific (~10% of tasks)**
"Complete all Combat Achievements for Theatre of Blood"
→ Map to dungeon entrance WorldPoint

### Data Sources for Coordinates
1. **OSRS Wiki API** — has coordinates for almost everything
2. **Shortest Path plugin** — has transport nodes with coordinates
3. **Leagues Planner** — has task → location mappings we can reference
4. **Manual curation** — for the ~200 most important tasks

---

## Key RuneLite APIs We'll Use

| API | Purpose |
|-----|---------|
| `client.getLocalPlayer().getWorldLocation()` | Get player's current position |
| `client.getRealSkillLevel(Skill.X)` | Check if player meets skill requirements |
| `client.getVarpValue(varpId)` | Read task completion bits |
| `client.getItemContainer(InventoryID.WORN)` | Check equipped items |
| `WorldMapPointManager` | Add task markers to world map |
| `Perspective.getCanvasTilePoly()` | Highlight task locations in 3D |
| `OverlayPanel` | Side panel with route info |
| `NavigationButton` | Side panel tab |
| `ConfigManager` | Persist user preferences + completed task cache |

---

## Open Questions for FattestCat 🐱

1. **Scope:** Do we want to build the FULL pathfinder ourselves, or integrate with
   Shortest Path plugin via `PluginMessage`?
2. **Location data:** Should we invest time scraping WorldPoints from the wiki API,
   or start with area-level clustering and refine later?
3. **What tech stack for the plugin?** Pure Java RuneLite plugin, or a hybrid
   (Java plugin + external web dashboard)?
4. **MVP first?** Start with just a filterable task list panel + area suggestions,
   then add pathfinding/routing later?
5. **Is the league still active?** Timing matters — if it's live NOW, we should
   ship an MVP fast. If it's upcoming, we have time to build the full thing.

---

## Quick Win MVP (Could ship in a weekend)

If we want something FAST:

1. Bundle `raging_echoes_tasks.json` as plugin resource
2. Side panel: filterable task list (by area, tier, skill, completion)
3. On `GameTick`: detect current region → highlight uncompleted tasks for that region
4. Simple "nearby tasks" panel that updates as you move between areas
5. No pathfinding yet — just "you're in Misthalin, here are 114 tasks sorted by points"

This gets us 80% of the value with 20% of the effort. 🐶

---

*Generated by FatCat 🐶 — your loyal code puppy*
*May 2025*
