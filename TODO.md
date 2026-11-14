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

Status: two astronaut NPCs and their state-aware dialogue are implemented and runtime-tested. Task minigames remain planned.

- [x] Add Commander Vega and Engineer Sol with original skins, idle/head-look animation, paginated dialogue, and mission-state-aware guidance; verify the conversation in the client game test.
- [ ] Build one original task minigame and connect completion to a real equipment/resource consequence.
- [ ] Validate role, distance, phase, supplies, and completion server-side; reject duplicate rewards.
- [ ] Provide readable text, color-independent cues, forgiving input targets, and optional timing assistance.
- [ ] Add task-linked quest progression and connection/routing, checklist, and inspection/matching tasks as justified. The implemented engineer is an astronaut, not a maintenance robot.
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
