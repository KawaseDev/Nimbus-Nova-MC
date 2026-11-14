# Nimbus Nova implementation batches

Updated November 14, 2026. This file records delivery work, not the complete product wish list. A checked item means the change and its relevant verification passed. Code present but awaiting integration remains unchecked. The lead agent updates evidence and commit/push status after each batch.

## Batch 1: mission core and HUD

Status: core implementation committed as `21cad61`; build and simulation checks passed. Visual and multiplayer integration checks remain pending.

- [x] Integrate the server-owned mission state: ready, active, paused, success, partial success, failed, and aborted.
- [x] Verify power/oxygen calculations, depletion, dust boundaries, and frozen pause state with `missionSelfTest`.
- [x] Require survival plus 90 seconds of research for full success; verify survival-only partial success in the simulation self-check.
- [x] Implement role controls for power engineer, life-support engineer, and observer; test the role/equipment permission mapping.
- [ ] Verify role control requests with separate players in-game.
- [ ] Verify operator lifecycle commands and permitted role assignment.
- [x] Integrate synchronized HUD, role/objective panels, warnings, and the J mission console; Java compilation passes.
- [ ] Verify console controls, readable layout, and default/rebound key behavior in-game.
- [x] Implement physical station binding without terrain replacement.
- [ ] Verify a role-authorized right-click equipment change in-game.
- [ ] Check reconnect, automatic empty-server pause, reset, and clean server shutdown.
- [x] Run `./gradlew build` including `missionSelfTest`.
- [x] Commit the working core batch as `21cad61`.
- [x] Confirm the remote push for this batch.

Evidence: the lead agent reports a successful build and simulation self-check covering resource balance, phases, dust, outcomes, idempotency, and role mapping. Runtime logs confirm that the client loaded a world without a mod crash. The native computer-control bridge cannot access the Java window, so HUD visual inspection remains unperformed. This is not a completed two-client or station-interaction test.

Commit: `21cad61`. GitHub authentication is configured. The core reached origin/main through merge commit `cc3bb99`, preserving the teammate's teacher-panel work. The merge also preserves the teammate's teacher-panel work.

## Batch 2: original Mars map, items, and textures

Status: parallel implementation in progress.

- [ ] Add a compact authored Mars training site with habitat, landing area, power yard, greenhouse, shelter, and marked routes.
- [ ] Use safe, explicit map creation/travel; prevent accidental overwrite of existing builds.
- [ ] Provide a safe arrival location and clearly label the site as fictional/authored terrain.
- [ ] Add original space items with valid item/model/texture resources.
- [ ] Give each functional item an honest description and a server-validated use where applicable.
- [ ] Check texture dimensions, resource paths, registry IDs, and language entries.
- [ ] Integrate registration with the common mod entry point.
- [ ] Test map entry, navigation, item acquisition/use, and survival of a restart where the map promises persistence.
- [ ] Run the build and relevant checks, then commit and attempt a push.

Evidence: pending integration. Decorative assets must not be described as functional spacecraft, pressure simulation, or suits.

Commit: pending. Push: pending.

## Batch 3: integrated playable handoff

Status: pending batches 1 and 2.

- [ ] Run the current build on the Mac and inspect the HUD/console at a usable GUI scale.
- [ ] Test one complete attempt from ready state through an outcome; compare the displayed reserves with server state.
- [ ] Test a balanced full-success strategy and survival-only partial success.
- [ ] Test two clients with different roles and confirm they see the same shared resource consequence.
- [ ] Confirm an observer cannot use another role's controls and a non-operator cannot start/reset the mission.
- [ ] Check error messages and recovery for disconnects or unavailable actions.
- [ ] Document actual commands, remaining limits, and backend integration points in README.
- [ ] Record any computer-control/window-access limitation instead of claiming an unperformed visual test.
- [ ] Commit the verified integration and attempt a push; record the remote result.

Evidence: pending.

Commit: pending. Push: pending.

## Batch 4: science and learning demonstration

Status: pending; required before strong NASA/education claims.

- [ ] Select one usable NASA observation or documented historical condition, with exact source, dates, site, units, and reuse terms.
- [ ] Document how that evidence changes solar supply or another visible mission condition.
- [ ] Label authored capacities, normalization, time compression, and missing data.
- [ ] Add a debrief tied to actual crew actions, with source cards and the survival/research trade-off.
- [ ] Ask two short questions before and after a small learner/educator test; record anonymous observations and limitations.
- [ ] Confirm that at least two reasonable strategies can produce useful outcomes.
- [ ] Show observation → change → communication → decision → consequence → explanation in the demo.

The current four-minute event and rates are prototype game balance. A historical article in the credits does not establish a calibrated measurement series.

## Batch 5: NPCs and task minigames

Status: planned after the complete core lesson.

- [ ] Add one authored NPC guide with short role instructions, state-aware dialogue, and replayable hints.
- [ ] Build one original task minigame and connect completion to a real equipment/resource consequence.
- [ ] Validate role, distance, phase, supplies, and completion server-side; reject duplicate rewards.
- [ ] Provide readable text, color-independent cues, forgiving input targets, and optional timing assistance.
- [ ] Expand to the maintenance robot and connection/routing, checklist, and inspection/matching tasks as justified.
- [ ] Add the remaining food/water, safety/maintenance, and commander/pilot roles with meaningful work.

## Batch 6: teacher platform integration and expanded space gameplay

Status: roadmap; teacher backend/admin panel has a separate teammate owner.

- [ ] Agree on mission snapshots, lifecycle actions, player identity, and report data with the backend owner.
- [ ] Connect authenticated teacher controls and session-code joining without duplicating resource calculations.
- [ ] Add multiple isolated crews and persistent session/report recovery when needed.
- [ ] Add functional suit oxygen, airlocks, outdoor hazards, and a controllable rover.
- [ ] Add bounded controllable shuttle flight before claiming free spacecraft piloting.
- [ ] Implement additional missions, then expose them as playable choices in the teacher library.

## Submission and eligibility

- [ ] Confirm the team's accepted local event and applicable local/global deadlines.
- [ ] Ask the Local Lead whether the proprietary Minecraft host dependency is acceptable for the intended award track.
- [ ] Keep licenses, source register, and AI disclosures current.
- [ ] Meet English/public-demo requirements and keep minors' likenesses/voices out of public material.
- [ ] Verify public judging links signed out and click Submit for Judging before the applicable deadline.

See [rules.md](rules.md) for governing official sources and [JUDGE_REVIEW.md](JUDGE_REVIEW.md) for the scoring weaknesses behind these priorities.
