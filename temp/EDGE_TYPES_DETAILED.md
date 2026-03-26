# Task Graph — Edge Types (Detailed)

Each edge represents a relationship between two tasks that means:
**"If you're doing Task A, you should know about Task B."**

The six edge types capture fundamentally different REASONS why two tasks are related.

---

## 1. `SUBSET` — "Doing A literally IS doing part of B"

### What it means
Task A's completion condition is a strict subset of Task B's completion condition.
Every unit of progress on A is simultaneously progress on B. They share the same
underlying activity — the only difference is the quantity/threshold.

### The key test
> "Can I complete A without making ANY progress on B?" — If no, it's SUBSET.

### Real examples from Raging Echoes

**Total Level chain (12 tasks, same activity):**
```
Reach Total Level 100  (30 pts, Medium)
Reach Total Level 250  (30 pts, Medium)
Reach Total Level 500  (30 pts, Medium)
...
Reach Total Level 2277 (400 pts, Master)
```
Every skill level you gain ticks ALL of these simultaneously.
Completing "Total Level 100" is literally progress toward "Total Level 250."

**Agility Lap chains:**
```
Complete 10 Laps of the Draynor Agility Course  (30 pts)
Complete 50 Laps of the Draynor Agility Course  (80 pts)
```
Lap 10 completes the first task AND is 20% progress toward the second.

**Pyramid Plunder rooms:**
```
Room 1 of Pyramid Plunder  (10 pts)
Room 2 of Pyramid Plunder  (10 pts)
...
Room 8 of Pyramid Plunder  (80 pts)
```
To reach Room 5, you MUST pass through Rooms 1-4. Each room completion
is a subset of deeper room completions within a single run.

### Why it matters for the plugin
- **Route builder:** If A is in the route, auto-suggest B (and vice versa).
  "You're already running Draynor laps for the 10-lap task — the 50-lap task
  will complete itself if you stay."
- **Suggestions:** Don't show A and B as separate suggestions — show them as one
  bundled activity with stacked point rewards.
- **Point efficiency:** SUBSET edges are the most point-efficient edges. You get
  multiple task completions from a single continuous activity.

### How to detect (auto)
Pattern match on task names: same text template, different numbers.
```
normalize("Complete 10 Laps of Draynor") = "Complete N Laps of Draynor"
normalize("Complete 50 Laps of Draynor") = "Complete N Laps of Draynor"
→ Same template → SUBSET edge, ordered by the number
```
Our analysis found **108 groups** containing subset chains.

### Edge direction
Always: smaller → larger. `10 laps` → `50 laps` (not the reverse).
The smaller task completes FIRST.

### Confidence
Very high (0.95+) for auto-detected count variants. These are near-certain.

---

## 2. `CHAIN` — "Completing A unlocks or trivially enables B"

### What it means
Task A's completion directly creates the conditions needed for Task B.
Unlike SUBSET, they're not the same activity — but A causally leads to B.
There's a logical dependency or natural progression between them.

### The key test
> "Does completing A remove a blocker or make B significantly easier?" — If yes, it's CHAIN.

### Real examples from Raging Echoes

**Achievement Diary progression:**
```
Complete the Easy Lumbridge & Draynor Diary    (30 pts, Medium)
Complete the Medium Lumbridge & Draynor Diary  (80 pts, Hard)
Complete the Hard Lumbridge & Draynor Diary    (80 pts, Hard)
Complete the Elite Lumbridge & Draynor Diary   (200 pts, Elite)
```
Each diary tier requires the previous tier to be completed first.
You literally CANNOT do Medium before Easy. Hard dependency chain.

**Skill level gates:**
```
Achieve Your First Level 5   (10 pts, Easy)
Achieve Your First Level 10  (10 pts, Easy)
Achieve Your First Level 20  (10 pts, Easy)
Achieve Your First Level 30  (30 pts, Medium)
...
Achieve Your First Level 95  (200 pts, Elite)
```
You must hit Level 5 before Level 10. Not the same activity as SUBSET
(you might switch skills between levels), but a natural progression.

**Combat Achievement count thresholds:**
```
50 Combat Achievements   (200 pts)
100 Combat Achievements  (200 pts)
150 Combat Achievements  (200 pts)
200 Combat Achievements  (200 pts)
250 Combat Achievements  (200 pts)
```

**Kill count → equipment tasks:**
```
Defeat Zulrah          → Equip Toxic Blowpipe
Complete Chambers of Xeric → Equip a Twisted Bow
```
The boss drop IS the equipment. You chain from the kill to the equip.

### Why it matters for the plugin
- **Route builder:** If player adds B but hasn't done A, show a warning:
  "This task requires completing 'Easy Lumbridge Diary' first."
- **Suggestions:** When A is completed, B should get a priority boost in
  suggestions — it just became available or much easier.
