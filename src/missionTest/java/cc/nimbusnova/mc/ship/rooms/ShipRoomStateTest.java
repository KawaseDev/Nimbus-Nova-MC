package cc.nimbusnova.mc.ship.rooms;

import cc.nimbusnova.mc.ship.rooms.ShipRoomState.DoorMode;
import cc.nimbusnova.mc.ship.rooms.ShipRoomState.Fault;
import cc.nimbusnova.mc.ship.rooms.ShipRoomState.Room;

public final class ShipRoomStateTest {
    private ShipRoomStateTest() {
        /* w */
    }

    public static void main(final String[] arguments) {
        final ShipRoomState ship = new ShipRoomState();
        for (final Room room : Room.values()) {
            assert ship.pressure(room) == 100 && ship.powered(room) && ship.mayOpen(room);
            assert ship.fault(room) == Fault.NONE && !ship.isolated(room);
        }
        assert !ship.hasUnresolved();

        if (!ship.trigger(Room.MEDICAL, Fault.BREACH)) throw new AssertionError("Breach did not trigger");
        assert ship.isolated(Room.MEDICAL) && !ship.mayOpen(Room.MEDICAL) : "A detected breach closes the room immediately";
        ship.doorMode(Room.MEDICAL, DoorMode.OPEN);
        assert !ship.mayOpen(Room.MEDICAL) : "Manual opening cannot bypass a live breach";
        final var paused = ship.snapshot(Room.MEDICAL);
        ship.tickSecond(true);
        assert ship.snapshot(Room.MEDICAL).equals(paused) : "Learning pauses freeze pressure";
        for (int second = 0; second < 15; second++)
            ship.tickSecond(false);
        assert ship.pressure(Room.MEDICAL) == 0 && ship.pressure(Room.BRIDGE) == 100 : "A local breach is bounded and compartmentalized";
        if (ship.trigger(Room.MEDICAL, Fault.BREACH)) throw new AssertionError("Duplicate triggers restart incidents");
        if (!ship.patch(Room.MEDICAL)) throw new AssertionError("Patch did not seal the breach");
        if (ship.patch(Room.MEDICAL)) throw new AssertionError("One repair could consume two patches");
        for (int second = 0; second < 15; second++)
            ship.tickSecond(false);
        assert ship.pressure(Room.MEDICAL) == 75 && !ship.mayOpen(Room.MEDICAL);
        ship.tickSecond(false);
        assert ship.pressure(Room.MEDICAL) == 80 && ship.mayOpen(Room.MEDICAL) && !ship.hasUnresolved();

        ship.trigger(Room.COMMS, Fault.POWER);
        for (int second = 0; second < 3; second++)
            ship.tickSecond(false);
        assert ship.pressure(Room.COMMS) == 100 && !ship.powered(Room.COMMS) && !ship.mayOpen(Room.COMMS)
                : "Power loss stops automatic doors without inventing instant vacuum";
        ship.doorMode(Room.COMMS, DoorMode.OPEN);
        assert ship.mayOpen(Room.COMMS) : "Safe manual access is possible without motor power";
        ship.isolate(Room.COMMS, true);
        assert !ship.mayOpen(Room.COMMS);
        ship.isolate(Room.COMMS, false);
        ship.trigger(Room.COMMS, Fault.BREACH);
        ship.tickSecond(false);
        ship.tickSecond(false);
        ship.patch(Room.COMMS);
        ship.tickSecond(false);
        assert ship.pressure(Room.COMMS) == 80 && ship.fault(Room.COMMS) == Fault.POWER : "A patch cannot repair the electrical fault or power its pump";
        if (!ship.repairWire(Room.COMMS)) throw new AssertionError("Wire repair did not restore power");
        if (ship.repairWire(Room.COMMS)) throw new AssertionError("A wire repair was duplicated");
        ship.tickSecond(false);
        assert ship.pressure(Room.COMMS) == 85 && ship.fault(Room.COMMS) == Fault.NONE;
        ship.doorMode(Room.COMMS, DoorMode.CLOSED);
        assert !ship.mayOpen(Room.COMMS) && !ship.hasUnresolved() : "A deliberately closed healthy door is not an incident";
        ship.doorMode(Room.COMMS, DoorMode.AUTO);
        assert ship.mayOpen(Room.COMMS);
        for (int second = 0; second < 20; second++)
            ship.tickSecond(false);
        assert ship.pressure(Room.COMMS) == 100 && ship.pressure(Room.MEDICAL) == 100;
        System.out.println("Ship room checks passed: compartment isolation, bounded pressure, power faults, physical repair semantics and safe door modes.");
    }
}
