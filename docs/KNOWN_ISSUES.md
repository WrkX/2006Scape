# Known Issues (Phase 0 Baseline)

Issues observed while running the stock 2006Scape client/server locally. These are upstream/client-runtime problems, not SingleScape custom code.

## Java version warning

**Symptom**

```text
Please downgrade to Java 8 to avoid problems! (AdoptOpenJDK.net)
```

**Cause**

The client checks `java.specification.version` on startup and warns if it is not Java 8 (`Game.java`, constructor).

**Severity**

Low. Warning only — the client can still run on Java 17.

**Workaround**

- Ignore for local testing, or
- Install Temurin 8 and point the client launch config / `run-client.sh` at Java 8.

The server is fine on Java 17. Client and server can use different Java versions.

---

## ~~Escape key crash before login~~ — FIXED

**Fix applied** (Game.java:11983): `closeOpenInterfaces()` is now guarded with
`if (!loggedIn) return;` at the top of the method body.  Pressing Escape on
the login screen is now harmless.  No more NullPointerException.

**Original symptom (kept for reference)**

```text
NullPointerException: Cannot invoke "ISAACRandomGen.getNextKey()" because "this.encryption" is null
  at Stream.createFrame(Stream.java:37)
  at Game.closeOpenInterfaces(Game.java:11983)
  at Game.keyPressed(Game.java:12652)
```

**Original cause:** Pressing Escape called `closeOpenInterfaces()`, which
sends a packet via `stream.createFrame(130)`.  The ISAAC cipher
(`stream.encryption`) was only initialised after a successful login
(~Game.java:6063).  On the login screen it was null.

---

## Mouse click offset on macOS — PARTIAL FIX

**Symptom**

Buttons and UI elements appear in one place, but clicks only register when the cursor is placed lower (typically ~20–30 px). Reported on macOS; not yet verified on Windows.

**Likely cause**

Combination of:

1. **Old Java AWT/Swing client** — fixed 765×503 logical resolution, bitmap blitted via `RSImageProducer.drawGraphics()`. No HiDPI-aware coordinate handling.
2. **macOS Retina scaling** — Java 17 on Retina displays can desync visual rendering from mouse coordinates in legacy AWT apps.
3. **Optional 25 px navbar** — `ClientSettings.SHOW_NAVBAR` adds a `BorderLayout.NORTH` bar (`RSApplet.java`). Unlikely to affect in-game coords (mouse listeners are on the applet), but worth disabling when testing.

The client uses raw `MouseEvent.getX()` / `getY()` with no scaling or inset correction (`RSApplet.java`).

**Severity**

Medium for Mac playtesting. Annoying but playable once you adapt.

**Old workarounds (kept for reference)**

1. Run with macOS UI-scale disabled:

   ```bash
   java -Dsun.java2d.uiScale=1 -jar client.jar -local
   ```

2. Disable the navbar:

   ```bash
   ./scripts/run-client.sh -local -no-nav
   ```

3. Use Java 8 for the client (best compatibility with this codebase).

4. Test on Windows — if clicks align there, it confirms a Mac/Java/AWT issue.

**Fix applied (SingleScape)**

`RSApplet.java` now refreshes the `graphics` field each frame via
`getGameComponent().getGraphics()` (disposing the previous instance) in the
game-loop tick before `processDrawing()`.  On macOS Java 17 the Graphics
obtained during `createClientFrame` can have a translated origin (Frame
title-bar inset), causing the Y-offset.  Verify by rebuilding and launching
`run-client.sh`.

---

## Discord / secrets messages on server start

**Symptom**

```text
Please open "data/secrets.json" file and enter your discord token bot there!
Discord Token Not Set So Bot Not Loaded
```

**Severity**

None for local single-player. Discord integration is optional.

---

## Phase 0 checklist

Use this file to track baseline findings:

- [x] Login / logout
- [x] Save / load character
- [x] Combat
- [x] Skills (pick 2–3)
- [x] One quest
- [ ] Mouse accuracy (Mac / Windows)
- [x] Client Java version (8 vs 17)
