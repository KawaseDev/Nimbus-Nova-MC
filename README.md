<p align="center">
  <img src="docs/img/banner.jpg" alt="Nimbus Nova: space mission training, built in Minecraft" width="100%">
</p>

<h1 align="center">🛰️ Nimbus Nova</h1>

<p align="center"><strong>Six space missions. Five crew roles. One shared supply of power and air.</strong><br>
A cooperative astronaut mission trainer inside Minecraft, built for the classroom.</p>

<p align="center">
  <a href="https://github.com/KawaseDev/Nimbus-Nova-MC/releases/latest"><img src="https://img.shields.io/github/v/release/KawaseDev/Nimbus-Nova-MC?color=ffc857&label=release" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Minecraft-26.3-5fe1f0" alt="Minecraft 26.3">
  <img src="https://img.shields.io/badge/Fabric-0.20.1-dbd0b4" alt="Fabric Loader 0.20.1">
  <img src="https://img.shields.io/badge/Java-25-e76f51" alt="Java 25">
  <img src="https://img.shields.io/badge/NASA%20Space%20Apps-2026-0b3d91" alt="NASA Space Apps Challenge 2026">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-8bd17c" alt="MIT License"></a>
</p>

<p align="center">
  <a href="https://github.com/KawaseDev/Nimbus-Nova-MC/releases/latest/download/NimbusNova.mrpack"><strong>⬇ Download the modpack</strong></a>
  · <a href="presentation/NimbusNova-Trailer.mp4"><strong>▶ Watch the trailer</strong></a>
  · <a href="PLAYER_GUIDE.md">Crew guide</a>
  · <a href="TEACHER_GUIDE.md">Teacher guide</a>
  · <a href="presentation/NOVA-Pitch-v2.pptx">Pitch deck</a>
</p>

<p align="center">
  <a href="presentation/NimbusNova-Trailer.mp4"><img src="docs/img/trailer.gif" alt="Trailer preview with gameplay from the missions" width="720"></a>
  <br><em>All footage recorded in-game. Click for the 30-second trailer with sound.</em>
</p>

Dust cuts power at a Mars outpost. The crew can keep oxygen running or use that energy to finish a science task, and every specialist sees different readings. Students have to compare what they know, decide together, and carry out the plan at working equipment in the world.

