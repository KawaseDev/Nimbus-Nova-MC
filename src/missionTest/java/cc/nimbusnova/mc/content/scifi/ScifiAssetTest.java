package cc.nimbusnova.mc.content.scifi;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.Set;

public final class ScifiAssetTest {
    private ScifiAssetTest() {
        /* w */
    }

    public static void main(final String[] arguments) throws IOException {
        final Path assets = Path.of("src/main/resources/assets/nimbus_nova");
        final Pattern modelReference = Pattern.compile("nimbus_nova:(block|item)/(scifi_[a-z_]+)");
        final Pattern textureReference = Pattern.compile("nimbus_nova:block/(scifi_[a-z_]+)");
        final String[] names = { "dome_glass", "hull_panel", "reinforced_hull", "floor_tile", "floor_light", "strip_light",
                "support_beam", "cable_trunk", "vent", "battery_bank", "air_tank", "cryo_cabinet", "hydroponic_tray", "airlock_door" };
        for (final String name : names) {
            for (final String directory : new String[] { "blockstates", "items", "models/item" }) {
                final Path file = assets.resolve(directory + "/scifi_" + name + ".json");
                final var references = modelReference.matcher(Files.readString(file)).results().toList();
                assert !references.isEmpty() : "Missing model reference: " + file;
                for (final var reference : references) {
                    assert Files.exists(assets.resolve("models/" + reference.group(1) + "/" + reference.group(2) + ".json")) : file;
                }
            }
        }

        try (final var files = Files.list(assets.resolve("models/block"))) {
            for (final Path file : files.filter(path -> path.getFileName().toString().startsWith("scifi_")).toList()) {
                final String model = Files.readString(file);
                assert !model.contains("minecraft:block/iron_door") : "Door must use our mesh";
                final var references = textureReference.matcher(model).results().toList();
                assert !references.isEmpty() || Set.of("scifi_hull_panel.json", "scifi_floor_tile.json", "scifi_reinforced_hull.json").contains(file.getFileName().toString()) && model.contains("minecraft:block/") : file;
                for (final var reference : references) {
                    final var image = ImageIO.read(assets.resolve("textures/block/" + reference.group(1) + ".png").toFile());
                    assert image != null && image.getWidth() == 32 && image.getHeight() % 32 == 0 : file;
                    assert image.getHeight() == 32 || Files.readString(assets.resolve("textures/block/" + reference.group(1) + ".png.mcmeta")).contains("frametime") : file;
                }
            }
        }

        final String door = Files.readString(assets.resolve("blockstates/scifi_airlock_door.json"));
        for (final String facing : new String[] { "north", "south", "east", "west" })
            for (final String half : new String[] { "lower", "upper" })
                for (final String hinge : new String[] { "left", "right" })
                    for (final boolean open : new boolean[] { false, true })
                        assert door.contains("facing=" + facing + ",half=" + half + ",hinge=" + hinge + ",open=" + open) : "Native door state missing";

        final var glass = ImageIO.read(assets.resolve("textures/block/scifi_dome_glass.png").toFile());
        assert glass != null && (glass.getRGB(16, 16) >>> 24) <= 24 : "Dome must remain clear";
        assert Files.readString(assets.resolve("models/block/scifi_dome_glass.json")).contains("\"force_translucent\": true") : "26.3 native translucent texture required";
        System.out.println("Sci-fi assets passed: 14 item/model chains, door states, animated textures and clear dome glass.");
    }
}
