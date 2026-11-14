pulapula# Nimbus-Nova-MC

# Nimbus Nova MC — mission trainer

Minecraft Fabric starter for NASA Space Apps 2026's **Build a Junior Astronaut Mission Trainer**. Current state: common/client initialization only; gameplay and NASA data integration come later.

## Development

Requires **Minecraft 26.3 and JDK 25**. Pinned dependencies: Fabric Loader 0.20.1, Fabric API 0.165.2+26.3, Loom 1.19.4, Gradle 9.8.2.

On this Mac, use the existing Homebrew JDK:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew build
./gradlew runClient
```

Elsewhere, set `JAVA_HOME` to your JDK 25 installation. Windows uses `gradlew.bat build` and `gradlew.bat runClient`. In IntelliJ, open this folder as a Gradle project and select JDK 25 for both the project and Gradle JVM.

The distributable is `build/libs/nimbus-nova-mc-0.1.0.jar`; the `-sources.jar` is for developers. Install Fabric Loader for Minecraft 26.3, then place our mod and Fabric API for 26.3 in your instance's `mods` folder. Obtain Minecraft separately.

`./gradlew runServer` starts a development server. Read and accept Minecraft's EULA yourself in `run/eula.txt` before running a server.

## Layout

- `src/main/java/cc/nimbusnova/mc/NimbusNovaMod.java`: shared entry point.
- `src/client/java/cc/nimbusnova/mc/client/NimbusNovaClient.java`: client entry point. Keep client imports out of common code.
- `src/main/resources/fabric.mod.json`: identity, runtime requirements, and AI metadata.
- `gradle.properties`: pinned dependency/project versions.
- [rules.md](rules.md): official sources, requirements, and submission checklist.
- [PLAN.md](PLAN.md): detailed product plan, roles, NPC dialogue, task minigames, missions, and five-person delivery roadmap.
- [CREDITS.md](CREDITS.md): references, licenses, and AI assistance disclosure.

## Next milestone

Connect the teacher session, two Minecraft clients, and one role action that changes a shared resource. Then build the first Mars dust-storm mission with NPC guidance, task minigames, suits, a rover, and a decision debrief. See [PLAN.md](PLAN.md) for milestones and the expanded spaceflight roadmap. This describes future work.

Original code is MIT licensed. Minecraft retains its own license. Independent student project; no NASA, Mojang, or Microsoft endorsement.
