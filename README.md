# Nimbus-Nova-MC

A cooperative Minecraft Mars mission trainer for NASA Space Apps 2026's **Build a Junior Astronaut Mission Trainer** challenge.

The mod includes an authored Mars outpost, shared power/oxygen simulation, two crew roles, a responsive HUD/console, functional equipment stations, and eight original space items with custom textures. Build, simulation, asset checks, and a single-client Fabric runtime smoke test pass. The current four-minute dust event uses **authored game balance**, not NASA measurements. Two-client testing and NASA evidence integration remain pending; see [TODO.md](TODO.md).

The teacher backend and admin panel belong to another teammate. Local mod development uses operator commands and does not depend on that platform being available.

## Build and run

Requires **Minecraft Java Edition 26.3 and JDK 25**. Pinned dependencies: Fabric Loader 0.20.1, Fabric API 0.165.2+26.3, Loom 1.19.4, Gradle 9.8.2.

On this Mac, use the existing Homebrew JDK:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew build
./gradlew runClient
```

`build` includes `missionSelfTest` and `contentAssetTest`. Run `./gradlew runClientGameTest` for the isolated single-client runtime test and render captures. It creates its own disposable test world and writes screenshots under `build/run/clientGameTest/screenshots`; it does not use your normal saves. The client test uses the version-pinned experimental Fabric test API and requires a graphical environment.

Elsewhere, set `JAVA_HOME` to your JDK 25 installation. Windows uses `gradlew.bat build` and `gradlew.bat runClient`. In IntelliJ, open this folder as a Gradle project and select JDK 25 for both the project and Gradle JVM.

The distributable is `build/libs/nimbus-nova-mc-0.1.0.jar`; the `-sources.jar` is for developers. Install Fabric Loader for Minecraft 26.3, then place our mod and Fabric API for 26.3 in your instance's `mods` folder. Obtain Minecraft separately.

`./gradlew runServer` starts a development server. Read and accept Minecraft's EULA yourself in `run/eula.txt` before running it.

The title screen has a **Join session** screen for the planned nickname and session code flow. Joining is disabled until session creation, code resolution, and the Minecraft server bridge are implemented.

## Try the prototype mission

1. Join a world/server with Nimbus Nova installed. Use a test world with cheats enabled, or a server account with gamemaster/operator permission, for Mission Control commands.
2. As an operator, run `/mars create` once to build Aster Outpost. Use `/mars join` to enter and `/mars leave` to return. Then press **J** to open the mission console. The key is rebindable in Minecraft's controls.
3. Before starting, choose **Power engineer** or **Life support** in the Systems tab. Two players can take different roles; an operator can control both systems for solo testing.
4. Start from the console or run `/mission start`.
5. Keep battery and oxygen above zero for four minutes. The dust event reduces solar generation from **01:15 to 03:15**.
6. Complete at least **90 seconds of powered research** for full success. Survival with insufficient research earns partial success; switching research off forever cannot earn full success.
7. Pause to discuss decisions, resume to continue, and reset before another attempt.

The map contains a habitat, landing pad, power yard, greenhouse, shelter, static rover workshop, and marked paths. Research and oxygen consoles are bound automatically. `/mars create` preserves an existing outpost; an interrupted construction claim blocks automatic rebuilding. The map has authored terrain and no NASA geographic-accuracy claim.

The HUD shows shared reserves, net energy rate, research progress, your role, the remaining mission time, and warnings. The console has Overview, Systems, and Briefing tabs. Equipment controls affect the server simulation; they are not separate client-side resource values.

### Commands

| Command | Permission / behavior |
| --- | --- |
| `/mission` | Show current mission status |
| `/mission role power` | Choose power engineer before start |
| `/mission role life_support` | Choose life-support engineer before start |
| `/mission role observer` | Choose observer before start |
| `/mission assign <player> <role>` | Operator assignment before start or while paused |
| `/mission start` | Operator; begin a ready mission |
| `/mission pause` / `/mission resume` | Operator; freeze/unfreeze simulation time and resources |
| `/mission abort` | Operator; end a running/paused mission |
| `/mission reset` | Operator; start a fresh ready state |
| `/mission equipment research true` / `false` | Power engineer or operator; enable/disable research while active |
| `/mission equipment oxygen true` / `false` | Life-support engineer or operator; enable/disable oxygen production while active |
| `/mission station research` | Operator; bind the block you are looking at as a research toggle station |
| `/mission station oxygen` | Operator; bind the block you are looking at as an oxygen toggle station |

A station binding uses an existing block within five blocks and replaces no terrain. Its permitted engineer can right-click it to toggle the associated system. Choose plain blocks so normal container or redstone interactions do not confuse the demonstration.

## Mars map and equipment

`/mars create` requires operator permission. `/mars join` and `/mars leave` are available to students after creation. The map lives in the separate `nimbus_nova:mars_training` dimension. Construction checks for existing blocks before editing and stores a world-local marker. Keep normal world backups. If the server restarts during a visit, `/mars leave` searches for a safe location near the overworld spawn when the original return position is unavailable.

Items appear in the **Tools & Utilities** creative tab, or operators can use `/give @s nimbus_nova:<id>`:

| ID | Current behavior |
| --- | --- |
| `mission_tablet` | Right-click to open the real mission console |
| `oxygen_canister` | Life-support engineer/operator restores up to 25 shared oxygen units during an active mission; consumes one on a successful survival-mode use |
| `engineering_tool` | Iron-grade pickaxe with original multitool artwork |
| `mars_sample` | Collectible sample; analysis mechanics remain planned |
| `space_helmet`, `space_chestplate`, `space_leggings`, `space_boots` | Wearable expedition armor with original item and worn textures; suit oxygen, pressure, and radiation protection remain planned |

The greenhouse, radiation shelter, and static rover are authored scenery for future systems. They do not yet grow crops, model radiation, or drive. The runtime smoke test checks tablet opening, canister consumption/refusal at full reserve, suit/item rendering, map travel, station callbacks, and denial of an unauthorized equipment change/reset. It is not a two-player classroom test.

## Current limits

- One transient mission runs per server. Resource state, roles, and manually bound stations do not survive a server restart. The authored outpost persists; joining it rebinds its built-in consoles.
- Rejoining the same running server retains the player's role in memory. The mission pauses when no players remain; an operator resumes it.
- The prototype counts a fixed crew demand rather than deriving oxygen use from individual suits or player count.
- Equipment uses normalized game units. There is no claim of a calibrated habitat or radiation model.
- The current dust event changes the resource model; a visual weather effect is a separate feature.
- Teacher session codes, browser controls, persistent reports, NPC dialogue, task minigames, suit life support, and controllable vehicles remain roadmap work unless their status is explicitly completed in [TODO.md](TODO.md).
- Minecraft's proprietary dependency remains an organizer eligibility question. Public mod source alone does not resolve it.

## Project documents

- [TODO.md](TODO.md): batches, verification evidence, and remaining work.
- [PLAN.md](PLAN.md): product direction, systems, NPC/minigame design, and five-person roadmap.
- [JUDGE_REVIEW.md](JUDGE_REVIEW.md): strict provisional idea review and resulting improvements.
- [rules.md](rules.md): official competition references and submission checklist.
- [CREDITS.md](CREDITS.md): sources, dependency licenses, and AI assistance disclosure.

Common simulation and command code lives in `src/main/java/cc/nimbusnova/mc/mission`. Client HUD and console code lives in `src/client/java/cc/nimbusnova/mc/client`. Keep client imports out of common code. The eventual backend must consume the same server-owned mission state rather than recreate the simulation.

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
