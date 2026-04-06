import json
import re

# Known real items that should NEVER be removed from search fields.
# Lowercase for comparison.
REAL_ITEMS = {
    # Access items
    'rope', 'knife', 'slash weapon', 'giant key', 'mossy key', 'brittle key',
    'frozen key', 'spade', 'falador shield', 'light source',
    # Combat gear
    'combat gear', 'food',
    # Potions
    'prayer potion(4)', 'super restore(4)', 'saradomin brew(4)',
    'antipoison(4)', 'antidote++(4)', 'super antipoison(4)',
    'anti-venom+(4)', 'super antifire potion(4)', 'antifire potion(4)',
    'sanfew serum(4)',
    # Specific gear
    'ring of recoil', 'spectral spirit shield', 'spirit shield',
    'anti-dragon shield', 'trident of the seas', "efaritay's aid",
    'facemask', 'warm clothing', 'mirror shield',
    # Slayer
    'slayer equipment',
    # Tools
    'axe', 'tinderbox', 'hammer', 'harpoon', 'bucket of water', 'pickaxe',
    # Crafting
    'needle', 'thread', 'chisel',
    # All items from existing hand-curated strategy (these are verified)
    'logs', 'oak logs', 'teak logs', 'mahogany logs', 'coins',
    'small fishing net', 'fishing rod', 'fishing bait', 'fly fishing rod',
    'feather', 'lobster pot', 'raw shrimp', 'raw lobster',
    'bones', 'rake', 'seed dibber',
    # Generic categories that are fine
    'any axe', 'any pickaxe',
    # Runes (for spell-based kills)
    'fire rune', 'air rune', 'mind rune', 'earth rune', 'water rune',
    'chaos rune', 'death rune', 'nature rune', 'law rune', 'body rune',
    # Boss-specific access items
    'dark totem',
}


def extract_npc_name(task_name):
    """Extract the NPC/target name from a combat task name.

    Returns lowercase NPC name, or None if not a combat task.
    """
    name_lower = task_name.lower().strip()

    # "Defeat a/an/the/each X" or "Defeat X"
    m = re.match(
        r'^defeat\s+(?:a\s+|an\s+|the\s+|each\s+|awakened\s+)?'
        r'(.+?)(?:\s+\d+\s+times?)?'
        r'(?:\s+without\s+.+)?$',
        name_lower,
    )
    if m:
        return m.group(1).strip()

    # "N X Kill(s)" e.g. "1 Sarachnis Kill", "50 Araxxor Kills"
    m = re.match(r'^(\d+)\s+(.+?)\s+kills?$', name_lower)
    if m:
        return m.group(2).strip()

    # "Kill a/an/the X ..." e.g. "Kill a Rat", "Kill a Spider by kicking it"
    m = re.match(
        r'^kill\s+(?:a\s+|an\s+|the\s+)?'
        r'(.+?)$',
        name_lower,
    )
    if m:
        return m.group(1).strip()

    return None


def is_npc_name_match(item_lower, npc_name):
    """Check if an item string is just the NPC name (not a real item).

    Handles exact match, substring containment in both directions,
    and count-prefixed variants like '50 Elves'.
    """
    # Exact match
    if item_lower == npc_name:
        return True

    # NPC name contained in item (e.g. "moss giant" in "moss giant 50 times")
    if npc_name in item_lower:
        return True

    # Item contained in NPC name (e.g. "elf" in "an elf in tirannwn")
    if item_lower in npc_name:
        return True

    # Count-prefixed: "50 Elves" where npc_name might be "elves in tirannwn"
    m = re.match(r'^(\d+)\s+(.+)$', item_lower)
    if m:
        stripped = m.group(2).strip()
        if stripped == npc_name or stripped in npc_name or npc_name in stripped:
            return True

    return False


def should_remove_item(item, npc_name):
    """Decide whether an item in the search field should be removed.

    Returns True if the item is just the NPC name / task description noise.
    Returns False if it's a legitimate item the player needs.
    """
    item_lower = item.lower().strip()
    if not item_lower:
        return True  # empty strings should be removed

    # Always keep known real items
    if item_lower in REAL_ITEMS:
        return False

    # Always keep items with dose notation like "Potion(4)"
    if '(' in item_lower:
        return False

    # Keep items starting with "any " only if they look like item categories
    # e.g. "any axe", "any pickaxe" — but NOT "Any God Wars Dungeon Boss 100 Times"
    if item_lower.startswith('any ') and len(item_lower.split()) <= 3:
        return False

    # Remove if it matches or contains the NPC name
    if is_npc_name_match(item_lower, npc_name):
        return True

    # Remove if it looks like a task description fragment
    # e.g. "three chickens in 6 seconds", "8 penguins within 5 seconds"
    if re.search(r'\d+\s+(?:seconds?|minutes?|times?)', item_lower):
        return True

    # Remove if it contains "in 6 seconds" style qualifiers
    if re.search(r'(?:within|in)\s+\d+', item_lower):
        return True

    # Remove if it contains "with the" (task description, not item)
    # e.g. "Kalphite with the Keris Partisan"
    if 'with the' in item_lower or 'with your' in item_lower:
        return True

    # Remove if it contains "by kicking" etc (task description)
    if re.search(r'\bby\s+\w+ing\b', item_lower):
        return True

    # Remove if it matches a location qualifier pattern (NPC + location)
    # e.g. "Greater Demon on Karamja", "Steel Dragon on Karamja"
    if re.search(r'\b(?:on|in|at|near)\s+[A-Z]', item):
        return True

    # Remove if it looks like "N boss" where N is a number
    # e.g. "50 Tormented Demons", "25 Echo Bosses"
    if re.match(r'^\d+\s+\w', item_lower):
        return True

    # Remove if it's a text sentence (contains periods but no parentheses)
    if '.' in item_lower and '(' not in item_lower:
        return True

    # Keep everything else (could be a legitimate item we don't know about)
    return False


def main():
    strategy_path = 'src/main/resources/com/ultimatetaskmaster/strategy.json'

    with open(strategy_path, 'r', encoding='utf-8') as f:
        strategy = json.load(f)

    changes = 0

    for sid, entry in strategy.items():
        name = entry.get('taskName', '')
        search = entry.get('search', '')
        if not search:
            continue

        items = [x.strip() for x in search.split(',')]
        if not items:
            continue

        # Check if this is a combat/kill task
        name_lower = name.lower()
        is_combat = (
            name_lower.startswith('defeat ')
            or name_lower.startswith('kill ')
            or re.match(r'^\d+\s+\w.*kill', name_lower)
        )

        if not is_combat:
            continue

        # Extract the NPC name from the task name
        npc_name = extract_npc_name(name)
        if not npc_name:
            continue

        # Filter out items that are just the NPC name or task description noise
        new_items = [item for item in items if not should_remove_item(item, npc_name)]

        new_search = ','.join(new_items)
        if new_search != search:
            print(f'  [{name}]')
            print(f'    BEFORE: {search}')
            print(f'    AFTER:  {new_search}')
            print()
            entry['search'] = new_search
            changes += 1

    print(f'Total changes: {changes}')

    with open(strategy_path, 'w', encoding='utf-8') as f:
        json.dump(strategy, f, indent=2, ensure_ascii=False)

    print(f'Strategy file updated successfully.')


if __name__ == '__main__':
    main()
