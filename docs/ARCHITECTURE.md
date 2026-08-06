# Architecture

## Base

Primary candidate:

**2006-Scape/2006Scape**

Why:

- strong inherited content base
- 2006-era world and mechanics
- local/single-player support
- existing client/cache/server stack
- better content completeness than starting from a modern clean engine

Reference projects:

- **RuneJS 435** — architecture/content API inspiration
- **2006SP / Progressive Singleplayer** — simulated-player design inspiration
- **2006Rebotted** — bot/client ecosystem reference
- **2009Scape** — modern content-organization and single-player reference

## Target architecture

```text
World
 ├── NPC
 └── Player
      ├── NetworkPlayer
      └── SimulatedPlayer
```

Both human and simulated players should share as much game logic as possible:

```text
inventory
bank
equipment
skills
movement
combat
item usage
shops
drops
quests
economy
```

Only their action source differs.

```text
NetworkPlayer
    └── commands come from client packets

SimulatedPlayer
    └── commands come from BotBrain
```

## Content modules

Avoid scattering custom content across the server.

Preferred structure:

```text
content/
├── core/
├── quests/
├── bosses/
├── areas/
│   └── dragon_island/
│       ├── index.ts
│       ├── npcs.ts
│       ├── objects.ts
│       ├── drops.ts
│       ├── shops.ts
│       └── quests/
└── raids/
    └── temple_of_zaros/
        ├── raid.ts
        ├── guardian-room.ts
        ├── puzzle-room.ts
        ├── boss.ts
        └── loot.ts
```

## Java responsibilities

Keep low-level systems in Java:

- protocol/networking
- packet decoding
- client synchronization
- world tick loop
- pathfinding
- collision
- entity lifecycle
- core combat engine
- cache integration
- persistence primitives
- scripting host

## TypeScript responsibilities

Move most custom gameplay into TypeScript:

- quests
- dialogues
- object interactions
- NPC interactions
- custom bosses
- raids
- drop tables
- custom regions
- bot goals
- economy logic
- custom events
