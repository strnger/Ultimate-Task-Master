# 🐶 Task Graph — Connection & Relationship System

## The Core Idea

Tasks aren't isolated dots. They're **nodes in a graph** with weighted edges.
The edges come from three sources at different trust/confidence levels:

```
┌─────────────────────────────────────────────────────────────┐
│                      TASK GRAPH                              │
│                                                              │
│   ┌──────────┐  "subset"   ┌─────────────┐  "co-located"   │
│   │ 10 Laps  │────────────>│  50 Laps    │<────────────┐   │
│   │ Draynor  │             │  Draynor    │             │   │
│   └──────────┘             └─────────────┘    ┌────────┴┐  │
│        │                         │            │ Insult  │  │
│        │ "co-located"            │ "chain"    │ Aggie   │  │
│        ▼                         ▼            └─────────┘  │
│   ┌──────────┐             ┌─────────────┐                  │
│   │  Ned's   │             │  100 Laps   │                  │
│   │  Rope    │             │  Any Course │                  │
│   └──────────┘             └─────────────┘                  │
│                                                              │
│   Edge Sources:                                              │
│   ── AI/Wiki (initial)    Confidence: Medium                │
│   ── Manual Crowdsource   Confidence: High                  │
│   ── Auto Telemetry       Confidence: Variable (by volume)  │
└─────────────────────────────────────────────────────────────┘
```

---

## Edge Types

### 1. `SUBSET` — One task is progress toward another
```
"Complete 10 Laps of Draynor" ──SUBSET──> "Complete 50 Laps of Draynor"
"Achieve Level 5"             ──SUBSET──> "Achieve Level 10"
"Room 1 of Pyramid Plunder"  ──SUBSET──> "Room 2 of Pyramid Plunder"
```
**Meaning:** Completing the smaller task gives you partial progress toward the bigger one.
**Source:** AI/Wiki (detectable by pattern: same task, different numbers).
**Weight:** Strong — these are basically guaranteed to be done together.

### 2. `CHAIN` — One task naturally leads to another
```
"Achieve Level 5"   ──CHAIN──> "Achieve Level 10"  ──CHAIN──> "Achieve Level 20"
"Pickpocket a Man"  ──CHAIN──> "Achieve First Level Up"
```
**Meaning:** Completing A will likely make B achievable/trivial very soon after.
**Source:** All three sources (AI detects it, players confirm it, telemetry proves it).

### 3. `CO_LOCATED` — Tasks at the same place
```
"Complete Draynor Agility"  ──CO_LOCATED──> "Have Ned make rope"
"Insult Aggie the Witch"   ──CO_LOCATED──> "Have Ned make rope"
```
**Meaning:** You're already HERE — might as well knock this one out too.
**Source:** Location data (wiki coordinates) + telemetry (completed within N minutes).

### 4. `SAME_GEAR` — Tasks requiring similar inventory/equipment
```
"Equip a Rune Scimitar"   ──SAME_GEAR──> "Defeat 10 Moss Giants"
"Fish 100 Lobsters"       ──SAME_GEAR──> "Fish 50 Swordfish"
```
**Meaning:** You already have the right gear equipped/in inventory.
**Source:** Item requirement overlap analysis + telemetry.

### 5. `SAME_SKILL` — Tasks training the same skill
```
"Mine 50 Iron Ore"    ──SAME_SKILL──> "Mine 25 Gold Ore"
"Fletch 100 Yew Bows" ──SAME_SKILL──> "Reach Level 70 Fletching"
```
**Meaning:** You're already in the training groove for this skill.
**Source:** Skill requirement analysis + telemetry.

### 6. `COMMUNITY_LINKED` — Players manually say "these go together"
```
"Complete Prince Ali Rescue" ──COMMUNITY──> "Get through Al Kharid gate free"
```
**Meaning:** Some relationship that's obvious to players but hard to auto-detect.
**Source:** Manual crowdsource only.

---

## Data Model

