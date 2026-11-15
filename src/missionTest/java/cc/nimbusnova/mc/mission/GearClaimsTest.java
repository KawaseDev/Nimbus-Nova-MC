package cc.nimbusnova.mc.mission;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class GearClaimsTest {
    public static void main(final String[] args) throws Exception {
        final Path root = Files.createTempDirectory("nimbus-gear-test");
        try {
            final Path world = root.resolve("world"), fresh = root.resolve("fresh-world");
            final UUID player = UUID.randomUUID(), other = UUID.randomUUID();
            if (GearClaims.claimed(world, player, GearClaims.Kit.MARS)) throw new AssertionError("A new player is unclaimed");
            if (!GearClaims.claim(world, player, GearClaims.Kit.MARS)) throw new AssertionError("The first kit must be issued");
            if (!GearClaims.claimed(world, player, GearClaims.Kit.MARS) || GearClaims.claim(world, player, GearClaims.Kit.MARS))
                throw new AssertionError("A stored claim must survive a new lookup and reject duplicates");
            if (!GearClaims.claim(world, other, GearClaims.Kit.MARS) || !GearClaims.claim(world, player, GearClaims.Kit.MOON))
                throw new AssertionError("Claims must be separate per player and kit");
            if (!GearClaims.claim(fresh, player, GearClaims.Kit.MARS)) throw new AssertionError("A fresh world must permit a new kit");
            final Path invalid = root.resolve("not-a-world");
            Files.writeString(invalid, "file");
            try { GearClaims.claim(invalid, player, GearClaims.Kit.MARS); throw new AssertionError("Failed storage must not issue gear"); }
            catch (java.io.IOException expected) { }
        } finally {
            try (var files = Files.walk(root)) {
                for (final Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
        System.out.println("Gear claims passed: persistence, duplicate prevention and fresh worlds.");
    }
}
