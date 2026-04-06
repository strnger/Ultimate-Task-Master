import json
import sys
import re

def main():
    with open('temp/output/strategy.json', 'r', encoding='utf-8') as f:
        strategy = json.load(f)
    
    changes = 0
    
    # =============================================
    # 1. BOSS-SPECIFIC REQUIREMENTS
    # =============================================
    BOSS_DATA = {
        'kalphite queen': {
            'access': 'Rope',
            'recommended': 'Combat gear,Antipoison(4),Antidote++(4),Super antipoison(4),Prayer potion(4),Food'
        },
        'sarachnis': {
            'access': 'Knife,Slash weapon',
            'recommended': 'Combat gear,Prayer potion(4),Food'
        },
        'zulrah': {
            'access': '',
            'recommended': 'Combat gear,Anti-venom+(4),Ring of recoil,Prayer potion(4),Food'
        },
        'vorkath': {
            'access': '',
            'recommended': 'Combat gear,Anti-venom+(4),Super antifire potion(4),Prayer potion(4),Food'
        },
        'cerberus': {
            'access': '',
            'recommended': 'Combat gear,Spectral spirit shield,Prayer potion(4),Food'
        },
        'kraken': {
            'access': '',
            'recommended': 'Combat gear,Trident of the seas,Prayer potion(4),Food'
        },
        'abyssal sire': {
            'access': '',
            'recommended': 'Combat gear,Efaritay\'s aid,Prayer potion(4),Food'
        },
        'thermonuclear smoke devil': {
            'access': '',
            'recommended': 'Combat gear,Facemask,Prayer potion(4),Food'
        },
        'grotesque guardians': {
            'access': 'Brittle key',
            'recommended': 'Combat gear,Prayer potion(4),Food'
        },
        'alchemical hydra': {
            'access': '',
            'recommended': 'Combat gear,Antidote++(4),Prayer potion(4),Food'
        },
        'dagannoth': {
            'access': '',
            'recommended': 'Combat gear,Prayer potion(4),Food'
        },
        'giant mole': {
            'access': 'Spade,Falador shield',
            'recommended': 'Combat gear,Prayer potion(4),Food'
        },
        'king black dragon': {
            'access': '',
            'recommended': 'Combat gear,Anti-dragon shield,Antifire potion(4),Prayer potion(4),Food'
        },
        'corporeal beast': {
            'access': '',
            'recommended': 'Combat gear,Spirit shield,Prayer potion(4),Food'
        },
        'barrows': {
            'access': 'Spade',
            'recommended': 'Combat gear,Prayer potion(4),Food'
        },
        'chaos fanatic': {
            'access': '',
            'recommended': 'Combat gear,Food'
        },
        'crazy archaeologist': {
            'access': '',
            'recommended': 'Combat gear,Food'
        },
        'scorpia': {
            'access': '',
            'recommended': 'Combat gear,Antipoison(4),Food'
        },
        'callisto': {
            'access': '',
            'recommended': 'Combat gear,Food'
        },
        'vet\'ion': {
            'access': '',
            'recommended': 'Combat gear,Food'
        },
        'venenatis': {
            'access': '',
            'recommended': 'Combat gear,Antipoison(4),Food'
        },
        'obor': {
            'access': 'Giant key',
            'recommended': 'Combat gear,Food'
        },
        'bryophyta': {
            'access': 'Mossy key',
            'recommended': 'Combat gear,Food'
        },
        'deranged archaeologist': {
            'access': '',
            'recommended': 'Combat gear,Food'
        },
        'hespori': {
            'access': '',
            'recommended': 'Combat gear,Antipoison(4),Food'
        },
        'mimic': {
            'access': '',
            'recommended': 'Combat gear,Food'
        },
        'nightmare': {
            'access': '',
            'recommended': 'Combat gear,Prayer potion(4),Sanfew serum(4),Food'
        },
        'nex': {
            'access': 'Frozen key',
            'recommended': 'Combat gear,Prayer potion(4),Saradomin brew(4),Super restore(4),Food'
        },
        'phantom muspah': {
            'access': '',
            'recommended': 'Combat gear,Prayer potion(4),Food'
        },
        'duke sucellus': {
            'access': '',
            'recommended': 'Combat gear,Prayer potion(4),Food'
        },
        'the leviathan': {
            'access': '',
            'recommended': 'Combat gear,Prayer potion(4),Food'
        },
        'the whisperer': {
            'access': '',
            'recommended': 'Combat gear,Prayer potion(4),Food'
        },
        'vardorvis': {
            'access': '',
            'recommended': 'Combat gear,Prayer potion(4),Food'
        },
        'scurrius': {
            'access': '',
            'recommended': 'Combat gear,Food'
        },
        'wintertodt': {
            'access': '',
            'recommended': 'Axe,Tinderbox,Knife,Hammer,Warm clothing,Food'
        },
        'tempoross': {
            'access': '',
            'recommended': 'Harpoon,Hammer,Rope,Bucket of water'
        },
        'zalcano': {
            'access': '',
            'recommended': 'Pickaxe'
        },
        'gauntlet': {
            'access': '',
            'recommended': ''
        },
        'corrupted gauntlet': {
            'access': '',
            'recommended': ''
        },
        'theatre of blood': {
            'access': '',
            'recommended': 'Combat gear,Prayer potion(4),Super restore(4),Saradomin brew(4),Food'
        },
        'chambers of xeric': {
            'access': '',
            'recommended': 'Combat gear,Prayer potion(4),Super restore(4),Saradomin brew(4),Food'
        },
        'tombs of amascut': {
            'access': '',
            'recommended': 'Combat gear,Prayer potion(4),Super restore(4),Saradomin brew(4),Food'
        },
        'colosseum': {
            'access': '',
            'recommended': 'Combat gear,Prayer potion(4),Super restore(4),Food'
        },
    }
    
    for sid, entry in strategy.items():
        name = entry.get('taskName', '').lower()
        for boss_key, boss_info in BOSS_DATA.items():
            if boss_key in name:
                items = []
                if boss_info['access']:
                    items.append(boss_info['access'])
                if boss_info['recommended']:
                    items.append(boss_info['recommended'])
                new_search = ','.join(items)
                if new_search and entry.get('search', '') != new_search:
                    old = entry.get('search', '')
                    entry['search'] = new_search
                    changes += 1
                    print(f"  BOSS: [{entry['taskName']}] {old} -> {new_search[:80]}")
                break
    
    # =============================================
    # 2. CRAFTING EXACT ITEM NAMES
    # =============================================
    CRAFT_FIXES = {
        'blue dragonhide body': 'Blue dragon leather,Needle,Thread',
        'blue dragonhide vambraces': 'Blue dragon leather,Needle,Thread',
        'blue dragonhide chaps': 'Blue dragon leather,Needle,Thread',
        'green dragonhide body': 'Green dragon leather,Needle,Thread',
        'green dragonhide vambraces': 'Green dragon leather,Needle,Thread',
        'green dragonhide chaps': 'Green dragon leather,Needle,Thread',
        'red dragonhide body': 'Red dragon leather,Needle,Thread',
        'red dragonhide vambraces': 'Red dragon leather,Needle,Thread',
        'red dragonhide chaps': 'Red dragon leather,Needle,Thread',
        'black dragonhide body': 'Black dragon leather,Needle,Thread',
        'black dragonhide vambraces': 'Black dragon leather,Needle,Thread',
        'black dragonhide chaps': 'Black dragon leather,Needle,Thread',
        'gold bracelet': 'Gold bar,Bracelet mould',
        'gold necklace': 'Gold bar,Necklace mould',
        'gold ring': 'Gold bar,Ring mould',
        'gold amulet': 'Gold bar,Amulet mould',
        'sapphire ring': 'Gold bar,Sapphire,Ring mould',
        'sapphire necklace': 'Gold bar,Sapphire,Necklace mould',
        'sapphire amulet': 'Gold bar,Sapphire,Amulet mould',
        'emerald ring': 'Gold bar,Emerald,Ring mould',
        'emerald necklace': 'Gold bar,Emerald,Necklace mould',
        'emerald amulet': 'Gold bar,Emerald,Amulet mould',
        'ruby ring': 'Gold bar,Ruby,Ring mould',
        'ruby necklace': 'Gold bar,Ruby,Necklace mould',
        'ruby amulet': 'Gold bar,Ruby,Amulet mould',
        'diamond ring': 'Gold bar,Diamond,Ring mould',
        'diamond necklace': 'Gold bar,Diamond,Necklace mould',
        'diamond amulet': 'Gold bar,Diamond,Amulet mould',
        'dragonstone ring': 'Gold bar,Dragonstone,Ring mould',
        'dragonstone necklace': 'Gold bar,Dragonstone,Necklace mould',
        'dragonstone amulet': 'Gold bar,Dragonstone,Amulet mould',
        'onyx ring': 'Gold bar,Onyx,Ring mould',
        'onyx necklace': 'Gold bar,Onyx,Necklace mould',
        'onyx amulet': 'Gold bar,Onyx,Amulet mould',
        'leather body': 'Leather,Needle,Thread',
        'leather chaps': 'Leather,Needle,Thread',
        'leather gloves': 'Leather,Needle,Thread',
        'leather boots': 'Leather,Needle,Thread',
        'leather vambraces': 'Leather,Needle,Thread',
        'leather cowl': 'Leather,Needle,Thread',
        'hardleather body': 'Hard leather,Needle,Thread',
        'air battlestaff': 'Battlestaff,Air orb',
        'water battlestaff': 'Battlestaff,Water orb',
        'earth battlestaff': 'Battlestaff,Earth orb',
        'fire battlestaff': 'Battlestaff,Fire orb',
    }
    
    for sid, entry in strategy.items():
        name = entry.get('taskName', '').lower()
        if name.startswith('craft'):
            # Extract the item being crafted (remove "Craft X " or "Craft a ")
            item_part = re.sub(r'^craft\s+(\d+\s+)?', '', name).strip()
            item_part = re.sub(r'^(a|an|some)\s+', '', item_part).strip()
            
            if item_part in CRAFT_FIXES:
                new_search = CRAFT_FIXES[item_part]
                if entry.get('search', '') != new_search:
                    old = entry.get('search', '')
                    entry['search'] = new_search
                    changes += 1
                    print(f"  CRAFT: [{entry['taskName']}] {old} -> {new_search}")
    
    # =============================================
    # 3. GENERAL COMBAT TASKS - add "Combat gear"
    # =============================================
    for sid, entry in strategy.items():
        name = entry.get('taskName', '').lower()
        search = entry.get('search', '')
        
        # If it's a kill/defeat task and only has the NPC name, add combat gear
        is_combat = any(name.startswith(p) for p in ['defeat ', 'kill ']) or re.match(r'^\d+\s+\w.*kill', name)
        if is_combat and search and 'combat gear' not in search.lower() and 'Combat gear' not in search:
            # Don't add combat gear to non-combat entries or already-detailed entries
            npc_only = len(search.split(',')) <= 2
            if npc_only:
                entry['search'] = search + ',Combat gear,Food'
                changes += 1
    
    # =============================================
    # 4. SPECIFIC FIXES
    # =============================================
    SPECIFIC_FIXES = {
        'Turn Logs Into Planks': 'Logs,Coins',
        'Turn Oak Logs Into Planks': 'Oak logs,Coins', 
        'Turn Teak Logs Into Planks': 'Teak logs,Coins',
        'Turn Mahogany Logs Into Planks': 'Mahogany logs,Coins',
    }
    
    for sid, entry in strategy.items():
        name = entry.get('taskName', '')
        if name in SPECIFIC_FIXES:
            new_search = SPECIFIC_FIXES[name]
            if entry.get('search', '') != new_search:
                old = entry.get('search', '')
                entry['search'] = new_search
                changes += 1
                print(f"  SPECIFIC: [{name}] {old} -> {new_search}")
    
    # Write back
    with open('temp/output/strategy.json', 'w', encoding='utf-8') as f:
        json.dump(strategy, f, indent=2, ensure_ascii=False)
    
    print(f"\nTotal changes: {changes}")
    print("Written to temp/output/strategy.json")

if __name__ == '__main__':
    main()
