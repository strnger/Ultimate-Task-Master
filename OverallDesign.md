# Design V3 — League Task Optimizer

> Local plugins wiki: `C:\Users\Strnger\Documents\Repos\runelite-wiki\docs\`
> Local examples: `C:\Users\Strnger\Documents\Repos\examples\`

---

## Components

### 1. Backend Server (Data Persistence + API)

We need our own server to store and serve: task graph edges, community
links, thumbs up/down votes, focus ratings, and telemetry data. The
plugin talks to this over HTTP (POST to submit, GET to retrieve).

**Reference plugins:**
- **ToG Crowdsourcing** — THE key reference. Runs its own backend at
  `togcrowdsourcing.com/worldinfo` (not RuneLite's servers, not wiki).
  `CrowdsourcingManager` is a `@Singleton` that handles all HTTP via
  injected `OkHttpClient` + `Gson`. Two-way data flow:
  - `submitToAPI()` — POST a `WorldData` JSON payload to server
  - `makeGetRequest()` — GET all crowdsourced data, parse `JsonArray`,
    hand off to UI for display
  - On successful POST, immediately fires a GET to refresh the UI
  - Example: `C:\Users\Strnger\Documents\Repos\examples\tog-crowdsourcing\`
    (see `CrowdsourcingManager.java`)
- **Tasks Tracker** — remote JSON API via `OkHttpClient` + Guice-injected
  `DataStoreReader` interface. Shows: HTTP fetch, JSON parse, async
  chaining with `CompletableFuture`.
  - Wiki: `docs/examples/example-plugins-leagues-region-tasks.md`
    (section "Pattern: Remote Data Store with Interface Binding")
  - Source: https://github.com/osrs-reldo/tasks-tracker-plugin
- **WikiSync (#7)** — HTTP POST + local WebSocket server.
  - Wiki: `docs/networking/overview.md`

**Key patterns to reuse:**
- Inject `OkHttpClient` + `Gson` (never create your own HTTP client)
- `enqueue()` for async, never `execute()` on game thread
- POST-then-GET flow from ToG: submit data, immediately refresh
- `DataStoreReader` interface pattern from Tasks Tracker for testability
- Exponential backoff retry from ToG's `@Schedule` error handler
  (retry at 10s, 20s, 30s, 60s, 120s, 300s, 300s, ...)

### 2. Crowdsourcing System (Community Links + Votes)

Players submit task links and vote on connections. This is NOT RuneLite's
built-in crowdsourcing (which is fire-and-forget to RuneLite's servers).
We need two-way crowdsourcing: submit data AND display crowdsourced
results back to the user.

**Reference plugins:**
- **ToG Crowdsourcing** — the best reference for our use case. Key parallels:
  - ToG auto-detects tear stream order via `DecorativeObjectSpawned` events
    -> we auto-detect task completions via `VarbitChanged` events
  - ToG silently POSTs detected data without user action
    -> we silently POST telemetry (task completion timestamps)
  - ToG displays crowdsourced data in a sortable panel with "Hits" column
    -> we display community links with thumbs up/down vote counts
  - ToG uses "hits" (submission count) as confidence metric
    -> we use upvote/downvote ratio as confidence metric
  - Example: `C:\Users\Strnger\Documents\Repos\examples\tog-crowdsourcing\`
    (see `StreamOrderDetector.java` for auto-detection pattern,
    `CrowdsourcingManager.java` for submit + retrieve flow)
- **Tasks Tracker** — throttled varp updates. Shows batching game state
  changes before sending.
  - Wiki: `docs/examples/example-plugins-leagues-region-tasks.md`
    (section "Pattern: Throttled Varp Updates")

**Key patterns to reuse:**
- `CrowdsourcingManager` as `@Singleton` service (ToG pattern)
- Auto-detect game events -> silently build payload -> POST to server
- Two-way flow: POST submissions + GET retrievals to show in UI
- `@Schedule` with exponential backoff for retries on server errors
- Throttle submissions: don't POST every game tick

### 3. Plugin Side Panel (Task List UI)

The main UI: task tree explorer, generated task lists, Add/Not Interested/
Block buttons, Link/Linked buttons with vote counts. Swing-based side panel.

**Reference plugins:**
- **Tasks Tracker** — the gold standard for task-related side panels.
  `TasksTrackerPluginPanel` with `TaskListPanel` (virtualized scrolling),
  `FilterPanel` (dynamic filter UI), and `TaskPanel` (individual task cards).
  - Wiki: `docs/examples/example-plugins-leagues-region-tasks.md`
    (section "Architecture Overview" under Tasks Tracker)
  - Source: https://github.com/osrs-reldo/tasks-tracker-plugin
- **ToG Crowdsourcing** — `WorldSwitcherPanel` extends `PluginPanel`.
  Sortable table with header click-to-sort, `WorldTableRow` components,
  alternating row colors, right-click refresh (throttled to 60s), error
  state messaging, and world filtering by config. Good reference for our
  task list with sortable columns and row interaction.
  - Example: `C:\Users\Strnger\Documents\Repos\examples\tog-crowdsourcing\`
    (see `ui/WorldSwitcherPanel.java`, `ui/WorldTableRow.java`,
    `ui/WorldTableHeader.java`)
- **Leagues Planner** — `LeaguesPlannerPanel` for task planning with region
  awareness. Shows `NavigationButton` registration pattern.
  - Wiki: `docs/examples/example-plugins-leagues-region-tasks.md`
    (section "Pattern: Background Initialization" under Leagues Planner)
  - Source: https://github.com/JacobNelsonGames/Leagues_Planner_And_Pather

**Key patterns to reuse:**
- `NavigationButton.builder()` for toolbar icon + panel registration
- `PluginPanel` base class for side panel
- `SwingUtilities.invokeLater()` for UI updates from game thread
- Sortable table headers with click-to-sort (ToG pattern)
- Alternating row colors with `ODD_ROW` / `ColorScheme.DARK_GRAY_COLOR`
- Error state + "no data" state messaging (ToG pattern)
- Virtualized/lazy task list for performance (1,589 tasks is a lot)

### 4. Wiki Data Integration (Task Definitions + Completion %)

Fetch task definitions, completion percentages, and requirement data.
The plugin needs to know what tasks exist, what they need, and how
popular they are.

**Reference plugins:**
- **Tasks Tracker** — full pipeline: fetch manifest -> fetch task types ->
  read struct data from client -> merge into unified task objects.
  `StructComposition` for reading game data, `EnumComposition` for game enums.
  - Wiki: `docs/examples/example-plugins-leagues-region-tasks.md`
    (sections "Pattern: StructComposition" and "Pattern: EnumComposition")
- **Tasks Tracker** — bitpacked varp reading for task completion status.
  Each varp holds 32 task completion flags.
  - Wiki: `docs/examples/example-plugins-leagues-region-tasks.md`
    (section "Pattern: Bitpacked Varp Task Completion")

**Key patterns to reuse:**
- `client.getStructComposition(structId)` for game structs (client thread only!)
- `BigInteger.testBit()` for clean bitpacked varp reads
- `CompletableFuture` chains for multi-step async loading
- Remote manifest + local struct data = hybrid data model

### 5. World Map Markers (Task Locations on Map)

Show tasks on the world map so players can visually plan trips. Clickable
markers, tooltips on hover, cluster visualization.

**Reference plugins:**
- **Leagues Planner** — `WorldMapPointManager` for adding/removing markers.
  Shows marker lifecycle: add on panel select, remove by predicate on cleanup.
  - Wiki: `docs/examples/example-plugins-leagues-region-tasks.md`
    (section "Pattern: WorldMapPointManager for Map Markers")
  - Also: `docs/overlays/world-map-and-minimap.md`
    (full world map overlay and tooltip patterns)

**Key patterns to reuse:**
- `WorldMapPoint` with icon, name, `setJumpOnClick(true)`
- `worldMapPointManager.removeIf()` for selective cleanup
- World map overlay with `OverlayLayer.MANUAL` + `drawAfterLayer()`
- Tooltip overlay for hover info on map markers

### 6. Game World Overlays (Nearby Task Indicators)

Highlight nearby task locations in the 3D game world — tile markers,
directional indicators, minimap dots.

**Reference plugins:**
- **Quest Helper** — 8 overlay types including world highlights, minimap
  markers, directional arrows, and path lines. The most comprehensive
  overlay system in the ecosystem.
  - Wiki: `docs/overlays/overview.md`
  - Also: `docs/overlays/3d-rendering.md`
- **Leagues Planner** — `PathTileOverlay` for game world tiles,
  `PathMinimapOverlay` for minimap rendering.
  - Wiki: `docs/examples/example-plugins-leagues-region-tasks.md`
    (Architecture Overview under Leagues Planner)
- **ToG Crowdsourcing** — `ToGCrowdsourcingOverlay` demonstrates an
  in-game overlay with `OverlayManager` registration in startUp/shutDown.
  - Example: `C:\Users\Strnger\Documents\Repos\examples\tog-crowdsourcing\`
    (see `ToGCrowdsourcingOverlay.java`)

**Key patterns to reuse:**
- `Perspective.getCanvasTilePoly()` for world-to-screen tile rendering
- `Perspective.localToMinimap()` for minimap dot rendering
- `OverlayLayer.ABOVE_SCENE` for world tiles
- `OverlayLayer.ABOVE_MAP` for minimap
- Multiple overlay classes per plugin (separate concerns)

### 7. Local Persistence (Block List + Player Preferences)

Block list, "Not Interested" session state, player preferences (default
goal count, preferred skills, etc.). Stored locally via RuneLite config.

**Reference plugins:**
- **Tasks Tracker** — `TrackerConfigStore` for persisting task state in
  RuneLite's `ConfigManager`. Shows the pattern of storing structured
  data in config keys.
  - Wiki: `docs/configuration/overview.md`
  - Also: `docs/examples/example-plugins-leagues-region-tasks.md`
- **Region Lock Enforcer** — `RegionSerializer` for JSON persistence
  of complex region/border data via config.
  - Wiki: `docs/examples/example-plugins-leagues-region-tasks.md`
- **ToG Crowdsourcing** — `ToGCrowdsourcingConfig` with `@ConfigGroup` /
  `@ConfigItem` for user preferences (show sidebar, hide PVP worlds,
  show overlay, etc.). Clean example of config-driven behavior.
  - Example: `C:\Users\Strnger\Documents\Repos\examples\tog-crowdsourcing\`
    (see `ToGCrowdsourcingConfig.java`)

**Key patterns to reuse:**
- `@ConfigGroup` / `@ConfigItem` for simple settings
- `configManager.setConfiguration()` for dynamic structured data
- JSON serialization via `Gson` for complex objects (block list)

### 8. Task Completion Detection (Event System)

Detect when the player completes a task in real-time so we can update
the UI, remove it from the todo list, and trigger suggestions.

**Reference plugins:**
- **Tasks Tracker** — `VarbitChanged` event subscriber with throttled
  processing. Bitpacked varp reads to check completion status.
  - Wiki: `docs/examples/example-plugins-leagues-region-tasks.md`
    (section "Pattern: Throttled Varp Updates")
  - Also: `docs/events/game-events.md`
- **ToG Crowdsourcing** — `StreamOrderDetector` uses `@Subscribe` on
  `DecorativeObjectSpawned` + `GameTick` + `GameStateChanged` to detect
  game state, validate data, and auto-submit. Shows the pattern of:
  detect event -> accumulate data -> validate -> auto-submit.
  - Example: `C:\Users\Strnger\Documents\Repos\examples\tog-crowdsourcing\`
    (see `StreamOrderDetector.java`)

**Key patterns to reuse:**
- `@Subscribe onVarbitChanged()` to detect task state changes
- Throttle with `GameTick` to avoid processing every varbit change
- Queue changed varp IDs, flush periodically
- Data validation before submission (ToG validates 6 consecutive
  streams with timing checks before considering data valid)

---

## Core Edge Types

| # | Edge | Detection | Description |
|---|------|-----------|-------------|
| 1 | **SUBSET** | Auto (pattern match) | Doing A literally IS doing part of B. Same activity, different threshold. "10 laps" is progress toward "50 laps." |
| 2 | **CHAIN** | Auto (wiki data) | Completing A unlocks or enables B. Prerequisite relationship. "Easy Diary" before "Medium Diary." |
| 3 | **CO_LOCATED** | Auto (coordinates) | A and B are at the same physical location. "While you're here, do both." |
| 4 | **SAME_SKILL** | Auto (requirements) | A and B train the same skill. Stay in the zone. "All Agility tasks in Morytania." |
| 5 | **COMMUNITY_LINKED** | Manual (crowdsource) | Players know these go together for a reason no algorithm catches. |

All edges are voteable. Community thumbs up/down is the ultimate validation layer.

---

## Feature 1: "What's Near Me?" (Spatial Query)

Player presses button, gets tasks within radius of current position.

- Spatial radius query from player WorldPoint
- Filters: tier, skill, area, requirements met
- Sorted by: distance, points, completion %, AFK rating
- One-click add to task list
- Shows on world map + minimap

---

## Feature 2: "Generate Me a Task List" (Tree Explorer)

The killer feature. A recommendation tree with player agency.

### Step 1: Player sets the goal

```
+------------------------------------+
| Generate Task List                 |
|                                    |
| Goal: o Number of tasks: [5]      |
|       o Target points:   [500]    |
|                                    |
| [Generate]                         |
+------------------------------------+
```

### Step 2: Plugin seeds the tree

1. Fetch wiki completion % data
2. Filter to: player's unlocked regions only
3. Filter out: already completed tasks
4. Filter out: permanently blocked tasks
5. Sort by completion % descending (most commonly done first)
6. Take top N * 3 seed candidates (oversample for branching)

### Step 3: Build tree using edges

Starting from seed tasks, traverse edges to find connected tasks:

```
SEED TASKS (most commonly completed, not yet done):
|-- A: "Pickpocket a Citizen" (93% done, 10pts, Easy)
|   |-- edge(CHAIN) -> D: "Achieve First Level Up" (94%)
|   |   |-- edge(CHAIN) -> F: "Achieve First Level 5" (93%)
|   |   +-- edge(CHAIN) -> G: "Achieve First Level 10" (93%)
|   +-- edge(SAME_SKILL) -> E: "Pickpocket a Guard" (78%)
|       +-- edge(SAME_SKILL) -> H: "Pickpocket a Knight" (62%)
|
|-- B: "Complete Draynor Agility Course" (87%, 10pts, Easy)
|   |-- edge(SUBSET) -> I: "10 Laps of Draynor" (72%)
|   |   +-- edge(SUBSET) -> J: "50 Laps of Draynor" (34%)
|   +-- edge(CO_LOCATED) -> K: "Have Ned make rope" (72%)
|
+-- C: "Milk a Cow" (89%, 10pts, Easy)
    +-- edge(CO_LOCATED) -> L: "Kill a Spider by kicking it" (85%)
