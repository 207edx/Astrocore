package com.astro.freecam.replay;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class ReplayMetadata {
    private static final int VERSION = 1;
    private final List<ReplayMarker> markers = new ArrayList<ReplayMarker>();
    private final List<ReplayKeyframe> keyframes = new ArrayList<ReplayKeyframe>();

    public List<ReplayMarker> markers() { return Collections.unmodifiableList(markers); }
    public List<ReplayKeyframe> keyframes() { return Collections.unmodifiableList(keyframes); }

    public void addMarker(ReplayMarker marker) { if (marker != null) { markers.add(marker); sortMarkers(); } }
    public void addKeyframe(ReplayKeyframe keyframe) { if (keyframe != null) { keyframes.add(keyframe); sortKeyframes(); } }

    public void sortMarkers() {
        Collections.sort(markers, new Comparator<ReplayMarker>() {
            @Override public int compare(ReplayMarker a, ReplayMarker b) { return Integer.compare(a.getFrame(), b.getFrame()); }
        });
    }

    public void sortKeyframes() {
        Collections.sort(keyframes, new Comparator<ReplayKeyframe>() {
            @Override public int compare(ReplayKeyframe a, ReplayKeyframe b) { return Integer.compare(a.getFrame(), b.getFrame()); }
        });
    }

    public static ReplayMetadata load(Path replay) {
        ReplayMetadata m = new ReplayMetadata();
        Path meta = metaPath(replay);
        if (!Files.isRegularFile(meta)) return m;
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(meta))));
            try {
                if (in.readInt() != VERSION) return m;
                int markerCount = readCount(in);
                for (int i = 0; i < markerCount; i++) m.markers.add(new ReplayMarker(in.readInt(), in.readUTF()));
                int keyframeCount = readCount(in);
                for (int i = 0; i < keyframeCount; i++) {
                    m.keyframes.add(new ReplayKeyframe(in.readInt(), in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat()));
                }
            } finally { in.close(); }
            m.sortMarkers();
            m.sortKeyframes();
        } catch (Throwable ignored) {
            m.markers.clear();
            m.keyframes.clear();
        }
        return m;
    }

    public void save(Path replay) throws IOException {
        Path meta = metaPath(replay);
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(meta))));
        try {
            out.writeInt(VERSION);
            out.writeInt(markers.size());
            for (ReplayMarker marker : markers) { out.writeInt(marker.getFrame()); out.writeUTF(marker.getName()); }
            out.writeInt(keyframes.size());
            for (ReplayKeyframe k : keyframes) {
                out.writeInt(k.getFrame());
                out.writeDouble(k.getX()); out.writeDouble(k.getY()); out.writeDouble(k.getZ());
                out.writeFloat(k.getYaw()); out.writeFloat(k.getPitch());
            }
        } finally { out.close(); }
    }

    public static Path metaPath(Path replay) {
        return replay.resolveSibling(replay.getFileName().toString() + ".meta");
    }

    private static int readCount(DataInputStream in) throws IOException {
        int value = in.readInt();
        if (value < 0 || value > 100000) throw new IOException("Invalid metadata count");
        return value;
    }
}
