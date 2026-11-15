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

The solar supply in `DustStormProfile.java` follows one of two real storms Opportunity lived through, defined in `DustStorm.java`. Each reset picks one at random unless Mission Control pins it with `/mission storm 2018|2007`. Retrieved November 14, 2026. The numbers are typed into the code; no dataset file is bundled, and nothing is live.

| Value | Source | Used for |
| --- | --- | --- |
| 21 Wh of solar energy and tau 10.5 on June 10, 2018 (sol 5111) | NASA Science, [On Mars, Light Is Energy](https://science.nasa.gov/resource/on-mars-light-is-energy/) (June 13, 2018) | Storm peak and the tau shown to players |
| About 645 Wh on June 1, 2018 | The Planetary Society, [Opportunity Pummeled by Massive Dust Storm](https://www.planetary.org/articles/06-mer-update-opportunity-dust-storm-sleep) (June 14, 2018) | Clear-sky reference |
| Storm detected May 30; last signal June 10; JPL reported tau 10.8; the 2007 storm reached tau above 5.5 | NASA JPL, [Opportunity Hunkers Down During Dust Storm](https://www.jpl.nasa.gov/news/opportunity-hunkers-down-during-dust-storm/) (updated to July 26, 2018) | Mission timeline, debrief text, 2007 opacity |
| About 700 Wh per day before the 2007 storms; 128 Wh on July 18, 2007; the team skipped communications to save energy | NASA JPL, [NASA Mars Rovers Braving Severe Dust Storms](https://www.jpl.nasa.gov/news/nasa-mars-rovers-braving-severe-dust-storms) (July 20, 2007, updated July 23) | 2007 storm peak and debrief text |

**Observed:** the ratios 21 / 645 ≈ 3% (2018) and 128 / 700 ≈ 18% (2007) set how much sunlight remains at each storm's peak. Opportunity survived the 2007 storm and was lost in 2018, which the debrief tells players. NASA's graphic gives tau 10.5 and JPL's article gives 10.8 for the same day; the game shows 10.5 from the graphic.

**Game assumptions, not measurements:** about 10 sols of dimming compressed into 60 seconds with a straight-line fall, battery and oxygen capacities, every energy rate, the 0.15 backup generator (inspired by nuclear-powered Curiosity working through the same storm), and the recovery to 50% sunlight. Opportunity did not recover from this storm.

## NASA data: the solar storm alert

At 00:10 the mission sends a solar storm alert; particles arrive at 00:40 and pass at 01:05. Crew members at the outpost but outside the crew shelter add to a shared radiation dose, and 100% ends the mission.

| Value | Source | Used for |
| --- | --- | --- |
| A solar event on September 11, 2017 produced surface radiation more than double any level Curiosity's Radiation Assessment Detector had measured since 2012; high readings lasted more than two days; the RAD lead said someone outdoors "would definitely want to take shelter" | NASA JPL, [Large Solar Storm Sparks Global Aurora and Doubles Radiation Levels on the Martian Surface](https://www.jpl.nasa.gov/news/large-solar-storm-sparks-global-aurora-and-doubles-radiation-levels-on-the-martian-surface/) (September 29, 2017) | Why the alert exists, the shelter rule, and the briefing text |

**Game assumptions, not measurements:** the alert time, the 30-second warning, the 25-second storm (the real high readings lasted days), the dose units, and the limit of 20 unprotected crew-seconds. The game does not model a real dose in sieverts. The same event should also be listed in NASA's DONKI space weather database; we could not reach DONKI from the build machine, so verify the record there before citing it.

The fictional relay-fault learning pause also cites NASA's [Power Subsystems](https://www.nasa.gov/smallsat-institute/sst-soa/power-subsystems/) reference for generation, storage, and electrical loads; the relay incident itself is authored for the game.

## AI assistance

Codex also assisted with the November 15 runtime checks, fixes, README, release packaging, and the Matplotlib chart of Opportunity's solar energy. The README gameplay images are captures from Minecraft.

On November 14, 2026, OpenAI Codex assisted with rules research, repository scaffolding, Gradle configuration, Java implementation, HUD/console design, the teacher dashboard and backend foundation, and documentation, including expanding the team's ideas into `PLAN.md`, maintaining `TODO.md`, and producing the internal `JUDGE_REVIEW.md` critique. The team supplied the challenge choice, Minecraft mod format, Minecraft 26.3 / Java 25 requirement, teacher platform, cooperative roles, Mars exploration, NPC dialogue, and task minigame direction. Assistance is also declared in `fabric.mod.json` under `custom.ai_assistance`; this file is included in the built JAR.

Carry this disclosure into the competition submission's AI/SI field. Update it as work continues. Generated images/video require visible generation labels under the submission guide. The item sprites, worn armor layers, equipment-block displays, astronaut skins, rover texture, and dust-particle artwork are original programmatically drawn assets created with Codex assistance under `src/main/resources/assets/nimbus_nova/textures`. The custom models and shaders are also original implementation work. Aster Outpost is an original block-built training site, and its default hills/craters are authored procedural terrain. The optional unpublished external import has separate provenance and permission limits in the [README terrain-import note](README.md#optional-local-terrain-import); it is not bundled as MIT content. Do not treat original game assets as NASA imagery or an accurate Mars reconstruction.

## Future science sources

When adding NASA data, record the exact dataset/study URL, retrieval date, units, transformations, use terms, and which mechanic uses it. Label archived data as archived and game balance values as simplified assumptions. An Earth-directed space-weather alert is not a calibrated lunar or Mars surface radiation dose. The dust storm's sunlight curve uses the Opportunity data documented above; the four-minute mission rates, time compression, and equipment capacities remain authored game balance.

## Rendering and font

The mission UI uses Minecraft's default font and Blaze3D GUI drawing. The client includes original RenderPearl rendering utilities, and dust storms use a dedicated soft-particle shader with fog and transparency support. Sapphire in the team's existing mod informed the rendering design. The bundled Geist MTSDF atlas is derived from [Geist](https://github.com/vercel/geist-font) and licensed under SIL OFL 1.1. The full license is bundled at `assets/nimbus_nova/fonts/OFL.txt`. RGB-median distance reconstruction follows the technique documented by [msdf-atlas-gen](https://github.com/Chlumsky/msdf-atlas-gen). The font software remains under OFL, separately from the repository's MIT code.


## Gravity reference

The [NASA Mars Fact Sheet](https://nssdc.gsfc.nasa.gov/planetary/factsheet/marsfact.html) gives a Mars/Earth surface-gravity ratio of about 0.38. The mod applies that ratio to vanilla gravity and fall-distance damage calculations; other Minecraft movement assumptions remain.

## Guided mission update

The cable-routing puzzle, coupled-valve model, holographic guidance, projector assets, and additional outpost models are original Codex-assisted implementation/artwork. These gameplay systems and the relay fault are authored teaching abstractions; they do not add NASA measurements beyond the cited environmental sources.

## Dome kit and equipment lessons

The sci-fi kit, three experiment stations, and associated pixel textures/model meshes are original Codex-assisted assets. The glass dome and walk-through doors are fictional architecture; pressure is demonstrated by an authored entrance cycle and a separate training console. Backup capacities (+10 units, two cells), airlock costs (2 reserve units), detector indices, and material mass values are authored game assumptions.

The airlock lesson uses the isolation/equalization principle described in NASA's [EVA Airlocks and Alternate Ingress/Egress Methods](https://www.nasa.gov/wp-content/uploads/2017/02/2018-_eva_airlocks_and_alternate_ingressegress_methods_document.pdf). The [ISS Systems Engineering Case Study](https://www.nasa.gov/wp-content/uploads/2015/05/design_iss_systems_engineering_case_study.pdf) describes recovering air with a compressor before depressurization; this game does not simulate that hardware. NASA's [Radiation Shielding Optimization on Mars](https://ntrs.nasa.gov/citations/20130012456) supplies context for shielding trade-offs and secondary particle production. The bench's simple monotonically decreasing index is not a physical radiation transport calculation and cannot rank real shielding designs. Sources checked 14 November 2026.

The personal-air model follows the general distinction between a pressure garment and portable life support described in NASA's [Spacewalk Spacesuit Basics](https://www.nasa.gov/centers-and-facilities/johnson/spacewalk-spacesuit-basics/), checked 14 November 2026. Its depletion/refill rates, damage, zone boundaries and four-second dust-purge scan are fictional teaching mechanics. No biological pathogen detection, real pressure transport or calibrated medical survival time is simulated. The revised floor albedo was generated with OpenAI image generation and resized for the game; portal and panel geometry remains original Codex-assisted asset work.

## Return Window spacecraft mission

The spacecraft structure is the team-supplied `ananaslafelie-1794683726306.schem`; its hash, dimensions, marker mappings and import transformations are recorded in [SHIP_LAYOUT.md](docs/SHIP_LAYOUT.md). Do not treat the supplied structure as NASA geometry. No source entities, command scripts, inventories or sign text are imported. The ten station meshes, ship HUD/minigames, role controls, fault/burn effects and mission wrapper are original Codex-assisted implementation. The brushed-metal `ship_casing.png` material was generated with OpenAI image generation and resized for the game; other material/animated emitters reuse the project's credited original assets.

The historical framing and distinction between electrical power, oxygen and CO2 removal follow [NASA's Apollo 13 mission details](https://www.nasa.gov/missions/apollo/apollo-13-mission-details/), checked 14 November 2026, plus the shared Moon mission's linked NASA incident cards. The ship is fictional. Resource quantities, synthetic communications waveforms, star patterns, cartridge assembly, pressure/seal test, docking physics, burn window and artificial cabin gravity are authored classroom models. No biomedical performance, real orbital maneuver accuracy or measured Apollo telemetry is claimed.

## Six-mission library and overnight polish

The three new maps, mission mechanics, lobby, applied-readings interfaces, lighting models and stylized orbital Earth/star geometry are original Codex-assisted work. Cabin switches, power alerts and repair cues use Minecraft's built-in event sounds; no standalone sound recordings were copied. The stylized Earth is procedural artwork, not a NASA photograph or a navigational model.

- [JPL's September 2017 Mars solar-event account](https://www.jpl.nasa.gov/news/large-solar-storm-sparks-global-aurora-and-doubles-radiation-levels-on-the-martian-surface/) motivates Shelter's distinction between exposed measurements and protection. The new lunar-inspired terrain, timings, shielding and exposure indices are authored.
- [NASA lunar surface technology](https://www.nasa.gov/lunar-surface-technology/) motivates power and thermal storage during lunar darkness. The Long Night uses a compressed authored site and resource budget.
- [NASA's reduced-gravity hydroponics research](https://science.nasa.gov/science-research/science-enabling-technology/technology-highlights/how-do-you-water-plants-in-space/) motivates balancing root water and oxygen. The greenhouse's moisture, air, lamp, humidity and output indices are teaching models rather than measured horticultural results.

The package includes the unmodified pinned Fabric API jar with its bundled license notice. The launcher provides Minecraft and Fabric Loader separately. No classroom-learning or real flight-safety conclusion follows from simulation tests.
