"""
Enrichment script for Ultimate Task Master.

Processes all 1589 tasks from tasks.json and generates:
  - strategy.json  (structId -> {taskName, search})  -- items/search terms per task
  - task_locations.json (structId -> [{x, y, count}]) -- location clusters per task

Uses pattern matching on task names + wikiNotes to infer items and locations.
Preserves existing strategy.json entries as a base, then fills gaps.

Usage:
    python temp/enrich_tasks.py

Run from the repo root (Ultimate-Task-Master/).
"""
import json
import os
import re
import sys
from collections import Counter

# ── Paths ────────────────────────────────────────────────────────────────────
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESOURCE_DIR = os.path.join(REPO_ROOT, "src", "main", "resources", "com", "ultimatetaskmaster")
TASKS_PATH = os.path.join(RESOURCE_DIR, "tasks.json")
EXISTING_STRATEGY_PATH = os.path.join(RESOURCE_DIR, "strategy.json")
OUTPUT_DIR = os.path.join(REPO_ROOT, "temp", "output")

OUTPUT_STRATEGY = os.path.join(OUTPUT_DIR, "strategy.json")
OUTPUT_LOCATIONS = os.path.join(OUTPUT_DIR, "task_locations.json")

# ── Constants ────────────────────────────────────────────────────────────────

# OSRS tile coordinates for well-known locations
# Format: {name: [(x, y), ...]}  -- multiple spots where applicable
LOCATIONS = {
    # Trees
    "normal_tree": [(3152, 3489), (3175, 3227), (2998, 3195)],
    "oak_tree": [(3204, 3242), (2435, 3523), (3015, 3200)],
    "willow_tree": [(3059, 3253), (2714, 3513), (3089, 3234)],
    "maple_tree": [(2728, 3501), (2417, 3415), (1761, 3558)],
    "yew_tree": [(3089, 3489), (2715, 3462), (2758, 3432), (1553, 3479)],
    "magic_tree": [(2702, 3397), (2364, 3187), (1618, 3238)],
    "teak_tree": [(2823, 3084), (2333, 3046)],
    "mahogany_tree": [(2822, 3078), (2717, 2710)],
    "redwood_tree": [(1570, 3487), (1590, 3487)],
    "arctic_pine": [(2345, 3807)],
    "blisterwood_tree": [(3636, 3363)],
    "juniper_tree": [(1525, 3593)],
    "sulliuscep": [(3684, 3752)],

    # Mining
    "copper_rock": [(3230, 3149), (3285, 3365), (2979, 3240)],
    "tin_rock": [(3226, 3147), (3282, 3367), (2984, 3237)],
    "iron_rock": [(3285, 3370), (2970, 3237), (1774, 3490)],
    "coal_rock": [(3037, 3150), (2584, 3478), (3149, 3544)],
    "silver_rock": [(3300, 3315), (2988, 3237)],
    "gold_rock": [(3297, 3316), (2978, 3235)],
    "mithril_rock": [(3051, 3149), (3291, 3360)],
    "adamantite_rock": [(3040, 3150), (3293, 3358)],
    "runite_rock": [(3059, 9764), (2946, 3282)],
    "gem_rock": [(2824, 2998)],
    "amethyst_rock": [(3020, 9700)],
    "dense_essence": [(1761, 3856)],
    "volcanic_mine": [(3815, 3810)],
    "blast_mine": [(1502, 3857)],
    "pure_ess_mine": [(2893, 4810)],

    # Fishing
    "fishing_shrimp": [(3246, 3157), (3086, 3230), (2837, 3432)],
    "fishing_herring": [(3246, 3157), (3086, 3230)],
    "fishing_trout": [(3103, 3426), (2835, 3432)],
    "fishing_salmon": [(3103, 3426), (2835, 3432)],
    "fishing_lobster": [(2924, 3178), (2838, 3432)],
    "fishing_swordfish": [(2924, 3178), (2838, 3432)],
    "fishing_shark": [(2838, 3432), (2604, 3419)],
    "fishing_monkfish": [(2340, 3701)],
    "fishing_karambwan": [(2899, 3119)],
    "fishing_karambwanji": [(2899, 3119)],
    "fishing_anglerfish": [(1826, 3774)],
    "fishing_minnow": [(2614, 3440)],
    "fishing_infernal_eel": [(2451, 5114)],
    "fishing_sacred_eel": [(2310, 3073)],
    "fishing_dark_crab": [(3149, 3624)],
    "fishing_barb": [(3106, 3434)],

    # Cooking
    "cooking_range": [(3211, 3216), (3270, 3180), (2818, 3442)],
    "rogues_den_fire": [(3043, 4973)],

    # Smithing/Smelting
    "furnace": [(3109, 3501), (3275, 3186), (2970, 3370)],
    "anvil": [(3188, 3426), (3276, 3187), (2971, 3369)],

    # Crafting
    "spinning_wheel": [(3210, 3214), (3209, 3416)],
    "crafting_table": [(2918, 3176), (3280, 3186)],
    "pottery_wheel": [(3085, 3410)],

    # Firemaking  (open spaces / GE / bonfire areas)
    "bonfire_area": [(3164, 3487), (3210, 3219)],

    # Thieving stalls
    "tea_stall": [(3268, 3412)],
    "cake_stall": [(2669, 3310), (3014, 3387)],
    "silk_stall": [(3282, 3419), (2663, 3316)],
    "fur_stall": [(3280, 3398)],
    "gem_stall": [(2824, 2998)],
    "fruit_stall": [(1800, 3608)],

    # Agility courses
    "draynor_agility": [(3103, 3279)],
    "al_kharid_agility": [(3273, 3199)],
    "varrock_agility": [(3221, 3415)],
    "canifis_agility": [(3505, 3489)],
    "falador_agility": [(3037, 3339)],
    "seers_agility": [(2729, 3488)],
    "pollnivneach_agility": [(3351, 2962)],
    "rellekka_agility": [(2625, 3678)],
    "ardougne_agility": [(2674, 3298)],
    "prif_agility": [(3254, 6109)],
    "penguin_agility": [(2636, 4040)],
    "barbarian_outpost_agility": [(2552, 3558)],
    "gnome_agility": [(2474, 3438)],
    "werewolf_agility": [(3538, 3452)],

    # Altars / Prayer
    "altar": [(3242, 3207), (2947, 3395)],

    # Bosses
    "obor": [(3095, 9832)],
    "bryophyta": [(3174, 9901)],
    "giant_mole": [(2996, 3375)],
    "kbd": [(3009, 3849)],
    "kalphite_queen": [(3228, 3108)],
    "sarachnis": [(1842, 9927)],
    "dagannoth_kings": [(2441, 10173)],
    "zulrah": [(2205, 3060)],
    "vorkath": [(2273, 4052)],
    "cerberus": [(1310, 1252)],
    "alchemical_hydra": [(1364, 10265)],
    "grotesque_guardians": [(3427, 3543)],
    "gauntlet": [(3032, 6124)],
    "corrupted_gauntlet": [(3032, 6124)],
    "skotizo": [(1693, 9886)],
    "nightmare": [(3808, 9754)],
    "nex": [(2904, 5204)],
    "corporeal_beast": [(2966, 4383)],
    "chambers_of_xeric": [(1233, 3558)],
    "theatre_of_blood": [(3656, 3218)],
    "tombs_of_amascut": [(3300, 2787)],
    "wintertodt": [(1630, 3962)],
    "tempoross": [(3135, 2840)],
    "zalcano": [(3298, 6035)],
    "mimic": [],
    "barrows": [(3565, 3316)],
    "araxxor": [(3720, 3261)],
    "hueycoatl": [(1636, 3017)],
    "amoxliatl": [(1653, 2970)],
    "hespori": [(1231, 3726)],
    "phantom_muspah": [(2982, 6400)],
    "duke_sucellus": [(3038, 6426)],
    "vardorvis": [(1135, 3090)],
    "the_leviathan": [(2870, 6413)],
    "the_whisperer": [(2722, 6425)],
    "sol_heredit": [(3165, 6450)],

    # Minigames
    "pest_control": [(2662, 2649)],
    "barbarian_assault": [(2533, 3569)],
    "guardians_of_the_rift": [(3614, 9482)],
    "mahogany_homes": [(3240, 3475)],
    "tithe_farm": [(1791, 3507)],
    "hallowed_sepulchre": [(3655, 3393)],
    "soul_wars": [(2204, 2860)],
    "castle_wars": [(2440, 3089)],
    "temple_trekking": [(3477, 3241)],
    "lms": [(3139, 3636)],
    "trouble_brewing": [(3817, 2843)],
    "tai_bwo_wannai": [(2794, 3068)],
    "mage_training_arena": [(3362, 3318)],
    "volcanic_mine_loc": [(3815, 3810)],

    # Misc locations
    "ge": [(3165, 3487)],
    "lumbridge": [(3222, 3219)],
    "varrock": [(3213, 3428)],
    "falador": [(2964, 3380)],
    "ardougne": [(2664, 3305)],
    "seers_village": [(2726, 3485)],
    "camelot": [(2757, 3477)],
    "yanille": [(2543, 3091)],
    "nardah": [(3315, 2797)],
    "kourend_castle": [(1643, 3674)],
    "prif": [(3239, 6072)],
    "edgeville": [(3093, 3502)],
    "canifis": [(3496, 3488)],
    "burgh_de_rott": [(3476, 3229)],
}


