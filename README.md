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

On Linux, if JDK 25 is installed in `~/.local/share/nimbus-nova/jdk-25`, set `JAVA_HOME` to that path before starting Gradle or the teacher panel.

The distributable is `build/libs/nimbus-nova-mc-0.1.0.jar`; the `-sources.jar` is for developers. Install Fabric Loader for Minecraft 26.3, then place our mod and Fabric API for 26.3 in your instance's `mods` folder. Obtain Minecraft separately.

`./gradlew runServer` starts a development server. Read and accept Minecraft's EULA yourself in `run/eula.txt` before running it.

Students can use **Join session** on the title screen. Enter a name, the teacher's session code, and the session server address shown in the teacher panel. On an offline test server, valid Minecraft names are used for the connection; other names appear in the teacher roster and receive a stable generated Minecraft name.

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

The solar warning, dust warning, and relay fault pause the simulation for an explanation and discussion. Mission Control confirms understanding before resuming. Earth–Mars transit and ISS remain planned.

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
| `/mission acknowledge` | Operator; confirm an educational pause before resuming |
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
| `mars_sample` | Collect at the marked exterior deposit and deliver to the analyzer during preparation |
| `space_helmet`, `space_chestplate`, `space_leggings`, `space_boots` | Wear all four pieces outside to seal personal life support. Air drains outside and refills in authored habitat zones. Suits do not replace the storm shelter. |

The greenhouse has decorative hydroponic trays; crop growth remains planned. The reinforced crew shelter is the protected zone during the authored solar-particle event. Its protection uses a fixed game zone, not calibrated radiation transport. The new rover is driveable: right-click to board, use movement keys to steer/drive, jump to brake, and sneak to exit. It stops when unoccupied. There is no fuel, energy, cargo, or expedition-objective simulation for the rover yet.

Commander Vega and Engineer Sol are original astronaut NPCs with idle/head-look behavior and paginated, mission-state-aware conversation. Right-click to talk. Their briefing and hints support the guided preparation, physical tasks, and active mission.

The Fabric runtime test checks tablet opening, canister consumption/refusal at full reserve, suit/item rendering, map travel, equipment-block power interactions, rover movement/stopping, NPC dialogue, and denial of unauthorized equipment changes/reset. Captures show the rendered UI and content. This is a single-client game test, not a two-player classroom test.

### Domed outpost and science labs

Fresh `/mars create` worlds include a transparent demonstration dome, continuous structural ribs, custom proximity portal frames and laser barriers, animated floor/service lights, hull panels, cable trunks, battery racks, tanks, cabinets, and hydroponic models. Four exits preserve access to exterior panels, the sample site, and rover routes. Solar arrays sit outside the dome. The 14 `scifi_*` blocks are available in Tools & Utilities; tank/rack/crop props are visual models, while the three `lesson_*` stations below have gameplay.

| Station | Location (x, y, z) | What students do and learn |
| --- | --- | --- |
| Airlock pressure lab | −1, 66, −10 | Seal the model hatches, vent/pump in steps to match the target, then cycle. Unsafe pressure differences reject cycling. Preparation is free practice; active cycles cost 2 shared oxygen units. This lab is separate from automatic walk-through doors. |
| Backup power reserve | 3, 66, 5 | Decide when to spend one of two shared reserve cells, each adding up to 10 energy units. Full batteries reject transfers. Practice spends no cells. Stored power cannot restore sunlight lost to dust. |
| Shielding experiment | 2, 66, 25 | Compare three material layers and watch the illustrative detector index and carried mass change. Glass is different from substantial shielding; values are authored teaching indices, not dosimetry or engineering specifications. |

The dome uses authored habitat-air zones; it is fictional architecture rather than a seal/pressure simulation, and it provides no radiation safety. The reinforced shelter remains the mission's protected game zone. Real engineering background is linked in [CREDITS.md](CREDITS.md).

Existing saves are preserved: `/mars upgrade` changes only recognized equipment/door anchors after checking them. `/mars dome` adds a shell only when its entire footprint is empty or already consists of that shell's blocks; it refuses occupied footprints. Use a fresh test world for the complete layout rather than deleting teammates' builds.

### Entrance scan and personal life support

New arrivals start outside the north dome entrance. Walk into the chamber and stand inside: both barriers close for a four-second dust-purge/suit scan, with air jets, sound, and a progress display. Once clear, the inner barrier admits the crew. The reverse cycle supports leaving; missing gear produces warnings without trapping a player in the chamber. This is an authored dust-removal sequence, not a medical virus detector or a claim about real laser airlocks.

