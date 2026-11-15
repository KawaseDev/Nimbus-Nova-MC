package cc.nimbusnova.mc.report;

import cc.nimbusnova.mc.NimbusNovaMod;
import cc.nimbusnova.mc.hub.MissionHub;
import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.moon.MoonController;
import cc.nimbusnova.mc.moon.MoonMission;
import cc.nimbusnova.mc.scenario.OutpostScenarioController;
import cc.nimbusnova.mc.scenario.ScenarioMission;
import cc.nimbusnova.mc.ship.ShipController;
import cc.nimbusnova.mc.ship.ShipMission;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@NullMarked
public final class ReportStore {
    private static final Map<MinecraftServer, Map<String, Trace>> TRACES = new IdentityHashMap<>();
    private static final Set<String> ENDED = Set.of("SUCCESS", "PARTIAL", "FAILED", "ABORTED");
    private static final String VERSION = FabricLoader.getInstance().getModContainer("nimbus_nova").orElseThrow().getMetadata().getVersion().getFriendlyString();

    private ReportStore() {
        /* w */
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ReportStore::capture);
        ServerLifecycleEvents.SERVER_STOPPED.register(TRACES::remove);
    }

    public static boolean validId(final String id) {
        return ReportArchive.validId(id);
    }

    public static JsonObject list(final MinecraftServer server) throws IOException {
        return archive(server).list();
    }

    public static @Nullable JsonObject read(final MinecraftServer server, final String id) throws IOException {
        return archive(server).read(id);
    }

    private static ReportArchive archive(final MinecraftServer server) {
        return new ReportArchive(server.getWorldPath(LevelResource.ROOT).resolve("nimbus-nova/reports"), NimbusNovaMod.LOGGER::warn);
    }

    public static void capture(final MinecraftServer server) {
        final Map<String, Trace> traces = TRACES.computeIfAbsent(server, _ -> new HashMap<>());
        final MissionController mars = MissionController.get(server);
        final MissionState.Snapshot marsState = mars.snapshot();
        final Trace marsTrace = track(server, traces, "mars", mars.mission(), "mars", marsState.phase().name());
        if (marsTrace != null && marsTrace.changed(marsState.elapsedSeconds(), marsState.phase().name(), marsState.lastEvent())) {
            final JsonObject metrics = numbers("battery", marsState.battery(), "oxygen", marsState.oxygen(),
                    "radiationDoseIndex", marsState.radiationDose(), "researchSeconds", marsState.researchSeconds());
            final JsonObject details = numbers("researchTargetSeconds", 90, "decisions", marsState.decisions(),
                    "solarSupply", marsState.generation(), "powerDemand", marsState.demand(),
                    "lowestBattery", marsState.lowestBattery(), "lowestOxygen", marsState.lowestOxygen());
            details.addProperty("historicalObservation", marsState.dustStorm().summary());
            details.addProperty("historicalOutcome", marsState.dustStorm().realOutcome);

            marsTrace.update(server, marsState.elapsedSeconds(), marsState.phase().name(), marsState.lastEvent(),
                    mars.assignedRoles(), metrics, details, List.of(marsState.dustStorm().id.equals("2018")
                            ? "https://science.nasa.gov/resource/on-mars-light-is-energy/"
                            : "https://www.jpl.nasa.gov/news/nasa-mars-rovers-braving-severe-dust-storms",
                    "https://www.jpl.nasa.gov/news/large-solar-storm-sparks-global-aurora-and-doubles-radiation-levels-on-the-martian-surface/"),
                    "Mars training outpost. Dust supply uses archived Opportunity energy ratios; the time, equipment fault, radiation index and operating costs are authored.");
        }

        final ShipController ship = ShipController.get(server);
        final ShipMission shipMission = ship.mission();
        if (shipMission == null) finishRemoved(server, traces.remove("ship"));
        else {
            final ShipMission.Snapshot state = shipMission.snapshot();
            final boolean expedition = shipMission.underlying().variant() == MoonMission.Variant.EXPLORATION;
            final Trace trace = track(server, traces, "ship", shipMission, expedition ? "moon" : "ship", state.phase().name());
            if (trace != null && trace.changed(state.elapsed(), state.phase().name(), state.feedback())) {
                final JsonObject details = numbers("verifiedTasks", state.completedTasks(), "decisions", state.decisions());
                details.addProperty("lastFlightStage", state.stage().name());
                if (state.phase().ended()) details.add("decisionJournal", strings(shipMission.debrief()));

                trace.update(server, state.elapsed(), state.phase().name(), state.feedback(), ship.assignedRoles(),
                        numbers("battery", state.battery(), "oxygen", state.oxygen(), "water", state.water(),
                                "fuel", state.fuel(), "parts", state.parts(), "co2Index", state.co2()),
                        details, expedition ? List.of(MoonMission.Incident.DOCKING.source, MoonMission.Incident.DESCENT.source,
                                MoonMission.Incident.SURFACE.source) : List.of(MoonMission.Incident.EMERGENCY.source, MoonMission.Incident.FILTER.source),
                        expedition ? "Fictional Earth–Moon trajectory and landing site. Instrument readings, maneuvers and reserves use classroom units."
                                : "Fictional spacecraft and rescue. Reserves, faults, pressure zones and repair timing are authored.");
            }
        }

        final OutpostScenarioController outpost = OutpostScenarioController.get(server);
        final ScenarioMission scenario = outpost.mission();
        if (scenario == null) finishRemoved(server, traces.remove("outpost"));
        else {
            final Trace trace = track(server, traces, "outpost", scenario, scenario.id, scenario.phase.name());
            if (trace != null && trace.changed(scenario.elapsed, scenario.phase.name(), scenario.feedback)) {
                final JsonObject details = switch (scenario.id) {
                    case "shelter" -> numbers("surveys", Integer.bitCount(scenario.scans), "installedShields", Integer.bitCount(scenario.shields),
                            "liveObservationSeconds", scenario.eventScanSeconds, "sosPulses", scenario.signal);
                    case "long_night" -> numbers("poweredLabSeconds", scenario.labSeconds, "sosPulses", scenario.signal);
                    default -> numbers("healthyCropObservations", Integer.bitCount(scenario.harvest), "extractionHumidityIndex", scenario.cabinHumidity);
                };
                details.addProperty("physicalRepairCompleted", scenario.repaired);
                details.addProperty("signalAcknowledged", scenario.acknowledgement >= 5);
                details.addProperty("remainingObjective", scenario.objective());
                trace.update(server, scenario.elapsed, scenario.phase.name(), scenario.feedback, outpost.assignedRoles(),
                        numbers("battery", scenario.battery, "oxygen", scenario.oxygen, "water", scenario.water,
                                "temperatureIndex", scenario.temperature, "exposureIndex", scenario.dose),
                        details, List.of(scenario.source()), scenario.evidence());
            }
        }

        final MoonController lunar = MoonController.get(server);
        final MoonMission legacy = lunar.mission();
        if (legacy == null) finishRemoved(server, traces.remove("legacy"));
        else {
            final String phase = legacy.stage() == MoonMission.Stage.COMPLETE ? "SUCCESS" : legacy.paused() ? "PAUSED" : "ACTIVE";
            final Trace trace = track(server, traces, "legacy", legacy, legacy.variant() == MoonMission.Variant.EXPLORATION ? "moon" : "ship", phase);
            if (trace != null && trace.changed(legacy.stage().ordinal(), phase, legacy.feedback())) {
                final JsonObject details = new JsonObject();
                details.addProperty("lastFlightStage", legacy.stage().name());
                details.addProperty("variant", "LEGACY");

                trace.update(server, legacy.stage().ordinal(), phase, legacy.feedback(), lunar.assignedRoles(),
                        numbers("battery", legacy.battery(), "oxygen", legacy.oxygen(), "water", legacy.water(), "fuel", legacy.fuel(), "parts", legacy.parts()),
                        details, List.of(legacy.variant() == MoonMission.Variant.EXPLORATION ? MoonMission.Incident.DESCENT.source
                                : MoonMission.Incident.EMERGENCY.source), "Legacy exercise. Sequence steps and resource values are authored; this sequence is untimed.");
            }
        }
    }

    private static @Nullable Trace track(final MinecraftServer server, final Map<String, Trace> traces, final String slot,
                                         final Object attempt, final String mission, final String phase) {
        final Trace current = traces.get(slot);
        if (current != null && current.attempt == attempt) return current;

        finishRemoved(server, traces.remove(slot));
        if (phase.equals("READY")) return null;

        final Trace trace = new Trace(attempt, mission, slot.equals("legacy"));
        traces.put(slot, trace);
        return trace;
    }

    private static void finishRemoved(final MinecraftServer server, final @Nullable Trace trace) {
        if (trace != null && !trace.saved) trace.save(server, "ABORTED", "Mission Control left or reset the active attempt.");
    }

    private static JsonArray strings(final Iterable<String> strings) {
        final JsonArray values = new JsonArray();
        strings.forEach(values::add);
        return values;
    }

    private static JsonObject numbers(final Object... pairs) {
        final JsonObject values = new JsonObject();
        for (int index = 0; index < pairs.length; index += 2) {
            final Number value = (Number) pairs[index + 1];
            values.addProperty((String) pairs[index], Math.round(value.doubleValue() * 100) / 100.0);
        }
        return values;
    }

    private static final class Trace {
        private final Object attempt;
        private final String clockKey;
        private final JsonObject report = new JsonObject();
        private final JsonArray timeline = new JsonArray();
        private final Set<String> roles = new TreeSet<>();
        private String phase = "", event = "";
        private int lastMark = -30;
        private boolean saved;

        private Trace(final Object attempt, final String mission, final boolean legacy) {
            this.attempt = attempt;
            clockKey = legacy ? "sequenceStep" : "elapsedSeconds";
            report.addProperty("schemaVersion", 1);
            report.addProperty("modVersion", VERSION);
            report.addProperty("mission", mission);
            for (final MissionHub.Mission entry : MissionHub.MISSIONS)
                if (entry.id().equals(mission)) report.addProperty("title", legacy ? entry.title() + " (legacy)" : entry.title());
            report.add("timeline", timeline);
        }

        boolean changed(final int mark, final String currentPhase, final String currentEvent) {
            return !saved && (mark - lastMark >= 15 || !phase.equals(currentPhase) || !event.equals(currentEvent));
        }

        void update(final MinecraftServer server, final int mark, final String currentPhase, final String currentEvent,
                    final Set<? extends Enum<?>> assignedRoles, final JsonObject reserves, final JsonObject details,
                    final List<String> sources, final String limits) {
            assignedRoles.forEach(role -> roles.add(role.name()));
            if (timeline.size() < 100) {
                final JsonObject sample = new JsonObject();
                sample.addProperty(clockKey, mark);
                sample.addProperty("phase", currentPhase);
                if (!event.equals(currentEvent)) sample.addProperty("event", currentEvent);
                sample.add("reserves", reserves);
                timeline.add(sample);
            } else report.addProperty("timelineTruncated", true);

            lastMark = mark;
            event = currentEvent;
            phase = currentPhase;
            report.addProperty(clockKey, mark);
            report.add("finalReserves", reserves);
            report.add("objectives", details);
            report.add("sources", strings(sources));
            report.addProperty("modelLimits", limits);

            if (ENDED.contains(currentPhase)) save(server, currentPhase, currentEvent);
        }

        void save(final MinecraftServer server, final String outcome, final String finalEvent) {
            saved = true;
            report.addProperty("outcome", outcome);
            report.addProperty("finalEvent", finalEvent);
            report.addProperty("endedAt", Instant.now().toString());
            report.add("crewRoles", strings(roles));
            report.add("reflection", strings(List.of("Which resource limited you?", "What information changed your plan?",
                    "What would you do differently next time?")));
            try {
                if (!archive(server).write(report))
                    NimbusNovaMod.LOGGER.warn("Mission archive is full. Move existing reports out of the world archive before the next mission.");
            } catch (final IOException error) {
                NimbusNovaMod.LOGGER.warn("Could not archive mission debrief", error);
            }
        }
    }
}
