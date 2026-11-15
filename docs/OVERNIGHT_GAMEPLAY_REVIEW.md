# Overnight gameplay review

Reviewed the implementation on `codex/mission-completion`, focusing on mission lifecycle, physical repairs, role permissions and student recovery. This is a code-path review, not a claim of observed GUI play. The parent owns the active native test run; the desktop was locked during this review. No Gradle runs or production code changes were made here.

## P1 — A repair during a teaching pause consumes its supply without fixing the fault

**Trigger:** The Long Night cable is broken, or Greenhouse's pipe is broken. Mission Control pauses the lesson. A student uses a wire kit or places a repair plate while paused.

`OutpostScenarioController`'s physical repair callbacks consume the kit/place the patch before calling `ScenarioMission.repair()`. That method returns immediately unless the phase is ACTIVE. The result is a lost item and a still-broken mission. Both finite wire kits can be wasted this way, preventing completion without a reset.

Evidence: `src/main/java/cc/nimbusnova/mc/scenario/OutpostScenarioController.java`, repair branches around lines 207–219; `ScenarioMission.repair()`.

**Minimum fix:** Reject non-ACTIVE repairs before any inventory/world mutation, with a clear paused/ended message; alternatively deliberately allow paused repairs and make the model and physical transaction agree. Also reject a pipe repair if its target is no longer the expected empty repair cell. Verify one paused attempt leaves both item count and fault/world state unchanged.

## P1 — Misplaced greenhouse plates can make the mission unrecoverable

**Trigger:** A child takes the two finite repair plates and clicks an ordinary floor/wall instead of the pipe's missing cell.

`PATCH_PLATE` is a normal `BlockItem`. The scenario callback handles the correct PIPE target, then returns PASS for other targets. Vanilla placement can consume both plates elsewhere. Scenario break protection prevents a non-operator from retrieving them, while the repair-store pool has already been issued.

Evidence: `OutpostScenarioController` lines 207–229 and `PlayerBlockBreakEvents` protection around line 185; compare the stricter ship repair handler.

**Minimum fix:** While carrying a scenario repair plate, preserve normal utility interactions, but reject other placements with a repair-target hint. Only the active empty PIPE cell should consume/place a plate. Verify an off-target click does not consume a plate or change the world.

## P1/P2 — The new scenario role routing sends students to unusable or forbidden stations

The suite advertises five selectable roles, but `send()` uses one role-to-station mapping for all three missions. The model and station permission mapping vary by mission:

- Shelter LIFE_SUPPORT targets station 1, for which the model has no action. POWER targets station 0, but that station requires MAINTENANCE.
- Long Night LIFE_SUPPORT targets station 1, which has no interaction branch. MAINTENANCE is sent to power-rack controls owned by POWER, although maintenance can perform the physical cable repair.
- Greenhouse POWER is sent to station 0, which has no action. PILOT is sent to sampling station 2, but that station is owned by SUPPLIES. Maintenance's pipe-repair target is also not the displayed station target.

Evidence: `OutpostScenarioController.send()` role target expression; `register()` task permission expression; `ScenarioMission.action()` branches; `ScenarioScreen.init()`.

**Minimum fix:** Define scenario-specific role objectives and permissions from the actual task set. Either expose only supported roles for a mission, or give the additional roles genuine work. During a fault, point the responsible role to the physical repair cell/stores. Do not offer a role whose entire attempt consists of being denied or viewing an empty task panel. Verify each offered non-operator role has at least one valid action and a correct navigation target.

## P2 — Shelter's protected-crew test silently excludes unassigned survival players

The hazard `safe` calculation filters out any non-operator without a role and uses `allMatch`, so an empty participant stream is considered safe. In an active attempt, a newly joined survival-mode student cannot claim a role (roles require READY), yet can remain outside the shield without affecting exposure. A crew that leaves the scenario while other players remain connected can also leave the timer running with `safe=true`.

Evidence: `OutpostScenarioController` around lines 246–255 and role assignment's READY-only guard.

**Minimum fix:** Make the participant/observer policy explicit. Count every non-spectator in the scenario for shelter safety, or turn unassigned late joiners into clearly marked safe observers. Pause if no actual scenario participants remain, rather than checking only the whole server's player count. This is a gameplay-policy issue; it does not establish a dose-model defect.

## What already has useful foundations

The unified hub validates mission IDs before switching and retains the existing authoritative controllers. Ship launch/select idempotency and readiness gating directly address the duplicate-launch complaint. Existing ship activities have finite rewards, room repairs use actual inventory items, and the new server-owned learning guard prevents acknowledgement alone from bypassing the applied check. Preserve those boundaries while fixing the scenario callbacks.

## Verification limit

This review did not independently run the parent's server fixture or inspect rendered/fullscreen controls. It cannot certify visual quality, keyboard/mouse hit targets, frame pacing, multiplayer client synchronization or learner engagement. Those are explicit playtest tasks, not evidence supplied by this review.
