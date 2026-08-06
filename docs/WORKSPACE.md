# Workspace boundaries

SingleScape is maintained as one repository with explicit internal boundaries:

```text
engine/              Java client, server, cache, and legacy runtime infrastructure
content/             TypeScript-authored game content and the JavaScript bridge entrypoint
tools/               Optional local tooling checkout; ignored by this repository
docs/                Project and architecture documentation
scripts/             Root build, run, clean, and environment commands
engine/server/data/  Server cache/config plus local runtime state
```

## Ownership

The root repository owns the game source under these boundaries. `engine/` contains
the Java source imported from the former `2006Scape/` checkout, including the
SingleScape-specific bridge and client/runtime changes already present there.
`tools/` is intentionally excluded from this repository and build; when present,
it is a separate local tooling checkout that exports artifacts into documented
engine/cache locations.

There is one authoritative TypeScript source tree: `content/src/`. Its generated
output is `content/dist/`. The Java bridge uses `singlescape.contentDir` first,
then `SINGLESCAPE_CONTENT_DIR`, then defaults to the root-relative
`content/dist` path. The former embedded example tree has been retired and is
not a second content source of truth.

## Working rules

- Run root commands from this directory.
- Use `./scripts/build.sh` for the complete TypeScript + Java build.
- Use `./scripts/run-server.sh` and `./scripts/run-client.sh` for local runtime.
- Treat `engine/` as engine code and keep game content in `content/` whenever the
  existing bridge API allows it.
- Treat `tools/` as optional local development tooling; its outputs must be
  explicitly exported into a documented engine/cache location before the game
  consumes them.
- Treat mutable runtime state under `engine/server/data/`—including bans,
  characters, logs, and local settings—as local state, not source code.
- Generated directories such as `target/`, `content/dist/`, and dependency stores
  are ignored and must not be edited by hand.
