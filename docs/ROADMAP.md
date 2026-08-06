# Development Roadmap

## Phase 0 — Baseline ✅

- [x] fork 2006Scape
- [x] get client/server running locally
- [x] document build process
- [x] verify save/load
- [x] verify quests, combat and skills
- [x] catalogue known broken content

## Phase 1 — Clean development foundation ✅

- [x] create development branch
- [x] isolate custom content directory (`content/`)
- [x] document server architecture (command system, plugin system, packet flow)
- [x] add automated build/run scripts (`build.sh`, `run-server.sh`, `run-client.sh`, `clean.sh`, `dev-check.sh`)
- [x] add developer commands (`::devmode`, `::getobject`, `::stack`)
- [x] build out TypeScript SDK type system (types and builders exist; no runtime bridge yet — see Phase 2)
- [x] fix Escape key crash on login screen (Game.java)
- [x] fix macOS Retina mouse click offset (RSApplet.java)

## Phase 2 — TypeScript bridge ✅

- [x] select embedded JavaScript runtime (GraalJS candidate)
- [x] add TS build pipeline (`tsc` via pnpm, strict mode, zero errors)
- [x] design typed TypeScript SDK (30 files: Player, Boss, Quest, Raid, Bot, Dialogue, DropTable, Area, DevConsole — all with fluent builders and example content modules)
- [x] create safe Java ↔ JS API — wire the bridge
- [x] hot-reload content if practical

First scripted features (all designed, typed, and running via the bridge):

- [x] dialogue
- [x] object interaction
- [x] NPC interaction
- [x] custom drop table
- [x] simple quest

## Phase 3 — First custom area

Build a small island using only existing models. (partial — town, NPCs, shop, quest, and rewards are defined; dungeon and miniboss remain)

Include:

- [x] town
- [ ] dungeon
- [x] NPCs
- [x] shop
- [x] quest
- [ ] miniboss
- [x] reward

This validates the complete content pipeline.

## Phase 4 — Simulated players

Implement:

```text
SimulatedPlayer
BotBrain
GoalSelector
Navigation
Banking
Skilling
Combat
Persistence
```

Start with:

- [ ] woodcutting bot
- [ ] fishing bot
- [ ] mining bot
- [ ] melee combat bot

Then add persistent goals.

## Phase 5 — Economy

- [ ] simulated trading
- [ ] market prices
- [ ] bot buying/selling
- [ ] resource sinks
- [ ] equipment progression
- [ ] player/bot trade hubs

## Phase 6 — Wilderness

- [ ] persistent PK bots
- [ ] gear selection
- [ ] food/supply management
- [ ] target selection
- [ ] death/drop logic
- [ ] Wilderness activity

## Phase 7 — First raid

Create a permanent-coordinate solo raid:

- [ ] 3–4 rooms
- [ ] puzzle
- [ ] miniboss
- [ ] final boss
- [ ] reward table

Then allow simulated players to participate.

## Phase 8 — Expansion model

Make content modular:

```text
expansions/
├── dragon_island/
├── temple_of_zaros/
└── northern_wastes/
```

Each expansion owns:

- [ ] map data
- [ ] NPCs
- [ ] objects
- [ ] quests
- [ ] drops
- [ ] bosses
- [ ] scripts

## Long-term goal

Turn the project into a self-hostable single-player RuneScape sandbox where:

- the world persists
- simulated players progress
- the economy lives
- the Wilderness feels active
- new lands and raids are easy to author
- most game content is written in TypeScript