# ═══════════════════════════════════════════════════════════════════════════
# PATTERN MATCHERS
# Each returns (search_terms: str, location_key: str|None) or None if no match.
# ═══════════════════════════════════════════════════════════════════════════

def match_woodcutting(name, task):
    """Chop X Logs / Cut X Trees patterns."""
    n = name.lower()
    if not (n.startswith("chop") or (n.startswith("cut") and "tree" in n)):
        return None

    # Specific tree types BEFORE generic fallback to avoid false matches
    tree_map = {
        "arctic pine": ("Arctic pine logs", "arctic_pine"),
        "blisterwood": ("Blisterwood logs", "blisterwood_tree"),
        "sulliuscep": ("Sulliuscep cap", "sulliuscep"),
        "redwood": ("Redwood logs", "redwood_tree"),
        "mahogany": ("Mahogany logs", "mahogany_tree"),
        "juniper": ("Juniper logs", "juniper_tree"),
        "willow": ("Willow logs", "willow_tree"),
        "maple": ("Maple logs", "maple_tree"),
        "magic": ("Magic logs", "magic_tree"),
        "teak": ("Teak logs", "teak_tree"),
        "yew": ("Yew logs", "yew_tree"),
        "oak": ("Oak logs", "oak_tree"),
        "normal": ("Logs", "normal_tree"),
    }

    for key, (log_name, loc_key) in tree_map.items():
        if key in n:
            axe = _extract_axe(name) or "Any axe"
            return f"{axe},{log_name}", loc_key

    # Generic "Chop Some Logs"
    if "log" in n:
        axe = _extract_axe(name) or "Any axe"
        return f"{axe},Logs", "normal_tree"

    # "Chop some Rising Roots" (Forestry)
    if "rising roots" in n:
        return "Any axe", None

    # "Chop a log from a potato tree"
    if "potato tree" in n:
        return "Any axe", None

    return "Any axe", None


def _extract_axe(name):
    """Extract specific axe type from task name if mentioned."""
    n = name.lower()
    for axe in ["bronze axe", "iron axe", "steel axe", "mithril axe",
                 "adamant axe", "rune axe", "dragon axe", "crystal axe",
                 "infernal axe", "3rd age axe"]:
        if axe in n:
            return axe.title()
    return None


def match_firemaking(name, task):
    """Burn X Logs patterns."""
    n = name.lower()
    if not n.startswith("burn"):
        return None

    log_map = {
        "normal": "Logs",
        "oak": "Oak logs",
        "willow": "Willow logs",
        "maple": "Maple logs",
        "yew": "Yew logs",
        "magic": "Magic logs",
        "redwood": "Redwood logs",
        "coloured": "Logs",
    }

    for key, log_name in log_map.items():
        if key in n:
            return f"{log_name},Tinderbox", "bonfire_area"

    # "Burn Some Food"
    if "food" in n:
        return "Raw food", "cooking_range"

    # generic
    if "log" in n:
        return "Logs,Tinderbox", "bonfire_area"

    return "Tinderbox", "bonfire_area"


def match_cooking(name, task):
    """Cook X / Cook a X patterns."""
    n = name.lower()
    if not n.startswith("cook"):
        return None

    food_map = {
        "shrimp": ("Raw shrimps", "fishing_shrimp"),
        "herring": ("Raw herring", "fishing_herring"),
        "trout": ("Raw trout", "fishing_trout"),
        "salmon": ("Raw salmon", "fishing_salmon"),
        "lobster": ("Raw lobster", "fishing_lobster"),
        "swordfish": ("Raw swordfish", "fishing_swordfish"),
        "shark": ("Raw shark", "fishing_shark"),
        "monkfish": ("Raw monkfish", "fishing_monkfish"),
        "karambwan": ("Raw karambwan", "fishing_karambwan"),
        "anglerfish": ("Raw anglerfish", "fishing_anglerfish"),
        "dark crab": ("Raw dark crab", "fishing_dark_crab"),
        "tuna": ("Raw tuna", "fishing_swordfish"),
        "bass": ("Raw bass", "fishing_lobster"),
        "rabbit": ("Raw rabbit", None),
        "meat": ("Raw meat", None),
        "pie": ("Pie dish,Pastry dough", None),
        "pizza": ("Pizza base,Tomato,Cheese", None),
        "cake": ("Cake tin,Pot of flour,Egg,Bucket of milk", None),
        "bread": ("Pot of flour,Bucket of water", None),
        "stew": ("Bowl,Cooked meat,Potato", None),
        "curry": ("Bowl,Cooked meat,Spice", None),
        "wine": ("Jug of water,Grapes", None),
    }

    for key, (items, loc) in food_map.items():
        if key in n:
            return items, "cooking_range"

    # Generic cook
    return "Raw food", "cooking_range"


def match_mining(name, task):
    """Mine X Ore / Mine X patterns."""
    n = name.lower()
    if not n.startswith("mine"):
        return None

    ore_map = {
        "copper": ("Copper ore", "copper_rock"),
        "tin": ("Tin ore", "tin_rock"),
        "iron": ("Iron ore", "iron_rock"),
        "coal": ("Coal", "coal_rock"),
        "silver": ("Silver ore", "silver_rock"),
        "gold": ("Gold ore", "gold_rock"),
        "mithril": ("Mithril ore", "mithril_rock"),
        "adamant": ("Adamantite ore", "adamantite_rock"),
        "runite": ("Runite ore", "runite_rock"),
        "amethyst": ("Amethyst", "amethyst_rock"),
        "gem": ("Gem", "gem_rock"),
        "essence": ("Rune essence", "pure_ess_mine"),
        "dense": ("Dense essence block", "dense_essence"),
        "volcanic": ("Volcanic mine", "volcanic_mine"),
        "blast": ("Dynamite", "blast_mine"),
        "barronite": ("Barronite deposit", None),
        "daeyalt": ("Daeyalt essence", None),
        "calcified": ("Calcified deposit", None),
    }

    pickaxe = _extract_pickaxe(name) or "Any pickaxe"

    for key, (ore_name, loc_key) in ore_map.items():
        if key in n:
            return f"{pickaxe},{ore_name}", loc_key

    # Generic mine
    if "ore" in n or "some" in n:
        return pickaxe, None

    return pickaxe, None


