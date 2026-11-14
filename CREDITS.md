# Credits and source register

## Development sources

| Resource | Use | License / terms |
| --- | --- | --- |
| [Fabric example mod, 26.3 branch](https://github.com/FabricMC/fabric-example-mod/tree/26.3) | Starter layout, dependency coordinates, Gradle wrapper files | [CC0-1.0](https://github.com/FabricMC/fabric-example-mod/blob/26.3/LICENSE); wrapper retains Apache notices |
| [Fabric documentation](https://docs.fabricmc.net/develop/getting-started/creating-a-project) | Setup reference | Documentation only; no artwork copied |
| [Fabric Loader](https://github.com/FabricMC/fabric-loader) | Runtime loader | Apache-2.0 |
| [Fabric API](https://github.com/FabricMC/fabric-api) | Modding API | Apache-2.0 |
| [Fabric Loom](https://github.com/FabricMC/fabric-loom) | Build plugin | MIT |
| [Gradle](https://github.com/gradle/gradle) | Build tooling and wrapper | Apache-2.0 |
| [SLF4J](https://www.slf4j.org/license.html) | Logging API provided by the game | MIT |
| [Minecraft](https://www.minecraft.net/eula) | Required, separately obtained host game | Proprietary Mojang/Microsoft terms; outside this repository's MIT license |

See [rules.md](rules.md) for competition references. No NASA imagery, NASA branding, or third-party game assets have been bundled.

## NASA data: The Darkening Sky

The solar supply in `DustStormProfile.java` follows the Opportunity rover's record of the 2018 Mars dust storm. Retrieved November 14, 2026. The numbers are typed into the code; no dataset file is bundled, and nothing is live.

| Value | Source | Used for |
| --- | --- | --- |
| 21 Wh of solar energy and tau 10.5 on June 10, 2018 (sol 5111) | NASA Science, [On Mars, Light Is Energy](https://science.nasa.gov/resource/on-mars-light-is-energy/) (June 13, 2018) | Storm peak and the tau shown to players |
| About 645 Wh on June 1, 2018 | The Planetary Society, [Opportunity Pummeled by Massive Dust Storm](https://www.planetary.org/articles/06-mer-update-opportunity-dust-storm-sleep) (June 14, 2018) | Clear-sky reference |
| Storm detected May 30; last signal June 10; JPL reported tau 10.8 | NASA JPL, [Opportunity Hunkers Down During Dust Storm](https://www.jpl.nasa.gov/news/opportunity-hunkers-down-during-dust-storm/) (updated to July 26, 2018) | Mission timeline and debrief text |

**Observed:** the ratio 21 / 645 ≈ 3% sets how much sunlight remains at the storm peak. NASA's graphic gives tau 10.5 and JPL's article gives 10.8 for the same day; the game shows 10.5 from the graphic.

**Game assumptions, not measurements:** about 10 sols of dimming compressed into 60 seconds with a straight-line fall, battery and oxygen capacities, every energy rate, the 0.15 backup generator (inspired by nuclear-powered Curiosity working through the same storm), and the recovery to 50% sunlight. Opportunity did not recover from this storm.

## AI assistance

On November 14, 2026, OpenAI Codex assisted with rules research, repository scaffolding, Gradle configuration, Java initialization classes, the teacher dashboard and backend foundation, and documentation, including expanding the team's ideas into `PLAN.md`. The team supplied the challenge choice, Minecraft mod format, Minecraft 26.3 / Java 25 requirement, teacher platform, cooperative roles, Mars exploration, NPC dialogue, and task minigame direction. Assistance is also declared in `fabric.mod.json` under `custom.ai_assistance`; this file is included in the built JAR.

Carry this disclosure into the competition submission's AI/SI field. Update it as work continues. Generated images/video require visible generation labels under the submission guide; none are included here.

## Future science sources

When adding NASA data, record the exact dataset/study URL, retrieval date, units, transformations, use terms, and which mechanic uses it. Label archived data as archived and game balance values as simplified assumptions. An Earth-directed space-weather alert is not a calibrated lunar surface radiation dose.
