package cc.nimbusnova.mc.mission;

public final class MissionPreparation {
    public enum TaskKind {
        BRIEFING, POWER, OXYGEN, COLLECT_SAMPLE, DELIVER_SAMPLE, SUPPLIES, LAUNCH, WEATHER_SCAN
    }

    public static final int OXYGEN_PRESSURE_MIN = 55, OXYGEN_PRESSURE_MAX = 65;
    public static final int OXYGEN_FLOW_MIN = 35, OXYGEN_FLOW_MAX = 45;
    public static final int OXYGEN_STABLE_TICKS = 40;
    private static final int[] POWER_PORTS = { 6, 10, 12, 9, 7, 3, 3, 10, 9 };
    private static final int[] NEIGHBOR_OFFSETS = { -3, 1, 3, -1 };
    private int powerBoard = 1 | 1 << 2 | 1 << 4 | 1 << 6 | 2 << 8 | 1 << 10 | 2 << 12 | 3 << 14 | 1 << 16;
    private int relayBoard = 2 | 3 << 2 | 1 << 4 | 2 << 6 | 2 << 10 | 1 << 12 | 1 << 14 | 3 << 16;
    private boolean briefed, powerChecked, oxygenChecked, sampleCollected, delivered, relayRepaired;
    private int powerRevision, oxygenRevision, relayRevision, oxygenValves, oxygenStableTicks;

    public boolean briefing() {
        if (briefed) return false;
        briefed = true;
        return true;
    }

    public boolean step(final TaskKind kind, final int expectedRevision, final int choice) {
        if (!briefed) return false;
        if (kind == TaskKind.POWER) {
            if (powerChecked || expectedRevision != powerRevision || choice < 0 || choice > 8) return false;
            powerBoard = rotate(powerBoard, choice);
            powerRevision++;
            powerChecked = powerConnected(powerBoard);
            return true;
        }

        if (kind != TaskKind.OXYGEN || oxygenChecked || expectedRevision != oxygenRevision || choice < 0 || choice > 6) return false;
        if (choice == 6) {
            if (oxygenStableTicks < OXYGEN_STABLE_TICKS) return false;
            oxygenChecked = true;
        } else {
            final int valve = choice / 2, level = oxygenValve(oxygenValves, valve);
            final int next = level + (choice % 2 == 0 ? 1 : -1);
            if (next < 0 || next > 4) return false;
            oxygenValves = oxygenValves & ~(7 << valve * 3) | next << valve * 3;
            oxygenStableTicks = 0;
        }

        oxygenRevision++;
        return true;
    }

    public boolean tickOxygen() {
        if (!briefed || oxygenChecked || oxygenStableTicks >= OXYGEN_STABLE_TICKS) return false;
        if (!oxygenBalanced(oxygenValves)) {
            final boolean changed = oxygenStableTicks != 0;
            oxygenStableTicks = 0;
            return changed;
        }

        oxygenStableTicks++;
        return true;
    }

    private static int rotate(final int board, final int cell) {
        final int shift = cell * 2;
        return board & ~(3 << shift) | (((board >> shift & 3) + 1) & 3) << shift;
    }

    public static int powerConnections(final int cell, final int board) {
        if (cell < 0 || cell >= POWER_PORTS.length) return 0;
        final int rotation = board >> cell * 2 & 3;
        return (POWER_PORTS[cell] << rotation | POWER_PORTS[cell] >> 4 - rotation) & 15;
    }

    public static int energizedCells(final int board) {
        if ((powerConnections(3, board) & 8) == 0) return 0;
        final int[] queue = new int[9];
        queue[0] = 3;
        int head = 0, tail = 1, visited = 1 << 3;
        while (head < tail) {
            final int cell = queue[head++], ports = powerConnections(cell, board);
            for (int direction = 0; direction < 4; direction++) {
                if ((ports & 1 << direction) == 0) continue;
                if (direction == 0 && cell < 3 || direction == 1 && cell % 3 == 2
                        || direction == 2 && cell >= 6 || direction == 3 && cell % 3 == 0) continue;
                final int neighbor = cell + NEIGHBOR_OFFSETS[direction];
                if ((visited & 1 << neighbor) != 0 || (powerConnections(neighbor, board) & 1 << (direction + 2) % 4) == 0) continue;
                visited |= 1 << neighbor;
                queue[tail++] = neighbor;
            }
        }

        return visited;
    }