**Base air** is the shared mission reserve. **Suit air** is personal: a complete four-piece suit loses 0.5 game units per second outside; an incomplete suit loses 5. Inside the dome/rooms or a cleared chamber, it recovers 10 per second. At zero air during an active mission, exposure causes 1 health damage per second. Preparation shows depletion/warnings without health damage. Teacher/incident pauses freeze suit air as well as shared resources. These rates and zones are game assumptions, not human physiology. Personal reserves reset per visit rather than persisting across disconnects.

Existing recognized base doors are migrated on server start or `/mars join`: their old plates are removed and proximity control starts automatically. The known original quartz counter is replaced with custom metal panels. Unknown blocks are preserved. `/mars upgrade` adds the new entrance chamber only if its footprint is clear or contains recognized original materials; it reports conflicts rather than overwriting builds. Restart the client/server to load changed Java behavior; asset reload alone cannot install it.

The rover has a translucent windscreen with a solid frame and a clear first-person driving view. Its movement controls are unchanged.

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

## Play Return Window: spacecraft rescue

The teacher panel also offers **Select Moon exploration prototype**, or an operator can use `/ship explore`. It reuses the spacecraft and adds simulated orbit capture, descent checks, landing, a suit check, sample selection, ascent and rendezvous. The lunar surface is currently represented at ship stations; it is not yet a walkable Moon environment or the complete classroom mission. The legacy Earth–Moon tablet remains a separate sequencing prototype.

Select **Return Window** in the teacher menu, or run `/ship select` as an operator. This creates the separate `nimbus_nova:return_window` dimension and brings the connected crew aboard. `/ship join` admits students who arrive later; J opens the ship tablet. End the current Mars/Moon attempt before selecting it, and use `/ship end` when switching to another mission.

The ship is imported from the team's newer `ananaslafelie-1794683726306.schem`: 26×31×82, with two decks and preserved ladder routes. [SHIP_LAYOUT.md](docs/SHIP_LAYOUT.md) documents station coordinates, doorway panels, provenance and import format. Source command blocks, inventories and sign NBT are omitted. Known unsupported decorative stairs/slabs receive documented shape-preserving substitutes; waterlogged furniture is made dry. Existing builds are checked before installation and never silently cleared. Re-importing a changed schematic requires a fresh test world; `/ship reset` resets the attempt, not the structure.

Choose the five roles before launch: power, life support, supplies, maintenance and pilot. Each student sees their system's readings; the crew shares the reserves. Operators can use every station for solo development. Normal station actions validate dimension, distance, role, revision and phase on the server. Roles survive a reconnect during the same server session; resetting clears assignments for another crew setup.

1. **Prepare without a timer.** Assemble correctly rated power cartridges at an engine manifold and choose repair spares or extra science cargo in storage. Optional galley, medical, plant and exercise activities improve readiness at a finite water/parts/energy cost.
2. **Dock using momentum.** Apply thruster impulses, coast and counter-thrust to stop inside the capture corridor. Being near the target is insufficient while still drifting. Maintenance tunes the communications carrier and gain without clipping the signal.
3. **Navigate by a star fix.** Identify the three stars matching the reference constellation rather than matching numerical dials.
4. **Isolate the emergency.** Retain life support while shedding nonessential engine loads. The engineering bay shows the authored fault with smoke and active equipment lights.
5. **Build the CO2 adapter.** Assemble the seal, housing, cartridge and fan in the correct connection order; run a pressure test and verify the seal before activating filtration. Adding oxygen does not remove CO2.
6. **Restart essential systems.** Reassemble the changed cartridge layout, then allocate heating to navigation and the air loop while preserving the return reserve.
7. **Make the return burn.** Ignite, observe the growing burn meter and cut off inside the six-to-eight-second model window. Maintenance verifies the return signal. Early/late burns consume reserves and require correction.

The active return window is six minutes; preparation and teacher/incident pauses do not consume it. Teachers read the NASA-linked incident cards, confirm understanding and resume. Full success requires safe return plus reserve/readiness targets; partial success and failure explain missed conditions. The student report summarizes consequences, and the authenticated teacher snapshot includes the decision journal. Ship mission state and reports remain in server memory; the imported map persists.

| Original marker | Replacement and activity |
| --- | --- |
| Lectern | Flight console: docking, constellation fix, return burn |
| Brewing stand | Galley/thermal module: ration metering and heating budget |
| Fletching table | Medical/filter bay: instrument diagnostics and CO2 adapter assembly |
| Note block | Crew intercom: up to two paused discussion breaks |
| Furnaces | Engine manifolds: power cartridges and emergency load isolation |
| Beacon | Science archive: classify observed specimens at an energy cost |
| Cartography table | Comms array: waveform/carrier reconstruction |
| Composter | Hydroponics: coupled moisture-balancing activity |
| Red wool | Custom punching bag: five physical hits complete a finite exercise set |
| Chest | Cargo locker: mass/balance packing of spares, kits and science |
| Lime wool | Substantial proximity portal frames and visible locked blue fields |