```

### Step 4: Player explores and curates

Each task in the tree has three buttons:

| Button | What it does |
|--------|-------------|
| **Add** | Puts task on todo list. Tree re-scores: connected tasks get boosted. Counter updates. |
| **Not Interested** | Removes task + subtree from current tree. Non-persistent -- comes back next generate. |
| **Block** | Same as Not Interested + adds to permanent block list. Never appears again (until unblocked). |

### Step 5: Completed list

```
+------------------------------------------+
| Your Task List (5 tasks, 90 pts)         |
|                                          |
| 1. [ ] Pickpocket a Citizen             |
| 2. [ ] Achieve First Level Up           |
| 3. [ ] Complete Draynor Agility Course  |
| 4. [ ] 10 Laps of Draynor Course       |
| 5. [ ] Have Ned make rope               |
|                                          |
| [Show on Map]  [Optimize Order]          |
| [Export]       [Start Over]              |
+------------------------------------------+
```

### Tree Algorithm

```python
def generate_tree(player, goal, blocked_tasks):
    # 1. Get candidate pool
    candidates = [
        t for t in all_tasks
        if t.region in player.unlocked_regions
        and t.id not in player.completed_tasks
        and t.id not in blocked_tasks
        and player_meets_requirements(player, t)
    ]

    # 2. Score candidates
    for t in candidates:
        t.seed_score = (
            t.completion_pct * 0.5        # Popular first
            + (t.points / 400) * 0.3      # Higher points = better
            + afk_bonus(t) * 0.2          # AFK-friendly bonus
        )

    # 3. Pick top seeds (oversample)
    seeds = sorted(candidates, key=lambda t: -t.seed_score)[:15]

    # 4. Build tree via edge traversal (depth limit: 3)
    tree = []
    for seed in seeds:
        node = TreeNode(task=seed)
        for edge in get_edges(seed.id, sort_by=confidence):
            child = get_task(edge.target_id)
            if child in candidates:
                node.children.append(TreeNode(child, edge.type))
        tree.append(node)

    return tree
