"""
Pre-compute task locations from LeaguesMap data files.

Input:
  - LeaguesMap/data_osrs/strategy.json (task -> search terms / explicit coords)
  - LeaguesMap/data_osrs/monsters.json (NPC locations)
  - LeaguesMap/data_osrs/item_spawns.json (item spawn locations)
  - LeaguesMap/data_osrs/scenery.json (scenery/object locations)

Output:
  - src/main/resources/com/ultimatetaskmaster/task_locations.json

Algorithm (matches LeaguesMap's leaflet.planner.js):
  1. For each task in strategy.json:
     a. If it has explicit "points" coords, cluster them (radius 10)
     b. Else if it has "search" terms, look up names in monsters/items/scenery
     c. Cluster all found coords, output centroids
"""
import json
import os
import shutil
import time

LMAP_DATA = r'C:\Users\Strnger\Documents\Repos\NonPluginExamples\LeaguesMap\data_osrs'
OUTPUT = r'C:\Users\Strnger\Documents\Repos\Ultimate-Task-Master\src\main\resources\com\ultimatetaskmaster\task_locations.json'
STRATEGY_COPY = r'C:\Users\Strnger\Documents\Repos\Ultimate-Task-Master\src\main\resources\com\ultimatetaskmaster\strategy.json'
CLUSTER_RADIUS = 10


def cluster_coords(coords):
    """Greedy radius-based clustering, matching LeaguesMap's clusterCoords()."""
    if not coords:
        return []
    # Deduplicate first to reduce work
    seen = set()
    unique = []
    for c in coords:
        key = (c['x'], c['y'])
        if key not in seen:
            seen.add(key)
            unique.append(c)
    coords = unique

    assigned = [False] * len(coords)
    clusters = []
    for i in range(len(coords)):
        if assigned[i]:
            continue
        members = [coords[i]]
        assigned[i] = True
        changed = True
        while changed:
            changed = False
            for j in range(len(coords)):
                if assigned[j]:
                    continue
                in_range = any(
                    max(abs(coords[j]['x'] - m['x']),
                        abs(coords[j]['y'] - m['y'])) <= CLUSTER_RADIUS
                    for m in members
                )
                if in_range:
                    members.append(coords[j])
                    assigned[j] = True
                    changed = True
        cx = round(sum(m['x'] for m in members) / len(members))
        cy = round(sum(m['y'] for m in members) / len(members))
        clusters.append({'x': cx, 'y': cy, 'count': len(members)})
    return clusters


def parse_points(points_str):
    """Parse explicit coordinate pairs from strategy.points field."""
    nums = []
    for s in points_str.split(','):
        s = s.strip()
        if s:
            try:
                nums.append(int(s))
            except ValueError:
                pass
    coords = []
    for i in range(0, len(nums) - 1, 2):
        coords.append({'x': nums[i], 'y': nums[i + 1]})
    return coords


def build_name_index(datasets):
    """
    Build a name -> coords index from all datasets for fast search.
    Returns dict: lowercase_page_name -> list of {'x':..,'y':..}
    """
    index = {}
    for dataset in datasets:
        for entry in dataset:
            page_name = entry.get('page_name', '')
            if not page_name:
                continue
            name_lower = page_name.lower()
            coords = entry.get('coordinates', [])
            parsed = []
            for c in coords:
                if isinstance(c, (list, tuple)) and len(c) >= 2:
                    parsed.append({'x': round(c[0]), 'y': round(c[1])})
            if parsed:
                if name_lower not in index:
                    index[name_lower] = parsed
                else:
                    index[name_lower].extend(parsed)
    return index


def search_index(search_terms, name_index):
    """Search the pre-built name index for matching coordinates."""
    all_coords = []
    terms = [t.strip().lower() for t in search_terms.split(',') if t.strip()]

    for term in terms:
        # Check if strict match (quoted)
        strict = term.startswith('"') and term.endswith('"')
        if strict:
            term = term[1:-1]

        if strict:
            # Exact match - O(1) lookup
            if term in name_index:
                all_coords.extend(name_index[term])
        else:
            # Substring match - scan index keys (much smaller than raw data)
            for name, coords in name_index.items():
                if term in name:
                    all_coords.extend(coords)

    return all_coords


def load_json(filename):
    path = os.path.join(LMAP_DATA, filename)
    print(f'  Loading {filename}...', end=' ', flush=True)
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    print(f'{len(data)} entries')
    return data


def main():
    t0 = time.time()
    print('=== Pre-computing task locations ===')

    # Load strategy
    with open(os.path.join(LMAP_DATA, 'strategy.json'), 'r', encoding='utf-8') as f:
        strategy = json.load(f)
    print(f'strategy.json: {len(strategy)} tasks')

    # Load data files (all are lists)
    monsters = load_json('monsters.json')
    item_spawns = load_json('item_spawns.json')
    scenery = load_json('scenery.json')

    # Build pre-indexed name -> coords lookup
    print('  Building name index...', end=' ', flush=True)
    name_index = build_name_index([monsters, item_spawns, scenery])
    print(f'{len(name_index)} unique names')

    # Free raw data (save memory)
    del monsters, item_spawns, scenery

    # Process each task
    result = {}
    tasks_with_locations = 0
    total_clusters = 0

    for struct_id, entry in strategy.items():
        points_str = entry.get('points', '')
        search_str = entry.get('search', '')

        clusters = []

        # Try explicit points first
        if points_str and points_str.strip().lower() != 'n/a':
            coords = parse_points(points_str)
            if coords:
                clusters = cluster_coords(coords)

        # Fall back to search
        if not clusters and search_str and search_str.strip().lower() != 'n/a':
            coords = search_index(search_str, name_index)
            if coords:
                clusters = cluster_coords(coords)

        if clusters:
            result[struct_id] = clusters
            tasks_with_locations += 1
            total_clusters += len(clusters)

    elapsed = time.time() - t0
    print(f'\nResults:')
    print(f'  Tasks with locations: {tasks_with_locations}/{len(strategy)}')
    print(f'  Total location clusters: {total_clusters}')
    print(f'  Processing time: {elapsed:.1f}s')

    # Write output
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, 'w', encoding='utf-8') as f:
        json.dump(result, f, separators=(',', ':'))
    output_size = os.path.getsize(OUTPUT)
    print(f'  Output: {OUTPUT}')
    print(f'  Size: {output_size / 1024:.1f} KB')

    # Also copy strategy.json (it's only ~21KB)
    shutil.copy2(os.path.join(LMAP_DATA, 'strategy.json'), STRATEGY_COPY)
    print(f'  Copied strategy.json ({os.path.getsize(STRATEGY_COPY) / 1024:.1f} KB)')

    print('\nDone!')


if __name__ == '__main__':
    main()