def _extract_pickaxe(name):
    """Extract specific pickaxe from task name if mentioned."""
    n = name.lower()
    for pick in ["bronze pickaxe", "iron pickaxe", "steel pickaxe", "mithril pickaxe",
                  "adamant pickaxe", "rune pickaxe", "dragon pickaxe", "crystal pickaxe",
                  "infernal pickaxe", "3rd age pickaxe"]:
        if pick in n:
            return pick.title()
    return None


def match_fishing(name, task):
    """Catch X / Catch a X patterns (fishing)."""
    n = name.lower()
    if not n.startswith("catch"):
        return None

    # Hunter catches come first to avoid false fishing matches
    hunter_creatures = [
        "impling", "chinchompa", "kebbit", "swift", "twitch", "falcon",
        "salamander", "butterfly", "impet", "swamp lizard",
        "red lizard", "black lizard", "orange lizard",
        "horned graahk", "spined larupia", "black warlock",
        "snowy knight", "sapphire glacialis", "ruby harvest",
        "ferret", "deadfall", "pitfall",
    ]
    for h in hunter_creatures:
        if h in n:
            return None  # let match_hunter handle it

    fish_map = {
        "shrimp": ("Small fishing net", "fishing_shrimp"),
        "anchovy": ("Small fishing net", "fishing_shrimp"),
        "herring": ("Fishing rod,Fishing bait", "fishing_herring"),
        "sardine": ("Fishing rod,Fishing bait", "fishing_herring"),
        "trout": ("Fly fishing rod,Feather", "fishing_trout"),
        "salmon": ("Fly fishing rod,Feather", "fishing_salmon"),
        "tuna": ("Harpoon", "fishing_swordfish"),
        "lobster": ("Lobster pot", "fishing_lobster"),
        "swordfish": ("Harpoon", "fishing_swordfish"),
        "shark": ("Harpoon", "fishing_shark"),
        "monkfish": ("Small fishing net", "fishing_monkfish"),
        "karambwan": ("Karambwan vessel,Raw karambwanji", "fishing_karambwan"),
        "karambwanji": ("Small fishing net", "fishing_karambwanji"),
        "anglerfish": ("Fishing rod,Sandworms", "fishing_anglerfish"),
        "minnow": ("Small fishing net", "fishing_minnow"),
        "infernal eel": ("Oily fishing rod,Fishing bait", "fishing_infernal_eel"),
        "sacred eel": ("Fishing rod,Fishing bait", "fishing_sacred_eel"),
        "dark crab": ("Dark fishing bait,Lobster pot", "fishing_dark_crab"),
        "bass": ("Big fishing net", "fishing_lobster"),
        "mackerel": ("Big fishing net", "fishing_lobster"),
        "pike": ("Fishing rod,Fishing bait", "fishing_herring"),
        "rainbow fish": ("Fly fishing rod,Stripy feather", "fishing_trout"),
        "leaping": ("Barbarian rod,Fishing bait,Feather", "fishing_barb"),
        "barb-tail": ("Barbarian rod,Fishing bait,Feather", "fishing_barb"),
        "casket": ("Big fishing net", "fishing_lobster"),
        "big harpoonfish": ("Fishing rod,Fishing bait", None),
    }

    for key, (items, loc) in fish_map.items():
        if key in n:
            return items, loc

    # Generic catch that seems fish-related
    if task.get("category") == "Skill" and any(kw in n for kw in ["fish", "catch a ", "catch an "]):
        return "Fishing rod", None

    return None


def match_hunter(name, task):
    """Catch X patterns for Hunter skill."""
    n = name.lower()
    if not n.startswith("catch"):
        return None

    hunter_map = {
        "baby impling": ("Butterfly net,Impling jar", None),
        "young impling": ("Butterfly net,Impling jar", None),
        "gourmet impling": ("Butterfly net,Impling jar", None),
        "earth impling": ("Butterfly net,Impling jar", None),
        "essence impling": ("Butterfly net,Impling jar", None),
        "eclectic impling": ("Butterfly net,Impling jar", None),
        "nature impling": ("Butterfly net,Impling jar", None),
        "magpie impling": ("Butterfly net,Impling jar", None),
        "ninja impling": ("Butterfly net,Impling jar", None),
        "dragon impling": ("Butterfly net,Impling jar", None),
        "lucky impling": ("Butterfly net,Impling jar", None),
        "crystal impling": ("Butterfly net,Impling jar", None),
        "impling": ("Butterfly net,Impling jar", None),
        "crimson swift": ("Bird snare", None),
        "golden warbler": ("Bird snare", None),
        "copper longtail": ("Bird snare", None),
        "cerulean twitch": ("Bird snare", None),
        "tropical wagtail": ("Bird snare", None),
        "chinchompa": ("Box trap", None),
        "red chinchompa": ("Box trap", None),
        "black chinchompa": ("Box trap", None),
        "polar kebbit": ("Noose wand", None),
        "common kebbit": ("Noose wand", None),
        "feldip weasel": ("Noose wand", None),
        "desert devil": ("Noose wand", None),
        "razor-backed kebbit": ("Noose wand", None),
        "wild kebbit": ("Deadfall trap", None),
        "barb-tailed kebbit": ("Deadfall trap", None),
        "prickly kebbit": ("Deadfall trap", None),
        "sabre-toothed kebbit": ("Deadfall trap", None),
        "ferret": ("Rabbit snare", None),
        "larupia": ("Teasing stick,Logs", None),
        "graahk": ("Teasing stick,Logs", None),
        "kyatt": ("Teasing stick,Logs", None),
        "swamp lizard": ("Rope,Small fishing net", None),
        "red salamander": ("Rope,Small fishing net", None),
        "black salamander": ("Rope,Small fishing net", None),
        "orange salamander": ("Rope,Small fishing net", None),
        "butterfly": ("Butterfly net,Butterfly jar", None),
        "black warlock": ("Butterfly net,Butterfly jar", None),
        "snowy knight": ("Butterfly net,Butterfly jar", None),
        "sapphire glacialis": ("Butterfly net,Butterfly jar", None),
        "ruby harvest": ("Butterfly net,Butterfly jar", None),
        "sunlight moth": ("Butterfly net,Butterfly jar", None),
        "moonlight moth": ("Butterfly net,Butterfly jar", None),
        "herbiboar": ("", None),
    }

    for key, (items, loc) in hunter_map.items():
        if key in n:
            return items if items else None, loc
    return None


def match_smithing(name, task):
    """Smith X / Smelt X patterns."""
    n = name.lower()

    if n.startswith("smelt"):
        bar_map = {
            "bronze": "Copper ore,Tin ore",
            "iron": "Iron ore",
            "steel": "Iron ore,Coal",
            "silver": "Silver ore",
            "gold": "Gold ore",
            "mithril": "Mithril ore,Coal",
            "adamant": "Adamantite ore,Coal",
            "rune": "Runite ore,Coal",
        }
        for key, ores in bar_map.items():
            if key in n:
                return ores, "furnace"
        return "Ore", "furnace"

    if not n.startswith("smith"):
        return None

    bar_map = {
        "bronze": "Bronze bar,Hammer",
        "iron": "Iron bar,Hammer",
        "steel": "Steel bar,Hammer",
        "mithril": "Mithril bar,Hammer",
        "adamant": "Adamantite bar,Hammer",
        "rune": "Runite bar,Hammer",
        "gold": "Gold bar,Hammer",
    }
    for key, items in bar_map.items():
        if key in n:
            return items, "anvil"

    return "Metal bar,Hammer", "anvil"  # location key, not item