### Compartment emergencies and physical repairs

The ship now has ten authored compartments with local pressure, power, isolation and door modes. Existing schematic walls/doors remain, and hydroponics/recreation gain a guarded partition. Ordinary hull/floor blocks use vanilla materials; utility equipment keeps its custom models and textures. Existing imported hull materials are restored when selecting the ship, while unrelated player blocks are preserved.

Open **Room control** on the crew tablet/intercom, or use `/ship rooms`. AUTO uses proximity; OPEN and CLOSE set room door modes. Low-pressure isolation overrides ordinary opening. A right-click emergency crank provides five seconds of escape/access through a quarantined or unpowered gate. Door links stay on their own deck. Pressure values are authored room indices, not a seal-detection or gas-flow simulation.

The rescue now introduces two physical incidents:

- **Engineering power fault:** room equipment stops. A power/maintenance specialist collects a wire kit from storage and right-clicks the marked engine junction at `-8 82 -38`. One real kit is consumed and local power returns. Emergency/navigation illumination remains available.
- **Science hull impact:** a real hull cell at `11 77 -12` becomes a hole. Pressure falls, nearby doors isolate the room and unsuited occupants face exposure at low pressure. A maintenance/life-support specialist takes a repair plate and places it into that exact hole using a neighboring block face. One plate is consumed. The leak stops, and pressure recovers before the interlock releases.

**Sneak-right-click the storage locker** to issue the crew's one shared pool of four patch plates and two wire kits; carry/drop/share the actual items with specialists. Operators may test every role. Return completion is blocked while room emergencies remain unresolved. Reset/end restores only the scenario's owned hull cell and clears repair supplies. A saved ownership marker also recovers an interrupted breach after restart without overwriting an unknown player block; mission progress itself is still transient.

Operators can schedule the basic drills with `/ship fault ENGINEERING POWER` and `/ship fault SCIENCE BREACH`, or use the dashboard's drill buttons. The fault sites are deliberately authored for this first version; arbitrary-room impact placement is not implemented. Changed hull/approach cells refuse the breach instead of erasing builds. Inspect the control alert if a custom map blocks a scenario.

### Hands-on secondary activities and hold controls

The optional stations now require work before applying their finite resource effect:

| Activity | Interaction | What it teaches |
| --- | --- | --- |
| Cargo | Pack three bays within the mass limit and balance the outer bays, then secure the manifest | Mass distribution; repair supplies compete with science cargo |
| Galley | Meter the requested portions into three pouches, drain excess and seal the allocation | Several allocations share one water reserve |
| Plants | Balance four soil readings; a dose also wets the next bed through the common channel | Coupled systems and overwatering; drainage does not create drinking water |
| Medical equipment | Scan all three sensor traces, identify and replace the bad lead, then run a self-test | Check measurement quality before trusting an instrument |
| Science | Inspect specimen observations, assign ferrous/layered/porous records and archive verified classifications | Evidence-based classification rather than guessing labels |
| Exercise | Close the tablet and punch the actual bag five times with a short interval between hits | A paced physical task with a visible bag reaction and a finite water/readiness consequence |

Final commit controls charge while held. Hold the mouse button over the control, or focus it and hold **Space/Enter**, until the progress fill completes. Early release, pointer departure, focus loss, closing the tablet or a mission pause cancels unfinished work. The server checks the elapsed hold and station state before applying any resource change. Thrust adjustments and timed burn controls respond immediately.

Ration amounts, moisture indices, sensor signals and specimen properties are authored equipment exercises, not nutritional prescriptions, plant-growth measurements or medical diagnoses. Each completed activity applies its cost/reward only once per attempt.

Operator commands: `/ship select`, `/ship start`, `/ship pause`, `/ship acknowledge`, `/ship resume`, `/ship abort`, `/ship reset`, `/ship end`. Students use `/ship`, `/ship join` and `/ship leave`. Teacher controls use the existing authenticated bridge; Mars and the teammate's Moon prototypes retain their own controls.

