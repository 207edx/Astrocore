package com.astro.freecam.replay;

public final class ReplayFrame {
    public final double playerX, playerY, playerZ;
    public final float playerYaw, playerPitch;
    public final double cameraX, cameraY, cameraZ;
    public final float cameraYaw, cameraPitch;
    public final boolean freecam;

    public ReplayFrame(double playerX, double playerY, double playerZ,
                       float playerYaw, float playerPitch,
                       double cameraX, double cameraY, double cameraZ,
                       float cameraYaw, float cameraPitch,
                       boolean freecam) {
        this.playerX = playerX;
        this.playerY = playerY;
        this.playerZ = playerZ;
        this.playerYaw = playerYaw;
        this.playerPitch = playerPitch;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.cameraYaw = cameraYaw;
        this.cameraPitch = cameraPitch;
        this.freecam = freecam;
    }
}