def match_crafting(name, task):
    """Craft X patterns."""
    n = name.lower()
    if not n.startswith("craft"):
        return None

    craft_map = {
        "leather": ("Leather,Needle,Thread", "crafting_table"),
        "hard leather": ("Hard leather,Needle,Thread", "crafting_table"),
        "dragonhide": ("Dragon leather,Needle,Thread", "crafting_table"),
        "d'hide": ("Dragon leather,Needle,Thread", "crafting_table"),
        "snakeskin": ("Snakeskin,Needle,Thread", "crafting_table"),
        "air rune": ("Rune essence,Air talisman", None),
        "water rune": ("Rune essence,Water talisman", None),
        "earth rune": ("Rune essence,Earth talisman", None),
        "fire rune": ("Rune essence,Fire talisman", None),
        "body rune": ("Rune essence,Body talisman", None),
        "mind rune": ("Rune essence,Mind talisman", None),
        "cosmic rune": ("Pure essence,Cosmic talisman", None),
        "nature rune": ("Pure essence,Nature talisman", None),
        "law rune": ("Pure essence,Law talisman", None),
        "death rune": ("Pure essence,Death talisman", None),
        "blood rune": ("Dense essence block,Chisel", "dense_essence"),
        "soul rune": ("Dense essence block", "dense_essence"),
        "wrath rune": ("Pure essence,Wrath talisman", None),
        "gold ring": ("Gold bar,Ring mould", "furnace"),
        "gold necklace": ("Gold bar,Necklace mould", "furnace"),
        "gold bracelet": ("Gold bar,Bracelet mould", "furnace"),
        "gold amulet": ("Gold bar,Amulet mould", "furnace"),
        "sapphire": ("Uncut sapphire,Chisel", None),
        "emerald": ("Uncut emerald,Chisel", None),
        "ruby": ("Uncut ruby,Chisel", None),
        "diamond": ("Uncut diamond,Chisel", None),
        "dragonstone": ("Uncut dragonstone,Chisel", None),
        "onyx": ("Uncut onyx,Chisel", None),
        "zenyte": ("Uncut zenyte,Chisel", None),
        "bow string": ("Flax", "spinning_wheel"),
        "bowstring": ("Flax", "spinning_wheel"),
        "ball of wool": ("Wool", "spinning_wheel"),
        "pottery": ("Soft clay", "pottery_wheel"),
        "pot": ("Soft clay", "pottery_wheel"),
        "pie dish": ("Soft clay", "pottery_wheel"),
        "bowl": ("Soft clay", "pottery_wheel"),
        "vial": ("Molten glass,Glassblowing pipe", None),
        "glass": ("Molten glass,Glassblowing pipe", None),
        "tiara": ("Silver bar,Tiara mould", "furnace"),
    }

    for key, (items, loc) in craft_map.items():
        if key in n:
            return items, loc

    return "Crafting materials", None


def match_fletching(name, task):
    """Fletch X patterns."""
    n = name.lower()
    if not n.startswith("fletch"):
        return None

    fletch_map = {
        "arrow shaft": ("Knife,Logs", None),
        "oak shortbow": ("Knife,Oak logs", None),
        "oak longbow": ("Knife,Oak logs", None),
        "willow shortbow": ("Knife,Willow logs", None),
        "willow longbow": ("Knife,Willow logs", None),
        "maple shortbow": ("Knife,Maple logs", None),
        "maple longbow": ("Knife,Maple logs", None),
        "yew shortbow": ("Knife,Yew logs", None),
        "yew longbow": ("Knife,Yew logs", None),
        "magic shortbow": ("Knife,Magic logs", None),
        "magic longbow": ("Knife,Magic logs", None),
        "iron arrow": ("Iron arrowheads,Headless arrow", None),
        "steel arrow": ("Steel arrowheads,Headless arrow", None),
        "mithril arrow": ("Mithril arrowheads,Headless arrow", None),
        "adamant arrow": ("Adamant arrowheads,Headless arrow", None),
        "rune arrow": ("Rune arrowheads,Headless arrow", None),
        "dragon arrow": ("Dragon arrowheads,Headless arrow", None),
        "broad arrow": ("Broad arrowheads,Headless arrow", None),
        "broad bolt": ("Broad bolt tips,Unfinished broad bolts", None),
        "bolt": ("Bolts", None),
        "dart": ("Dart tips,Feather", None),
        "javelin": ("Javelin shaft", None),
        "redwood shield": ("Knife,Redwood logs", None),
    }

    for key, (items, loc) in fletch_map.items():
        if key in n:
            return items, loc

    return "Knife", None


def match_thieving(name, task):
    """Steal X / Pickpocket X patterns."""
    n = name.lower()

    if n.startswith("pickpocket"):
        target = name[len("Pickpocket "):].strip()
        npc_map = {
            "man": (None, "lumbridge"),
            "woman": (None, "lumbridge"),
            "farmer": (None, "lumbridge"),
            "warrior": (None, "varrock"),
            "rogue": (None, "edgeville"),
            "master farmer": (None, "lumbridge"),
            "guard": (None, "falador"),
            "knight": (None, "ardougne"),
            "paladin": (None, "ardougne"),
            "hero": (None, "ardougne"),
            "elf": (None, "prif"),
            "gnome": (None, "gnome_agility"),
            "citizen": (None, "varrock"),
            "vyre": (None, "canifis"),
            "wealthy citizen": (None, None),
        }
        tl = target.lower()
        for key, (items, loc) in npc_map.items():
            if key in tl:
                return items or "", loc
        return "", None

    if n.startswith("steal"):
        stall_map = {
            "chocolate": (None, "cake_stall"),
            "cake": (None, "cake_stall"),
            "tea stall": (None, "tea_stall"),
            "silk": (None, "silk_stall"),
            "fur": (None, "fur_stall"),
            "gem": (None, "gem_stall"),
            "fruit": (None, "fruit_stall"),
            "golovanova": (None, "fruit_stall"),
            "artefact": (None, None),
        }
        for key, (items, loc) in stall_map.items():
            if key in n:
                return items or "", loc
        return "", None

    return None