This is an Apollo-13-inspired engineering lesson, not a reconstruction of its spacecraft or measurements. The historical life-support emergency is described in [NASA's Apollo 13 mission details](https://www.nasa.gov/missions/apollo/apollo-13-mission-details/). Capacities, synthetic signals, star patterns, cartridge ratings, timing windows and artificial cabin gravity are authored game assumptions. Flight happens through bounded instruments and mission state; the block structure is not a freely flyable vehicle.

## Current limits

- One transient mission runs per server. Resource state and roles do not survive a server restart; station bindings do. The authored outpost and imported ship persist; joining the outpost rebinds its built-in consoles.
- Rejoining the same running server retains the player's role in memory. The mission pauses when no players remain; an operator resumes it.
- The Mars base counts a fixed shared crew demand; personal EVA air is a separate authored reserve.
- Equipment uses normalized game units. Only the dust storm's sunlight curve comes from NASA data. There is no claim of a calibrated habitat or radiation model.
- Solar supply and dust effects are implemented. Habitat-air zones, shielding, docking and burn windows are authored teaching models rather than calibrated physical simulations.
- Task minigames, suit air, the spacecraft rescue, NPC dialogue and basic rover driving are implemented. Persistent reports, rover resource costs, multiple isolated crews and free spacecraft flight remain roadmap work.
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

**Update server** stops the panel-managed server, builds the current repository version, and starts it again using the existing world. Pull the desired version first; the button does not fetch code. Students must restart their clients with the same mod version. If the build fails, the server remains stopped and the saved world is kept. **Update outpost** applies supported Mars equipment and doorway upgrades only where the expected locations are safe; it stops if player blocks conflict. It does not rebuild existing spacecraft or regenerate terrain.

To test student joining on this computer, start the server in the panel. The session code appears automatically while the server is running and closes when it stops. Enter the displayed code and `127.0.0.1:3002` in the mod. The nickname appears in the teacher roster and is linked to the player's Minecraft identity while the server is running.

For a local network, set the server computer's LAN address in `run/server.properties` as `server-ip`, keep `online-mode=true`, and start the panel with these variables set to that same address:

```sh
export SESSION_BIND=192.168.1.10
export SESSION_ADDRESS=192.168.1.10:3002
export SESSION_GAME_ADDRESS=192.168.1.10:25565
export MINECRAFT_HOST=192.168.1.10
```

Replace `192.168.1.10` with the server computer's actual LAN address, and allow TCP ports 3002 and 25565 through its firewall. Students need Minecraft Java Edition, Fabric, and the mod. Online-mode servers also require valid Minecraft accounts. Offline mode is for trusted testing: names are not authenticated and anyone on the network can impersonate a player. The session code locates the server; it does not restrict direct server connections. The code expires after one hour and renews automatically while the server is running. Restarting the server issues a new code. Reservations and nicknames are held in memory and are lost when the corresponding process restarts. Starting a session does not reset the world.

Create a teacher account on the sign-in screen. Usernames use 3–32 letters, digits, dots, underscores, or hyphens; a password is required. Accounts are stored in `teacher-panel/data/teachers.json` with salted password hashes. Keep a backup of that file; deleting it deletes the accounts. Sessions expire after 12 hours or when the server restarts. There is no password reset yet.

The connection indicator checks only whether the configured TCP port is reachable. To use the roster, mission controls, and crew messages, start both the Fabric server and the panel with the same `BRIDGE_TOKEN` (at least 32 characters), and start the panel with `BRIDGE_URL=http://127.0.0.1:3001`. The bridge listens only on localhost. Each signed-in teacher controls the same server and can see and kick any connected player; session isolation is not implemented. Registration is open to anyone with access to this local service; it is not ready for public hosting or classroom use.

**View** on a player displays their Minecraft game as refreshed images, without audio. The student sees an in-game notice while their view is requested. Frames are kept in server memory until the player disconnects and are not saved to disk. Close the view to stop requesting frames. The student must have the updated mod installed.

### Mission backend foundation

The local Node service also exposes a first bridge API. Configure `TEACHER_TOKEN` and `BRIDGE_TOKEN` as separate random secrets of at least 32 characters, and set `BRIDGE_URL` to the local HTTP address of the Minecraft bridge, for example `http://127.0.0.1:3001`. Keep the tokens out of source control. The service binds to loopback and only accepts a loopback bridge address; this is for local development, not remote classroom deployment.

- `GET /api/mission` requires `Authorization: Bearer <TEACHER_TOKEN>` and forwards to `GET /mission` on the bridge.
- `POST /api/commands` requires the same authorization and a JSON body with UUID strings `id` and `sessionId`, an `action` (`start`, `pause`, `resume`, `end`, or `assign_role`), and an optional `parameters` object. It forwards to `POST /commands` on the bridge.
- The backend authenticates to the bridge with `Authorization: Bearer <BRIDGE_TOKEN>` and passes its JSON response and status back to the teacher. Missing configuration fails closed; an unreachable bridge returns an error.

The dashboard reads the server's mission state and can start, pause, resume, abort, or reset an ended mission. Starting requires at least one connected player. The message box sends text to all connected players. **Create outpost** runs the same guarded operation as `/mars create` in the current server world; it does not create a separate save. Command IDs are deduplicated in server memory; they do not survive a server restart. The legacy `/api/commands` endpoint described above still has no corresponding bridge route. Role assignment and persistent reports are not yet available in the panel.

Original code is MIT licensed. Minecraft retains its own license. Independent student project; no NASA, Mojang, or Microsoft endorsement.