### Edge Structure
```json
{
  "source_task_id": "complete-10-laps-draynor",
  "target_task_id": "complete-50-laps-draynor",
  "edge_type": "SUBSET",
  "confidence": 0.95,
  "sources": [
    {
      "type": "AI_WIKI",
      "reason": "Pattern match: same activity, count 10 < 50",
      "generated_at": "2025-05-20T00:00:00Z"
    },
    {
      "type": "TELEMETRY",
      "co_completion_rate": 0.89,
      "avg_time_between_sec": 240,
      "sample_size": 1523,
      "updated_at": "2025-06-20T12:00:00Z"
    }
  ]
}
```

### Confidence Scoring
```
confidence = weighted_average(
    ai_wiki_confidence   * 0.3,   # Starting baseline
    manual_link_votes    * 0.4,   # High trust - humans said so
    telemetry_confidence * 0.3    # Proven by data volume
)

Where:
  ai_wiki_confidence = 0.0-1.0 based on pattern match quality
  manual_link_votes  = upvotes / (upvotes + downvotes)
  telemetry_confidence = min(1.0, sample_size / 100) * co_completion_rate
```

---

## Three Data Sources — Detailed Design

### Source 1: AI/Wiki Analysis (Seed Data — Day 0)

Before league even launches, we pre-analyze the task list to generate initial edges.

**Detectable patterns:**
```
Pattern                          Edge Type    Example
─────────────────────────────────────────────────────────────────
Same text, different count       SUBSET       "10 laps" → "50 laps"
Same location in description     CO_LOCATED   Both mention "Draynor"
Same skill requirement           SAME_SKILL   Both need Agility
Overlapping item requirements    SAME_GEAR    Both need fishing rod
Quest prerequisite chains        CHAIN        "Easy diary" → "Medium diary"
Combat achievement groupings     CHAIN        All CAs for same boss
```

**From our Raging Echoes data, already detected:**
- 108 subset/count-variant groups (see analysis output)
- Co-location clusters for 13+ named locations
- Skill clusters: Defence(100), Slayer(96), Magic(88), etc.

**This gives us hundreds of edges on Day 1 with zero player input.**

### Source 2: Manual Crowdsource (High Trust)

Players explicitly link tasks in the plugin UI:

```
┌──────────────────────────────────────┐
│ 🔗 Link Tasks                        │
│                                      │
│ You just completed:                  │
│   "Pickpocket a citizen"             │
│                                      │
│ Related tasks? (click to link)       │
│   ☐ Achieve Your First Level Up      │
│   ☐ Achieve Your First Level 5       │
│   ☐ Pickpocket a Guard               │
│   ☐ Pickpocket a Knight              │
│                                      │
│ Or search: [________________] 🔍     │
│                                      │
│ [Submit Links]                       │
└──────────────────────────────────────┘
```

**Design decisions:**
- Show AI-suggested related tasks first (likely candidates)
- Allow free-text search to link any task
- Rate-limit: max 10 link submissions per hour
- Community validation: other players can upvote/downvote links
- Minimum 3 independent submitters before edge is "confirmed"

### Source 3: Telemetry Auto-Generation (Proven by Data)

The plugin silently logs: `{ task_id, completed_at_timestamp, world_point, player_hash }`

Backend processes this into edges:

```python
# Pseudocode for telemetry edge generation
def find_temporal_edges(completions_log):
    """Find tasks frequently completed close together in time."""
    edges = defaultdict(lambda: {"count": 0, "total_time": 0})

    for player_session in group_by_player(completions_log):
        for task_a, task_b in sliding_window(player_session, window=2):
            time_gap = task_b.timestamp - task_a.timestamp

            if time_gap < MAX_GAP_SECONDS:  # e.g., 30 minutes
                pair = tuple(sorted([task_a.id, task_b.id]))
                edges[pair]["count"] += 1
                edges[pair]["total_time"] += time_gap

    # Filter: only keep pairs with enough data
    return {
        pair: {
            "co_completion_rate": data["count"] / total_completions(pair),
            "avg_time_between": data["total_time"] / data["count"],
            "sample_size": data["count"],
        }
        for pair, data in edges.items()
        if data["count"] >= MIN_SAMPLE_SIZE  # e.g., 50
    }
```

