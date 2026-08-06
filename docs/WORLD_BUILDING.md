# World Building, Custom Lands and Raids

## Two-layer model

Creating new content has two separate parts:

### 1. Client/cache map work

Used for:

- terrain
- height
- water
- floor textures
- walls
- buildings
- trees
- rocks
- bridges
- stairs
- decorative objects

### 2. Server/gameplay work

Used for:

- NPC spawns
- combat
- dialogues
- doors
- ladders
- shops
- quests
- drops
- boss mechanics
- puzzles
- rewards
- raid state

## Map tools

Investigate old-cache-compatible tooling such as:

- RSPSi variants/plugins with 317/pre-400 support
- Scape Editor / pre-400 cache tooling

Exact compatibility must be tested against the 2006Scape client/cache.

## Custom continent strategy

Do not initially modify the main RuneScape map.

Create custom land at unused coordinates.

Example:

```text
Port Sarim
   │
   └── boat
         │
         ▼
     teleport
         │
         ▼
Custom Island
x=7000 y=7000
```

This isolates custom content and reduces map conflicts.

## Reuse existing assets

Prefer composing new areas from existing 2006 models:

- walls
- doors
- roofs
- trees
- rocks
- furniture
- statues
- dungeon pieces
- terrain textures

This preserves the authentic visual style.

## Example raid

```text
Entrance
   │
   ▼
Guardian Room
   │
   ▼
Puzzle Room
   │
   ▼
Crypt / Miniboss
   │
   ▼
Final Boss
   │
   ▼
Reward Chest
```

The map editor makes the rooms.

TypeScript/Java controls progression.

## Instances

Single-player mode does not initially require real raid instancing.

A raid can simply exist at reserved coordinates.

Later:

```text
RaidTemplate
     ↓
copy region/chunks
     ↓
PrivateRaidInstance
     ↓
player + simulated party
```

## Developer mode

A custom in-game developer mode would be extremely useful.

Example:

```text
::devmode
```

Clicking an object could show:

```text
Object ID: 1276
X: 3212
Y: 3424
Plane: 0
Type: 10
Rotation: 2
Region: 12854
```

NPC inspection:

```text
NPC ID: 1
X: 3222
Y: 3218
Combat: 2
Spawn radius: 4
```

This would dramatically improve world/content creation.