- **Ordering:** Route should always place A before B.

### How to detect
- **Diary chains:** Parse "Easy/Medium/Hard/Elite" + region name.
- **Quest prerequisites:** Wiki has quest dependency data.
- **Boss → item:** Wiki lists boss drops; match drop items to "equip X" tasks.
- **Level gates:** Detect ascending level requirements in same skill.

### Edge direction
Always: prerequisite → dependent. `Easy Diary` → `Medium Diary`.

### Confidence
High for diary/quest chains (0.9+). Medium for boss→equip (0.7-0.8, since
there might be alternative sources for items).

### How it differs from SUBSET
SUBSET: doing A literally IS doing B (same activity, same progress bar).
CHAIN: doing A ENABLES B (different activities, but A must come first).

```
SUBSET:  "10 laps" and "50 laps" — same action (running laps)
CHAIN:   "Easy diary" and "Medium diary" — different requirements, but ordered
```

---

## 3. `CO_LOCATED` — "A and B are at the same physical place"

### What it means
Both tasks can be completed at or very near the same in-game WorldPoint.
You'd walk to this area for Task A, and Task B is right there too — so
you might as well knock it out while you're here.

### The key test
> "If I walked to A's location, could I see B's location on my screen
> (or reach it in under 30 seconds of walking)?" — If yes, CO_LOCATED.

### Real examples from Raging Echoes

**Draynor cluster (8 tasks):**
```
Complete the Draynor Agility Course        — at the agility course
Complete 10 Laps of the Draynor Course     — at the agility course
Have Ned make you some rope                — Ned's house, 50 tiles south
Insult Aggie the Witch                     — Aggie's house, 40 tiles south
Complete Easy Lumbridge & Draynor Diary    — partially in Draynor
Complete Medium Lumbridge & Draynor Diary  — partially in Draynor
Complete Hard Lumbridge & Draynor Diary    — partially in Draynor
Complete Elite Lumbridge & Draynor Diary   — partially in Draynor
```
A player going to Draynor for agility could do Ned + Aggie in the same trip.

**Lumbridge cluster (15 tasks):**
```
Ask for a Quest from Bob         — Bob's Axes, central Lumbridge
Kill a Spider by kicking it      — Lumbridge basement
Milk a cow                       — cow field east of Lumbridge
Pray at an Altar in Lumbridge    — Lumbridge church
Use the Northern Staircase       — Lumbridge castle
...
```

**Al Kharid cluster (11 tasks):**
```
Complete the Al Kharid Agility Course
Craft a Fire Rune                 — fire altar nearby
Drink some of Ali's tea
Enter the Kalphite Lair
...
```

### Why it matters for the plugin
This is the **bread and butter** of the suggestion system.
"You're standing in Draynor — here are 8 things you can do without teleporting."

- **Suggestions:** The core spatial query. Tasks within radius X of player position.
- **Route builder:** Group co-located tasks together. Don't put Draynor → Varrock →
  Draynor in a route — batch the Draynor tasks.
- **World map:** Show clusters of tasks so player can visually plan trips.

### How to detect
- **Wiki coordinates:** Extract WorldPoint from wiki pages' `{{Map|x=N|y=N}}` templates.
  Compare distances. Tasks within ~100 tiles = CO_LOCATED.
- **Description parsing:** Both mention "Draynor" → likely co-located.
- **Telemetry:** Tasks completed within 2-3 minutes of each other, by many players.

### Edge direction
**Bidirectional.** If A is near B, then B is near A.

### Confidence
- From wiki coordinates: Very high (0.95) — math doesn't lie.
- From description parsing: Medium (0.7) — "Lumbridge" is a big area.
- From telemetry: Variable — depends on sample size.

### Important nuance: Radius matters
"Lumbridge" is huge. Two tasks both "in Lumbridge" could be 200+ tiles apart.
Ideally we use actual WorldPoint coordinates, not just area names.

---

## 4. `SAME_GEAR` — "A and B need the same inventory setup"

### What it means
Both tasks require the player to have similar items equipped or in their inventory.
Switching between these tasks doesn't require a bank trip to re-gear.

### The key test
> "If I'm geared up for A, do I already have what I need for B in my
> inventory/equipment?" — If yes (or close to it), SAME_GEAR.

### Real examples

**Melee combat gear shared:**
```
"Defeat a Fire Giant in Kandarin"    — needs melee gear
"Defeat a Moss Giant in Kandarin"    — needs same melee gear
"Equip a Rune Scimitar"             — needs the weapon anyway
```
Same gear, same area. Zero bank trips between these.

**Fishing rod tasks:**
```
"Catch a Herring"       — fishing rod + bait
"Catch an Anchovy"      — small fishing net (different!)
"Catch a Salmon"        — fly fishing rod + feathers
```
Wait — these actually need DIFFERENT gear! This is why SAME_GEAR needs actual
item data, not just "both are Fishing tasks."