def match_combat(name, task):
    """Defeat X / Kill X / X Kill(s) patterns."""
    n = name.lower()
    monster = None

    if n.startswith("defeat "):
        monster = name[len("Defeat "):].strip()
    elif n.startswith("kill "):
        monster = name[len("Kill "):].strip()
    elif re.match(r'^\d+\s+.+\s+kills?$', n, re.IGNORECASE):
        # "50 Sarachnis Kills" -> extract monster
        m = re.match(r'^\d+\s+(.+?)\s+kills?$', n, re.IGNORECASE)
        if m:
            monster = m.group(1).strip()
    elif " kill" in n.lower():
        # "1 Wintertodt Kill"
        m = re.match(r'^\d+\s+(.+?)\s+kills?$', n, re.IGNORECASE)
        if m:
            monster = m.group(1).strip()

    if not monster:
        return None

    # Clean up location qualifiers
    monster_clean = monster
    for suffix in [" in the Wilderness", " in Tirannwn", " in Kandarin",
                   " in the Fremennik Province", " in Morytania", " in Asgarnia",
                   " in Karamja", " in Kourend", " in Varlamore",
                   " in the Wizards' Tower", " in Misthalin",
                   " in the Feldip Hills", " in Shayzien", " in Prifddinas",
                   " in the Barbarian Village", " in Varlamore underground",
                   " in Tirannwn by Fishing"]:
        if monster_clean.lower().endswith(suffix.lower()):
            monster_clean = monster_clean[:len(monster_clean)-len(suffix)].strip()
            break

    # Boss mapping
    boss_map = {
        "obor": ("Giant key", "obor"),
        "bryophyta": ("Mossy key", "bryophyta"),
        "giant mole": ("Spade,Light source", "giant_mole"),
        "king black dragon": ("", "kbd"),
        "kbd": ("", "kbd"),
        "kalphite queen": ("Rope", "kalphite_queen"),
        "sarachnis": ("Knife", "sarachnis"),
        "dagannoth": ("", "dagannoth_kings"),
        "zulrah": ("", "zulrah"),
        "vorkath": ("", "vorkath"),
        "cerberus": ("", "cerberus"),
        "alchemical hydra": ("", "alchemical_hydra"),
        "grotesque guardians": ("Brittle key", "grotesque_guardians"),
        "gauntlet": ("", "gauntlet"),
        "corrupted gauntlet": ("", "corrupted_gauntlet"),
        "skotizo": ("Dark totem", "skotizo"),
        "nightmare": ("", "nightmare"),
        "nex": ("Frozen key", "nex"),
        "corporeal beast": ("", "corporeal_beast"),
        "wintertodt": ("Knife,Tinderbox,Axe,Hammer", "wintertodt"),
        "tempoross": ("Harpoon,Rope,Bucket of water,Hammer", "tempoross"),
        "zalcano": ("Pickaxe", "zalcano"),
        "mimic": ("Mimic casket", "mimic"),
        "barrows": ("Spade", "barrows"),
        "araxxor": ("", "araxxor"),
        "hueycoatl": ("", "hueycoatl"),
        "amoxliatl": ("", "amoxliatl"),
        "hespori": ("", "hespori"),
        "phantom muspah": ("", "phantom_muspah"),
        "duke sucellus": ("", "duke_sucellus"),
        "vardorvis": ("", "vardorvis"),
        "the leviathan": ("", "the_leviathan"),
        "the whisperer": ("", "the_whisperer"),
        "sol heredit": ("", "sol_heredit"),
        "chambers of xeric": ("", "chambers_of_xeric"),
        "theatre of blood": ("", "theatre_of_blood"),
        "tombs of amascut": ("", "tombs_of_amascut"),
    }

    mc_lower = monster_clean.lower()
    # Remove leading article
    for article in ["a ", "an ", "the "]:
        if mc_lower.startswith(article):
            mc_lower = mc_lower[len(article):]
            monster_clean = monster_clean[len(article):]
            break

    for boss_key, (items, loc_key) in boss_map.items():
        if boss_key in mc_lower:
            search = f"{monster_clean}"
            if items:
                search += f",{items}"
            return search, loc_key

    # Regular monster - search by name
    return monster_clean, None


def match_equip(name, task):
    """Equip X patterns - the item itself is what's needed."""
    n = name.lower()
    if not n.startswith("equip"):
        return None

    item = name[len("Equip "):].strip()
    # Remove leading articles
    for article in ["a ", "an ", "the ", "some ", "full "]:
        if item.lower().startswith(article):
            item = item[len(article):]
            break

    return item, None


def match_quest(name, task):
    """Complete X quest patterns."""
    n = name.lower()
    if not n.startswith("complete"):
        return None

    rest = name[len("Complete "):].strip()

    # Agility courses
    if "agility" in n.lower():
        course_map = {
            "draynor": "draynor_agility",
            "al kharid": "al_kharid_agility",
            "varrock": "varrock_agility",
            "canifis": "canifis_agility",
            "falador": "falador_agility",
            "seers": "seers_agility",
            "pollnivneach": "pollnivneach_agility",
            "rellekka": "rellekka_agility",
            "ardougne": "ardougne_agility",
            "prif": "prif_agility",
            "penguin": "penguin_agility",
            "barbarian outpost": "barbarian_outpost_agility",
            "gnome": "gnome_agility",
            "werewolf": "werewolf_agility",
            "rooftop": None,
        }
        for key, loc in course_map.items():
            if key in n:
                return "", loc
        return "", None

    # Diary tasks
    if "diary" in n.lower():
        return "", None

    # Farming contracts
    if "farming contract" in n.lower():
        return "Seed dibber,Rake,Spade", None

    # Quest completions
    if "quest" not in n.lower() and task.get("category") == "Quest":
        return rest, None

    # Temple trek
    if "temple trek" in n.lower():
        return "", "temple_trekking"

    # Volcanic mine
    if "volcanic mine" in n.lower():
        return "Any pickaxe", "volcanic_mine_loc"

    return "", None


def match_reach(name, task):
    """Reach level X / Reach total level X patterns."""
    n = name.lower()
    if not (n.startswith("reach") or n.startswith("achieve") or n.startswith("gain")):
        return None
    # These are level milestones - no items, no location
    return "", None


def match_cast(name, task):
    """Cast X spell patterns."""
    n = name.lower()
    if not n.startswith("cast"):
        return None

    spell_runes = {
        "home teleport": ("", None),
        "kourend castle teleport": ("Law rune,Fire rune,Water rune,Soul rune", "kourend_castle"),
        "moonclan teleport": ("Law rune,Earth rune,Astral rune", None),
        "spellbook swap": ("Cosmic rune,Astral rune,Law rune", None),
        "high level alchemy": ("Nature rune,Fire rune", None),
        "blast": ("Death rune,Fire rune", None),
        "wave": ("Blood rune,Fire rune", None),
        "surge": ("Wrath rune,Fire rune", None),
        "ice rush": ("Chaos rune,Death rune,Water rune", None),
        "ice burst": ("Chaos rune,Death rune,Water rune", None),
        "ice blitz": ("Blood rune,Death rune,Water rune", None),
        "ice barrage": ("Blood rune,Death rune,Water rune", None),
        "saradomin strike": ("Air rune,Fire rune,Blood rune,Staff of saradomin", None),
        "claws of guthix": ("Air rune,Fire rune,Blood rune,Guthix staff", None),
        "flames of zamorak": ("Air rune,Fire rune,Blood rune,Staff of zamorak", None),
        "degrime": ("Astral rune,Nature rune", None),
    }

    for key, (runes, loc) in spell_runes.items():
        if key in n:
            return runes, loc

    return "Runes", None


def match_enter(name, task):
    """Enter X patterns."""
    n = name.lower()
    if not n.startswith("enter"):
        return None

    location_map = {
        "taverley dungeon": ("", None),
        "kalphite": ("Rope", "kalphite_queen"),
        "wilderness": ("", None),
        "abyss": ("", None),
        "godwars": ("Rope", None),
        "god wars": ("Rope", None),
        "prifddinas": ("", "prif"),
        "gauntlet": ("", "gauntlet"),
        "cox": ("", "chambers_of_xeric"),
        "chambers": ("", "chambers_of_xeric"),
        "theatre": ("", "theatre_of_blood"),
        "tombs": ("", "tombs_of_amascut"),
    }

    for key, (items, loc) in location_map.items():
        if key in n:
            return items, loc

    return "", None


def match_open(name, task):
    """Open X patterns."""
    n = name.lower()
    if not n.startswith("open"):
        return None

    open_map = {
        "grubby chest": ("Grubby key", None),
        "crystal chest": ("Crystal key", None),
        "barrows chest": ("Spade", "barrows"),
        "larran": ("Larran's key", None),
        "brimstone": ("Brimstone key", None),
        "muddy": ("Muddy key", None),
        "ecumenical": ("Ecumenical key", None),
    }

    for key, (items, loc) in open_map.items():
        if key in n:
            return items, loc

    return "", None


