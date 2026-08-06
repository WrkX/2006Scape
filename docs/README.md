# SingleScape — Project Concept

A self-hosted, single-player RuneScape-like game built on a 2006-era RuneScape server foundation.

## Core idea

Use **2006Scape** as the base because it offers:

- strong 2006-era authenticity
- a large amount of already-implemented content
- local/single-player operation
- an existing Java server and old-school client/cache
- a better starting point for a complete playable game than a cleaner-but-emptier engine

Then evolve it into a moddable single-player RPG with:

- persistent simulated players
- a living economy
- bot PKers and skillers
- custom lands
- custom quests
- bosses and raids
- selected OSRS-inspired quality-of-life/content
- new content written mostly in **TypeScript**

The goal is not to reproduce modern OSRS exactly.

The goal is:

> “What if 2006 RuneScape kept evolving as a single-player game?”

## Proposed stack

```text
2006-era Client / Cache
        │
        ▼
2006Scape Java Core
        │
        ├── networking
        ├── world ticking
        ├── movement
        ├── combat primitives
        ├── player/NPC state
        ├── persistence
        └── TypeScript bridge
                  │
                  ▼
             TypeScript SDK
        ├── quests
        ├── bosses
        ├── raids
        ├── NPC behavior
        ├── dialogues
        ├── drops
        ├── custom areas
        └── bot behavior
```

## Design principles

1. Preserve the 2006 visual identity.
2. Prefer existing old-school assets over importing modern-looking content.
3. Keep Java as infrastructure; put new gameplay in TypeScript where practical.
4. Simulated players should behave like actual accounts, not decorative NPCs.
5. New areas/content should be modular and removable.
6. Build for one human player first; multiplayer compatibility is optional.
7. Add OSRS ideas selectively rather than cloning OSRS wholesale.

See the other files for architecture, bots, world-building, TypeScript scripting, and roadmap.

## Docs index

| File | Purpose |
|------|---------|
| [WORKSPACE.md](./WORKSPACE.md) | Repository boundaries and ownership |
| [ENGINE_BOUNDARY.md](./ENGINE_BOUNDARY.md) | Java engine ownership and maintenance boundary |
| [DEVELOPMENT.md](./DEVELOPMENT.md) | Build, run, project layout |
| [KNOWN_ISSUES.md](./KNOWN_ISSUES.md) | Phase 0 bugs and workarounds |
| [ROADMAP.md](./ROADMAP.md) | Development phases |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | Target system design |
| [API_INVENTORY.md](./API_INVENTORY.md) | Generated TypeScript SDK API inventory (runtime globals + SDK barrel exports) |
| [TYPESCRIPT.md](./TYPESCRIPT.md) | TypeScript content model and current examples |
| [SCRIPT_BRIDGE.md](./SCRIPT_BRIDGE.md) | Exact GraalJS bridge, state, quest, lifecycle, reload, and declarative-runtime contracts |
| [typescript-content-authoring.md](./typescript-content-authoring.md) | How to author new game content with the public TypeScript SDK |
| [typescript-content-migration.md](./typescript-content-migration.md) | How to migrate legacy content onto the TypeScript platform |
