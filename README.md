# Nimbus-Nova-MC

A cooperative Minecraft Mars mission trainer for NASA Space Apps 2026's **Build a Junior Astronaut Mission Trainer** challenge.

The mod includes an authored Mars outpost, a shared power/oxygen simulation, three crew roles, a custom HUD/console with an end-of-mission debrief, interactive equipment blocks, a driveable six-wheel rover, two astronaut NPCs, and original space items with custom textures. The dust storm's solar supply follows real Opportunity dust-storm records; battery, oxygen, and energy rates are authored game balance. See [CREDITS.md](CREDITS.md) for sources and [TODO.md](TODO.md) for remaining work such as two-client testing.

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

`build` includes mission, content, terrain, imported-format, dust-profile, and NPC-dialogue self-checks. Run `./gradlew runClientGameTest` for the isolated single-client runtime test and render captures. It creates its own disposable test world and writes screenshots under `build/run/clientGameTest/screenshots`; it does not use your normal saves. The client test uses the version-pinned experimental Fabric test API and requires a graphical environment.

Elsewhere, set `JAVA_HOME` to your JDK 25 installation. Windows uses `gradlew.bat build` and `gradlew.bat runClient`. In IntelliJ, open this folder as a Gradle project and select JDK 25 for both the project and Gradle JVM.

The distributable is `build/libs/nimbus-nova-mc-0.1.0.jar`; the `-sources.jar` is for developers. Install Fabric Loader for Minecraft 26.3, then place our mod and Fabric API for 26.3 in your instance's `mods` folder. Obtain Minecraft separately.

`./gradlew runServer` starts a development server. Read and accept Minecraft's EULA yourself in `run/eula.txt` before running it.

The title screen has a **Join session** screen for the planned nickname and session code flow. Joining is disabled until session creation, code resolution, and the Minecraft server bridge are implemented.

## Play The Darkening Sky

Host setup: use a fresh test world with cheats enabled, run `/mars create`, then `/mars join`. For an existing generated outpost, run `/mars upgrade` before joining. The upgrade refuses unknown changes at mission anchors and does not reset the world.

1. **Follow the amber hologram to Commander Vega.** WASD moves, the mouse looks, and right-click talks or uses equipment. Your HUD shows the next action and its direction/distance.
2. **Choose a role** in the tablet's Crew tab. Power, life support, and weather can prepare in parallel. A local operator can perform every job for solo training.
3. **Collect your kit from the marked locker.** It issues a tablet, tool, emergency canister, and equips empty suit slots without replacing your existing armor.
4. **Prepare the outpost.** Power engineers rotate the 3×3 cable board to connect SOLAR to BASE. Life support adjusts intake, pump, and vent until pressure and flow stay in the green bands for two seconds, then starts the recycler. The field crew collects the marked sample and returns it to the analyzer or Commander Vega.
5. **Launch when all five crew checks are complete.** Talk to Vega or use Start mission. Preparation has no hazard timer.
6. **Respond to the solar warning.** Follow the shelter marker. The warning begins at 00:10; particles arrive at 00:40 and clear at 01:05. The HUD confirms when you are sheltered. Shared exposure does not multiply with class size.
7. **Keep working through the dust.** Dust reduces sunlight from 01:15. At 01:20 an authored relay fault stops science; the engineer repairs a fresh circuit and chooses when to power research again. Weather can use the terminal to send forecasts; life support watches air and has two shared emergency refills.
8. **Finish the mission.** Keep power and air above zero until 04:00 and complete 90 seconds of powered science for full success. Survival alone earns partial success. The report explains the outcome and supports another attempt.

The world uses holographic labels, marker effects, colored routes, automatic doors, and interactive models instead of sign clutter. The tablet opens with **J**. Its Mission page shows the current task; Crew holds roles/controls, Science explains sources, and Report reviews the attempt. Active oxygen-station clicks open explicit controls instead of silently turning the recycler off.

NASA observations determine the selected Opportunity storm profile. The relay fault, pressure puzzle, radiation counter, mission timing, and resource capacities are teaching/gameplay assumptions, not calibrated astronaut procedures.

### Commands

