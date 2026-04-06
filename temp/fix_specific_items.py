import json, sys

def main():
    with open('src/main/resources/com/ultimatetaskmaster/strategy.json', 'r', encoding='utf-8') as f:
        strategy = json.load(f)
    
    # MANUAL CORRECTIONS - task name → correct search items
    FIXES = {
        # === FLETCHING FIXES ===
        'Fletch 200 Dragon Darts': 'Dragon dart tip,Feather',
        'Fletch 100 Dragon Javelins': 'Dragon javelin heads,Javelin shaft',
        'Fletch a Magic Shield': 'Knife,Magic logs',
        'Fletch a Rune Crossbow': 'Yew stock,Runite limbs,Hammer',
        'Fletch 25 Oak Stocks': 'Knife,Oak logs',
        'Fletch an Oak Shortbow': 'Knife,Oak logs,Bowstring',
        
        # === BLOWPIPE/TRIDENT FIXES ===
        'Load a blowpipe with Dragon Darts': 'Toxic blowpipe,Dragon dart',
        'Load a blowpipe with Rune Darts': 'Toxic blowpipe,Rune dart',
        'Craft a Toxic Blowpipe': 'Tanzanite fang,Chisel',
        'Craft a Toxic Trident': 'Magic fang,Trident of the seas (full)',
        
        # === CRAFTING "Crafting materials" FIXES ===
        'Craft 100 Unpowered Orbs': 'Molten glass,Glassblowing pipe',
        'Craft 200 Essence Into Runes': 'Rune essence,Tiara',
        'Craft 2500 Essence Into Runes': 'Pure essence,Tiara',
        'Craft 4 Runes With 1 Essence': 'Pure essence,Tiara',
        'Craft 50 Astral Runes': 'Pure essence',
        'Craft Any Combination Rune': 'Rune essence,Talisman',
        'Craft a Ghorrock Teleport Tablet': 'Dark essence block,Chisel',
        'Craft a Lava Rune at the Fire Altar': 'Rune essence,Earth rune,Fire talisman',
        'Craft a Piece of Crystal Armour': 'Crystal shard,Crystal armour seed',
        'Craft a Rune Using Daeyalt Essence': 'Daeyalt essence,Tiara',
        'Craft a Snelm': 'Chisel,Blamish snail shell',
        'Craft an Air Battlestaff in the Wilderness': 'Battlestaff,Air orb',
        'Craft an Eternal Teleport Crystal': 'Crystal shard,Enhanced crystal teleport seed',
        'Craft a Fire Rune': 'Rune essence,Fire talisman',
        'Craft an Air Rune': 'Rune essence,Air talisman',
        
        # === BLUE DRAGONHIDE FIX (still wrong) ===
        'Craft 30 Blue Dragonhide Bodies': 'Blue dragon leather,Needle,Thread',
        
        # === COOKING FIXES ===
        'Cook a Shark': 'Raw shark',
        'Cook a Monkfish': 'Raw monkfish',
        'Cook a Manta Ray': 'Raw manta ray',
        'Cook an Anglerfish': 'Raw anglerfish',
        'Cook a Dark Crab': 'Raw dark crab',
        'Cook a Karambwan': 'Raw karambwan',
        'Cook a Lobster': 'Raw lobster',
        'Cook a Swordfish': 'Raw swordfish',
        'Cook a Bass': 'Raw bass',
        'Cook a Tuna': 'Raw tuna',
        'Cook a Pike': 'Raw pike',
        'Cook a Trout': 'Raw trout',
        'Cook a Salmon': 'Raw salmon',
        
        # === MAKE FIXES ===
        'Make a Pineapple Pizza': 'Pizza base,Tomato,Cheese,Pineapple ring',
        'Make a Super Combat Potion': 'Super attack(4),Super strength(4),Super defence(4),Torstol',
        'Make some Flour': 'Pot,Grain',
        
        # === SMITHING FIXES ===
        'Blast Furnace 100 Mithril Bars': 'Mithril ore,Coal',
        'Blast Furnace 100 Runite Bars': 'Runite ore,Coal',
        
        # === USE/LOAD/ATTACH FIXES ===
        'Attach a Holy ornament kit to the Scythe of Vitur': 'Holy ornament kit,Scythe of vitur',
        'Attach a Sanguine ornament kit to the Scythe': 'Sanguine ornament kit,Scythe of vitur',
        
        # === MISC FIXES ===
        'Assemble a Slayer Helm': 'Black mask,Nosepeg,Facemask,Earmuffs,Spiny helmet,Enchanted gem',
        'Add a Jar to a Display Case': 'Jar',
        'Activate an Arcane or Dexterous Prayer Scroll': 'Arcane prayer scroll,Dexterous prayer scroll',
        'Activate an Imbued Heart': 'Imbued heart',
        'Balance 5 barrels on your head': '',
        'Barehand catch a Shark': '',
        'Ask for a Quest from Bob': '',
        'Beat Jacky Jester': '',
        
        # === PRAYER FIXES ===
        'Superhuman Strength and Improved Reflexes': '',
        
        # === OBTAIN ITEM FIXES ===
        'Obtain a Fire Cape': 'Combat gear,Prayer potion(4),Saradomin brew(4),Super restore(4),Food',
        'Obtain an Infernal Cape': 'Combat gear,Prayer potion(4),Saradomin brew(4),Super restore(4),Ranging potion(4),Food',
        
        # === EQUIP FIXES (wrong items) ===
        'Equip a Sarachnis Cudgel': 'Sarachnis cudgel',
        'Equip Bryophyta\'s Staff': 'Bryophyta\'s staff',
        'Equip a Nightmare Staff': 'Nightmare staff',
        'Equip a Nightmare Staff With an Orb': 'Nightmare staff,Harmonised orb',
        'Equip a Piece of any Barrows Armour Set': 'Barrows equipment',
        'Equip any Full Barrows Armour Set': 'Barrows equipment',
        'Equip Every Dagannoth King Ring': 'Berserker ring,Warrior ring,Seers ring,Archers ring',
    }
    
    changes = 0
    for sid, entry in strategy.items():
        name = entry.get('taskName', '')
        if name in FIXES:
            new_search = FIXES[name]
            old_search = entry.get('search', '')
            if old_search != new_search:
                entry['search'] = new_search
                changes += 1
                print(f"  [{name}] {old_search[:50]} -> {new_search[:50]}")
    
    with open('src/main/resources/com/ultimatetaskmaster/strategy.json', 'w', encoding='utf-8') as f:
        json.dump(strategy, f, indent=2, ensure_ascii=False)
    
    print(f"\nTotal changes: {changes}")

if __name__ == '__main__':
    main()