Nimbus Nova is an independent student project for the 2026 NASA Space Apps Challenge. [Build a Junior Astronaut Mission Trainer](https://www.spaceappschallenge.org/2026/challenges/junior-astronaut-mission-trainer/)

## 🚀 Start playing

1. Import [NimbusNova.mrpack](https://github.com/KawaseDev/Nimbus-Nova-MC/releases/latest/download/NimbusNova.mrpack) in a Modrinth-compatible launcher and use **Java 25**. You need Minecraft Java Edition **26.3**.
2. For a solo demo, create a world with commands enabled and open **`/nimbus`**. Select a mission and pick a role in the crew tablet. In a class, choose **Join session** on the title screen and enter the code and address from your teacher.
3. Press **J** for the crew tablet. Follow the amber waypoint and right-click the marked equipment. Complete the preparation checks before Mission Control starts the timer.

The release also offers a standalone mod JAR for manual Fabric setups. Install **Fabric Loader 0.20.1** and **Fabric API 0.165.2+26.3** on clients and the server. Use the same Nimbus Nova build on both. [Release files](https://github.com/KawaseDev/Nimbus-Nova-MC/releases/latest)

## 🪐 Mission library

| Mission | What your crew has to decide |
| --- | --- |
| 🔴 **The Darkening Sky** | Keep air flowing and finish research as a Martian dust storm cuts solar output. |
| 🌕 **Moon Expedition** | Verify the approach, manage descent speed, collect a lunar sample, and return the crew. |
| 🛰️ **Return Window** | Restore a ship’s power, patch a hull breach, rebuild the CO₂ filter, and time the return burn. |
| 🛡️ **Shelter Before the Event** | Build shielding and decide when a live radiation reading is worth leaving cover. |
| 🔋 **The Long Night** | Repair a failed junction and share limited power between heat, air, the lab, and the rescue beacon. |
| 🌱 **Greenhouse Emergency** | Patch a feed pipe and balance irrigation, root aeration, lights, and cabin humidity. |

| | | |
| --- | --- | --- |
| ![Aster Outpost dome on Mars](docs/img/mars-outpost.jpg) | ![Lunar surface with the sample waypoint and minimap](docs/img/lunar-surface.jpg) | ![Greenhouse site with solar panels and landing pad](docs/img/greenhouse-site.jpg) |
| **Mars.** Aster Outpost under a dust storm. | **Moon.** Walk to the basalt sample on suit air. | **Greenhouse.** Keep the crops and the cabin alive. |

## 🕹️ Hands-on stations

Every job is something you do at a console, with the reason it matters on screen next to it.

| | | |
| --- | --- | --- |
| ![Rotating cable tiles to connect solar power to the base](docs/img/power-routing.jpg) | ![Balancing pressure and flow with three valves](docs/img/oxygen-recycler.jpg) | ![Docking with thruster pulses that add drift](docs/img/momentum-docking.jpg) |
| **Route power.** Turn cable tiles until the solar array reaches the base. | **Balance air.** Each valve changes two readings at once. | **Dock.** Every push adds drift, so you brake by pushing the other way. |
| ![Picking three sharp guide stars on a star map](docs/img/star-navigation.jpg) | ![Checking descent speeds and choosing a landing site](docs/img/lunar-descent.jpg) | ![Shelter site with shielding and survey station](docs/img/shelter-site.jpg) |
| **Navigate.** Fix your heading from three sharp stars. | **Land.** Brake, pick a site, or cancel and go around. | **Shelter.** Build shielding before the radiation arrives. |

- 🧭 **Minimap and waypoints** show the floor plan, your crew, and where the next job is. Press **M** to toggle the map.
- 📟 **Role-specific readings** mean no one player sees the whole picture.
- 💡 **Space facts** on every task link the puzzle to how real missions handled the same problem.
- 🧑‍🏫 **Teacher controls** pause the mission for discussion and save a debrief of the crew's decisions.

## 🎛️ A mission from briefing to debrief

```mermaid
flowchart LR
    Select["Teacher selects a mission"] --> Prepare["Crew picks roles and checks equipment"]
    Prepare --> Launch["Mission Control starts the clock"]
    Launch --> Incident["Crew compares readings during an incident"]
    Incident --> Decide["Crew proposes a response"]
    Decide --> Repair["Crew acts at the marked stations"]
    Repair --> Report["Debrief shows what their choices changed"]
```

The teacher panel runs locally. It hosts the server, issues a join code, shows the roster and student launch requests, controls the mission clock, and downloads saved debriefs. A local operator can run the same missions through `/nimbus` in singleplayer. [Teacher setup](docs/REFERENCE.md#teacher-panel) · [Host guide](TEACHER_GUIDE.md)

## 📡 From rover readings to game decisions

**The Darkening Sky** uses two real Opportunity dust storms to set how much solar energy remains at each storm’s peak. In the game, this changes the power available to oxygen production and research. The crew can pause one load, use a finite reserve cell, or risk waiting for more generation.

![Opportunity retained 18.3 percent of its reference solar energy in the 2007 storm and 3.3 percent in the 2018 storm](docs/img/opportunity-solar-energy.png)

The bars divide reported storm-day output by the corresponding pre-storm reference. Game time, resource capacities, and other training values are authored. The credits record the inputs and how each source was used. [NASA JPL’s 2007 report](https://www.jpl.nasa.gov/news/nasa-mars-rovers-braving-severe-dust-storms/) · [NASA’s 2018 report](https://science.nasa.gov/resource/on-mars-light-is-energy/) · [2018 reference output](https://www.planetary.org/articles/06-mer-update-opportunity-dust-storm-sleep) · [Sources and AI assistance](CREDITS.md)

## 🛠️ Build and explore

With JDK 25 installed, run these commands from the repository root. On Windows, use `gradlew.bat`.

```sh
./gradlew build
./gradlew runClient
```

`build` runs the simulation and headless Minecraft server checks. For the rendered gameplay suite, run `./gradlew runClientGameTest` on a machine with a graphical session. Add `-Ptrailer` to that command to record the 120 fps footage used by the trailer scripts in `tools/trailer`.

- 📖 [Technical reference](docs/REFERENCE.md) for setup, commands, equipment, and mission behavior.
- 🎙️ [Pitch deck](presentation/NOVA-Pitch-v2.pptx), [trailer](presentation/NimbusNova-Trailer.mp4) and [pitch outline](presentation/pitch.md).
- 🧾 [Competition requirements](rules.md) and [source register](CREDITS.md).

### Optional local terrain import

The public release uses the team’s authored Mars terrain. The project also supports a private, scaled preview of Mars elevation through MarsCraft. [Import instructions and source permissions](docs/REFERENCE.md#optional-local-terrain-import)

Original project code is available under the [MIT License](LICENSE). [Asset and dependency credits](CREDITS.md)
