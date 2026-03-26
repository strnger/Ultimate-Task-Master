"""
Deep analysis of each edge type using real Raging Echoes task data.
Finds concrete examples for every edge category.
"""
import json
import re
from collections import defaultdict

tasks = json.load(open("raging_echoes_tasks.json", encoding="utf-8"))
by_name = {t["name"]: t for t in tasks}


def print_section(title):
    print(f"\n{'=' * 70}")
    print(f"  {title}")
    print(f"{'=' * 70}")


def analyze_subset():
    """SUBSET: One task's completion is literal progress toward another."""
    print_section("SUBSET EDGES")

    # Find count-variant groups
    count_pattern = re.compile(r"(\d+)")
    groups = defaultdict(list)
    for t in tasks:
        normalized = count_pattern.sub("N", t["name"].lower()).strip()
        groups[normalized].append(t)

    multi = {k: v for k, v in groups.items() if len(v) > 1}
    print(f"\n  Total subset groups found: {len(multi)}")
    print(f"  Total tasks in subset chains: {sum(len(v) for v in multi.values())}")

    # Show the best examples
    print("\n  --- Example: Total Level Chain ---")
    total_lvl = [t for t in tasks if "total level" in t["name"].lower()]
    for t in sorted(total_lvl, key=lambda x: x["points"]):
        print(f"    {t['points']:3d}pts [{t['tier']:6s}] {t['name']}")

    print("\n  --- Example: Agility Lap Chains ---")
    lap = [t for t in tasks if re.search(r"\d+.*lap", t["name"].lower())]
    for t in sorted(lap, key=lambda x: x["name"]):
        print(f"    {t['points']:3d}pts [{t['tier']:6s}] {t['name']}")

    print("\n  --- Example: Slayer Encounter Chain ---")
    slayer_enc = [t for t in tasks if "superior slayer" in t["name"].lower()]
    for t in sorted(slayer_enc, key=lambda x: x["points"]):
        print(f"    {t['points']:3d}pts [{t['tier']:6s}] {t['name']}")


def analyze_chain():
    """CHAIN: Completing A makes B trivially achievable or unlocks it."""
    print_section("CHAIN EDGES")

    # Diary chains (Easy -> Medium -> Hard -> Elite)
    print("\n  --- Example: Diary Progression ---")
    diaries = [t for t in tasks if "diary" in t["name"].lower()]
    diary_groups = defaultdict(list)
    for t in diaries:
        # Extract region name (before Easy/Medium/Hard/Elite)
        region = re.sub(r"complete the (easy|medium|hard|elite)\s+", "", t["name"].lower())
        diary_groups[region].append(t)
    for region, group in sorted(diary_groups.items()):
        if len(group) >= 3:
            print(f"\n    {region.title()}:")
            for t in sorted(group, key=lambda x: x["points"]):
                print(f"      {t['points']:3d}pts [{t['tier']:6s}] {t['name']}")

    # Quest chains
    print("\n  --- Example: Combat Achievement Chains ---")
    ca = [t for t in tasks if "combat achievement" in t["name"].lower()
          and re.search(r"\d+", t["name"])]
    for t in sorted(ca, key=lambda x: x["points"]):
        print(f"    {t['points']:3d}pts [{t['tier']:6s}] {t['name']}")

    # Level-up chains that naturally follow each other
    print("\n  --- Example: Skill Level Chain ---")
    levels = [t for t in tasks if re.match(r"achieve your first level \d+$", t["name"].lower())]
    for t in sorted(levels, key=lambda x: x["points"]):
        print(f"    {t['points']:3d}pts [{t['tier']:6s}] {t['name']}")


def analyze_colocated():
    """CO_LOCATED: Tasks at the same physical location."""
    print_section("CO_LOCATED EDGES")

    locations = {
        "draynor": [],
        "lumbridge": [],
        "varrock": [],
        "falador": [],
        "al kharid": [],
        "seers' village": [],
        "catherby": [],
        "edgeville": [],
        "ardougne": [],
        "prifddinas": [],
        "canifis": [],
        "port sarim": [],
    }

    for loc in locations:
        locations[loc] = [
            t for t in tasks
            if loc in t["description"].lower() or loc in t["name"].lower()
        ]

    for loc, group in sorted(locations.items(), key=lambda x: -len(x[1])):
        if len(group) >= 3:
            print(f"\n  {loc.title()} — {len(group)} tasks at same location:")
            for t in group[:6]:
                print(f"    {t['points']:3d}pts [{t['tier']:6s}] {t['name'][:55]}")
            if len(group) > 6:
                print(f"    ... +{len(group) - 6} more")