```

- Depth limit: 3 levels
- Oversample seeds (15 for a goal of 5)
- Sort children by edge confidence
- Re-score after each Add (SAME_SKILL affinity boost)

---

## Block List

Persistent per-player, stored in RuneLite ConfigManager.

```
+-------------------------------------+
| Blocked Tasks                        |
|                                      |
| Complete the Inferno 15 Times        |
|   Blocked on: June 15, 2025         |
|   [Unblock]                          |
|                                      |
| TzHaar-Ket-Rak's Special Challenge  |
|   Blocked on: June 15, 2025         |
|   [Unblock]                          |
|                                      |
| 3 tasks blocked                      |
+-------------------------------------+
```

---

## Task Link / Linked UI

Every task card has two connection buttons:

### Link Button
Player searches for a task to connect. Submits a COMMUNITY_LINKED edge.
Many-to-many: one task can link to any number of others.

### Linked Button
Shows ALL edges for a task (auto-detected + community-submitted).
Each edge has thumbs up/down. Community can vote on auto-detected edges
too -- if an auto-edge consistently gets thumbs down, confidence drops.

```
+-------------------------------------+
| Tasks linked to:                     |
| "Pickpocket a Citizen"              |
|                                      |
| Achieve First Level Up    47up 3dn  |
|   Edge: CHAIN                        |
|   [thumbs up] [thumbs down]         |
|                                      |
| Pickpocket a Guard        31up 5dn  |
|   Edge: SAME_SKILL                   |
|   [thumbs up] [thumbs down]         |
|                                      |
| 2 linked tasks                       |
+-------------------------------------+
```

---

## Component-to-Feature Matrix

```
                          Near Me    Tree Gen   Links/Votes   Block List
