package cc.nimbusnova.mc.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@NullMarked
final class ReportArchive {
    private static final int CAPACITY = 100, MAX_BYTES = 262_144;
    private static final Pattern ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private final Path directory;
    private final Consumer<String> invalidFile;

    ReportArchive(final Path directory, final Consumer<String> invalidFile) {
        this.directory = directory;
        this.invalidFile = invalidFile;
    }

    boolean write(final JsonObject report) throws IOException {
        Files.createDirectories(directory);
        if (files().size() >= CAPACITY) return false;
        final String id = UUID.randomUUID().toString();
        report.addProperty("id", id);
        final byte[] bytes = report.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Mission report exceeds size limit");

        final Path temp = Files.createTempFile(directory, "report-", ".tmp");
        try {
            Files.write(temp, bytes);
            Files.move(temp, directory.resolve(id + ".json"), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        return true;
    }

    static boolean validId(final String id) {
        return ID.matcher(id).matches();
    }

    @Nullable JsonObject read(final String id) throws IOException {
        if (!validId(id)) return null;
        final Path file = directory.resolve(id + ".json");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return null;

        try (final var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            final byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("report too large");

            final JsonObject data = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!id.equals(data.get("id").getAsString()) || !data.get("title").getAsJsonPrimitive().isString()
                    || !data.get("mission").getAsJsonPrimitive().isString() || !data.get("outcome").getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("invalid report metadata");

            Instant.parse(data.get("endedAt").getAsString());
            return data;
        } catch (final JsonParseException | IllegalStateException | IllegalArgumentException | DateTimeParseException | NullPointerException error) {
            invalidFile.accept("Ignoring invalid mission report " + file.getFileName());
            return null;
        } catch (final IOException error) {
            invalidFile.accept("Could not read mission report " + file.getFileName());
            return null;
        }
    }

    JsonObject list() throws IOException {
        final List<String> ids = files();
        final List<JsonObject> recent = new ArrayList<>();
        for (final String id : ids) {
            final JsonObject data = read(id);
            if (data == null) continue;
            final JsonObject row = new JsonObject();
            for (final String key : List.of("id", "mission", "title", "outcome", "endedAt")) row.add(key, data.get(key));
            recent.add(row);
        }

        recent.sort(Comparator.comparing((final JsonObject report) -> Instant.parse(report.get("endedAt").getAsString())).reversed());
        final JsonArray entries = new JsonArray();
        recent.stream().limit(5).forEach(entries::add);
        final JsonObject response = new JsonObject();
        response.add("reports", entries);
        response.addProperty("capacityReached", ids.size() >= CAPACITY);
        return response;
    }

    private List<String> files() throws IOException {
        if (Files.notExists(directory)) return List.of();
        final List<String> ids = new ArrayList<>();
        try (final var files = Files.newDirectoryStream(directory, "*.json")) {
            for (final Path file : files) {
                final String name = file.getFileName().toString(), id = name.substring(0, name.length() - 5);
                if (!validId(id) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                ids.add(id);
                if (ids.size() >= CAPACITY) break;
            }
        }
        return ids;
    }
}