**What this catches that the other sources miss:**
- "People who do X often do Y next" (Amazon-style recommendations)
- Non-obvious geographic clusters (tasks at different spots but on same route)
- Skill training progressions (people naturally do these in order)
- Meta/efficiency patterns (experienced players' routes bubble up)

**Privacy:**
- Only task IDs + timestamps + hashed player ID
- No character names, no precise locations stored
- Aggregated — individual sessions never exposed
- `client.getAccountHash()` for anonymous dedup

---

## How Edges Power the Plugin Features

### Feature 1: Route Builder — "Smart Suggestions"
When player adds task A to their route, plugin suggests:
```
"You added 'Complete 10 Laps of Draynor'. Also consider:"
  → Complete 50 Laps of Draynor (SUBSET, 94% do both)
  → Have Ned make rope (CO_LOCATED, 67% do in same trip)
  → Insult Aggie the Witch (CO_LOCATED, 58% do in same trip)
```

### Feature 2: Task Suggestions — "Connected Tasks Nearby"
Radius-based suggestions are SORTED by connection strength:
```
"You're in Draynor. 8 tasks nearby:"
  1. Complete Draynor Agility (42 tiles) ← also connected to your route!
  2. Have Ned make rope (67 tiles) ← 67% co-completion rate
  3. Insult Aggie (80 tiles)
  ...
```

### Feature 3: Pathfinding (Future)
With the graph, pathfinding becomes smarter:
```
Instead of: shortest path between random tasks
We get:     shortest path through CLUSTERS of connected tasks
```

The AI routing you mentioned earlier becomes:
"Find the route through Misthalin that maximizes total points
 while prioritizing connected task clusters."

This is a weighted graph traversal — not pure TSP,
but a cluster-then-route approach that's much more tractable.

---

## Pathfinding & AI Routing (Parked, Not Scrapped)

Keeping this as a Phase 3 feature. When we get there:

### Approach: Cluster-Then-Route
1. Use task graph edges to identify natural clusters
2. Use pathfinding (Leagues Planner / Shortest Path) for distances between clusters
3. Greedy or branch-and-bound to order clusters
4. Within each cluster, nearest-neighbor ordering

### Why It Works Better With The Graph
Without graph: 1,589 independent tasks → intractable TSP
With graph: ~50-100 clusters of connected tasks → very tractable

### Data We'll Need
- Collision maps (from Shortest Path plugin resources)
- Transport nodes (teleports, boats, fairy rings, etc.)
- Player's available transports (quest/level gated)

All of this already exists in the Leagues Planner plugin. We'd integrate, not rebuild.

---

## API Additions for Graph Data

```
GET  /api/v1/edges
     → All task edges with confidence scores
     → Versioned, cacheable, same pattern as tasks

POST /api/v1/edges/suggest
     → { "source_task": "abc", "target_task": "def", "player_hash": "..." }
     → Manual link submission

POST /api/v1/edges/vote
     → { "edge_id": "...", "vote": "up"|"down", "player_hash": "..." }
     → Community validation of edges

POST /api/v1/telemetry/completion
     → { "task_id": "abc", "timestamp": 1234567890, "player_hash": "..." }
     → Silent completion logging for auto-edge generation
```

---

## Implementation Priority

### Day 0 (Before League Launch)
- [ ] AI/Wiki edge generation from task data (subset, co-location, same-skill)
- [ ] Seed the edge database with ~500+ auto-detected edges

### Week 1 (Launch)
- [ ] Telemetry collection starts (silent, just logging)
- [ ] Manual link UI in plugin (simple version)
- [ ] Route builder uses edges for "also consider" suggestions

### Week 2+
- [ ] First telemetry edge batch processed
- [ ] Community voting on manual links
- [ ] Suggestion engine weighted by edge confidence

### Month 1+
- [ ] Pathfinding integration using graph clusters
- [ ] "Optimal route" generator using cluster-then-route algorithm
- [ ] Edge quality dashboard (which edges are most/least validated)

---

*Generated by FatCat 🐶 — your loyal code puppy*
*May 2025*