| Command | Permission / behavior |
| --- | --- |
| `/mission` | Show current mission status |
| `/mission role power` | Choose power engineer before start |
| `/mission role life_support` | Choose life-support engineer before start |
| `/mission role weather` | Choose weather officer before start |
| `/mission role observer` | Choose observer before start |
| `/mission forecast` | Weather officer or operator; broadcast the current sunlight and 30-second forecast |
| `/mission storm 2018` / `2007` / `random` | Operator; pin the real Opportunity storm for the next missions, or pick one at random on each reset (default) |
| `/mission assign <player> <role>` | Operator assignment before start or while paused |
| `/mission start` | Operator; launch after crew preparation is complete |
| `/mission debug_start` | Operator development bypass for test fixtures |
| `/mission pause` / `/mission resume` | Operator; freeze/unfreeze simulation time and resources |
| `/mission abort` | Operator; end a running/paused mission |
| `/mission reset` | Operator; start a fresh ready state |
| `/mission equipment research true` / `false` | Power engineer or operator; enable/disable research while active |
| `/mission equipment oxygen true` / `false` | Life-support engineer or operator; enable/disable oxygen production while active |
| `/mission station research` | Operator; bind the block you are looking at as a research toggle station |
| `/mission station oxygen` | Operator; bind the block you are looking at as an oxygen toggle station |
| `/mission station canister` | Operator; bind the block you are looking at as an emergency oxygen canister station |
| `/mission station clear` | Operator; remove every station binding |
| `/rover spawn` | Operator; place a rover on nearby clear, dry ground |
| `/npc spawn guide` / `/npc spawn engineer` | Operator; place an astronaut NPC on nearby clear, dry ground |

A station binding uses an existing block within five blocks and replaces no terrain. Its permitted engineer can right-click it to toggle the associated system. Choose plain blocks so normal container or redstone interactions do not confuse the demonstration. Bindings are saved to `nimbus_nova_stations.txt` in the world folder and reload on server start.

## Mars map and equipment

`/mars create` requires operator permission. `/mars join` and `/mars leave` are available to students after creation. The map lives in the separate `nimbus_nova:mars_training` dimension. Construction checks for existing blocks before editing and stores a world-local marker. Keep normal world backups. If the server restarts during a visit, `/mars leave` searches for a safe location near the overworld spawn when the original return position is unavailable.

Items appear in the **Tools & Utilities** creative tab, or operators can use `/give @s nimbus_nova:<id>`:

| ID | Current behavior |
| --- | --- |
| `mission_tablet` | Right-click to open the real mission console |
| `oxygen_canister` | Life-support engineer/operator restores up to 8 shared oxygen units during an active mission, from a pool of two canisters per mission; consumes one on a successful survival-mode use |
| `engineering_tool` | Iron-grade pickaxe with original multitool artwork |
| `mars_sample` | Collectible sample; analysis mechanics remain planned |
| `space_helmet`, `space_chestplate`, `space_leggings`, `space_boots` | Wearable expedition armor with original item and worn textures; suit oxygen, pressure, and radiation protection remain planned |

The greenhouse and radiation shelter are scenery for future systems; they do not yet grow crops or model radiation. The new rover is driveable: right-click to board, use movement keys to steer/drive, jump to brake, and sneak to exit. It stops when unoccupied. There is no fuel, energy, cargo, or expedition-objective simulation for the rover yet.

Commander Vega and Engineer Sol are original astronaut NPCs with idle/head-look behavior and paginated, mission-state-aware conversation. Right-click to talk. Their briefing and hints support the existing mission; a task minigame and NPC-controlled quest progression remain planned.

The Fabric runtime test checks tablet opening, canister consumption/refusal at full reserve, suit/item rendering, map travel, equipment-block power interactions, rover movement/stopping, NPC dialogue, and denial of unauthorized equipment changes/reset. Captures show the rendered UI and content. This is a single-client game test, not a two-player classroom test.

### Interface and rendering

Mod screens use the Nova renderer for rounded surfaces, strokes, restrained glow, and Geist distance-field typography. Screen backgrounds apply native blur once, and custom text fields support cursor/selection behavior. The dust particles use a dedicated soft-particle shader with fog and transparency support. These paths have passed a runtime render test; physical two-client interaction remains an open check.

### Optional local terrain import

