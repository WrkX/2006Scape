# SingleScape Engine

The Java engine contains the legacy 2006-era client and the game server. It is
one Maven reactor with two independently buildable modules:

```text
engine/
├── client/   legacy client, rendering, input, and cache access
└── server/   world, networking, persistence, and runtime services
```

## Build

From the workspace root, use the complete build:

```bash
./scripts/build.sh
```

To build only the Java modules:

```bash
cd engine
mvn -B clean package
```

## Run

Use the root launchers so the server receives the canonical TypeScript content
directory and the client/server working directories are configured correctly:

```bash
./scripts/run-server.sh
./scripts/run-client.sh
```

The server module can also be launched directly from `engine/server/` after the
content build. See `engine/server/README.md` and `engine/server/SCRIPTING.md`
for server-specific details.
