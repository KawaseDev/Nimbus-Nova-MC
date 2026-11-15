# Nimbus Nova implementation batches

Updated November 14, 2026. This file records delivery work, not the complete product wish list. A checked item means the change and its relevant verification passed. Code present but awaiting integration remains unchecked. The lead agent updates evidence and commit/push status after each batch.

## Batch 1: mission core and HUD

Status: core implementation committed as `21cad61`; build and simulation checks passed. Single-client runtime checks and render inspection passed. Two-client integration checks remain pending.

- [x] Integrate the server-owned mission state: ready, active, paused, success, partial success, failed, and aborted.
- [x] Verify power/oxygen calculations, depletion, dust boundaries, and frozen pause state with `missionSelfTest`.
- [x] Require survival plus 90 seconds of research for full success; verify survival-only partial success in the simulation self-check.
- [x] Implement role controls for power engineer, life-support engineer, and observer; test the role/equipment permission mapping.
- [ ] Verify role control requests with separate players in-game.
- [x] Verify operator start/pause/reset, role selection, and rejection of a non-operator reset in the client smoke test.
- [x] Integrate synchronized HUD, role/objective panels, warnings, and the J mission console; Java compilation passes.
- [x] Verify console controls and readable layout through real client render captures; fix the compact HUD overlap.
- [ ] Verify default/rebound physical key input in-game.
- [x] Implement physical station binding without terrain replacement.
- [x] Verify the role-authorized station callback and rejection of another role's equipment change in the running game test.
- [ ] Verify physical mouse input/raycasting at the stations with two players.
- [ ] Check reconnect, automatic empty-server pause, reset, and clean server shutdown.
- [x] Run `./gradlew build` including `missionSelfTest`.
- [x] Commit the working core batch as `21cad61`.
- [x] Confirm the remote push for this batch.

Evidence: the lead agent reports a successful build and simulation self-check covering resource balance, phases, dust, outcomes, idempotency, and role mapping. Runtime logs confirm that the client loaded a world without a mod crash. The native computer-control bridge cannot access the Java window. Fabric's own client test runner provided real render captures, which were inspected; the compact HUD was corrected. The test exercised console buttons and server station callbacks. Physical key/mouse input and a two-client test remain unperformed.

Commit: `21cad61`. GitHub authentication is configured. The core reached origin/main through merge commit `cc3bb99`, preserving the teammate's teacher-panel work.

## Batch 2: original Mars map, items, and textures

Status: integrated; build, asset checks, and single-client runtime smoke test passed. Restart recovery and multiplayer checks remain open.

- [x] Add a compact authored Mars training site with habitat, landing area, power yard, greenhouse, shelter, and marked routes.
- [x] Use safe, explicit map creation/travel; prevent accidental overwrite of existing builds.
- [x] Provide a safe arrival location and clearly label the site as fictional/authored terrain.
- [x] Add original space items with valid item/model/texture resources.
- [x] Give each functional item an honest description and a server-validated use where applicable.
- [x] Check texture dimensions, resource paths, registry IDs, and language entries.
- [x] Integrate registration with the common mod entry point.
- [x] Test dimension load, repeated-create preservation, map entry/exit, item use, and visible suit/item rendering.
- [ ] Check map persistence and safe return after a full server restart; explore the complete site manually.
- [x] Run the build and relevant checks, commit the batch, and confirm the push.

Evidence: `build` passes including simulation and eight-item resource checks. `runClientGameTest` creates the outpost, preserves a repeated creation, travels in/out, starts via console, toggles research, checks canister consumption/full-reserve refusal, opens the tablet, captures equipment rendering, exercises station role validation, rejects non-operator reset, and verifies pause/reset. The original batch contained scenic vehicle structures. Basic custom rover driving has since passed the tests described in batch 7; greenhouse/shelter mechanics and suit pressure/radiation remain planned.

Commit: `ce5b1fb` (map/equipment and client smoke test), integrated through `93a7e11`. Push to `origin/main` confirmed.

## Batch 3: integrated playable handoff

Status: single-client integration and render inspection passed; two-client classroom/recovery checks remain open.

