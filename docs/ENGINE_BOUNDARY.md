# Engine boundary

The Java code lives under `engine/` and is organized by runtime role:

```text
engine/
├── client/   legacy 2006-era client, cache access, rendering, input
└── server/   world loop, networking, entities, persistence, scripting bridge
```

## What belongs in the engine

Keep Java changes in `engine/` when they are infrastructure or when the
TypeScript API cannot express the required behavior:

- network protocol and packet handling
- world ticking and entity lifecycle
- collision, movement, pathfinding, and cache loading
- core combat and player/NPC state
- persistence primitives
- the JavaScript runtime host and narrow bridge adapters
- client/cache decoding and synchronization

## SingleScape-specific engine changes

The current engine includes a small set of product-specific changes carried over
from the imported source, including the GraalJS bridge, scripted interaction
dispatch, dialogue callbacks, developer commands, client window/input fixes, and
selected baseline gameplay fixes. These should remain easy to identify in code
review and should expose stable APIs to `content/` rather than pulling content
definitions back into Java.

## What does not belong in the engine

Normal game content belongs in root `content/`:

- quests, dialogue, NPC/object interactions
- bosses, raids, areas, drops, shops, and gathering resources
- bot profiles and goals
- custom events and expansion-specific definitions

Asset conversion and map authoring belong in the optional external tooling project
(often kept locally at `tools/`). Tooling is not part of the runtime build; its
outputs must be explicitly exported into a cache/data location before the engine
consumes them.

## Remaining Java-only boundaries

Even with the full TypeScript content platform, the following stay in Java.
A content author should not need to touch them for normal content, but they
cannot be expressed through the public SDK:

- network protocol, packet decoding, and client synchronization
- the world tick loop, collision, pathfinding, and cache loading
- core combat resolution, entity lifecycle, and NPC/AI state machines
- persistence primitives (`PlayerSave`, the script-state codec, quarantine)
- the GraalJS host, the bridge wrappers, the route registry, and the
  runtime activation transaction
- the deterministic RNG owners, the transactional drop/reward engines, and
  the resource/raid/boss/area/shop runtimes (all Java-owned; TypeScript only
  supplies the descriptors and callbacks)
- the admin diagnostics commands and the reserved `scripts`/`reload`/
  `scriptdir` aliases

The TypeScript layer owns descriptors, content composition, and
interaction/lifecycle orchestration. See
[typescript-content-authoring.md](./typescript-content-authoring.md) for what
an author can express entirely in TypeScript and
[typescript-content-migration.md](./typescript-content-migration.md) for how
to move existing content onto it.

## Build contract

Run `./scripts/build.sh` from the workspace root. It builds `content/` first,
then runs the Maven reactor in `engine/`. Runtime launchers use artifacts from
`engine/server/target/` and `engine/client/target/` and pass the root content
directory through `SINGLESCAPE_CONTENT_DIR`.
