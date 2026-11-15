package cc.nimbusnova.mc.mission;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

/** Per-world claims survive server and mission restarts. */
public final class GearClaims {
    public enum Kit { MARS, MOON }

    private GearClaims() { }

    private static Path marker(final Path world, final UUID player, final Kit kit) {
        return world.resolve("nimbus-nova/gear-claims").resolve(kit.name().toLowerCase(java.util.Locale.ROOT)).resolve(player.toString());
    }

    public static boolean claimed(final Path world, final UUID player, final Kit kit) throws IOException {
        try { Files.readAttributes(marker(world, player, kit), BasicFileAttributes.class); return true; }
        catch (NoSuchFileException missing) { return false; }
    }

    public static boolean claim(final Path world, final UUID player, final Kit kit) throws IOException {
        final Path file = marker(world, player, kit);
        Files.createDirectories(file.getParent());
        try { Files.createFile(file); return true; }
        catch (FileAlreadyExistsException duplicate) { return false; }
    }
}
