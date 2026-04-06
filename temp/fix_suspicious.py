#!/usr/bin/env python3
"""
fix_suspicious.py - Fix suspicious search values and fill empty searches in strategy.json

Part 1: Fix 18+ manually-identified suspicious items (bad search values)
Part 2: Apply rules to fill/clear ~395+ empty-search tasks where possible
"""

import json
import re
import os
from collections import Counter

# --- Config ---
STRATEGY_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..",
    "src", "main", "resources", "com", "ultimatetaskmaster", "strategy.json"
)

# === PART 1: Manual fixes for suspicious items ================================
# Keys are the SEARCH values that are wrong; values are the corrected search
MANUAL_FIXES = {
    # Equip tasks with compound names - these ARE the items, just clean up
    'Dragon Platelegs or a Dragon Plateskirt': 'Dragon platelegs,Dragon plateskirt',
    'Combination Battlestaff or Mystic Staff': 'Lava battlestaff,Mud battlestaff,Steam battlestaff,Smoke battlestaff,Mystic lava staff',
    'Dark Bow in Tirannwn': 'Dark bow',
    'Dragon 2-Handed Sword in the Wilderness': 'Dragon 2h sword',
    'Dragon Chainbody in the Kharidian Desert': 'Dragon chainbody',
    'Dragon Platebody or Dragon Kiteshield': 'Dragon platebody,Dragon kiteshield',
    'Elemental Battlestaff or Mystic Staff': 'Air battlestaff,Water battlestaff,Earth battlestaff,Fire battlestaff,Mystic air staff',

    # Agility obstacles - not items, clear them
    'Rough wall (Draynor Village Rooftop Course)': '',
    'Rough wall (Varrock Rooftop Course)': '',
    'Log balance (Gnome Stronghold Agility Course)': '',
    'Minecart (Lovakengj Minecart Network)': '',

    # Task descriptions leaked in - clear/fix
    '35 Mining All buckets of sand must be claimed in one go.': 'Bucket',
    'Items requested by the farmer to protect the crop': '',
    'Use a gold ring on the Wilderness Volcano': 'Gold ring',

    # Quest names - these are task names not items, clear the search
    'A Porcine of Interest': '',
    'Death on the Isle': '',
    'The Golem': '',
    'a Ribbiting Tale': '',
    'the Garden of Death': '',
}

# Also fix known bad description-leaked searches (match by task name)
DESCRIPTION_LEAK_FIXES = {
    'Read a prayer book near a lectern': {
        'old_contains': 'Great Brain Robbery',
        'new_search': 'Holy book,Unholy book,Book of balance,Book of war,Book of law,Book of darkness',
    },
    'Have the Taxidermist stuff something for you': {
        'old_contains': 'Big bass requires',
        'new_search': 'Big bass,Crawling hand,Cockatrice head,Basilisk head',
    },
    "Commune a Pharoah's Sceptre to the Necropolis": {
        'old_contains': '21 Thieving',
        'new_search': "Pharaoh's sceptre",
    },
    'Smuggle some Rum': {
        'old_contains': 'Garden of Tranquillity',
        'new_search': 'Karamjan rum',
    },
    'Hang a Painting of a Watermill': {
        'old_contains': '44 Construction',
        'new_search': 'Painting',
    },
    'Hit 150 with the Keris Partisan': {
        'old_contains': '65 Attack',
        'new_search': 'Keris partisan,Combat gear,Food',
    },
}


# === PART 2: Rule-based fills for empty-search tasks =========================

# OSRS skills list for detecting XP milestone tasks
SKILLS = [
    'Attack', 'Strength', 'Defence', 'Ranged', 'Prayer', 'Magic',
    'Runecraft', 'Construction', 'Hitpoints', 'Agility', 'Herblore',
    'Thieving', 'Crafting', 'Fletching', 'Slayer', 'Hunter',
    'Mining', 'Smithing', 'Fishing', 'Cooking', 'Firemaking',
    'Woodcutting', 'Farming',
]

