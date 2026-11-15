# Education and challenge review

Reviewed November 15, 2026 against the repository's working challenge/rubric in `rules.md`, the mission goals in `PLAN.md`, and the current scenario/learning implementation. This is an internal implementation review, not an official NASA score or eligibility determination. The original `JUDGE_REVIEW.md` describes an earlier proposal and should not be read as the current implementation verdict.

The strongest submission evidence is the original Mars power/science trade-off and the ship's consequential physical repairs. The new room system, finite supplies and instrument-specific tasks support the intended cooperation considerably better than a space-themed button quiz. The new scenarios still have the following material educational gaps.

## Ranked findings

### P1 — The Long Night rewards turning the laboratory off forever

**Evidence:** `src/main/java/cc/nimbusnova/mc/scenario/ScenarioMission.java:103` charges an additional 0.55 energy/second for the laboratory, but records no laboratory output. The full-success condition at `ScenarioMission.java:132` requires only cable repair and a verified SOS. There is no competing benefit to keeping the laboratory on.

**Trigger:** turn off load bit 2 before launch, transmit SOS, repair the authored cable fault and keep heat/life support/beacon supplied. A standalone Java model probe reproduced **SUCCESS with battery 22.75**, with the laboratory off for the entire 150-second attempt. The probe invoked the model repair directly; it did not test physical travel or a human playthrough.

**Implication:** a system labelled as a scientific/resource trade-off has a dominant action with no downside. This repeats the specific weakness already identified in the earlier judge review: switching optional science off forever becomes the winning strategy.

**Minimum fix:** make a bounded observation/experiment output part of full success, while survival without it remains partial success. Expose the progress and drain together so players can decide when to run the laboratory. Add a model check for both a useful science-on window and the survival-only path; do not simply make every load mandatory throughout.

### P1 — Shelter completes the meaningful outdoor work before hazard time starts

**Evidence:** `ScenarioMission.java:25` requires all three survey bearings and all three shielding modules before `start()`. `OutpostScenarioController.java:282` issues exactly the three required modules without a mass/resource choice. After launch, the full outcome is shielding plus SOS (`ScenarioMission.java:132`); the radiation window only checks protection. The model therefore has no remaining exploration-versus-sheltering objective to weigh during the warning.

**Trigger:** finish the untimed checklist, stand in the covered shelter, enter the fixed SOS pattern and wait until rescue. The passive-shielding principle is represented, but the promised engineering choice is not.

**Minimum fix:** retain forgiving untimed training, then offer one valuable, bounded observation/cargo task outside during the active warning. Returning with that result and surviving earns full success; choosing shelter immediately remains a valid survival/partial outcome. Show the warning window and consequence clearly. Do not require a risky task that makes exposure inevitable.

### P2 — Crew checks currently assess a static safe-answer choice, not use of the live readings

**Evidence:** `src/main/java/cc/nimbusnova/mc/learning/LearningChecks.java:63` sends live indicator values, but episode construction at line 113 creates `AppliedLearning` from the incident enum alone. `AppliedLearning.java:68` checks a fixed choice index. For example, DUST always accepts “isolate nonessential science loads”; the displayed generation/load values cannot change the accepted prediction. Several distractors are plainly destructive rather than plausible competing plans.

**Implication:** these are useful formative concept prompts with explanatory retry feedback. They do not establish that learners interpreted the displayed data, and one participant solving a crew-wide check is not evidence that every child understood it. Lack of punishment for retrying is appropriate; adding forced reading time or penalties would not resolve this gap.

**Minimum fix:** make at least one prediction depend on an actual captured indicator relationship—for example whether current supply is above/below demand—and show the resulting trend after the choice. Use indicator labels relevant to the incident: docking latch/contact and descent guidance/radar should not be illustrated only by generic battery/oxygen bars. Until then, describe the feature as a crew prediction/discussion check rather than verified individual mastery.

### P2 — Communication delay remains absent from the implemented objectives

**Evidence:** `rules.md`'s challenge summary includes communication delay. `PLAN.md:114` and line 150 defer Earth Mission Control delay lessons. The scenario communication task (`ScenarioMission.java:52`) is a fixed SOS pulse sequence; ship communications verify a local signal. Neither represents waiting for remote advice or choosing an autonomous response while a message is in transit.

**Minimum fix:** if the working brief's delay objective is retained, add one explicit delayed Earth guidance/acknowledgement interaction to an existing mission. Let the crew continue a necessary local decision while waiting; repeated sends must not erase latency. Label game timing and distinguish immediate crew chat from Earth contact. This needs one meaningful consequence, not another complete mission or an unsupported claim that an arbitrary delay is a NASA measurement.

### P2 — The rules document still says only the template exists

**Evidence:** `rules.md:53` says “Only repository/template setup is implemented” and says science integration has not been performed. Its implementation/science checklist at lines 47–48 is also unchecked. Those statements contradict the implemented missions and documented Opportunity transformation.

**Minimum fix:** keep registration, organizer interpretation and submission/public-link items unresolved, but replace the stale implementation-status sentence with a dated pointer to current delivery evidence. Keep the earlier proposal review clearly labelled as a historical snapshot. Do not infer that code completion proves submission or eligibility.

## Source and claim boundaries

- The existing Opportunity solar observation/transformation gives the project a substantially stronger NASA-resource story than generic space decoration. The new scenario `evidence()` strings correctly label their timing, capacities and environmental indices as authored.
- Shelter's cited event is a real Mars context used in a lunar-inspired exercise (`ScenarioMission.java:163`), not a measured lunar dose profile. That distinction is already stated and should remain visible in the pitch.
- A NASA resource is required by the repository's working submission summary; this review does **not** demand a calibrated dataset for every fictional subsystem. New physiology, food, pressure and sensor values must not be presented as operational specifications.
- Learner/educator observation remains absent. The game can demonstrate implemented learning opportunities and successful task completion; it cannot yet claim improved educational outcomes, broad age suitability or classroom effectiveness.
- Organizer confirmation of Minecraft dependency eligibility and public submission requirements remains an external task. This review did not reopen it as a coding blocker.

## Highest-value delivery choice

Demonstrate one short causal chain: real dust-linked solar reduction → a crew sees supply/load change → they preserve life support while finding a science window → the debrief shows what they achieved and what it cost. Then show a ship breach consuming a real patch plate and pressure recovering. These show the strongest implemented engineering decisions more clearly than a rapid six-map feature reel.

Functional role/navigation defects and the exclusion of unassigned players from shelter exposure were sent to the separate gameplay reviewer to avoid duplicate investigation. They should be resolved alongside the educational changes above.

No code or Git state was changed by this review. No Gradle build, rendered/fullscreen playtest, participant study or fresh legal/rules certification was performed; the Mac was locked during the review.

## Owner response after the review

The scenario owner reported fixes in progress for the two P1 findings: an active-event outside measurement in Shelter, and a bounded powered laboratory logger in Long Night; survival-only outcomes would remain partial. This is an owner response, not a verified closure. The parent should validate the revised model/playthrough and update disposition rather than treating the earlier repro as a defect in a later revision. The communication-delay gap was also sent to that owner.