**Ranged combat set:**
```
"Defeat 10 Blue Dragons with Ranged"  — ranged gear + anti-dragon shield
"Defeat 5 Black Dragons"              — same gear works
```

**Skilling outfits:**
```
"Equip a piece of Alchemists outfit"  — need the outfit piece
"Make 100 Prayer Regeneration Potions" — need the outfit for bonus
```

### Why it matters for the plugin
**Bank trips are the enemy of efficiency.** Every bank trip is dead time.
If we can group tasks that share gear, the player does fewer bank trips.

- **Route builder:** Warn when adjacent route tasks need different gear.
  "Tasks 3 and 4 need different equipment — consider reordering."
- **Suggestions:** Boost score for tasks matching current equipment.
  "You're wearing melee gear — here are combat tasks nearby."
- **Smart inventory:** Eventually, suggest what to bring for a route.

### How to detect
- **Item requirement analysis:** Wiki lists required items. Compare overlap.
- **Equipment check:** `client.getItemContainer(InventoryID.WORN)` → match
  equipped items against task requirements.
- **Telemetry:** Tasks completed with the same equipment hash → SAME_GEAR.
- **Skill category heuristic:** All Fishing tasks likely share gear (mostly).
  All melee combat tasks likely share gear. But this is ROUGH — see the fishing
  rod example above.

### Edge direction
**Bidirectional.** Gear overlap is symmetric.

### Confidence
- From exact item overlap: High (0.85+)
- From skill category heuristic: Low-Medium (0.5) — too many exceptions
- From telemetry equipment hash: High (0.9) if sample size is good

### Why this is harder than it sounds
OSRS has complex gear interactions:
- Task says "Defeat X" — what gear? Melee? Ranged? Magic? Depends on player.
- Task says "Fish a Shark" — harpoon? Crystal harpoon? Barbarian fishing?
- Some tasks don't specify gear but implicitly need it.

This is where **crowdsource** and **telemetry** shine — let players tell us
what they actually used, rather than trying to infer it.

---

## 5. `SAME_SKILL` — "A and B train the same skill"

### What it means
Both tasks involve actively using or training the same skill.
You're already in "Agility mode" — might as well do all Agility tasks nearby.

### The key test
> "Am I training the same skill for both tasks?" — If yes, SAME_SKILL.

### How it differs from SAME_GEAR
**SAME_GEAR** is about inventory contents — physical items.
**SAME_SKILL** is about player activity/focus — what you're actively doing.

Sometimes they overlap (Fishing tasks need fishing gear AND train Fishing).
Sometimes they don't:
- "Mine 50 Iron" and "Smith 50 Iron Bars" are SAME_GEAR (pickaxe + ore) but
  DIFFERENT SKILL (Mining vs Smithing).
- "Mine 50 Iron" and "Mine 50 Coal" are SAME_SKILL but might be at different locations.

### Real examples from Raging Echoes

**Agility in Morytania (12 tasks):**
```
Complete the Canifis Agility Course      (Agility 40)
Complete the Werewolf Agility Course     (Agility 60)
Floor 1 of the Hallowed Sepulchre       (Agility 52)
Floor 2 of the Hallowed Sepulchre       (Agility 62)
Floor 3 of the Hallowed Sepulchre       (Agility 72)
Floor 4 of the Hallowed Sepulchre       (Agility 82)
Floor 5 of the Hallowed Sepulchre       (Agility 92)
...
```
Player grinding Agility in Morytania can check off many of these in one session.

**Thieving in Desert (21 tasks!):**
```
Room 1-8 of Pyramid Plunder             (Thieving 21-91)
Pickpocket a Menaphite Thug             (Thieving 65)
Turn in Sq'irkjuices to Osman           (Thieving various)
...
```
A Thieving-focused session in the Desert is worth 21 tasks!

**Slayer everywhere (96 tasks!):**
```
Defence (100 tasks) — but really combat in general
Slayer (96 tasks)
Magic (88 tasks)
Ranged (80 tasks)
Attack (79 tasks)
```

### Why it matters for the plugin
- **Session planning:** "I want to train Agility today" → show all Agility tasks
  sorted by location, forming a natural route between courses.
- **Mental context:** Switching skills takes mental effort. Group same-skill tasks
  to reduce cognitive load.
- **Suggestions:** "You just did an Agility task — here are more Agility tasks
  nearby" (while you're still in the zone).

### How to detect
- **Skill requirements:** Most reliable. Task requires Agility 40 → it's an Agility task.
- **Description keywords:** "Mine", "Chop", "Fish", "Cook" → infer skill.
- **Wiki categories:** Tasks are often categorized by skill on the wiki.