# Build XP milestone regex: "Obtain X Million Skill XP"
_skills_re = '|'.join(SKILLS)
XP_MILESTONE_RE = re.compile(
    rf"^Obtain \d[\d,]* Million ({_skills_re}) XP$", re.IGNORECASE
)

# Patterns that mean "no items needed" -> leave search empty (confirm clear)
CLEAR_PATTERNS = [
    # Quest / achievement completions
    r"^Complete .+ quest",
    r"^Complete the .+ quest",
    # Level / XP milestones
    r"^Reach .+ level",
    r"^Reach level",
    r"^Gain .+ XP",
    r"^Gain .+ experience",
    r"^Achieve .+ total level",
    # Navigation / visiting
    r"^Enter ",
    r"^Visit ",
    r"^Check ",
    r"^Talk to ",
    r"^Speak to ",
    # Prayer (no items needed to pray at altar)
    r"^Pray at ",
    r"^Pray using ",
    # Collection log / discovery
    r"^Fill .+ collection log",
    r"^Discover ",
    r"^Learn ",
    r"^Unlock ",
    r"^Read ",
    # Favour
    r"^Have .+ (?:favour|favor)",
    r"^Reach .+ (?:favour|favor)",
    r"^Obtain .+ (?:favour|favor)",
    # Emotes
    r"^Dance ", r"^Emote ", r"^Perform ", r"^Cry ", r"^Clap ",
    r"^Wave ", r"^Bow ", r"^Laugh ", r"^Cheer ", r"^Jump for joy",
    r"^Think ", r"^Yawn ", r"^Headbang ", r"^Spin ", r"^Shrug ",
    r"^Panic ", r"^Jig ", r"^Salute ", r"^Raspberry ",
    # Pickpocket / steal (no items needed)
    r"^Pickpocket ",
    r"^Steal from ",
    r"^Steal a ",
    # Opening things (usually no item needed)
    r"^Open ",
    # Obtain XP milestones (no items)
    r"^Obtain \d[\d,]* Million .+ XP$",
    # Glory milestones (Colosseum)
    r"^Obtain [\d,]+ Glory$",
    # Bank usage
    r"^Use the Bank ",
    # "Obtain Every X" - too vague, clear
    r"^Obtain Every ",
    # "Obtain X Coins" - no items, just coins
    r"^Obtain [\d,]+ Coins",
]

# Patterns for combat tasks -> "Combat gear,Food"
COMBAT_PATTERNS = [
    r"^Defeat ",
    r"^Kill ",
    r"^Slay ",
    r"\d+ .+ Kill$",
    r"\d+ .+ Kills$",
    r"Combat Achievements?$",
]

# Equip pattern -> extract item name
EQUIP_RE = re.compile(
    r"^Equip (?:a |an |the |some |full |)(.+?)(?:\s+in\b.*|\s+at\b.*|\s+on\b.*|\s+while\b.*)?$",
    re.IGNORECASE,
)

# Map specific verb starts to items
VERB_ITEM_MAP = {
    "Chop": "Axe",
    "Cut down": "Axe",
    "Burn a": "Tinderbox",
    "Burn an": "Tinderbox",
    "Burn some": "Tinderbox",
    "Light a": "Tinderbox",
    "Mine a": "Pickaxe",
    "Mine an": "Pickaxe",
    "Mine some": "Pickaxe",
    "Smith a": "Hammer",
    "Smith an": "Hammer",
    "Smelt a": "Pickaxe",
    "Smelt an": "Pickaxe",
    "Fletch a": "Knife",
    "Fletch an": "Knife",
    "Fletch some": "Knife",
}


def load_strategy():
    """Load strategy.json and return the dict."""
    with open(STRATEGY_PATH, "r", encoding="utf-8") as f:
        return json.load(f)


