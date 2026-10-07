package com.astro.freecam.replay;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public final class ReplayFile {
    private static final String MAGIC = "ASTROREPLAY";
    private static final int VERSION = 1;

    private ReplayFile() {}

    public static ReplayData read(Path file) throws IOException {
        DataInputStream in = new DataInputStream(
                new BufferedInputStream(
                        new GZIPInputStream(Files.newInputStream(file))));
        try {
            String magic = in.readUTF();
            int version = in.readInt();
            int sampleRate = in.readInt();
            String dimension = in.readUTF();
            long createdAt = in.readLong();

            if (!MAGIC.equals(magic)) {
                throw new IOException("Not an Astro Freecam replay");
            }
            if (version != VERSION) {
                throw new IOException("Unsupported replay version: " + version);
            }
            if (sampleRate <= 0 || sampleRate > 240) {
                throw new IOException("Invalid replay sample rate: " + sampleRate);
            }

            List<ReplayFrame> frames = new ArrayList<ReplayFrame>();
            try {
                while (true) frames.add(readFrame(in));
            } catch (EOFException expected) {
            }

            if (frames.isEmpty()) {
                throw new IOException("Replay contains no frames");
            }

            return new ReplayData(frames, dimension, createdAt, sampleRate);
        } finally {
            try { in.close(); } catch (IOException ignored) {}
        }
    }

    private static ReplayFrame readFrame(DataInputStream in) throws IOException {
        double px = in.readDouble();
        double py = in.readDouble();
        double pz = in.readDouble();
        float pyaw = in.readFloat();
        float ppitch = in.readFloat();

        double cx = in.readDouble();
        double cy = in.readDouble();
        double cz = in.readDouble();
        float cyaw = in.readFloat();
        float cpitch = in.readFloat();
        boolean freecam = in.readBoolean();

        return new ReplayFrame(
                px, py, pz, pyaw, ppitch,
                cx, cy, cz, cyaw, cpitch, freecam);
    }
}