def analyze_same_gear():
    """SAME_GEAR: Tasks requiring overlapping equipment/items."""
    print_section("SAME_GEAR EDGES")

    # Find tasks mentioning specific equipment
    gear_keywords = {
        "fishing": ["fish", "lobster", "shark", "swordfish", "harpoon", "net"],
        "melee_combat": ["defeat", "kill", "slay"],
        "ranged": ["range", "bow", "crossbow"],
        "magic": ["cast", "spell", "rune"],
        "woodcutting": ["chop", "log", "tree", "axe"],
        "mining": ["mine", "ore", "pickaxe", "rock"],
    }

    for category, keywords in gear_keywords.items():
        matching = [
            t for t in tasks
            if any(kw in t["description"].lower() for kw in keywords)
        ]
        print(f"\n  {category} gear tasks: {len(matching)}")
        # Show pairs in same area
        area_groups = defaultdict(list)
        for t in matching:
            area_groups[t["area"]].append(t)
        for area, group in sorted(area_groups.items()):
            if len(group) >= 3:
                print(f"    {area} ({len(group)} tasks):")
                for t in group[:3]:
                    print(f"      [{t['tier']:6s}] {t['name'][:50]}")


def analyze_same_skill():
    """SAME_SKILL: Tasks requiring the same skill focus."""
    print_section("SAME_SKILL EDGES")

    skill_groups = defaultdict(list)
    for t in tasks:
        if t["requirements"]:
            for req in t["requirements"]:
                skill_groups[req["skill"]].append(t)

    for skill, group in sorted(skill_groups.items(), key=lambda x: -len(x[1])):
        # Show tasks in same area with same skill - prime candidates
        area_skill = defaultdict(list)
        for t in group:
            area_skill[t["area"]].append(t)

        print(f"\n  {skill} — {len(group)} tasks total")
        for area, area_tasks in sorted(area_skill.items(), key=lambda x: -len(x[1]))[:3]:
            print(f"    In {area} ({len(area_tasks)}):")
            for t in area_tasks[:3]:
                lvl = [r["level"] for r in t["requirements"] if r["skill"] == skill][0]
                print(f"      Lvl {lvl:2d} [{t['tier']:6s}] {t['name'][:45]}")


def analyze_community():
    """COMMUNITY_LINKED: Non-obvious connections humans would spot."""
    print_section("COMMUNITY_LINKED EDGES (hypothetical examples)")

    # These are things AI might miss but players know
    print("""
  These are relationships that require game knowledge to identify.
  Can't be auto-detected — need players to submit them.

  Example pairs a player might link:

  1. Quest unlocks access:
     "Complete Prince Ali Rescue" ↔ "Use Al Kharid gate for free"
     (Quest removes the 10gp toll — not obvious from task text)

  2. Shared prerequisite grind:
     "Equip a Barrows Gloves" ↔ "Complete Recipe for Disaster"
     (B-gloves come FROM RFD — obvious to players, hard to parse)

  3. Efficient training combos:
     "Catch 100 Chinchompas" ↔ "Defeat 50 Maniacal Monkeys"
     (You catch chins TO use them at monkeys — item flow)

  4. Same NPC interaction:
     "Have Ned make rope" ↔ "Start Dragon Slayer"
     (Both involve talking to Ned in Draynor)

  5. Shared bank trip:
     "Smith a Rune Platebody" ↔ "Equip a Rune Platebody"
     (You smith it then equip it — same inventory moment)
    """)


if __name__ == "__main__":
    print(f"Analyzing {len(tasks)} tasks for edge type examples...\n")
    analyze_subset()
    analyze_chain()
    analyze_colocated()
    analyze_same_gear()
    analyze_same_skill()
    analyze_community()
