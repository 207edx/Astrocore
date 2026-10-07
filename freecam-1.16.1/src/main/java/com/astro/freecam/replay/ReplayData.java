package com.astro.freecam.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ReplayData {
    private final List<ReplayFrame> frames;
    private final String dimension;
    private final long createdAt;
    private final int sampleRate;

    public ReplayData(List<ReplayFrame> frames, String dimension, long createdAt, int sampleRate) {
        this.frames = Collections.unmodifiableList(new ArrayList<ReplayFrame>(frames));
        this.dimension = dimension == null ? "minecraft:unknown" : dimension;
        this.createdAt = createdAt;
        this.sampleRate = sampleRate <= 0 ? 20 : sampleRate;
    }

    public List<ReplayFrame> getFrames() { return frames; }
    public String getDimension() { return dimension; }
    public long getCreatedAt() { return createdAt; }

    public int getSampleRate() { return sampleRate; }

    public long getDurationMillis() {
        if (frames.isEmpty()) return 0L;
        return frames.size() == 1
                ? 1000L / sampleRate
                : (frames.size() - 1L) * 1000L / sampleRate;
    }

    public int size() { return frames.size(); }
}