- [x] Run the current build on the Mac and inspect real HUD/console captures at the default test GUI scale.
- [ ] Test one complete attempt from ready state through an outcome; compare the displayed reserves with server state.
- [x] Verify a balanced full-success strategy and survival-only partial success in `missionSelfTest`.
- [ ] Test two clients with different roles and confirm they see the same shared resource consequence.
- [ ] Confirm an observer cannot use another role's controls and a non-operator cannot start/reset the mission.
- [ ] Check error messages and recovery for disconnects or unavailable actions.
- [x] Document actual commands, remaining limits, and backend integration points in README.
- [x] Record the native Java window-access limitation and use inspected Fabric render captures; leave physical input testing open.
- [x] Commit the verified single-client integration and confirm its push; keep remaining manual/multiplayer checks open.

Evidence: single-client Fabric smoke test and inspected captures under `build/run/clientGameTest/screenshots`. No two-client or school-device claim.

Integration: `93a7e11`, including the teammate's student session screen. Build and client smoke test passed on the merged code; push confirmed.

## Batch 4: science and learning demonstration

Status: in progress. NASA evidence, labels, debrief, and strategy checks are done; learner testing and the demo remain.

- [x] Select one usable NASA observation or documented historical condition, with exact source, dates, site, units, and reuse terms. Opportunity, Perseverance Valley, June 1–10, 2018: 645 Wh → 21 Wh per sol, tau 10.5 (see CREDITS.md).
- [x] Document how that evidence changes solar supply or another visible mission condition. `DustStormProfile` scales solar output to 21/645 ≈ 3% at the storm peak.
- [x] Label authored capacities, normalization, time compression, and missing data. Briefing tab and CREDITS.md.
- [x] Add a debrief tied to actual crew actions, with source cards and the survival/research trade-off. Debrief tab opens for everyone when the mission ends.
- [ ] Ask two short questions before and after a small learner/educator test; record anonymous observations and limitations.
- [x] Confirm that at least two reasonable strategies can produce useful outcomes. `missionSelfTest` covers an early research cut (success), survival-only (partial), and a late cut with early restart (failure).
- [ ] Show observation → change → communication → decision → consequence → explanation in the demo.

Only the sunlight curve comes from NASA data; the four-minute event, rates, and capacities are game balance. Four data points from one rover are not a calibrated measurement series, and the dimming between them is a straight-line assumption.

## Batch 5: NPCs and task minigames

Status: two astronaut NPCs, state-aware dialogue, guided preparation and consequential task minigames are implemented and runtime-tested. Additional specialist roles remain roadmap work.

- [x] Add Commander Vega and Engineer Sol with original skins, idle/head-look animation, paginated dialogue, and mission-state-aware guidance; verify the conversation in the client game test.
- [x] Build one original task minigame and connect completion to a real equipment/resource consequence.
- [x] Validate role, distance, phase, supplies, and completion server-side; reject duplicate rewards.
- [ ] Provide readable text, color-independent cues, forgiving input targets, and optional timing assistance.
- [x] Add task-linked quest progression and connection/routing, checklist, and inspection/matching tasks as justified. The implemented engineer is an astronaut, not a maintenance robot.
- [ ] Add the remaining food/water, safety/maintenance, and commander/pilot roles with meaningful work.

## Batch 6: teacher platform integration and expanded space gameplay

Status: roadmap; teacher backend/admin panel has a separate teammate owner.

- [ ] Agree on mission snapshots, lifecycle actions, player identity, and report data with the backend owner.
- [ ] Connect authenticated teacher controls and session-code joining without duplicating resource calculations.
- [ ] Add multiple isolated crews and persistent session/report recovery when needed.
- [x] Implement and runtime-test basic six-wheel rover boarding, driving/steering, braking, stopping, and dismounting.
- [ ] Add rover energy/fuel/cargo and meaningful mission costs; add functional suit oxygen, airlocks, and outdoor hazards.
- [ ] Add bounded controllable shuttle flight before claiming free spacecraft piloting.
- [ ] Implement additional missions, then expose them as playable choices in the teacher library.

## Batch 7: custom rendering, equipment, NPCs, rover, and terrain

Status: implemented and pushed as `f556b0d`, integrated with the teammate tutorial through `40d78ca`. Build and client game test passed on the merged code. Physical two-client testing remains open.

