# Handoff: Brimhaven Agility Arena — Obstacle Implementation

**Date:** 2026-07-30  
**Repo:** `/Users/jonas/Developer/RS`  
**Next focus:** Implement remaining Brimhaven Agility Arena obstacles (not just plank/rope). Use patterns and pitfalls below.

---

## Goal

OSRS-style Brimhaven Agility Arena (2024 rebalance). Tracker: [GitHub Issue #490](https://github.com/2006-Scape/2006Scape/issues/490). Design notes were discussed against OSRS wiki; compare with `2006scape_differences/` if present in repo.

---

## What already works (uncommitted unless noted)

| Feature | Status | Main files |
|--------|--------|------------|
| Ladder down/up | ✅ Committed (`ff920b8`) | `BrimhavenAgilityArena.java` |
| Dispenser timer + minimap arrow | ✅ Working | `BrimhavenAgilityArena.java`, `GameEngine.process()` |
| Ticket tag + XP + voucher | ✅ Working | `BrimhavenAgilityArena.java`, `StaticItemList.BRIMHAVEN_VOUCHER` |
| Planks `3570`–`3577` | ✅ Working | `BrimhavenAgilityCourse.java` |
| Rope swing `3566` | ✅ Working (anim + landing) | `BrimhavenAgilityCourse.java`, `ClickObject.java` |
| Cap'n Izzy / Jackie shop / NPC spawns | ❌ Not done | — |
| All other obstacles | ❌ Not done | — |

**Wiring pattern (all obstacles):**

1. `ObjectsActions.firstClickObject` / `secondClickObject` → early call `BrimhavenAgilityArena.handleObject(...)` (already in place).
2. Global tick: `BrimhavenAgilityArena.process()` from `GameEngine` (after Castle Wars).
3. Per-obstacle logic belongs in `BrimhavenAgilityCourse.java` (or split later if it grows).

---

## Arena geometry (critical for every obstacle)

Do **not** assume players stand on the 5×5 pillar grid coordinates.

| Constant | Value | Notes |
|----------|-------|--------|
| Plane | `3` | `BrimhavenAgilityArena.ARENA_PLANE` |
| Grid origin | `2761, 9546` | Pillar corners |
| Grid spacing | `11` | Distance between platforms |
| Grid size | `5×5` | 24 dispensers + exit at `2805, 9590` |
| Ladder landing | `2805, 9589` | **Off-grid** (`9589 % 11 ≠ 0`) |
| Course rows | Often `…9589`, `…9584`, etc. | Obstacle tiles ≠ snapped pillar centers |

**Rule:** Preserve the player’s offset on the axis *perpendicular* to travel. Move `±11` on the travel axis toward/through the obstacle. **Do not** `snapPlatformX/Y` for landing unless the obstacle truly targets pillar centers (planks use snap for walk destination — different case).

**Rope swing object tiles (verified in cache):** `2804,9584` · `2806,9585` · `2766,9569` · `2767,9567` — all object id `3566`.

---

## Click pipeline pitfalls (applies to all obstacles)

### 1. No debug message = click never reached handler

Object debug (`ObjectId: … Objectclick = 1`) is emitted in `ClickObject.completeObjectClick` **after** `onObjectReached`.

`onObjectReached` waits until the player can walk adjacent to the object tile. Obstacles over gaps (rope, maybe ledges) are **not walkable** → handler never runs.

**Fix pattern:** `BrimhavenAgilityCourse.canInteractFromDistance(...)` + early accept in `ClickObject.onObjectReached` (already done for rope `3566`). **Extend this** for any obstacle where the object sits over void/gap.

### 2. Client menu vs server packet

- Cyan (`@cya@`) in menu = object click (good).
- Red (`@lre@`) = item/ground item — not an object handler issue.
- Rope uses first option **“Swing-on”** → packet `132` / first click.

### 3. Left-click default may be Examine

Right-click the correct option when testing. Plank debug appeared because user used the walk option, not because left-click always fires first action.

---

## Animation workflow (all obstacles)

**Cache has no animation names** — only numeric IDs in `seq.dat` (client: `engine/client/src/main/java/Animation.java`).

**In-game test (admin):** `::anim <id>`

| ID | Use |
|----|-----|
| `751` | **Brimhaven rope swing** (confirmed on this client) |
| `775` | Generic “swing on rope” — wrong for this revision |
| `3067` | Jump/land — used by other courses, **not** Brimhaven rope |
| `762` | Plank walk (`PLANK_WALK_ANIMATION`) |

Community name lists: [Rune-Server emote/GFX list](https://rune-server.org/threads/emote-gfx-id-list.272373/) — verify every ID with `::anim` on this cache.

**Movement + animation patterns in this engine:**

| Pattern | When | Example in repo |
|---------|------|-----------------|
| `playerWalkIndex` + `walkTo2` over distance | Walkable path on platform | Planks |
| `Agility.walk(dx, dy, emote, -1)` + short delay + `movePlayer` | 1-tile emote then teleport | Barbarian/Ape rope |
| `startAnimation(id)` + interpolated `movePlayer` over N ticks | Gap crossing without ForceMovement | Brimhaven rope (current) |
| `movePlayer` + `startAnimation` same tick | Instant snap with anim | `ObjectsActions` cases `4551`/`4558` |

**This engine has no ForceMovement packet.** OSRS rope arc is approximated; perfect fidelity would need new client packet support (RS-633 `ForceMovement` reference was fetched during session — not in this codebase).

---

## Rope swing reference implementation (template for gaps)

File: `engine/server/src/main/java/com/rs2/game/content/minigames/brimhavenagilityarena/BrimhavenAgilityCourse.java`

- `canInteractFromDistance`: object `3566` + `isInArena` + valid destination.
- `resolveRopeSwingDestination`: `dest = player ± 11` on dominant axis toward object; **keep other axis**.
- `handleRopeSwing`: `startAnimation(751)`, 4 ticks interpolated `movePlayer`, then reset walk state + XP (`20`).

---

## Remaining obstacles — object IDs (`StaticObjectList.java`)

Implement in `BrimhavenAgilityCourse.handleObject` switch (or dedicated methods):

| Obstacle | Object ID(s) | OSRS XP (approx) | Notes |
|----------|--------------|------------------|--------|
| Low wall | `3565` | 8 | |
| Log balance | `3553`–`3558` | 12 | Multiple variants |
| Balancing ledge | `3559`–`3562` | 16 | |
| Balancing rope | `3551`, `3552` | 10 | RS-633 used anims `1121`/`1122` + forced steps — different revision |
| Monkey bars | `3563`, `3564` | 14 | |
| Pillar | `3578` (verify in map) | 18 | |
| Blade | `3567`–`3569` | 0 (damage) | Timer-driven; `BladesManager` idea in RS-633 ref |
| Floor spikes | TBD | 24 | Level 20 |
| Pressure pad | TBD | 26 | Level 20 |
| Hand holds | `3583` | 22 | Level 20 |
| Spinning blades | TBD | 28 | Level 40 |
| Darts | TBD | 30 | Level 40 |
| Climbing rope (after fall) | `3610` | — | Recovery from pit |
| Ticket dispenser | `3581`, `3608` | — | Already in `BrimhavenAgilityArena` |

Copy interaction patterns from existing agility courses:

- `BarbarianAgility.java`, `WildernessAgility.java`, `GnomeAgility.java`, `ApeAtollAgility.java`, `PyramidAgility.java`
- `Agility.java` → `getAnimation(objectId)`, `walk()`, `destinationReached()`

---

## Dispenser / rewards (already implemented)

- Rotation: ~100 ticks (~60s), random pillar, `createObjectHints` yellow arrow.
- Tag: active dispenser only, one tag per rotation, XP `min(300, 30 * (agility/10))`, items ticket `2996` + voucher `29482`.
- `isInArena(player)` uses coordinate bounds + plane 3 (not only `inBrimhavenAgilityArena` flag).

---

## Dev commands

| Command | Purpose |
|---------|---------|
| `::tele 2805 9589 3` | Arena center / post-ladder |
| `::tele 2809 3193 0` | Surface entrance |
| `::pos` | Current coords |
| `::anim <id>` | Test animation on self |
| `::object <id>` | Spawn test object at feet |

Admin debug on object click: `playerRights == 3` or `debugMode` in `ClickObject` / `ObjectFirstClick` plugin.

---

## Testing checklist (per new obstacle)

1. Right-click → correct cyan object option appears.
2. Server debug line appears (`ObjectId`, `ObjectX`, `ObjectY`).
3. Animation tested via `::anim` before hardcoding.
4. Landing tile: preserve offset; don’t snap to pillar center unless intended.
5. If object over gap: add `canInteractFromDistance` bypass in `ClickObject`.
6. `stopPlayerPacket` / `getPlayerAction()` lock during obstacle; unlock after.
7. Award XP per OSRS wiki values.

---

## Git state

- Only ladder commit `ff920b8` was committed at session start; **Phase 2 + obstacles are likely still uncommitted**.
- `ObjectsActions.java` in working tree may include unrelated script-bridge changes — review before commit.

---

## Suggested skills

| Skill | When to use |
|-------|-------------|
| `resume-plan` | If continuing from a formal multi-phase plan in `docs/` or `plans/` |
| `research` | OSRS wiki mechanics for a specific obstacle (XP, fail rate, anim hints) |
| `prototype` | Quick spike for anim + movement feel before wiring handler |
| `diagnosing-bugs` | “Click does nothing” / no debug / wrong landing |
| `code-review` | Before committing large `BrimhavenAgilityCourse` expansion |
| `update-docs` | After obstacle batch is stable — update `docs/` if arena is documented |
| `generate-handoff` | End of next session if work continues across agents |

---

## Key file paths

```
engine/server/src/main/java/com/rs2/game/content/minigames/brimhavenagilityarena/
  BrimhavenAgilityArena.java    # timers, dispensers, ladders, arena bounds
  BrimhavenAgilityCourse.java   # obstacle handlers (extend here)

engine/server/src/main/java/com/rs2/net/packets/impl/ClickObject.java
  # onObjectReached → canInteractFromDistance bypass

engine/server/src/main/java/com/rs2/game/objects/ObjectsActions.java
  # firstClickObject / secondClickObject hooks

engine/server/src/main/java/com/rs2/game/content/StaticObjectList.java
  # object ID constants

engine/server/data/cache/               # map + loc/seq via client cache
engine/client/src/main/java/Animation.java
```

---

## Lessons learned (don’t repeat)

1. **Grid snap ≠ player position** — landing on pillar centers looks wrong.
2. **Gap objects need walk-to bypass** — otherwise zero server-side clicks.
3. **Verify animations on client** — IDs differ by revision; `751` not `775` for rope here.
4. **Don’t use `Agility.walk` full gap distance** — player walks over void; use anim + staged `movePlayer`.
5. **Distinguish client examine vs first action** when debugging “nothing happens”.