def match_use(name, task):
    """Use X patterns."""
    n = name.lower()
    if not n.startswith("use"):
        return None

    use_map = {
        "special attack": ("", None),
        "bank": ("", None),
        "fairy ring": ("Dramen staff", None),
        "spirit tree": ("", None),
        "charter": ("Coins", None),
        "cannon": ("Dwarf multicannon,Cannonball", None),
        "glory": ("Amulet of glory", None),
        "obelisk": ("", None),
    }

    for key, (items, loc) in use_map.items():
        if key in n:
            return items, loc

    return "", None


def match_create(name, task):
    """Create X / Make X patterns (Herblore, etc.)."""
    n = name.lower()
    if not (n.startswith("create") or n.startswith("make")):
        return None

    potion_map = {
        "antipoison": ("Marrentill,Unicorn horn dust,Vial of water", None),
        "compost potion": ("Volcanic ash,Harralander,Vial of water", None),
        "super attack": ("Irit leaf,Eye of newt,Vial of water", None),
        "super strength": ("Kwuarm,Limpwurt root,Vial of water", None),
        "super defence": ("Cadantine,White berries,Vial of water", None),
        "super restore": ("Snapdragon,Red spiders' eggs,Vial of water", None),
        "prayer potion": ("Ranarr weed,Snape grass,Vial of water", None),
        "ranging potion": ("Dwarf weed,Wine of zamorak,Vial of water", None),
        "magic potion": ("Lantadyme,Potato cactus,Vial of water", None),
        "saradomin brew": ("Toadflax,Crushed nest,Vial of water", None),
        "sanfew serum": ("Snapdragon,Unicorn horn dust,Super restore", None),
        "antidote": ("Toadflax,Coconut milk,Antidote+", None),
        "anti-venom": ("Antidote++,Zulrah's scales", None),
        "stamina potion": ("Super energy,Amylase crystal", None),
        "extended antifire": ("Antifire potion,Lava scale shard", None),
        "divine": ("Crystal dust,Potion", None),
        "cannonball": ("Steel bar,Ammo mould", "furnace"),
        "plank": ("Logs,Coins", None),
        "godsword": ("Godsword blade,Godsword hilt", None),
    }

    for key, (items, loc) in potion_map.items():
        if key in n:
            return items, loc

    # Herblore - Clean herb patterns
    if "clean" in n or "grimy" in n:
        return "Grimy herb", None

    return "", None


def match_fill(name, task):
    """Fill X patterns."""
    n = name.lower()
    if not n.startswith("fill"):
        return None

    if "coal bag" in n:
        return "Coal bag,Coal", "coal_rock"
    if "gem bag" in n:
        return "Gem bag", None
    if "herb sack" in n:
        return "Herb sack", None
    if "seed box" in n:
        return "Seed box", None
    if "rune pouch" in n:
        return "Rune pouch", None
    if "looting bag" in n:
        return "Looting bag", None
    if "collection log" in n or "log slot" in n:
        return "", None

    return "", None


def match_build(name, task):
    """Build X / Room patterns (Construction)."""
    n = name.lower()
    if not (n.startswith("build") or n.startswith("room")):
        return None

    return "Saw,Hammer,Planks", None


def match_prayer(name, task):
    """Bury X / Pray patterns."""
    n = name.lower()

    if n.startswith("bury"):
        bone_map = {
            "bones": "Bones",
            "big bones": "Big bones",
            "dragon bones": "Dragon bones",
            "superior dragon": "Superior dragon bones",
            "wyvern": "Wyvern bones",
            "lava dragon": "Lava dragon bones",
            "dagannoth": "Dagannoth bones",
        }
        for key, item in bone_map.items():
            if key in n:
                return item, None
        return "Bones", None

    if n.startswith("pray") or n.startswith("restore") and "prayer" in n:
        return "", "altar"

    return None


def match_plant(name, task):
    """Plant X / Harvest X patterns (Farming)."""
    n = name.lower()
    if not (n.startswith("plant") or n.startswith("harvest") or n.startswith("rake")):
        return None

    if "rake" in n:
        return "Rake", None

    return "Seed dibber,Seeds,Spade", None


def match_clean(name, task):
    """Clean X Grimy herb patterns."""
    n = name.lower()
    if not n.startswith("clean"):
        return None

    herb_map = {
        "guam": "Grimy guam leaf",
        "marrentill": "Grimy marrentill",
        "tarromin": "Grimy tarromin",
        "harralander": "Grimy harralander",
        "ranarr": "Grimy ranarr weed",
        "toadflax": "Grimy toadflax",
        "irit": "Grimy irit leaf",
        "avantoe": "Grimy avantoe",
        "kwuarm": "Grimy kwuarm",
        "snapdragon": "Grimy snapdragon",
        "cadantine": "Grimy cadantine",
        "lantadyme": "Grimy lantadyme",
        "dwarf weed": "Grimy dwarf weed",
        "torstol": "Grimy torstol",
    }
    for key, item in herb_map.items():
        if key in n:
            return item, None
    return "Grimy herb", None


def match_obtain_xp(name, task):
    """Obtain X Million XP patterns."""
    n = name.lower()
    if not n.startswith("obtain"):
        return None
    if "million" in n and "xp" in n:
        return "", None
    if "glory" in n:
        return "", None
    if "collection log" in n:
        return "", None
    return None  # Let other matchers try


def match_minigame_kills(name, task):
    """N Wintertodt/Tempoross/etc Kill(s) patterns."""
    n = name.lower()
    m = re.match(r'^(\d+)\s+(.+?)\s+kills?$', n)
    if not m:
        return None

    count = m.group(1)
    subject = m.group(2).strip()

    minigame_map = {
        "wintertodt": ("Knife,Tinderbox,Axe,Hammer", "wintertodt"),
        "tempoross": ("Harpoon,Rope,Bucket of water,Hammer", "tempoross"),
        "sarachnis": ("Knife", "sarachnis"),
        "lizardmen shaman": ("", None),
        "alchemical hydra": ("", "alchemical_hydra"),
        "skotizo": ("Dark totem", "skotizo"),
        "araxxor": ("", "araxxor"),
        "hueycoatl": ("", "hueycoatl"),
        "amoxliatl": ("", "amoxliatl"),
        "mimic": ("Mimic casket", "mimic"),
    }

    for key, (items, loc) in minigame_map.items():
        if key in subject.lower():
            search = subject
            if items:
                search += f",{items}"
            return search, loc

    # Superior slayer encounters
    if "superior" in subject.lower():
        return subject, None

    return subject, None


def match_clue_scroll(name, task):
    """N Easy/Medium/Hard/Elite/Master Clue Scroll(s)."""
    n = name.lower()
    m = re.match(r'^(\d+)\s+(easy|medium|hard|elite|master)\s+clue\s+scrolls?$', n)
    if m:
        return f"Clue scroll ({m.group(2)})", None
    return None


def match_collection_log(name, task):
    """N Collection log slots."""
    if "collection log" in name.lower():
        return "", None
    return None


def match_combat_achievements(name, task):
    """N Combat Achievements."""
    if "combat achievement" in name.lower():
        return "", None
    return None


def match_chambers(name, task):
    """N Chambers of Xeric."""
    if "chambers of xeric" in name.lower():
        return "", "chambers_of_xeric"
    return None


def match_pet(name, task):
    """Pet X patterns."""
    n = name.lower()
    if not n.startswith("pet"):
        return None
    return "", None


def match_subdue(name, task):
    """Subdue X patterns (Wintertodt/Tempoross etc)."""
    n = name.lower()
    if not n.startswith("subdue"):
        return None
    if "wintertodt" in n:
        return "Knife,Tinderbox,Axe,Hammer", "wintertodt"
    if "tempoross" in n:
        return "Harpoon,Rope,Bucket of water,Hammer", "tempoross"
    return "", None


