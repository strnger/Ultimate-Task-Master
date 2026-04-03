# Ultimate Task Master - TODO

Status: Active development on feature/scraper-integration
Last Updated: Post-Current Plan feature completion

---

## ✅ Completed

- [x] Scrollbar clipping fix (PANEL_WIDTH + SCROLLBAR_WIDTH)
- [x] TaskRowPanel rewrite (tasks-tracker pattern)
- [x] +/- toggle buttons for plan management
- [x] Unified Plan tab (TaskRowPanel + LocationButtonsPanel)
- [x] Icon caching (static finals, eliminated disk reads)
- [x] "Show on map" toggle (replaces coordinate button overflow)
- [x] Orange 14px world map dots (was grey 10px)
- [x] Right-click "Pin location" on world map
- [x] Mouse-to-worldpoint calculation (WorldMap API)
- [x] Maven compilation verified via CLI

---

## Documentation Needed

### 1. task_locations.json Generation
- Document LeaguesMap scraping process
- Clustering algorithm details
- Coverage: 187/1589 tasks (11.8%%)

### 2. Scrollbar Clipping Bug History
- 5-commit fix saga
- Final solution: PANEL_WIDTH + SCROLLBAR_WIDTH

### 3. tasks-tracker Layout Pattern
- Why we adopted their proven patterns
- BorderLayout vs BoxLayout decisions

### 4. ConfigManager Schema
- PlanService persistence format

### 5. LocationCluster Count Field
- Confidence relationship

---

## Features / Enhancements

### High Priority
- [ ] Test coverage
- [ ] Varp-based detection
- [ ] Error handling
- [ ] Performance profiling

### Medium Priority
- [ ] HTTP crowdsourcing
- [ ] Location confidence scoring
- [ ] Plan reordering (drag-drop)
- [ ] Plan export/import
- [ ] Multi-pin per task
- [ ] Distance calculation
- [ ] Completion progress

### Low Priority
- [ ] Custom icons
- [ ] Theme support
- [ ] Better tooltips
- [ ] Keyboard shortcuts

---

## Technical Debt

- [ ] No CI/CD
- [ ] Feature branch merge strategy
- [ ] Resource file versioning
- [ ] temp/ cleanup
- [ ] Server status unclear
- [ ] Build warnings check

---

## Known Bugs

- [x] ~~Scrollbar clipping~~ (fixed)
- [x] ~~Grey map dots~~ (fixed: now orange 14px)
- [x] ~~Coordinate buttons overflow~~ (fixed: "Show on map" toggle)
- [x] ~~LocationButtonsPanel alignment shift~~ (fixed: LEFT_ALIGNMENT)
- [ ] Deprecation warnings in plugin (cosmetic, not blocking)

---

## Research

- [ ] full-task-scraper integration
- [ ] LeaguesMap data quality
- [ ] Task graph analysis
- [ ] AFK ratings

---

## User Requested Tweaks