- [x] Add the native Nova renderer and primitives for rounded surfaces, strokes, glow, and bundled Geist MTSDF typography; retain the font's OFL license.
- [x] Apply native once-per-screen blur and custom text fields with cursor/selection support to the mod UI.
- [x] Add interactive research and oxygen blocks with directional 3D models and animated on/off displays.
- [x] Add a custom six-wheel rover and `/rover spawn`; verify movement and stopping in the client game test.
- [x] Add two original astronaut NPCs and `/npc spawn guide|engineer`; verify paginated dialogue in the client game test.
- [x] Add bounded, mission-driven 3D dust particles with a dedicated soft/fog/transparency shader and reduced-particle support.
- [x] Add authored procedural hills/craters for new chunks while preserving existing generated terrain.
- [x] Run the integrated build/client game test, including content interactions and render captures. The lead reports a passing 27-second run and successful final dust-shader compilation/runtime.
- [x] Confirm the commit and remote push for this batch.
- [ ] Test the same experience with two physical clients, rebound inputs, and classroom hardware.

Evidence: the integrated Fabric client test covers rendered content, rover movement/stopping, NPC dialogue, equipment-block power interactions, and dust rendering. These automated interactions and captures do not establish physical two-player input or classroom performance.

Commit/push: `f556b0d` and merge `40d78ca` reached `origin/main`. The earlier UI renderer batch is `7bcbb25`; this batch adds the world/gameplay/effect integration above.

## Batch 8: bounded local terrain import

Status: local extraction, format tests, and imported-world runtime passed. Source archive and derived patch remain unpublished and excluded from Git; server permission is unresolved.

- [x] Validate the bounded archive extraction and height conversion with provenance and range checks.
- [x] Load the selected patch in a disposable runtime world and inspect its actual placement and relief.
- [x] Document final patch dimensions, coordinate transform, clipping, and fictional base-pad changes after verification.
- [x] Confirm that default authored terrain still works when the local patch is absent.
- [x] Keep external source files and derived local terrain outside the repository's MIT asset claim.
- [ ] Obtain the required creator permission before public classroom/server use or redistribution.