def match_drink(name, task):
    """Drink X patterns."""
    n = name.lower()
    if not n.startswith("drink"):
        return None
    item = name[len("Drink "):].strip()
    for article in ["a ", "an ", "the ", "some "]:
        if item.lower().startswith(article):
            item = item[len(article):]
            break
    return item, None


def match_eat(name, task):
    """Eat X patterns."""
    n = name.lower()
    if not n.startswith("eat"):
        return None
    item = name[len("Eat "):].strip()
    for article in ["a ", "an ", "the ", "some "]:
        if item.lower().startswith(article):
            item = item[len(article):]
            break
    return item, None


def match_teleport(name, task):
    """Teleport X patterns."""
    n = name.lower()
    if not n.startswith("teleport"):
        return None
    return "", None


def match_light(name, task):
    """Light X patterns (Firemaking)."""
    n = name.lower()
    if not n.startswith("light"):
        return None
    if "log" in n:
        return "Tinderbox,Logs", "bonfire_area"
    return "Tinderbox", None


def match_pick(name, task):
    """Pick X patterns."""
    n = name.lower()
    if not n.startswith("pick"):
        return None
    if n.startswith("pickpocket"):
        return None  # handled by thieving
    if "flax" in n:
        return "", None
    if "lock" in n:
        return "Lockpick", None
    return "", None


def match_sacrifice(name, task):
    """Sacrifice X patterns."""
    n = name.lower()
    if not n.startswith("sacrifice"):
        return None
    return "", None


def match_turn(name, task):
    """Turn X patterns."""
    n = name.lower()
    if not n.startswith("turn"):
        return None
    if "bone" in n:
        return "Bones", "altar"
    return "", None


def match_visit(name, task):
    """Visit X patterns."""
    n = name.lower()
    if not n.startswith("visit"):
        return None
    return "", None


def match_floor(name, task):
    """Floor X patterns (Hallowed Sepulchre)."""
    n = name.lower()
    if not n.startswith("floor"):
        return None
    if "hallowed" in n or "sepulchre" in n:
        return "", "hallowed_sepulchre"
    return "", None


def match_giants_foundry(name, task):
    """Giants' Foundry patterns."""
    n = name.lower()
    if "giants' foundry" in n or "giant's foundry" in n:
        return "Metal bars,Hammer", None
    return None


def match_guardians_rift(name, task):
    """Guardians of the Rift patterns."""
    n = name.lower()
    if "guardians of the rift" in n:
        return "Chisel,Any pickaxe", "guardians_of_the_rift"
    return None


def match_restore(name, task):
    """Restore X patterns."""
    n = name.lower()
    if not n.startswith("restore"):
        return None
    if "prayer" in n:
        return "", "altar"
    return "", None


def match_cut_gem(name, task):
    """Cut a Sapphire/Emerald/etc. patterns."""
    n = name.lower()
    if not n.startswith("cut"):
        return None
    gem_map = {
        "sapphire": "Uncut sapphire,Chisel",
        "emerald": "Uncut emerald,Chisel",
        "ruby": "Uncut ruby,Chisel",
        "diamond": "Uncut diamond,Chisel",
        "dragonstone": "Uncut dragonstone,Chisel",
        "onyx": "Uncut onyx,Chisel",
        "zenyte": "Uncut zenyte,Chisel",
    }
    for key, items in gem_map.items():
        if key in n:
            return items, None
    return "Chisel", None


def match_travel(name, task):
    """Travel X / Charter X / Cross X patterns."""
    n = name.lower()
    if not (n.startswith("travel") or n.startswith("charter") or n.startswith("cross")):
        return None
    if "charter" in n:
        return "Coins", None
    return "", None


def match_talk(name, task):
    """Talk to X patterns."""
    n = name.lower()
    if not n.startswith("talk"):
        return None
    return "", None


def match_misc_verb(name, task):
    """Catch-all for remaining verb patterns."""
    n = name.lower()
    first_word = n.split()[0] if n.split() else ""

    misc_verbs = {
        "successfully": "",
        "get": "",
        "buy": "Coins",
        "order": "Coins",
        "give": "",
        "slay": "",
        "trap": "",
        "move": "",
        "win": "",
        "unlock": "",
        "set": "",
        "ring": "",
        "churn": "Bucket of milk,Pot of cream",
        "telegrab": "Law rune,Air rune",
        "feed": "",
        "loot": "",
        "perform": "",
        "check": "",
        "milk": "Bucket",
        "bank": "",
        "receive": "",
        "pray": "",
        "activate": "",
        "purchase": "Coins",
        "trade": "Coins",
        "find": "",
        "take": "",
        "charge": "",
        "string": "Ball of wool",
        "tan": "Coins",
        "deliver": "",
        "sell": "",
        "mix": "Vial of water",
        "grind": "Pestle and mortar",
        "enchant": "Cosmic rune",
        "spin": "",
        "offer": "",
        "imbue": "",
        "recharge": "",
        "decant": "",
        "deposit": "",
        "withdraw": "",
        "crush": "Pestle and mortar",
        "fully": "",
    }

    if first_word in misc_verbs:
        items = misc_verbs[first_word]
        return items, None

    return None


def match_obtain_misc(name, task):
    """Obtain X patterns (non-XP, non-Glory)."""
    n = name.lower()
    if not n.startswith("obtain"):
        return None
    return "", None


def match_superior_encounters(name, task):
    """N Superior Slayer Encounters."""
    n = name.lower()
    if "superior slayer" in n:
        return "Slayer equipment", None
    return None


# ═══════════════════════════════════════════════════════════════════════════
# MAIN ENRICHMENT ENGINE
# ═══════════════════════════════════════════════════════════════════════════

def match_remaining_edge_cases(name, task):
    """Catch remaining tasks by keyword matching."""
    n = name.lower()

    edge_cases = {
        "superhuman strength": ("", None),
        "scrape": ("", None),
        "sleep": ("", None),
        "provide": ("", None),
        "land a hoop": ("", None),
        "worship": ("Bucket of slime,Bones", None),
        "dig": ("Spade", None),
        "capture": ("", None),
        "fish a": ("Fishing rod", None),
        "thieve": ("", None),
        "investigate": ("", None),
        "consume": ("", None),
        "ferment": ("Jug of water,Grapes", None),
        "survive": ("", None),
        "blow": ("Glassblowing pipe,Molten glass", None),
        "score": ("", None),
        "beat": ("", None),
        "redeem": ("", None),
        "blast furnace": ("Ore,Coal", None),
        "dismantle": ("", None),
        "enhance": ("", None),
        "catch": ("", None),  # catch any remaining Catch tasks
    }

    for key, (items, loc) in edge_cases.items():
        if key in n:
            return items, loc

    return None


# Order matters! More specific patterns first.
MATCHERS = [
    ("obtain_xp", match_obtain_xp),
    ("collection_log", match_collection_log),
    ("combat_achievements", match_combat_achievements),
    ("chambers", match_chambers),
    ("clue_scroll", match_clue_scroll),
    ("superior_encounters", match_superior_encounters),
    ("minigame_kills", match_minigame_kills),
    ("giants_foundry", match_giants_foundry),
    ("guardians_rift", match_guardians_rift),
    ("woodcutting", match_woodcutting),
    ("firemaking", match_firemaking),
    ("cooking", match_cooking),
    ("mining", match_mining),
    ("hunter", match_hunter),
    ("fishing", match_fishing),
    ("smithing", match_smithing),
    ("crafting", match_crafting),
    ("fletching", match_fletching),
    ("thieving", match_thieving),
    ("combat", match_combat),
    ("equip", match_equip),
    ("cut_gem", match_cut_gem),
    ("quest", match_quest),
    ("reach", match_reach),
    ("cast", match_cast),
    ("enter", match_enter),
    ("open", match_open),
    ("use", match_use),
    ("create", match_create),
    ("fill", match_fill),
    ("build", match_build),
    ("prayer", match_prayer),
    ("plant", match_plant),
    ("clean", match_clean),
    ("subdue", match_subdue),
    ("drink", match_drink),
    ("eat", match_eat),
    ("teleport", match_teleport),
    ("light", match_light),
    ("pick", match_pick),
    ("sacrifice", match_sacrifice),
    ("turn", match_turn),
    ("visit", match_visit),
    ("floor", match_floor),
    ("restore", match_restore),
    ("pet", match_pet),
    ("travel", match_travel),
    ("talk", match_talk),
    ("obtain_misc", match_obtain_misc),
    ("misc_verb", match_misc_verb),
    ("edge_cases", match_remaining_edge_cases),
]