1. Backend Server            x          x           x
2. Crowdsourcing                                    x
3. Side Panel                x          x           x            x
4. Wiki Data                 x          x
5. World Map Markers         x          x
6. Game World Overlays       x
7. Local Persistence                                             x
8. Task Completion           x          x
```

---

---

## Phase 3 (Future)

### CHAIN: Hard vs Soft Split

CHAIN has two flavors worth distinguishing eventually:

**CHAIN_HARD** -- B is literally impossible without A.
```
"Complete Demon Slayer" -> "Complete Recipe for Disaster"
RFD requires Demon Slayer. The game blocks you.
```

**CHAIN_SOFT** -- B can be partially worked on, but not claimed until A is done.
```
"Complete Easy Lumbridge Diary" -> "Complete Medium Lumbridge Diary"
You CAN do Medium diary tasks before finishing Easy.
You CANNOT claim the Medium diary reward until Easy is complete.
```

For Phase 3, this would affect suggestions:
- CHAIN_HARD: Don't suggest B until A is done.
- CHAIN_SOFT: Suggest B freely, but note the prerequisite for claiming.

Classifying every chain edge is case-by-case work. For MVP, CHAIN is a
single edge type. The hard/soft split can be added later via community
tagging or manual curation.

### SAME_GEAR Edge Type

Tasks requiring overlapping equipment/items. E.g., two melee combat tasks
that need the same weapon setup = zero bank trips between them.

Hard to detect accurately. Telemetry data (log equipment hash on task
completion) could eventually make this viable. Not worth building for MVP.
