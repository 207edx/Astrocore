package com.astro.freecam.client;

public final class TripodPose {
    public final double x, y, z;
    public final float yaw, pitch;

    public TripodPose(double x, double y, double z, float yaw, float pitch) {
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch;
    }
}
