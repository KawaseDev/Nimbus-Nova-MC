package cc.nimbusnova.mc.ship.rooms;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Authored compartment indices; this is not a fluid, electrical or human-survival simulation. */
public final class ShipRoomState {
    public enum Room {
        BRIDGE, ENGINEERING, MEDICAL, GALLEY, CONTROL, COMMS, SCIENCE, HYDROPONICS, STORAGE, RECREATION
    }

    public enum Fault {
        NONE, POWER, BREACH
    }

    public enum DoorMode {
        AUTO, OPEN, CLOSED
    }

    private final Map<Room, Compartment> rooms = new EnumMap<>(Room.class);

    public ShipRoomState() {
        for (final Room room : Room.values())
            rooms.put(room, new Compartment());
    }

    public boolean trigger(final Room room, final Fault fault) {
        final Compartment compartment = rooms.get(Objects.requireNonNull(room));
        return switch (Objects.requireNonNull(fault)) {
            case NONE -> false;
            case POWER -> {
                if (!compartment.powered) yield false;
                compartment.powered = false;
                yield true;
            }
            case BREACH -> {
                if (compartment.breached) yield false;
                compartment.breached = true;
                yield true;
            }
        };
    }

    public void tickSecond(final boolean paused) {
        if (paused) return;

        for (final Compartment compartment : rooms.values()) {
            if (compartment.breached)
                compartment.pressure = Math.max(0, compartment.pressure - 10);
            else if (compartment.powered)
                compartment.pressure = Math.min(100, compartment.pressure + 5);
        }
    }

    public boolean patch(final Room room) {
        final Compartment compartment = rooms.get(Objects.requireNonNull(room));
        if (!compartment.breached) return false;

        compartment.breached = false;
        return true;
    }

    public boolean repairWire(final Room room) {
        final Compartment compartment = rooms.get(Objects.requireNonNull(room));
        if (compartment.powered) return false;

        compartment.powered = true;
        return true;
    }

    public boolean isolate(final Room room, final boolean isolated) {
        final Compartment compartment = rooms.get(Objects.requireNonNull(room));
        if (compartment.manualIsolation == isolated) return false;

        compartment.manualIsolation = isolated;
        return true;
    }

    public boolean doorMode(final Room room, final DoorMode mode) {
        final Compartment compartment = rooms.get(Objects.requireNonNull(room));
        Objects.requireNonNull(mode);
        if (compartment.doorMode == mode) return false;

        compartment.doorMode = mode;
        return true;
    }

    public DoorMode doorMode(final Room room) {
        return rooms.get(Objects.requireNonNull(room)).doorMode;
    }

    public int pressure(final Room room) {
        return rooms.get(Objects.requireNonNull(room)).pressure;
    }

    public boolean powered(final Room room) {
        return rooms.get(Objects.requireNonNull(room)).powered;
    }

    public Fault fault(final Room room) {
        final Compartment compartment = rooms.get(Objects.requireNonNull(room));
        return compartment.breached ? Fault.BREACH : compartment.powered ? Fault.NONE : Fault.POWER;
    }

    public boolean isolated(final Room room) {
        final Compartment compartment = rooms.get(Objects.requireNonNull(room));
        return compartment.manualIsolation || compartment.breached || compartment.pressure < 80;
    }

    public boolean mayOpen(final Room room) {
        final Compartment compartment = rooms.get(Objects.requireNonNull(room));
        if (compartment.manualIsolation || compartment.breached || compartment.pressure < 80
                || compartment.doorMode == DoorMode.CLOSED) return false;

        return compartment.doorMode == DoorMode.OPEN || compartment.powered;
    }

    public boolean hasUnresolved() {
        return rooms.values().stream().anyMatch(compartment -> compartment.breached || !compartment.powered || compartment.pressure < 80);
    }

    public Snapshot snapshot(final Room room) {
        return new Snapshot(pressure(room), powered(room), isolated(room), doorMode(room), fault(room));
    }

    public record Snapshot(int pressure, boolean powered, boolean isolated, DoorMode doorMode, Fault fault) {
        /* w */
    }

    private static final class Compartment {
        private int pressure = 100;
        private boolean powered = true;
        private boolean breached, manualIsolation;
        private DoorMode doorMode = DoorMode.AUTO;
    }
}