### Edge direction
**Bidirectional.** Same-skill relationship is symmetric.

### Confidence
- From matching skill requirements: High (0.85)
- From keyword inference: Medium (0.6) — some tasks train unexpected skills
- From telemetry XP tracking: Very high (0.95) — we can see which skill got XP

### The "training location" dimension
SAME_SKILL + CO_LOCATED is the most powerful combo:
"These 12 Agility tasks are all in Morytania" → amazing training session.

SAME_SKILL without CO_LOCATED is less actionable:
"These 64 Agility tasks span 11 areas" → too scattered, filter by area first.

---

## 6. `COMMUNITY_LINKED` — "Players know these go together"

### What it means
A human player recognizes a relationship that no algorithm can reliably detect.
These are connections based on game knowledge, meta strategies, or non-obvious
interactions between game systems.

### The key test
> "Would an experienced player say 'oh yeah, obviously do these together'
> but the reason isn't captured by any other edge type?" — COMMUNITY_LINKED.

### Hypothetical examples

**Quest unlocks game mechanic:**
```
"Complete Prince Ali Rescue" ↔ "Travel through Al Kharid gate"
```
Why: The quest removes the 10gp toll at the gate. A player who knows this
would do them together. Nothing in the task text connects them — you need
game knowledge.

**Item crafting → equipping flow:**
```
"Smith a Rune Platebody" ↔ "Equip a Rune Platebody"
```
Why: You smith it, then immediately equip it. Two tasks, one moment.
AI might not catch this because "Smith" and "Equip" are different verbs
with no textual overlap.

**Shared NPC interaction:**
```
"Have Ned make you some rope" ↔ "Start Dragon Slayer"
```
Why: Both tasks require talking to Ned in Draynor. While you're there
talking to him for rope, start the quest too. CO_LOCATED would partially
catch this, but the NPC-level connection is more specific.

**Efficient training meta:**
```
"Catch 100 Chinchompas" ↔ "Defeat 50 Maniacal Monkeys"
```
Why: You catch chinchompas specifically to use them as ranged ammo at
monkeys. This is a supply→consumption chain that requires meta knowledge.

**Shared unlock:**
```
"Enter Kourend Catacombs" ↔ "Defeat a Greater Demon in Kourend"
```
Why: Greater Demons in Kourend are IN the catacombs. Entering is a
prerequisite for the kill, but the text doesn't make this connection.

**Diary task shortcuts:**
```
"Light a Fire on top of Trollheim" ↔ "Complete Eadgar's Ruse"
```
Why: Eadgar's Ruse unlocks a teleport to Trollheim. Players know to
do the quest first, then the diary task becomes trivial. This is a
CHAIN edge really, but it crosses the quest→diary boundary in a way
that's hard to auto-detect.

### Why it matters for the plugin
This is the **long tail of connections** that makes the plugin feel smart.
The first five edge types catch the obvious stuff. COMMUNITY_LINKED catches
the "wow, I didn't think of that" connections.

It also builds community engagement:
- Players feel ownership: "I submitted this link and 200 people upvoted it!"
- Shared game knowledge becomes structured data
- Newer players discover connections they'd never know otherwise

### How it works (crowdsource only)
1. Player completes a task
2. Plugin offers: "Know any related tasks? Link them!"
3. Player searches and selects related tasks
4. Backend stores the link with player hash
5. Other players can upvote/downvote the link
6. After N independent confirmations, edge is "validated"

### Moderation
- Require minimum 3 independent submitters before edge shows up
- Upvote/downvote system (link disappears if ratio < 0.3)
- Rate limiting per player (prevents spam)
- Edge types can be suggested: "Why are these related?"
  (player picks: same place / same gear / quest unlock / training combo / other)

### Edge direction
**Depends on the relationship.** Some are bidirectional, some aren't.
Player should indicate: "A should come before B" or "order doesn't matter."

### Confidence
Starts low (0.3 with 1 submitter). Grows with votes:
```
confidence = upvotes / (upvotes + downvotes) * min(1.0, submitters / 5)
```
Peaks at 0.9ish (never 1.0 — humans can be wrong).

---

## Edge Type Comparison Matrix

```
                    SUBSET  CHAIN  CO_LOC  GEAR  SKILL  COMMUNITY
Same activity?       Yes     No     No      No    Yes*    Varies
Same location?       Yes     No     Yes     No    No      Varies
Directional?         Yes→    Yes→   No↔     No↔   No↔     Varies
Auto-detectable?     Easy    Med    Med     Hard  Easy    No
Confidence floor?    0.95    0.80   0.70    0.50  0.60    0.30
Changes per league?  Some    Some   No**    Some  Some    Yes
```

*SAME_SKILL tasks share the skill but not necessarily the same specific activity
**Locations don't move between leagues, but task-to-location mappings might change
