package cc.nimbusnova.mc.content;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContentAssetTest {
    private ContentAssetTest() {
        /* w */
    }

    public static void main(final String[] arguments) throws IOException {
        final Path assets = Path.of("src/main/resources/assets/nimbus_nova");
        final String registration = Files.readString(Path.of("src/main/java/cc/nimbusnova/mc/content/ModContent.java"));
        final Matcher items = Pattern.compile("register\\(\"([a-z_]+)\"").matcher(registration);
        final Pattern modelReference = Pattern.compile("\"model\"\\s*:\\s*\"nimbus_nova:([^\"]+)\"");
        final Pattern textureReference = Pattern.compile("\"layer0\"\\s*:\\s*\"nimbus_nova:([^\"]+)\"");
        final String language = Files.readString(assets.resolve("lang/en_us.json"));
        int checked = 0;
        while (items.find()) {
            final String name = items.group(1);
            final Matcher model = modelReference.matcher(Files.readString(assets.resolve("items/" + name + ".json")));
            if (!model.find())
                throw new AssertionError("Missing item model reference: " + name);

            final Matcher texture = textureReference.matcher(Files.readString(assets.resolve("models/" + model.group(1) + ".json")));
            if (!texture.find())
                throw new AssertionError("Missing texture reference: " + name);
            assert language.contains("\"item.nimbus_nova." + name + "\"") : "Missing localized item name: " + name;
            checkTexture(assets.resolve("textures/" + texture.group(1) + ".png"), 16, 16);
            checked++;
        }

        assert checked > 0 : "No registered items were checked";
        final String equipment = Files.readString(assets.resolve("equipment/expedition_suit.json"));
        assert equipment.contains("\"humanoid\"") && equipment.contains("\"humanoid_leggings\"");
        checkTexture(assets.resolve("textures/entity/equipment/humanoid/expedition_suit.png"), 64, 32);
        checkTexture(assets.resolve("textures/entity/equipment/humanoid_leggings/expedition_suit.png"), 64, 32);
        System.out.println("Content asset check passed: " + checked + " registered items, models, sprites, names and armor layers.");
    }

    private static void checkTexture(final Path path, final int width, final int height) throws IOException {
        final BufferedImage image = ImageIO.read(path.toFile());
        assert image != null : "Unreadable PNG: " + path;
        assert image.getWidth() == width && image.getHeight() == height : "Wrong texture dimensions: " + path;
        boolean transparent = false, opaque = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                transparent |= image.getRGB(x, y) >>> 24 == 0;
                opaque |= image.getRGB(x, y) >>> 24 == 255;
            }
        }

        assert transparent && opaque : "Texture must include visible artwork and transparent background: " + path;
    }
}