def enrich_task(task, existing_strategy):
    """
    Enrich a single task with items/search and location.

    Returns:
        (search_str, location_tiles) or (None, None) if no match
    """
    struct_id = str(task["structId"])
    name = task["name"]

    # Try each matcher
    for matcher_name, matcher_fn in MATCHERS:
        result = matcher_fn(name, task)
        if result is not None:
            search_str, loc_key = result
            tiles = []
            if loc_key and loc_key in LOCATIONS:
                raw_coords = LOCATIONS[loc_key]
                tiles = [{"x": x, "y": y, "count": 1} for x, y in raw_coords]
            return search_str, tiles, matcher_name

    return None, None, None


def use_wiki_notes_fallback(task):
    """
    Extract useful info from wikiNotes as a fallback.
    wikiNotes often contain skill requirements and item hints.
    """
    notes = task.get("wikiNotes", "")
    if not notes:
        return None, None

    # wikiNotes often has items mentioned
    # Example: "Any axe", "Fishing rod,Fishing bait", etc.
    # These are typically just level reqs though: "30 Fishing"
    # We can't reliably extract items from notes without more parsing
    # Return the raw notes as search terms for now
    # Filter out pure level requirements
    items = []
    for part in notes.split("  "):
        part = part.strip()
        if not part:
            continue
        # Skip level requirements like "30 Fishing" or "99 Cooking"
        if re.match(r'^\d+\s+(Attack|Strength|Defence|Ranged|Prayer|Magic|'
                    r'Hitpoints|Mining|Smithing|Fishing|Cooking|Firemaking|'
                    r'Woodcutting|Fletching|Crafting|Runecraft|Herblore|'
                    r'Agility|Thieving|Farming|Construction|Hunter|Slayer|'
                    r'Combat)$', part, re.IGNORECASE):
            continue
        # Skip quest references
        if part.startswith("Completion of") or part.startswith("See "):
            continue
        # Skip location suggestions
        if part.startswith("Either ") or part.startswith("Partial completion"):
            continue
        # Skip HTML/wiki artifacts
        if ".mw-parser" in part or "<" in part:
            continue
        if len(part) > 3 and len(part) < 60:
            items.append(part)

    if items:
        return ",".join(items[:5]), None  # limit to 5 items

    return None, None


def main():
    print("=" * 70)
    print("  ULTIMATE TASK MASTER - Task Enrichment Script")
    print("  Husky husky-f54a28 pulling the sled!")
    print("=" * 70)
    print()

    # ── Load data ────────────────────────────────────────────────────────
    print(f"Loading tasks from: {TASKS_PATH}")
    with open(TASKS_PATH, "r", encoding="utf-8") as f:
        tasks = json.load(f)
    print(f"  Total tasks: {len(tasks)}")

    print(f"Loading existing strategy from: {EXISTING_STRATEGY_PATH}")
    try:
        with open(EXISTING_STRATEGY_PATH, "r", encoding="utf-8") as f:
            existing_strategy = json.load(f)
        print(f"  Existing entries: {len(existing_strategy)}")
    except FileNotFoundError:
        existing_strategy = {}
        print("  No existing strategy.json found (starting fresh)")

    # ── Process ──────────────────────────────────────────────────────────
    print()
    print("Processing tasks...")

    new_strategy = {}
    new_locations = {}
    stats = Counter()
    matcher_stats = Counter()
    unmatched = []

    for task in tasks:
        struct_id = str(task["structId"])
        name = task["name"]

        # Keep existing strategy entries as-is
        if struct_id in existing_strategy:
            entry = existing_strategy[struct_id]
            new_strategy[struct_id] = entry
            stats["preserved_existing"] += 1
            matcher_stats["existing"] += 1
            continue

        # Try pattern matching
        search_str, tiles, matcher_name = enrich_task(task, existing_strategy)

        if search_str is not None:
            new_strategy[struct_id] = {
                "taskName": name,
                "search": search_str
            }
            if tiles:
                new_locations[struct_id] = tiles
            stats["pattern_matched"] += 1
            matcher_stats[matcher_name] += 1
        else:
            # Try wikiNotes fallback
            wiki_search, _ = use_wiki_notes_fallback(task)
            if wiki_search:
                new_strategy[struct_id] = {
                    "taskName": name,
                    "search": wiki_search
                }
                stats["wiki_fallback"] += 1
                matcher_stats["wiki_fallback"] += 1
            else:
                stats["unmatched"] += 1
                unmatched.append(task)

    # ── Output ───────────────────────────────────────────────────────────
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    print(f"\nWriting strategy.json to: {OUTPUT_STRATEGY}")
    with open(OUTPUT_STRATEGY, "w", encoding="utf-8") as f:
        json.dump(new_strategy, f, indent=2, ensure_ascii=False)
    strat_size = os.path.getsize(OUTPUT_STRATEGY) / 1024
    print(f"  Size: {strat_size:.1f} KB")

    print(f"Writing task_locations.json to: {OUTPUT_LOCATIONS}")
    with open(OUTPUT_LOCATIONS, "w", encoding="utf-8") as f:
        json.dump(new_locations, f, indent=2, ensure_ascii=False)
    loc_size = os.path.getsize(OUTPUT_LOCATIONS) / 1024
    print(f"  Size: {loc_size:.1f} KB")

    # ── Statistics ────────────────────────────────────────────────────────
    total = len(tasks)
    matched = stats["preserved_existing"] + stats["pattern_matched"] + stats["wiki_fallback"]

    print()
    print("=" * 70)
    print("  COVERAGE STATISTICS")
    print("=" * 70)
    print(f"  Total tasks:          {total}")
    print(f"  Matched (total):      {matched} ({100*matched/total:.1f}%)")
    print(f"    Existing preserved: {stats['preserved_existing']}")
    print(f"    Pattern matched:    {stats['pattern_matched']}")
    print(f"    Wiki fallback:      {stats['wiki_fallback']}")
    print(f"  Unmatched:            {stats['unmatched']} ({100*stats['unmatched']/total:.1f}%)")
    print(f"  Locations generated:  {len(new_locations)}")
    print()

    print("  MATCHER BREAKDOWN:")
    for matcher_name, count in matcher_stats.most_common():
        print(f"    {matcher_name:25s} {count:4d}")
    print()

    if unmatched:
        print(f"  UNMATCHED TASKS ({len(unmatched)}):")
        for t in unmatched[:40]:
            wiki = t.get("wikiNotes", "")
            wiki_preview = wiki[:40] + "..." if len(wiki) > 40 else wiki
            print(f"    [{t['structId']:5d}] {t['name'][:50]:50s} cat={t['category']:12s} wiki={wiki_preview}")
        if len(unmatched) > 40:
            print(f"    ... and {len(unmatched) - 40} more")
    print()
    print("=" * 70)
    print("  Done! Output files in temp/output/")
    print("=" * 70)


if __name__ == "__main__":
    main()
