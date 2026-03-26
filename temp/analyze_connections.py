"""
Analyze task data to find natural connections/relationships between tasks.
This informs the Task Graph design for the League Optimizer plugin.
"""
import json
import re
from collections import defaultdict

tasks = json.load(open("raging_echoes_tasks.json", encoding="utf-8"))


def find_chain_tasks():
    """Find tasks that form natural progression chains (e.g., level 5 → 10 → 20)."""
    print("=" * 60)
    print("CHAIN TASKS (natural progression)")
    print("=" * 60)

    # Level-up chains
    level_tasks = [t for t in tasks if re.search(r"level \d+", t["name"].lower())]
    level_tasks.sort(key=lambda t: t["name"])
    print(f"\n  Level chain tasks: {len(level_tasks)}")
    for t in level_tasks[:20]:
        print(f"    [{t['tier']:6s}] {t['name']}")

    # Lap count chains
    lap_tasks = [t for t in tasks if re.search(r"\d+.*lap", t["name"].lower())]
    lap_tasks.sort(key=lambda t: t["name"])
    print(f"\n  Lap chain tasks: {len(lap_tasks)}")
    for t in lap_tasks[:15]:
        print(f"    [{t['tier']:6s}] {t['name']}")

    # Kill count chains
    kill_tasks = [t for t in tasks if re.search(r"(defeat|kill|slay).*\d+", t["name"].lower())]
    kill_tasks.sort(key=lambda t: t["name"])
    print(f"\n  Kill count chain tasks: {len(kill_tasks)}")
    for t in kill_tasks[:15]:
        print(f"    [{t['tier']:6s}] {t['name']}")


def find_colocation_tasks():
    """Find tasks that mention the same specific location."""
    print("\n" + "=" * 60)
    print("CO-LOCATION TASKS (same place)")
    print("=" * 60)

    # Extract location names from descriptions
    locations = [
        "draynor", "lumbridge", "varrock", "falador", "ardougne",
        "camelot", "seers", "catherby", "barbarian village", "edgeville",
        "al kharid", "wizards' tower", "grand exchange",
    ]

    for loc in locations:
        matching = [
            t for t in tasks
            if loc in t["description"].lower() or loc in t["name"].lower()
        ]
        if len(matching) >= 2:
            print(f"\n  {loc.title()} ({len(matching)} tasks):")
            for t in matching[:8]:
                print(f"    [{t['tier']:6s}] {t['name'][:55]}")
            if len(matching) > 8:
                print(f"    ... and {len(matching) - 8} more")


def find_subset_tasks():
    """Find tasks where one is a subset of another."""
    print("\n" + "=" * 60)
    print("SUBSET/SUPERSET TASKS (one contains another)")
    print("=" * 60)

    # Pattern: "Do X N times" where N varies
    count_pattern = re.compile(r"(\d+)")
    groups = defaultdict(list)

    for t in tasks:
        # Normalize: remove numbers to find "same task, different count"
        normalized = count_pattern.sub("N", t["name"].lower()).strip()
        groups[normalized].append(t)

    multi_groups = {k: v for k, v in groups.items() if len(v) > 1}
    print(f"\n  Found {len(multi_groups)} task groups with count variants:")
    for norm_name, group in sorted(multi_groups.items(), key=lambda x: -len(x[1]))[:15]:
        print(f"\n  Group ({len(group)} tasks):")
        for t in sorted(group, key=lambda x: x["points"]):
            print(f"    [{t['tier']:6s} {t['points']:3d}pts] {t['name'][:55]}")


def find_skill_clusters():
    """Find tasks that share the same skill requirement."""
    print("\n" + "=" * 60)
    print("SKILL CLUSTERS (same skill requirement)")
    print("=" * 60)

    skill_groups = defaultdict(list)
    for t in tasks:
        if t["requirements"]:
            for req in t["requirements"]:
                skill_groups[req["skill"]].append(t)

    for skill, group in sorted(skill_groups.items(), key=lambda x: -len(x[1]))[:10]:
        print(f"\n  {skill} ({len(group)} tasks)")


def find_same_area_easy_clusters():
    """Find areas with lots of easy tasks close together - the 'quick win' routes."""
    print("\n" + "=" * 60)
    print("QUICK WIN CLUSTERS (same area, easy/medium, no hard reqs)")
    print("=" * 60)

    for area in sorted(set(t["area"] for t in tasks)):
        easy = [
            t for t in tasks
            if t["area"] == area
            and t["tier"] in ("Easy", "Medium")
            and (not t["requirements"] or all(r["level"] <= 30 for r in t["requirements"]))
        ]
        if easy:
            total_pts = sum(t["points"] for t in easy)
            print(f"\n  {area}: {len(easy)} easy tasks, {total_pts} total points")
            for t in easy[:5]:
                print(f"    [{t['tier']:6s}] {t['name'][:55]}")
            if len(easy) > 5:
                print(f"    ... and {len(easy) - 5} more")


if __name__ == "__main__":
    print(f"Analyzing {len(tasks)} tasks for natural connections...\n")
    find_chain_tasks()
    find_colocation_tasks()
    find_subset_tasks()
    find_skill_clusters()
    find_same_area_easy_clusters()
