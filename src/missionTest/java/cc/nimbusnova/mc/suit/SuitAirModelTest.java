package cc.nimbusnova.mc.suit;

public final class SuitAirModelTest {
    private SuitAirModelTest() {
        /* w */
    }

    public static void main(final String[] arguments) {
        final SuitAirModel model = new SuitAirModel();
        model.tick(false, true, false);
        assert model.air == 99.5 : "Sealed expedition suits consume a bounded reserve";
        model.tick(false, false, false);
        assert model.air == 94.5 : "An incomplete suit loses air faster";
        model.tick(true, false, true);
        assert model.air == 94.5 : "Pause freezes personal reserves too";
        model.tick(true, false, false);
        assert model.air == 100 : "Pressurized areas refill the reserve without overshoot";
        for (int second = 0; second < 21; second++)
            model.tick(false, false, false);

        assert model.air == 0 : "Air must not become negative";
        model.tick(true, false, false);
        assert model.air == 10 : "A safe return can recover an exhausted reserve";
        assert SuitAirModel.insideDome(0, 66, 0);
        assert !SuitAirModel.insideDome(40, 66, -16) : "Sample site remains an EVA";
        assert !SuitAirModel.insideDome(0, 66, -44) : "Arrival happens outside the dome";
        assert !SuitAirModel.insideDome(0, 100, 0);
        assert SuitAirModel.insideRoom(0, 66, 25) : "Legacy shelter stays breathable";
        assert !SuitAirModel.insideRoom(0, 70, 25) : "Above the shelter is outside";
        System.out.println("Suit air model passed: sealed/unsealed use, refill, pause, clamps and pressure zones.");
    }
}
