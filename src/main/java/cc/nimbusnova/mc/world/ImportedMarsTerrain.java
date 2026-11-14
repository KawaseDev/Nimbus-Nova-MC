package cc.nimbusnova.mc.world;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

public final class ImportedMarsTerrain {
    private final int width, height, verticalOffset;
    private final short[] samples;

    private ImportedMarsTerrain(final int width, final int height, final short[] samples) {
        this.width = width;
        this.height = height;
        this.samples = samples;
        int minimum = Short.MAX_VALUE, maximum = Short.MIN_VALUE;
        for (final short sample : samples) {
            minimum = Math.min(minimum, sample);
            maximum = Math.max(maximum, sample);
        }

        final int lowestOffset = -62 - minimum, highestOffset = 300 - maximum;
        if (lowestOffset > highestOffset)
            throw new IllegalArgumentException("Imported Mars relief cannot fit the training dimension without clipping or rescaling.");

        verticalOffset = Math.clamp(64 - samples[(height / 2) * width + width / 2], lowestOffset, highestOffset);
    }

    public static ImportedMarsTerrain loadConfigured() {
        final String configured = System.getProperty("nimbusnova.marsHeightmap");
        if (configured == null) return null;
        if (configured.isBlank())
            throw new IllegalArgumentException("nimbusnova.marsHeightmap must name an absolute heightmap file, or be omitted.");

        final Path path = Path.of(configured);
        if (!path.isAbsolute())
            throw new IllegalArgumentException("nimbusnova.marsHeightmap must be an absolute path: " + path);

        try {
            return load(path);
        } catch (final IOException exception) {
            throw new IllegalStateException("Cannot load the explicitly configured Mars heightmap: " + path, exception);
        }
    }

    public static ImportedMarsTerrain load(final Path path) throws IOException {
        if (!path.isAbsolute())
            throw new IllegalArgumentException("The Mars heightmap path must be absolute: " + path);

        try (final DataInputStream input = new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
            if (input.readInt() != 0x4E4D5331)
                throw new IOException("Invalid Mars heightmap header; expected NMS1.");

            final int width = input.readUnsignedShort(), height = input.readUnsignedShort();
            if (width < 2 || height < 2 || width > 1024 || height > 1024)
                throw new IOException("Mars heightmap dimensions must each be between 2 and 1024.");

            final short[] samples = new short[width * height];
            for (int index = 0; index < samples.length; index++)
                samples[index] = input.readShort();

            if (input.read() != -1)
                throw new IOException("Unexpected trailing data in Mars heightmap.");

            return new ImportedMarsTerrain(width, height, samples);
        }
    }

    public int normalizedHeight(final int x, final int z) {
        final long column = (long) x + width / 2, row = (long) z + height / 2;
        if (column < 0 || column >= width || row < 0 || row >= height)
            throw new IllegalArgumentException("Requested coordinate lies outside the imported Mars crop: " + x + ", " + z);

        return samples[(int) row * width + (int) column] + verticalOffset;
    }

    public int verticalOffset() {
        return verticalOffset;
    }

    public double influence(final int x, final int z) {
        final long column = (long) x + width / 2, row = (long) z + height / 2;
        if (column < 0 || column >= width || row < 0 || row >= height) return 0;

        final long edgeDistance = Math.min(Math.min(column, width - 1 - column), Math.min(row, height - 1 - row));
        final double blend = Math.clamp(edgeDistance / 16.0, 0, 1);
        return blend * blend * (3 - 2 * blend);
    }
}
