package cc.nimbusnova.mc.content.outpost;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class OutpostAssetTest {
    private OutpostAssetTest() {
        /* w */
    }

    public static void main(final String[] arguments) throws IOException {
        final Path assets = Path.of("src/main/resources/assets/nimbus_nova");
        final Pattern texture = Pattern.compile("nimbus_nova:block/([a-z_]+)");
        for (final String name : new String[] { "sample_deposit", "sample_analyzer", "outpost_supply_locker", "outpost_weather_terminal" }) {
            final String states = Files.readString(assets.resolve("blockstates/" + name + ".json"));
            for (final String facing : new String[] { "north", "east", "south", "west" }) {
                assert states.contains("facing=" + facing + ",open=false") : name;
                assert states.contains("facing=" + facing + ",open=true") : name;
            }

            assert Files.readString(assets.resolve("items/" + name + ".json")).contains("nimbus_nova:item/" + name) : name;
            assert Files.readString(assets.resolve("models/item/" + name + ".json")).contains("nimbus_nova:block/" + name) : name;
            for (final String suffix : new String[] { "", "_open" }) {
                final String model = Files.readString(assets.resolve("models/block/" + name + suffix + ".json"));
                assert model.contains("\"elements\"") : "The equipment needs actual 3D geometry";
                final var references = texture.matcher(model);
                while (references.find()) {
                    final Path path = assets.resolve("textures/block/" + references.group(1) + ".png");
                    final var image = ImageIO.read(path.toFile());
                    assert image != null && image.getWidth() == 16 && (image.getHeight() == 16 || image.getHeight() == 64) : path;
                }
            }
        }

        final String animation = Files.readString(assets.resolve("textures/block/outpost_screen.png.mcmeta"));
        assert animation.contains("frametime") && animation.contains("interpolate") : "Screen animation metadata missing";
        assert Files.readString(assets.resolve("blockstates/outpost_hologram_projector.json")).contains("nimbus_nova:block/outpost_hologram_projector");
        assert Files.readString(assets.resolve("items/outpost_hologram_projector.json")).contains("nimbus_nova:item/outpost_hologram_projector");
        assert Files.readString(assets.resolve("models/block/outpost_hologram_projector.json")).contains("\"elements\"");
        final var emitter = ImageIO.read(assets.resolve("textures/block/outpost_emitter.png").toFile());
        assert emitter != null && emitter.getWidth() == 16 && emitter.getHeight() == 64;
        assert Files.readString(assets.resolve("textures/block/outpost_emitter.png.mcmeta")).contains("frametime");
        System.out.println("Outpost assets passed: four task model chains, projector, textures and animated displays.");
    }
}