Source and permission detail: [README terrain-import note](README.md#optional-local-terrain-import). Evidence: importer self-check, loader validation, and `build runClientGameTest -PmarsHeightmap=.local/marscraft/marscraft-heights.nms.gz` passed. Runtime `/mars` reported the imported source and offset −121. One source region produced a 512², 50,094-byte local grid; source/transform details are in the README and ignored provenance file.

## Submission and eligibility

- [ ] Confirm the team's accepted local event and applicable local/global deadlines.
- [ ] Ask the Local Lead whether the proprietary Minecraft host dependency is acceptable for the intended award track.
- [ ] Keep licenses, source register, and AI disclosures current.
- [ ] Meet English/public-demo requirements and keep minors' likenesses/voices out of public material.
- [ ] Verify public judging links signed out and click Submit for Judging before the applicable deadline.

See [rules.md](rules.md) for governing official sources and [JUDGE_REVIEW.md](JUDGE_REVIEW.md) for the scoring weaknesses behind these priorities.

## Mars movement batch

- [x] Apply 0.38 gravity in the Mars dimension on both client and server without persistent attribute changes.
- [x] Scale fall damage and use the native gravity path for the rover.
- [x] Verify higher jumps, longer airtime, item gravity, no-gravity behavior, client/server agreement, and ordinary physics after leaving Mars in the runtime test.

Build and client game test passed. Physical multiplayer movement testing remains open.

## First-mission usability and interaction batch

Status: implemented. The actual client test passed preparation, both visual puzzles, sheltering, relay repair, full mission success, and report navigation. Integrated on main through `b42c8d3`, preserving teacher joining and live game view.

- [x] Record the conceptual Minecraft/Unity decision and research limitations.
- [x] Add a guided, server-owned preparation flow before starting the hazard timer.
- [x] Give each role a clear task with a destination, interaction hint, and progress feedback.
- [x] Add a visual circuit-routing board and coupled oxygen-valve balancing and sample collection/delivery.
- [x] Give the timed mission an active relay repair and fair shelter response time.
- [x] Replace the dense default HUD/console with a child-readable objective-first presentation.
- [x] Add automatic doors, holographic labels/projectors, routes, and functional custom models.
- [x] Support safe upgrades of known legacy outpost anchors without resetting player builds.
- [x] Test a full preparation-to-outcome mission and invalid/duplicate task actions.
- [x] Inspect the rendered task UI, navigation, doors, and first-mission report.
- [x] Commit and push working batches, preserving teammate changes.

Two-child observation remains necessary before claiming age-appropriate usability or educational effectiveness.

## Dome, equipment lessons, and rendering fixes

- [x] Add 14 custom structural/equipment blocks with original models and animated textures.
- [x] Build a domed fresh-world layout with exterior solar arrays and preserved mission exits.
- [x] Add pressure equalization, finite backup energy, and shielding/mass experiments.
- [x] Keep real science principles distinct from authored resource values and decorative props.
- [x] Fix overlapping/coplanar equipment faces, cropped UVs, disconnected dome ribs, and the locker door's adjacent-block intrusion.
- [x] Complete the final native-client station playthrough and inspect corrected geometry screenshots.
- [x] Push the final verified batch and sync teammate changes.
- [ ] Observe learners/educators before claiming usability or educational effectiveness.

Next proposed mission: Return Window, using a separate spacecraft map and departure objective; see PLAN.md. Do not mark it playable before its own mission loop exists.

Verification: Java 25 `build runClientGameTest` passed with the pressure/backup/shielding runtime checks and complete guided mission success. Inspected fresh native captures of the dome, airlocks, oxygen console, hollow open locker, air tanks, cabinet, greenhouse approach, and lab screens. Existing saves are not rebuilt automatically; reload assets/restart the client for model changes and use a fresh test world for the complete authored layout. Multiplayer/classroom testing remains open.

## First-level completion pass

- [x] Replace hinged panels/thin wire fields with substantial 3D proximity portal hardware.
- [x] Remove old authored pressure plates in existing saves without touching unrelated blocks.
- [x] Improve room materials and replace the white habitat counter.
- [x] Add a timed entrance dust-purge/suit scan with an interlocked entry sequence.
- [x] Add personal suit-air depletion outside and recovery inside; show missing-helmet warnings.
- [x] Fix the rover's obstructed first-person view.
- [x] Run the complete mission with the teammate's incident-learning pauses and inspect new native captures.
- [x] Push the integrated level and document the next mission brief.

This pass completes the first playable mission's requested systems. Additional specialist roles, crop growth, free spacecraft flight, multiple isolated crews and real classroom validation stay separately tracked; they are not silently claimed complete.

Completed verification: Java 25 `build runClientGameTest` passed atmosphere/scan, science stations, original preparation puzzles, incident-learning pauses, shelter/relay response, full mission success and report. Native captures confirm a blue closed field, clear open passage, no legacy plates, revised room surfaces, readable scan progress, personal-air warnings and the mounted first-person rover view. The merge with the teammate's initial Moon sequencing also passes `build` including its self-test. No multiplayer/classroom learning claim is made.

Next proposed build: **Moon Rescue: Return Window**, a separate spacecraft map using the teammate's `MoonMission.RESCUE` sequence. The scaffold exists; it is not a playable second mission yet.

## Spaceship mission: Return Window

- [x] Pull teammate changes and inspect both schematics; use the newer file with lime-wool door markers.
- [x] Preserve the two-deck layout, ladders and bridge spawn in a bounded, validated runtime import.
- [x] Replace the supplied pilot, galley, medical, intercom, engine, science, communications, plants, exercise and storage markers with original 3D equipment.
- [x] Replace lime panels and ordinary iron doors with proximity barriers; omit imported command/inventory NBT.
- [x] Add teacher selection/lifecycle controls and operator/student ship commands without removing the other missions.
- [x] Add five crew roles, server-validated station actions, finite supplies, teaching pauses and consequence reports.
- [x] Complete a native Minecraft rescue attempt and inspect station models, task screens and success report.
- [x] Replace the initial reused Mars puzzle mechanics with distinct spacecraft minigames requested during testing.
- [x] Inspect the revised minigames and new casing material in Minecraft; rerun the rescue sequence.
- [x] Finish the play guide/source notes and push the final batch after checking for another map update.

The initial ship playthrough passed circuits, alignment, emergency isolation, filtering, heating and safe return. The subsequent minigame redesign also passed its full native runtime check. Classroom-sized multiplayer and learner observation remain open.

Final ship verification: Java 25 `build runClientGameTest` passed the complete Mars regression and Return Window rescue, including the revised cartridge, momentum, constellation, signal, adapter and burn tasks. All station approaches were checked for actual visibility/collision clearance; native captures were inspected. Teacher relay tests pass 14/14, and the built-in browser checked selection/start/pause/acknowledgement/resume against an isolated fixture. Single-client verification does not establish classroom-sized multiplayer or learning effectiveness. Latest source remains `1794683726306.schem`; no newer remote map was present at the final check.

## Hull-panel seam repair

- [x] Seal all four panel corner strips and restrict face culling to actual block boundaries.
- [x] Inspect the ship corridor in Minecraft and pass the build/full client playthrough.

The rendering fix applies to existing hull blocks after resource reload; no map regeneration is required.

## Hands-on secondary station batch

- [x] Replace one-click galley, plants, medical, science and storage rewards with distinct visual activities.
- [x] Preserve the primary spacecraft minigames and apply each activity's resource effect only after successful completion.
- [x] Add mouse/keyboard hold controls with animated charge, early-release cancellation and server timing/state validation.
- [x] Add punching-bag pacing and a reactive model that returns to its resting pose.
- [x] Check invalid/incomplete work, duplicate rewards, minimum hold duration and expired tokens in model tests.
- [x] Test direct-click/early-finish rejection, physical Space-hold cancellation/completion and the full rescue in Minecraft.
- [x] Inspect the new activity/charge screenshots, update the guide and push the batch.

Java 25 `build runClientGameTest` passed. Native checks completed all five secondary activities, paced physical bag hits, primary rescue tasks and a successful return; captures include the cargo control at 50% charge. The interactive client was closed and its save completed as requested; the disposable test client also exited. Multiplayer/classroom testing remains open.

## Compartment emergency gameplay

- [x] Define ten authored room zones with pressure, power, isolation and AUTO/OPEN/CLOSED controls.
- [x] Add a guarded hydroponics/recreation partition and room-aware door interlocks/emergency cranks.
- [x] Restore ordinary structural materials and retain custom utility equipment.
- [x] Add control-room/tablet alerts and local pressure/power feedback.
- [x] Integrate engineering power loss and science hull impact into the rescue sequence.
- [x] Issue finite physical repair stores; consume a wire kit or placed hull plate for a validated repair.
- [x] Block return completion until faults/unsafe pressure are resolved; freeze hazards during pauses.
- [x] Preserve unknown builds and recover the scenario's owned breach after interruption.
- [x] Test door modes, real item repairs, pressure recovery and full rescue in the native client; preserve teammate exploration changes.

Native door and repair checks passed, followed by rescue success and the merged exploration prototype checks. The dashboard/repair captures were inspected. Room pressure is authored, not fluid simulation; real multiplayer/classroom testing remains open.

Final integration note: compartment door/repair/recovery and rescue checks passed in native Minecraft before the latest lunar-surface updates. All teammate changes were preserved, and the final merged `build compileGametestJava` plus model checks passed. The fresh post-merge client rerun twice stalled before world creation in native `SDL_GL_SwapWindow`; it was stopped. No multiplayer or final post-merge graphics certification is claimed.

## Six-mission delivery goal

- [x] Add the `/nimbus` library and unify teacher selection while retaining legacy commands.
- [x] Gate launch on authoritative preparation readiness and make repeats harmless.
- [x] Build the shelter, night and greenhouse worlds and their distinct mission loops; preserve Mars, Moon and spacecraft rescue.
- [x] Add applied crew checks in place of skipping through incident text, including a live supply/demand prediction.
- [x] Add ship windows, orbital sky, switchable room lights, bounded fault flicker and audible state cues.
- [x] Add actual two-layer hull breaches and rover cargo/energy/charging.
- [x] Obtain independent gameplay and education reviews; address paused repair item loss, misplacement, role routing and missing observation trade-offs.
- [x] Verify merged build, server gameplay and saved report retrieval.
- [x] Generate the importable classroom pack and verify its archive contents.
- [ ] Inspect rendered windowed/fullscreen playthroughs and test desktop input on the unlocked Mac.

See [gameplay review](docs/OVERNIGHT_GAMEPLAY_REVIEW.md) and [education review](docs/OVERNIGHT_EDUCATION_REVIEW.md) for findings at the beginning of the polish pass. A locked desktop can stall the Mac OpenGL startup before the client creates a world. Headless tests exercise actual Minecraft worlds and multiple embedded server actors; they do not verify GPU rendering, real classroom devices or educational outcomes. Organizer decisions, a real learner observation, and public submission remain separate.