    public static boolean powerConnected(final int board) {
        return (energizedCells(board) & 1 << 5) != 0 && (powerConnections(5, board) & 2) != 0;
    }

    public static int oxygenValve(final int state, final int valve) {
        return valve < 0 || valve > 2 ? 0 : state >> valve * 3 & 7;
    }

    public static int oxygenPressure(final int state) {
        return 20 + 10 * oxygenValve(state, 0) + 10 * oxygenValve(state, 1) - 5 * oxygenValve(state, 2);
    }

    public static int oxygenFlow(final int state) {
        return Math.max(0, 10 + 5 * oxygenValve(state, 0) + 10 * oxygenValve(state, 1) - 5 * oxygenValve(state, 2));
    }

    public static boolean oxygenBalanced(final int state) {
        final int pressure = oxygenPressure(state), flow = oxygenFlow(state);
        return pressure >= OXYGEN_PRESSURE_MIN && pressure <= OXYGEN_PRESSURE_MAX
                && flow >= OXYGEN_FLOW_MIN && flow <= OXYGEN_FLOW_MAX;
    }

    public boolean collectSample() {
        if (!briefed || sampleCollected) return false;
        sampleCollected = true;
        return true;
    }

    public boolean deliverSample() {
        if (!sampleCollected || delivered) return false;
        delivered = true;
        return true;
    }

    public boolean repairStep(final int expectedRevision, final int choice) {
        if (relayRepaired || expectedRevision != relayRevision || choice < 0 || choice > 8) return false;
        relayBoard = rotate(relayBoard, choice);
        relayRevision++;
        relayRepaired = powerConnected(relayBoard);
        return true;
    }

    public int puzzleState(final TaskKind kind, final boolean relayFault) {
        return kind == TaskKind.POWER ? relayFault ? relayBoard : powerBoard : oxygenValves;
    }

    public int puzzleAux(final TaskKind kind, final boolean relayFault) {
        return kind == TaskKind.POWER ? energizedCells(puzzleState(kind, relayFault))
                : oxygenPressure(oxygenValves) | oxygenFlow(oxygenValves) << 8 | oxygenStableTicks << 16;
    }

    public int puzzleRevision(final TaskKind kind, final boolean relayFault) {
        if (kind == TaskKind.POWER)
            return relayFault ? relayRepaired ? -1 : relayRevision : powerChecked ? -1 : powerRevision;
        return oxygenChecked ? -1 : oxygenRevision;
    }

    public int taskStep(final TaskKind kind, final boolean relayFault) {
        if (kind == TaskKind.POWER)
            return (relayFault ? relayRepaired : powerChecked) ? 3 : Math.min(2, Integer.bitCount(energizedCells(puzzleState(kind, relayFault))) / 2);
        return oxygenChecked ? 3 : oxygenBalanced(oxygenValves) ? 2 : oxygenValves != 0 ? 1 : 0;
    }

    public boolean briefed() {
        return briefed;
    }

    public boolean checked(final TaskKind kind) {
        return switch (kind) {
            case POWER -> powerChecked;
            case OXYGEN -> oxygenChecked;
            case COLLECT_SAMPLE -> sampleCollected;
            case DELIVER_SAMPLE -> delivered;
            case BRIEFING -> briefed;
            default -> false;
        };
    }

    public int completed() {
        return (briefed ? 1 : 0) + (powerChecked ? 1 : 0) + (oxygenChecked ? 1 : 0)
                + (sampleCollected ? 1 : 0) + (delivered ? 1 : 0);
    }

    public boolean ready() {
        return completed() == 5;
    }
}