The optional local landscape uses a **cropped, scaled Minecraft interpretation of Mars MOLA elevation data**, through [MarsCraft](https://www.planetminecraft.com/project/mars-4521195/) by proteinguy. The creator states a 1:600 scale and requests credit, noncommercial use, and permission for servers. This is not a raw scan or a 1:1 reconstruction. Our 512×512 crop comes from source region `(17,18)`, containing the creator-captioned Valles Marineris coordinate `(9000,9250)`. The camp pad, block coloring, boundary transitions, and terrain outside the crop are authored. The derived data remains local and outside MIT; creator permission is still required for classroom/server use.

The importer reads one 7.89 MiB region entry, not the whole archive, and writes a 50,094-byte height file plus provenance. Original surface values span 59–230 blocks. A constant −121 vertical offset preserves that relative relief within the destination world; the camp is flattened within radius 80 and blended out to radius 128. The outer 16 cells blend into procedural terrain. No source player data or entities are imported.

For a fresh, private local preview using the downloaded archive:

```sh
python3 tools/import_marscraft.py --self-test
python3 tools/import_marscraft.py --archive "$HOME/Downloads/Marscraft Updated.zip" --region 17 18 --output .local/marscraft
./gradlew runClient -PmarsHeightmap=.local/marscraft/marscraft-heights.nms.gz
```

Create a **new test world**, then run `/mars create` and `/mars join`. `/mars` reports the active terrain source. For the isolated imported-world smoke test, run `./gradlew runClientGameTest -PmarsHeightmap=.local/marscraft/marscraft-heights.nms.gz`. Without the option, the mod uses its procedural fallback. Existing chunks and saves are never regenerated automatically; changing generation sources mid-world can create seams.


## Mars movement

The Mars dimension uses **38% of normal Minecraft gravity** for living entities, dropped items, and other entities using the native gravity API. Jumps rise higher and stay airborne longer, falls are slower, and fall damage scales with the lower gravity. Walking controls and jump impulse stay familiar. The rover uses the same gravity path. Returning to another dimension restores its normal physics automatically.

This applies the Mars/Earth gravity ratio to Minecraft physics; it is a gameplay approximation, not a calibrated astronaut or atmosphere simulation. Native drag, creative flight, and no-gravity behavior remain intact. Runtime checks measured jumps of about 1.25 blocks/12 ticks on the Overworld and 2.69 blocks/27 ticks on Mars, verified client/server agreement, and checked restoration on return.

## Current limits

- One transient mission runs per server. Resource state and roles do not survive a server restart; station bindings do. The authored outpost persists; joining it rebinds its built-in consoles.
- Rejoining the same running server retains the player's role in memory. The mission pauses when no players remain; an operator resumes it.
- The prototype counts a fixed crew demand rather than deriving oxygen use from individual suits or player count.
- Equipment uses normalized game units. Only the dust storm's sunlight curve comes from NASA data. There is no claim of a calibrated habitat or radiation model.
- The mission drives both solar supply and a bounded dust-particle effect; atmosphere, pressure, radiation, and a physically calibrated storm model remain outside the simulation.
- Teacher session codes, browser controls, persistent reports, task minigames, suit life support, rover resource costs, and controllable spacecraft remain roadmap work unless their status is explicitly completed in [TODO.md](TODO.md). NPC dialogue and basic rover driving are implemented.
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

The panel can start, stop, and restart a Minecraft server that it started. Start the panel with JDK 25 available in `JAVA_HOME` or `PATH`, and accept the Minecraft EULA in `run/eula.txt` before starting the server. Stop and restart send Minecraft's `stop` command so the world is saved. A server started outside the panel must be stopped separately before the panel can manage it. If the panel exits while its server is running, stop that server separately before starting it again from the panel.

Create a teacher account on the sign-in screen. Usernames use 3–32 letters, digits, dots, underscores, or hyphens; a password is required. Accounts are stored in `teacher-panel/data/teachers.json` with salted password hashes. Keep a backup of that file; deleting it deletes the accounts. Sessions expire after 12 hours or when the server restarts. There is no password reset yet.

The connection indicator checks only whether the configured TCP port is reachable. To use the roster, mission controls, and crew messages, start both the Fabric server and the panel with the same `BRIDGE_TOKEN` (at least 32 characters), and start the panel with `BRIDGE_URL=http://127.0.0.1:3001`. The bridge listens only on localhost. Each signed-in teacher controls the same server and can see and kick any connected player; session isolation is not implemented. Registration is open to anyone with access to this local service; it is not ready for public hosting or classroom use.

### Mission backend foundation

The local Node service also exposes a first bridge API. Configure `TEACHER_TOKEN` and `BRIDGE_TOKEN` as separate random secrets of at least 32 characters, and set `BRIDGE_URL` to the local HTTP address of the Minecraft bridge, for example `http://127.0.0.1:3001`. Keep the tokens out of source control. The service binds to loopback and only accepts a loopback bridge address; this is for local development, not remote classroom deployment.

- `GET /api/mission` requires `Authorization: Bearer <TEACHER_TOKEN>` and forwards to `GET /mission` on the bridge.
- `POST /api/commands` requires the same authorization and a JSON body with UUID strings `id` and `sessionId`, an `action` (`start`, `pause`, `resume`, `end`, or `assign_role`), and an optional `parameters` object. It forwards to `POST /commands` on the bridge.
- The backend authenticates to the bridge with `Authorization: Bearer <BRIDGE_TOKEN>` and passes its JSON response and status back to the teacher. Missing configuration fails closed; an unreachable bridge returns an error.

The dashboard reads the server's mission state and can start, pause, resume, abort, or reset an ended mission. Starting requires at least one connected player. The message box sends text to all connected players. **Create outpost** runs the same guarded operation as `/mars create` in the current server world; it does not create a separate save. Command IDs are deduplicated in server memory; they do not survive a server restart. The legacy `/api/commands` endpoint described above still has no corresponding bridge route. The backend does not yet create or reserve sessions, assign roles, or persist reports.

Original code is MIT licensed. Minecraft retains its own license. Independent student project; no NASA, Mojang, or Microsoft endorsement.
