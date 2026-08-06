# Development Setup

How to build and run SingleScape locally. The repository is a single project with
separate internal boundaries for the engine and content. Asset tooling is optional
and may be kept as a separate local checkout.

## Prerequisites

- Java 17 (Temurin) — server and Maven build
- Java 8 (optional, recommended for client) — best legacy client compatibility
- Maven 3.9+
- pnpm 11.13.1 (the repository-pinned package manager)

## Build

```bash
pnpm install          # JS deps (content workspace)
./scripts/build.sh    # TypeScript + Maven
```

Or in Cursor: `Cmd+Shift+B` → **build-all**.

## Run

Terminal (two windows):

```bash
./scripts/run-server.sh
./scripts/run-client.sh
```

Cursor: **Run and Debug** → **Server + Client**.

The client connects to `localhost:43594` via the `-local` flag.

## Project layout

```text
engine/        ← Java server + client source (Maven)
content/       ← authoritative TypeScript content source
tools/         ← optional ignored local tooling checkout
scripts/       ← build/run helpers
.vscode/       ← Cursor tasks, launch configs (same format as VS Code)
```

## Java versions

| Component | Tested with | Notes |
|-----------|-------------|-------|
| Server | Java 17 | Set via `JAVA_HOME` in scripts |
| Client | Java 17 | Works with warnings; see [KNOWN_ISSUES.md](./KNOWN_ISSUES.md) |
| Client (ideal) | Java 8 | Fewer warnings, better Mac mouse behaviour |

## Known issues

See [KNOWN_ISSUES.md](./KNOWN_ISSUES.md) for client warnings and macOS mouse offset.