def save_strategy(data):
    """Save strategy.json with consistent formatting."""
    with open(STRATEGY_PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    print(f"  Saved to {STRATEGY_PATH}")


# ---------------------------------------------------------------------------
# Part 1 logic
# ---------------------------------------------------------------------------

def apply_manual_fixes(data):
    """Fix the suspicious search values + description leaks."""
    fixes_applied = 0
    fix_details = []

    # Fix by matching search value exactly
    for struct_id, entry in data.items():
        search = entry.get("search", "")
        if search in MANUAL_FIXES:
            old = search
            new = MANUAL_FIXES[search]
            entry["search"] = new
            fixes_applied += 1
            fix_details.append(
                f"  [{struct_id}] {entry['taskName']}\n"
                f"    OLD: \"{old}\"\n"
                f"    NEW: \"{new}\""
            )

    # Fix description leaks by task name match
    for struct_id, entry in data.items():
        task_name = entry.get("taskName", "")
        search = entry.get("search", "")
        for name_pattern, fix_info in DESCRIPTION_LEAK_FIXES.items():
            if (name_pattern.lower() in task_name.lower()
                    and fix_info["old_contains"] in search):
                old = search
                entry["search"] = fix_info["new_search"]
                fixes_applied += 1
                fix_details.append(
                    f"  [{struct_id}] {task_name}\n"
                    f"    OLD: \"{old}\"\n"
                    f"    NEW: \"{fix_info['new_search']}\""
                )

    return fixes_applied, fix_details


# ---------------------------------------------------------------------------
# Part 2 helpers
# ---------------------------------------------------------------------------

def should_clear(task_name):
    """Check if this task clearly needs no items."""
    for pattern in CLEAR_PATTERNS:
        if re.search(pattern, task_name, re.IGNORECASE):
            return True
    return False


def is_combat_task(task_name):
    """Check if this is a combat task."""
    for pattern in COMBAT_PATTERNS:
        if re.search(pattern, task_name, re.IGNORECASE):
            return True
    return False


def extract_equip_item(task_name):
    """Try to extract item name from 'Equip a X' tasks."""
    m = EQUIP_RE.match(task_name)
    if m:
        item = m.group(1).strip()
        item = re.sub(r"\s+(from|during|after|before|without).*$", "", item, flags=re.IGNORECASE)
        return item
    return None


def get_verb_item(task_name):
    """Check if the task starts with a known verb and return appropriate item."""
    for verb, item in VERB_ITEM_MAP.items():
        if task_name.startswith(verb):
            return item
    return None


def fill_empty_searches(data):
    """Apply rules to fill empty-search tasks."""
    changes = []
    stats = Counter()

    for struct_id, entry in data.items():
        search = entry.get("search", "").strip()
        if search:
            continue  # Already has a search value

        task_name = entry.get("taskName", "")

        # Rule 1: Tasks that clearly need no items (quests, levels, XP, etc.)
        if should_clear(task_name):
            stats["clear_confirmed"] += 1
            continue

        # Rule 2: Combat tasks
        if is_combat_task(task_name):
            entry["search"] = "Combat gear,Food"
            changes.append(f"  [{struct_id}] {task_name} -> \"Combat gear,Food\" (combat)")
            stats["combat_filled"] += 1
            continue

        # Rule 3: Equip tasks - extract item name
        equip_item = extract_equip_item(task_name)
        if equip_item:
            entry["search"] = equip_item
            changes.append(f"  [{struct_id}] {task_name} -> \"{equip_item}\" (equip)")
            stats["equip_filled"] += 1
            continue

        # Rule 4: Verb-based items (chop->axe, mine->pickaxe, etc.)
        verb_item = get_verb_item(task_name)
        if verb_item:
            entry["search"] = verb_item
            changes.append(f"  [{struct_id}] {task_name} -> \"{verb_item}\" (verb)")
            stats["verb_filled"] += 1
            continue

        # Rule 5: "Bury X" tasks -> extract bone name
        bury_match = re.match(
            r"^Bury (?:a |an |some |)(.+?)(?:\s+at\b.*|\s+in\b.*)?$",
            task_name, re.IGNORECASE,
        )
        if bury_match:
            bone = bury_match.group(1).strip()
            entry["search"] = bone
            changes.append(f"  [{struct_id}] {task_name} -> \"{bone}\" (bury)")
            stats["bury_filled"] += 1
            continue

        # Rule 6: "Scatter X" tasks -> extract ash name
        scatter_match = re.match(
            r"^Scatter (?:some |)(.+?)(?:\s+at\b.*|\s+in\b.*)?$",
            task_name, re.IGNORECASE,
        )
        if scatter_match:
            ash = scatter_match.group(1).strip()
            entry["search"] = ash
            changes.append(f"  [{struct_id}] {task_name} -> \"{ash}\" (scatter)")
            stats["scatter_filled"] += 1
            continue

        # Rule 7: "Use X on Y" tasks (but NOT "Use the Bank")
        use_match = re.match(
            r"^Use (?:a |an |the |some |)(.+?)\s+on\s+",
            task_name, re.IGNORECASE,
        )
        if use_match:
            item = use_match.group(1).strip()
            # Skip vague non-item things
            if item.lower() not in ('bank',):
                entry["search"] = item
                changes.append(f"  [{struct_id}] {task_name} -> \"{item}\" (use-on)")
                stats["use_filled"] += 1
                continue

        # Rule 8: "Eat X" tasks
        eat_match = re.match(
            r"^Eat (?:a |an |some |)(.+?)(?:\s+in\b.*|\s+at\b.*|\s+while\b.*)?$",
            task_name, re.IGNORECASE,
        )
        if eat_match:
            food = eat_match.group(1).strip()
            entry["search"] = food
            changes.append(f"  [{struct_id}] {task_name} -> \"{food}\" (eat)")
            stats["eat_filled"] += 1
            continue

        # Rule 9: "Drink a X" tasks (but NOT "Drink from")
        drink_match = re.match(
            r"^Drink (?:a |an |some )(.+?)(?:\s+in\b.*|\s+at\b.*|\s+while\b.*)?$",
            task_name, re.IGNORECASE,
        )
        if drink_match:
            potion = drink_match.group(1).strip()
            entry["search"] = potion
            changes.append(f"  [{struct_id}] {task_name} -> \"{potion}\" (drink)")
            stats["drink_filled"] += 1
            continue

        # Rule 10: "Obtain a/an X" (specific item, not milestones)
        # Only match when there's a clear item name, not XP/Coins/Every/Glory
        obtain_match = re.match(
            r"^Obtain (?:a |an |the )(.+?)(?:\s+from\b.*|\s+in\b.*|\s+at\b.*|\s+while\b.*)?$",
            task_name, re.IGNORECASE,
        )
        if obtain_match:
            item = obtain_match.group(1).strip()
            # Skip if it looks like a milestone or vague task
            if not re.match(r"^\d", item) and "XP" not in item:
                entry["search"] = item
                changes.append(f"  [{struct_id}] {task_name} -> \"{item}\" (obtain)")
                stats["obtain_filled"] += 1
                continue

        # Rule 11: "Subdue X" (Wintertodt-like)
        if task_name.lower().startswith("subdue"):
            entry["search"] = "Axe,Tinderbox,Knife,Hammer,Warm clothing,Food"
            changes.append(f"  [{struct_id}] {task_name} -> \"Axe,Tinderbox,...\" (subdue)")
            stats["subdue_filled"] += 1
            continue

        # Rule 12: "Sacrifice X at/to Y" tasks
        sacrifice_match = re.match(
            r"^Sacrifice (?:a |an |the |some |)(.+?)(?:\s+at\b.*|\s+to\b.*)?$",
            task_name, re.IGNORECASE,
        )
        if sacrifice_match:
            item = sacrifice_match.group(1).strip()
            entry["search"] = item
            changes.append(f"  [{struct_id}] {task_name} -> \"{item}\" (sacrifice)")
            stats["sacrifice_filled"] += 1
            continue

        # Rule 13: "Offer X at/to Y" tasks (bones on altar)
        offer_match = re.match(
            r"^Offer (?:a |an |the |some |)(.+?)(?:\s+at\b.*|\s+to\b.*)?$",
            task_name, re.IGNORECASE,
        )
        if offer_match:
            item = offer_match.group(1).strip()
            entry["search"] = item
            changes.append(f"  [{struct_id}] {task_name} -> \"{item}\" (offer)")
            stats["offer_filled"] += 1
            continue

        # If nothing matched, leave empty
        stats["still_empty"] += 1

    return changes, stats


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    print("=" * 70)
    print("fix_suspicious.py - Fixing strategy.json search values")
    print("=" * 70)

    # Load
    data = load_strategy()
    total = len(data)
    empty_before = sum(1 for v in data.values() if not v.get("search", "").strip())
    non_empty_before = total - empty_before
    print(f"\nLoaded {total} tasks ({non_empty_before} with search, {empty_before} empty)")

    # -- Part 1: Manual fixes --
    print("\n" + "-" * 70)
    print("PART 1: Fixing suspicious search values")
    print("-" * 70)

    fixes_applied, fix_details = apply_manual_fixes(data)
    for detail in fix_details:
        print(detail)
    print(f"\n  [OK] Applied {fixes_applied} manual fixes")

    # Verify all manual fixes were consumed
    remaining_suspects = []
    for struct_id, entry in data.items():
        s = entry.get("search", "")
        if s in MANUAL_FIXES:
            remaining_suspects.append(f"  STILL BAD [{struct_id}] {entry['taskName']} -> \"{s}\"")
    if remaining_suspects:
        print(f"\n  [WARN] {len(remaining_suspects)} suspicious values NOT found/fixed:")
        for r in remaining_suspects:
            print(r)
    else:
        print("  All suspicious values resolved!")

    # -- Part 2: Fill empty searches --
    print("\n" + "-" * 70)
    print("PART 2: Filling empty-search tasks with rules")
    print("-" * 70)

    changes, stats = fill_empty_searches(data)

    # Print all changes
    for c in changes:
        print(c)

    print(f"\n  Rule stats:")
    for k, v in sorted(stats.items()):
        print(f"    {k}: {v}")
    total_fills = sum(v for k, v in stats.items() if k not in ('still_empty', 'clear_confirmed'))
    print(f"    ---")
    print(f"    TOTAL fills: {total_fills}")

    # -- Summary --
    empty_after = sum(1 for v in data.values() if not v.get("search", "").strip())
    non_empty_after = total - empty_after
    print("\n" + "-" * 70)
    print("SUMMARY")
    print("-" * 70)
    print(f"  Total tasks:          {total}")
    print(f"  Had search before:    {non_empty_before}")
    print(f"  Empty before:         {empty_before}")
    print(f"  Manual fixes (Pt 1):  {fixes_applied}")
    print(f"  Rule-based fills:     {total_fills}")
    print(f"  Confirmed clear:      {stats.get('clear_confirmed', 0)}")
    print(f"  Still empty after:    {empty_after}")
    print(f"  With search after:    {non_empty_after}")
    print(f"  Net new searches:     {non_empty_after - non_empty_before}")

    # Show some still-empty tasks for review
    still_empty_list = [
        (k, v["taskName"])
        for k, v in data.items()
        if not v.get("search", "").strip()
    ]
    if still_empty_list:
        print(f"\n  Sample of still-empty tasks ({len(still_empty_list)} total):")
        for sid, name in still_empty_list[:30]:
            print(f"    [{sid}] {name}")
        if len(still_empty_list) > 30:
            print(f"    ... and {len(still_empty_list) - 30} more")

    # Save
    print("\n  Saving...")
    save_strategy(data)
    print("\n  [DONE] Complete!")


if __name__ == "__main__":
    main()
