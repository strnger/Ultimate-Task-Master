"""
Scrape Raging Echoes League tasks from the OSRS Wiki.
Outputs clean JSON for the tracker to consume.
"""
import json
import re
import requests
from bs4 import BeautifulSoup

URL = "https://oldschool.runescape.wiki/w/Raging_Echoes_League/Tasks"

TIER_MAP = {
    10: "Easy",
    30: "Medium",
    80: "Hard",
    200: "Elite",
    400: "Master",
}


def scrape_tasks():
    print("Fetching page...")
    resp = requests.get(URL, headers={"User-Agent": "FatCat-LeagueTracker/1.0"})
    resp.raise_for_status()

    soup = BeautifulSoup(resp.text, "html.parser")

    # The main task table is the big sortable one with ~1500+ rows
    table = soup.find("table", class_="tbrl-tasks")
    if not table:
        raise RuntimeError("Could not find the task table (class='tbrl-tasks')!")

    rows = table.find_all("tr")
    print(f"Found {len(rows)} rows (including header)")

    # Verify headers: Area, Name, Task, Requirements, Pts, Comp%
    header_row = rows[0]
    headers = [th.get_text(strip=True) for th in header_row.find_all("th")]
    print(f"Headers: {headers}")

    tasks = []
    for row in rows[1:]:
        cells = row.find_all("td")
        if len(cells) < 6:
            continue

        # Area — stored in data-sort-value, or text
        area = cells[0].get("data-sort-value", "") or cells[0].get_text(strip=True)

        # Task name
        name = cells[1].get_text(strip=True)

        # Task description
        description = cells[2].get_text(strip=True)

        # Requirements — extract skill/level data from scp spans
        req_cell = cells[3]
        skill_spans = req_cell.find_all("span", class_="scp")
        requirements = []
        if skill_spans:
            for span in skill_spans:
                skill = span.get("data-skill", "")
                level = span.get("data-level", "")
                if skill and level:
                    requirements.append({"skill": skill, "level": int(level)})
        req_text = req_cell.get_text(strip=True)
        if req_text == "N/A":
            req_text = None

        # Points (determines tier)
        pts_text = cells[4].get("data-sort-value", "") or cells[4].get_text(strip=True)
        try:
            points = int(pts_text)
        except ValueError:
            points = 0
        tier = TIER_MAP.get(points, f"Unknown({pts_text})")

        # Completion percentage
        comp_text = cells[5].get_text(strip=True)
        completion = None
        match = re.search(r"([\d.]+)%", comp_text)
        if match:
            completion = float(match.group(1))

        tasks.append({
            "area": area if area else "General",
            "name": name,
            "description": description,
            "requirements_text": req_text,
            "requirements": requirements if requirements else None,
            "points": points,
            "tier": tier,
            "completion_pct": completion,
        })

    return tasks


if __name__ == "__main__":
    tasks = scrape_tasks()
    print(f"\nScraped {len(tasks)} tasks!")

    # Summary stats
    tiers = {}
    areas = {}
    for t in tasks:
        tiers[t["tier"]] = tiers.get(t["tier"], 0) + 1
        areas[t["area"]] = areas.get(t["area"], 0) + 1

    print("\n--- By Tier ---")
    for tier, count in sorted(tiers.items(), key=lambda x: x[1], reverse=True):
        print(f"  {tier}: {count}")

    print("\n--- By Area ---")
    for area, count in sorted(areas.items(), key=lambda x: x[1], reverse=True):
        print(f"  {area}: {count}")

    # Save to JSON
    output_path = "raging_echoes_tasks.json"
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(tasks, f, indent=2, ensure_ascii=False)
    print(f"\nSaved {len(tasks)} tasks to {output_path}")

    # Show a few samples
    print("\n--- Sample Tasks ---")
    for t in tasks[:5]:
        print(json.dumps(t, indent=2))
