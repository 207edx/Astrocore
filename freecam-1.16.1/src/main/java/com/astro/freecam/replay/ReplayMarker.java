package com.astro.freecam.replay;

public final class ReplayMarker {
    private final int frame;
    private final String name;

    public ReplayMarker(int frame, String name) {
        this.frame = Math.max(0, frame);
        this.name = name == null || name.trim().isEmpty() ? "Marker" : name.trim();
    }

    public int getFrame() { return frame; }
    public String getName() { return name; }
}
