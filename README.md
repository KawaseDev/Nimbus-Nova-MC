# Nimbus-Nova-MC

# Nimbus Nova MC — mission trainer

Minecraft Fabric starter for NASA Space Apps 2026's **Build a Junior Astronaut Mission Trainer**. Current state: a shared four-minute dust-storm mission with roles, HUD, and console. Its solar supply follows NASA's record of the Opportunity rover in the 2018 Mars dust storm; see [CREDITS.md](CREDITS.md) for sources and game assumptions.

## Development

Requires **Minecraft 26.3 and JDK 25**. Pinned dependencies: Fabric Loader 0.20.1, Fabric API 0.165.2+26.3, Loom 1.19.4, Gradle 9.8.2.

On this Mac, use the existing Homebrew JDK:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew build
./gradlew runClient
```

Elsewhere, set `JAVA_HOME` to your JDK 25 installation. Windows uses `gradlew.bat build` and `gradlew.bat runClient`. In IntelliJ, open this folder as a Gradle project and select JDK 25 for both the project and Gradle JVM.

The distributable is `build/libs/nimbus-nova-mc-0.1.0.jar`; the `-sources.jar` is for developers. Install Fabric Loader for Minecraft 26.3, then place our mod and Fabric API for 26.3 in your instance's `mods` folder. Obtain Minecraft separately.

`./gradlew runServer` starts a development server. Read and accept Minecraft's EULA yourself in `run/eula.txt` before running a server.

## Layout

- `src/main/java/cc/nimbusnova/mc/NimbusNovaMod.java`: shared entry point.
- `src/client/java/cc/nimbusnova/mc/client/NimbusNovaClient.java`: client entry point. Keep client imports out of common code.
- `src/main/resources/fabric.mod.json`: identity, runtime requirements, and AI metadata.
- `gradle.properties`: pinned dependency/project versions.
- [rules.md](rules.md): official sources, requirements, and submission checklist.
- [PLAN.md](PLAN.md): detailed product plan, roles, NPC dialogue, task minigames, missions, and five-person delivery roadmap.
- [CREDITS.md](CREDITS.md): references, licenses, and AI assistance disclosure.

## Next milestone

Connect the teacher session, two Minecraft clients, and one role action that changes a shared resource. Then build the first Mars dust-storm mission with NPC guidance, task minigames, suits, a rover, and a decision debrief. See [PLAN.md](PLAN.md) for milestones and the expanded spaceflight roadmap. This describes future work.

## Teacher panel (early development)

With Node.js 18 or newer installed, run `node teacher-panel/server.js` and open <http://127.0.0.1:3000>. The panel listens only on the local machine. Set `PANEL_PORT`, `MINECRAFT_HOST`, or `MINECRAFT_PORT` before starting it to change the defaults (3000, 127.0.0.1, and 25565). Run `node --test teacher-panel/server.test.js` to check the panel.

Create a teacher account on the sign-in screen. Usernames use 3–32 letters, digits, dots, underscores, or hyphens; a password is required. Accounts are stored in `teacher-panel/data/teachers.json` with salted password hashes. Keep a backup of that file; deleting it deletes the accounts. Sessions expire after 12 hours or when the server restarts. There is no password reset yet.

The connection indicator checks only whether the configured TCP port is reachable; it does not verify a Minecraft server or connect to mission state. The roster and session controls need the planned authenticated Fabric server bridge and are not implemented yet. Registration is open to anyone with access to this local service; it is not ready for public hosting or classroom use.

### Mission backend foundation

The local Node service also exposes a first bridge API. Configure `TEACHER_TOKEN` and `BRIDGE_TOKEN` as separate random secrets of at least 32 characters, and set `BRIDGE_URL` to the local HTTP address of the Minecraft bridge, for example `http://127.0.0.1:3001`. Keep the tokens out of source control. The service binds to loopback and only accepts a loopback bridge address; this is for local development, not remote classroom deployment.

- `GET /api/mission` requires `Authorization: Bearer <TEACHER_TOKEN>` and forwards to `GET /mission` on the bridge.
- `POST /api/commands` requires the same authorization and a JSON body with UUID strings `id` and `sessionId`, an `action` (`start`, `pause`, `resume`, `end`, or `assign_role`), and an optional `parameters` object. It forwards to `POST /commands` on the bridge.
- The backend authenticates to the bridge with `Authorization: Bearer <BRIDGE_TOKEN>` and passes its JSON response and status back to the teacher. Missing configuration fails closed; an unreachable bridge returns an error.

The Fabric bridge is not implemented. It must authenticate the backend, own mission state, validate session ownership and each action and its parameters, and reject duplicate command IDs before applying effects. Teacher accounts do not yet authorize these bridge endpoints; they still require the separate token. The dashboard does not yet call them. The backend does not yet create or reserve sessions, assign roles, or persist reports.

Original code is MIT licensed. Minecraft retains its own license. Independent student project; no NASA, Mojang, or Microsoft endorsement.
