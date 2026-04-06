import json
import re
import sys

def is_suspicious_item(item, task_name):
    """Check if an item looks like it might be an NPC name or task description fragment"""
    item_lower = item.strip().lower()
    
    # Known good items - don't flag these
    KNOWN_GOOD = {
        'combat gear', 'food', 'rope', 'knife', 'slash weapon', 'spade',
        'giant key', 'mossy key', 'brittle key', 'frozen key', 'dark totem',
        'facemask', 'warm clothing', 'any axe', 'any pickaxe', 'tinderbox',
        'hammer', 'harpoon', 'pickaxe', 'axe', 'needle', 'thread', 'chisel',
        'coins', 'rake', 'seed dibber', 'bucket of water',
        'fly fishing rod', 'fishing rod', 'fishing bait', 'small fishing net',
        'lobster pot', 'feather', 'bones',
    }
    if item_lower in KNOWN_GOOD:
        return False
    # Potions are fine
    if re.match(r'.+\(\d\)$', item_lower):
        return False
    # Runes are fine
    if item_lower.endswith(' rune') or item_lower.endswith(' runes'):
        return False
    
    # Suspicious patterns:
    # Contains "times" or large numbers
    if re.search(r'\d+\s+times?', item_lower, re.IGNORECASE):
        return True
    # Matches common NPC/boss patterns
    if item_lower.startswith('the ') or item_lower.startswith('a '):
        return True
    # Contains "without", "in", "on" - sounds like task description
    if any(w in item_lower for w in ['without', ' in ', ' on ', ' at ']):
        return True
    # Very long item name (>30 chars) - probably task description
    if len(item_lower) > 35:
        return True
    
    return False

def main():
    with open('src/main/resources/com/ultimatetaskmaster/tasks.json', 'r', encoding='utf-8') as f:
        tasks = json.load(f)
    with open('src/main/resources/com/ultimatetaskmaster/strategy.json', 'r', encoding='utf-8') as f:
        strategy = json.load(f)
    
    # Build structId -> strategy lookup
    strat_by_id = {}
    for sid, entry in strategy.items():
        strat_by_id[int(sid)] = entry
    
    # Group by category
    by_cat = {}
    for t in tasks:
        cat = t.get('category', 'Unknown')
        by_cat.setdefault(cat, []).append(t)
    
    issues = 0
    total = 0
    
    with open('temp/output/full_review.txt', 'w', encoding='utf-8') as out:
        for cat in ['Combat', 'Skill', 'Other', 'Minigame', 'Achievement', 'Quest']:
            cat_tasks = by_cat.get(cat, [])
            out.write(f"\n{'='*80}\n")
            out.write(f"  {cat} ({len(cat_tasks)} tasks)\n")
            out.write(f"{'='*80}\n\n")
            
            for t in sorted(cat_tasks, key=lambda x: x['name']):
                total += 1
                sid = t['structId']
                name = t['name']
                entry = strat_by_id.get(sid, {})
                search = entry.get('search', '')
                
                # Check for issues
                flags = []
                items = [x.strip() for x in search.split(',') if x.strip()] if search else []
                
                for item in items:
                    if is_suspicious_item(item, name):
                        flags.append(f"SUSPICIOUS: '{item}'")
                
                if not search and not any(name.lower().startswith(p) for p in ['reach ', 'gain ', 'achieve ', 'visit ', 'enter ']):
                    if t.get('category') not in ['Achievement']:
                        flags.append("EMPTY SEARCH")
                
                flag_str = ''
                if flags:
                    issues += 1
                    flag_str = f"  ⚠️  {'; '.join(flags)}"
                
                out.write(f"  [{sid}] {name}\n")
                out.write(f"         → {search if search else '(none)'}\n")
                if flag_str:
                    out.write(f"        {flag_str}\n")
                out.write(f"\n")
        
        out.write(f"\n{'='*80}\n")
        out.write(f"  SUMMARY: {total} tasks reviewed, {issues} potential issues flagged\n")
        out.write(f"{'='*80}\n")
    
    print(f"Full review written to temp/output/full_review.txt")
    print(f"  {total} tasks reviewed")
    print(f"  {issues} potential issues flagged")

if __name__ == '__main__':
    main()
