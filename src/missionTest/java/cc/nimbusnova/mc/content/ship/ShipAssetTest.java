package cc.nimbusnova.mc.content.ship;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class ShipAssetTest {
    private ShipAssetTest() {
        /* w */
    }

    public static void main(final String[] arguments) throws IOException {
        final Path assets = Path.of("src/main/resources/assets/nimbus_nova");
        final String[] stations = { "pilot_console", "galley", "medical_station", "meeting_terminal", "engine_core",
                "science_lab", "comms_array", "hydroponics", "punching_bag", "storage_locker" };
        final Pattern bounds = Pattern.compile("\"from\"\\s*:\\s*\\[([^]]+)]\\s*,\\s*\"to\"\\s*:\\s*\\[([^]]+)]");
        final Pattern texture = Pattern.compile("nimbus_nova:block/([a-z_]+)");
        int elementCount = 0;
        for (final String station : stations) {
            final String state = Files.readString(assets.resolve("blockstates/ship_" + station + ".json"));
            for (final String facing : new String[] { "north", "east", "south", "west" }) {
                for (final boolean powered : new boolean[] { false, true }) {
                    if (!state.contains("facing=" + facing + ",powered=" + powered))
                        throw new AssertionError("Missing station state: " + station + "/" + facing + "/" + powered);
                }
            }

            for (final String directory : new String[] { "items", "models/item" }) {
                final String item = Files.readString(assets.resolve(directory + "/ship_" + station + ".json"));
                if (!item.contains("ship_" + station))
                    throw new AssertionError("Item chain does not point to its station: " + station);
            }

            for (final String suffix : new String[] { "", "_active" }) {
                final String model = Files.readString(assets.resolve("models/block/ship_" + station + suffix + ".json"));
                final var elements = bounds.matcher(model).results().toList();
                if (elements.size() < 12)
                    throw new AssertionError("Station needs a substantial original mesh: " + station);

                elementCount += elements.size();
                for (final var element : elements) {
                    final String[] minimum = element.group(1).split(","), maximum = element.group(2).split(",");
                    if (minimum.length != 3 || maximum.length != 3)
                        throw new AssertionError("Malformed model bounds: " + station);
                    for (int axis = 0; axis < 3; axis++) {
                        final double lower = Double.parseDouble(minimum[axis].strip()), upper = Double.parseDouble(maximum[axis].strip());
                        if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower < 0 || upper > 16 || lower >= upper)
                            throw new AssertionError("Station clips beyond its block: " + station);
                    }
                }

                for (final var reference : texture.matcher(model).results().toList()) {
                    final var image = ImageIO.read(assets.resolve("textures/block/" + reference.group(1) + ".png").toFile());
                    if (image == null)
                        throw new AssertionError("Unreadable material: " + reference.group(1));
                }

                if (!suffix.isEmpty() && !model.contains("light_emission"))
                    throw new AssertionError("The active station needs a visible powered state: " + station);
            }
        }

        System.out.println("Ship assets passed: ten original station meshes, eight states each, item chains and " + elementCount + " bounded faces.");
    }
}
