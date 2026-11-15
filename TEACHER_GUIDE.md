# Nimbus Nova: host guide

Use a private classroom server or a local singleplayer demonstration with the matching client/server mod. The current design is one selected cooperative crew per server; do not present it as isolated simultaneous classes.

## Run a lesson

1. Start the Fabric server and existing teacher panel using the documented local bridge configuration. Do not publish private bridge tokens.
2. Students join using **Join session** and the supplied code/address. Have the crew connected before launch.
3. Use the teacher's primary **Mission control** library, or open **`/nimbus`** as an operator. Select one of the six missions. Selecting another mission ends the current attempt but preserves its map.
4. Students choose roles and finish the untimed preparation. **Start** becomes available only when the server confirms readiness.
5. During incidents, students complete the applied **Crew check** from the readings. Confirm/resume uses that server-owned result; clicking through text cannot substitute for it.
6. Use Pause for discussion or recovery. Review the outcome and resource choices, then reset for another attempt or end before switching missions.

The five spacecraft roles are power, life support, supplies, maintenance and pilot. The additional scenarios give each offered role a concrete objective. Operators may work every station for solo development; that is not a classroom multiplayer test.

In **Player roster**, use **Make OP** or **Remove OP** to change a connected player's Minecraft operator status. Operators can use server commands.

Once preparation is complete, a student can use **Ask teacher to start** in the mission tablet or in `/nimbus`. The teacher sees the request in **Classroom mission** and can **Accept** to start or **Reject** to keep preparing. The student is notified of the decision.

## Gameplay checks to discuss

- Which observation changed the crew's decision?
- What did the crew preserve, and what useful work did it defer?
- Which readings distinguish oxygen reserve, CO2 and pressure?
- How did a failed prediction or repair change the next attempt?

Compare a cautious survival-only attempt with a safe attempt that also gathers observations. Ask learners to explain the difference; do not claim learning effectiveness from passing the software tests alone.

## Recovery and testing

- `/nimbus` is the unified mission entry point. Legacy commands remain for development.
- Ship **Room control** exposes doors, pressure, power and basic drills. Repair stores are finite and shared.
- Maps persist. Use fresh disposable worlds to test a changed schematic; reset does not erase builds.
- Active attempts are transient. Saved anonymous debriefs survive restart under `<world>/nimbus-nova/reports`; download recent reports from **Crew debriefs**. The archive stops at 100 and does not delete older files. Move finished reports out for long-term storage when it reaches capacity.
- Keep an adult/operator present for the first classroom test. Installation, school hardware, event eligibility and third-party terrain permission require their own confirmation.
