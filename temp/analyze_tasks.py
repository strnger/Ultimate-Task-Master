import json
import sys

def main():
    with open('src/main/resources/com/ultimatetaskmaster/tasks.json', 'r', encoding='utf-8') as f:
        tasks = json.load(f)
    
    print(f"Total tasks: {len(tasks)}")
    
    # Group by first word
    patterns = {}
    for t in tasks:
        words = t['name'].split()
        p = words[0] if words else '?'
        patterns[p] = patterns.get(p, 0) + 1
    
    print("\nTop 30 task name patterns (first word):")
    for k, v in sorted(patterns.items(), key=lambda x: -x[1])[:30]:
        print(f"  {v:4d} {k}")
    
    # Categories
    cats = {}
    for t in tasks:
        c = t.get('category', '?')
        cats.setdefault(c, []).append(t)
    
    print("\nCategories:")
    for c, items in sorted(cats.items(), key=lambda x: -len(x[1])):
        print(f"  {c}: {len(items)}")
    
    # Show wikiNotes samples
    has_notes = [t for t in tasks if t.get('wikiNotes')]
    has_skills = [t for t in tasks if t.get('skills')]
    print(f"\nHas wikiNotes: {len(has_notes)}")
    print(f"Has skills[]: {len(has_skills)}")
    
    # Sample wikiNotes
    print("\nSample wikiNotes:")
    for t in has_notes[:10]:
        print(f"  [{t['name']}] -> {t['wikiNotes'][:100]}")
    
    # Show combat tasks
    combat = [t for t in tasks if t.get('category') == 'Combat']
    print(f"\nCombat task name patterns:")
    combat_patterns = {}
    for t in combat:
        name = t['name']
        if name.startswith("Defeat"):
            combat_patterns["Defeat X"] = combat_patterns.get("Defeat X", 0) + 1
        elif "Kill" in name:
            combat_patterns["X Kill(s)"] = combat_patterns.get("X Kill(s)", 0) + 1
        elif "Slayer" in name:
            combat_patterns["Slayer"] = combat_patterns.get("Slayer", 0) + 1
        else:
            combat_patterns["Other combat"] = combat_patterns.get("Other combat", 0) + 1
    for k, v in sorted(combat_patterns.items(), key=lambda x: -x[1]):
        print(f"  {v:4d} {k}")

if __name__ == '__main__':
    main()
