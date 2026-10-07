package com.astro.freecam.replay;

public final class ReplayKeyframe {
    private final int frame;
    private final double x, y, z;
    private final float yaw, pitch;

    public ReplayKeyframe(int frame, double x, double y, double z, float yaw, float pitch) {
        this.frame = Math.max(0, frame);
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public int getFrame() { return frame; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
}
