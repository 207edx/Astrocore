package com.astro.freecam.replay;

import com.astro.freecam.client.FreecamCamera;
import com.astro.freecam.client.FreecamClient;
import com.astro.freecam.client.ReplayCamera;
import com.astro.freecam.client.ReplayGhostPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ReplayManager {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Path DIR = Paths.get("freecam", "replays");
    private static final ReplayRecorder RECORDER = new ReplayRecorder(MC);

    private static ReplayData playback;
    private static Path playbackFile;
    private static ReplayCamera playbackCamera;
    private static ReplayGhostPlayer ghost;
    private static int playbackIndex;
    private static boolean paused;
    private static boolean freecamView;
    private static double speed = 1.0D;
    private static double accumulator;

    private ReplayManager() {}

    public static boolean isRecording() { return RECORDER.isRecording(); }
    public static boolean isPlaying() { return playback != null; }
    public static boolean isPaused() { return paused; }
    public static boolean isFreecamView() { return freecamView; }
    public static double getSpeed() { return speed; }
    public static int getFrameIndex() { return playbackIndex; }

    public static Path replayDir() {
        return MC.gameDir.toPath().resolve(DIR);
    }

    public static List<Path> listReplays() {
        Path dir = replayDir();

        try {
            Files.createDirectories(dir);
            List<Path> files = new ArrayList<Path>();
            java.nio.file.DirectoryStream<Path> stream =
                    Files.newDirectoryStream(dir, "*.replay");
            try {
                for (Path path : stream) {
                    if (Files.isRegularFile(path)) files.add(path);
                }
            } finally {
                stream.close();
            }

            Collections.sort(files, new Comparator<Path>() {
                @Override
                public int compare(Path a, Path b) {
                    try {
                        return Long.compare(
                                Files.getLastModifiedTime(b).toMillis(),
                                Files.getLastModifiedTime(a).toMillis());
                    } catch (IOException e) {
                        return a.getFileName().toString()
                                .compareToIgnoreCase(b.getFileName().toString());
                    }
                }
            });

            return files;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public static Path createRecordingPath(String requestedName) throws IOException {
        Path dir = replayDir().toAbsolutePath().normalize();
        Files.createDirectories(dir);

        String base = sanitize(requestedName);
        if (base.length() == 0) {
            base = "replay_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT)
                    .format(new Date());
        }
        if (!base.toLowerCase(Locale.ROOT).endsWith(".replay")) {
            base += ".replay";
        }

        Path file = dir.resolve(base).normalize();
        if (!dir.equals(file.getParent())) {
            throw new IOException("Invalid replay name");
        }

        if (Files.exists(file)) {
            String stem = base.substring(0, base.length() - ".replay".length());
            int n = 2;
            do {
                file = dir.resolve(stem + "_" + n++ + ".replay");
            } while (Files.exists(file));
        }

        return file;
    }

    public static void startRecording(String requestedName) {
        if (MC.player == null || MC.world == null
                || RECORDER.isRecording() || playback != null) return;

        try {
            Path file = createRecordingPath(requestedName);
            RECORDER.start(file);
            notify(TextFormatting.RED + "Recording started: " + file.getFileName());
        } catch (Throwable t) {
            notifyError("Could not start recording: " + safe(t));
        }
    }

    public static void captureTick() {
        if (!RECORDER.isRecording() || MC.player == null || MC.world == null) return;

        net.minecraft.entity.Entity view = MC.getRenderViewEntity();

        double x = MC.player.getPosX();
        double y = MC.player.getPosYEye();
        double z = MC.player.getPosZ();
        float yaw = MC.player.rotationYaw;
        float pitch = MC.player.rotationPitch;

        boolean freecam =
                FreecamClient.enabled() && view instanceof FreecamCamera;

        if (freecam) {
            x = view.getPosX();
            y = view.getPosY();
            z = view.getPosZ();
            yaw = view.rotationYaw;
            pitch = view.rotationPitch;
        }

        RECORDER.capture(freecam, x, y, z, yaw, pitch);
    }

    public static void stopRecording() {
        if (!RECORDER.isRecording()) return;

        Path file = RECORDER.stop();

        if (RECORDER.getFailure() == null) {
            notify(TextFormatting.RED + "Recording saved: "
                    + (file == null ? "replay.replay" : file.getFileName()));
        } else {
            notifyError("Replay recording error: " + safe(RECORDER.getFailure()));
        }
    }

    public static void startPlayback(Path file) {
        if (file == null || MC.world == null) return;

        try {
            Path normalized = file.toAbsolutePath().normalize();
            Path dir = replayDir().toAbsolutePath().normalize();

            if (!Files.isRegularFile(normalized)
                    || !dir.equals(normalized.getParent())) {
                throw new IOException("Invalid replay file");
            }

            ReplayData data = ReplayFile.read(normalized);

            if (RECORDER.isRecording()) stopRecording();
            FreecamClient.stopForReplay();
            stopPlayback();

            playback = data;
            playbackFile = normalized;
            playbackIndex = 0;
            accumulator = 0.0D;
            paused = false;
            freecamView = false;
            speed = 1.0D;

            playbackCamera = new ReplayCamera(
                    MC.world,
                    MC.player == null ? null : MC.player.getGameProfile());
            ghost = new ReplayGhostPlayer(
                    MC.world,
                    MC.player == null ? null : MC.player.getGameProfile());

            MC.world.addPlayer(ReplayGhostPlayer.ENTITY_ID, ghost);
            applyFrame();

            notify(TextFormatting.GREEN + "Replay loaded: "
                    + normalized.getFileName());
        } catch (Throwable t) {
            notifyError("Could not load replay: " + safe(t));
        }
    }

    public static void tickPlayback() {
        if (playback == null || MC.world == null
                || playbackCamera == null || ghost == null) return;

        if (!paused && playback.size() > 1) {
            accumulator += speed;

            while (accumulator >= 1.0D
                    && playbackIndex < playback.size() - 1) {
                playbackIndex++;
                accumulator -= 1.0D;
            }

            if (playbackIndex >= playback.size() - 1) {
                playbackIndex = playback.size() - 1;
                accumulator = 0.0D;
                paused = true;
            }
        }

        applyFrame();
    }

    public static void togglePause() {
        if (playback != null) paused = !paused;
    }

    public static void toggleView() {
        if (playback == null) return;
        freecamView = !freecamView;
        applyFrame();
        notify("Replay view: "
                + (freecamView ? "Freecam" : "Player POV"));
    }

    public static void seek(int frames) {
        if (playback == null || playback.size() == 0) return;
        playbackIndex = MathHelper.clamp(
                playbackIndex + frames, 0, playback.size() - 1);
        accumulator = 0.0D;
        applyFrame();
    }

    public static void setSpeed(double value) {
        speed = MathHelper.clamp(value, 0.25D, 4.0D);
        notify(String.format(
                Locale.ROOT, "Replay speed: %.2fx", speed));
    }

    public static void delete(Path file) throws IOException {
        if (file == null) return;

        Path normalized = file.toAbsolutePath().normalize();
        Path dir = replayDir().toAbsolutePath().normalize();

        if (!dir.equals(normalized.getParent())) {
            throw new IOException("Invalid replay path");
        }

        if (playbackFile != null
                && playbackFile.equals(normalized)) {
            stopPlayback();
        }

        Files.deleteIfExists(normalized);
    }

    public static void stopPlayback() {
        try {
            if (MC.player != null) MC.setRenderViewEntity(MC.player);
        } catch (Throwable ignored) {
        }

        if (MC.world != null && ghost != null) {
            try { ghost.remove(); } catch (Throwable ignored) {}
        }

        if (playbackCamera != null) {
            try { playbackCamera.remove(); } catch (Throwable ignored) {}
        }

        playback = null;
        playbackFile = null;
        playbackIndex = 0;
        paused = false;
        accumulator = 0.0D;
        speed = 1.0D;
        freecamView = false;
        playbackCamera = null;
        ghost = null;
    }

    public static void lifecycleReset() {
        if (RECORDER.isRecording()) stopRecording();
        stopPlayback();
    }

    private static void applyFrame() {
        if (playback == null || playback.size() == 0
                || MC.world == null || playbackCamera == null
                || ghost == null) return;

        ReplayFrame a = playback.getFrames().get(playbackIndex);
        ReplayFrame b = a;

        if (playbackIndex < playback.size() - 1) {
            b = playback.getFrames().get(playbackIndex + 1);
        }

        double t = paused ? 0.0D : accumulator;

        double playerX = lerp(a.playerX, b.playerX, t);
        double playerY = lerp(a.playerY, b.playerY, t);
        double playerZ = lerp(a.playerZ, b.playerZ, t);
        float playerYaw = angleLerp(a.playerYaw, b.playerYaw, (float) t);
        float playerPitch = (float) lerp(a.playerPitch, b.playerPitch, t);

        setEntity(ghost, playerX, playerY, playerZ,
                playerYaw, playerPitch);

        double cameraX;
        double cameraY;
        double cameraZ;
        float cameraYaw;
        float cameraPitch;

        if (freecamView) {
            cameraX = lerp(a.cameraX, b.cameraX, t);
            cameraY = lerp(a.cameraY, b.cameraY, t);
            cameraZ = lerp(a.cameraZ, b.cameraZ, t);
            cameraYaw = angleLerp(a.cameraYaw, b.cameraYaw, (float) t);
            cameraPitch = (float) lerp(
                    a.cameraPitch, b.cameraPitch, t);
        } else {
            cameraX = playerX;
            cameraY = playerY + 1.62D;
            cameraZ = playerZ;
            cameraYaw = playerYaw;
            cameraPitch = playerPitch;
        }

        playbackCamera.setPositionAndRotation(
                cameraX, cameraY, cameraZ, cameraYaw, cameraPitch);

        playbackCamera.prevPosX = cameraX;
        playbackCamera.prevPosY = cameraY;
        playbackCamera.prevPosZ = cameraZ;
        playbackCamera.prevRotationYaw = cameraYaw;
        playbackCamera.prevRotationPitch = cameraPitch;

        if (MC.getRenderViewEntity() != playbackCamera) {
            MC.setRenderViewEntity(playbackCamera);
        }
    }

    private static void setEntity(ReplayGhostPlayer entity,
                                  double x, double y, double z,
                                  float yaw, float pitch) {
        entity.setPositionAndRotation(x, y, z, yaw, pitch);
        entity.rotationYawHead = yaw;
        entity.renderYawOffset = yaw;
        entity.prevRotationYaw = yaw;
        entity.prevRotationPitch = pitch;
        entity.prevRotationYawHead = yaw;
        entity.prevRenderYawOffset = yaw;
        entity.lastTickPosX = x;
        entity.lastTickPosY = y;
        entity.lastTickPosZ = z;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static float angleLerp(float a, float b, float t) {
        return a + MathHelper.wrapDegrees(b - a) * t;
    }

    private static String sanitize(String raw) {
        if (raw == null) return "";

        String value = raw.trim()
                .replaceAll("[\\/:*?\"<>|]", "_");

        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }

        return value;
    }

    private static String safe(Throwable t) {
        if (t == null || t.getMessage() == null) return "unknown error";
        return t.getMessage();
    }

    private static void notify(String message) {
        try {
            if (MC.player != null) {
                MC.player.sendStatusMessage(
                        new StringTextComponent("§bReplay: " + message), true);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void notifyError(String message) {
        try {
            if (MC.player != null) {
                MC.player.sendStatusMessage(
                        new StringTextComponent("§cReplay: " + message), false);
            }
        } catch (Throwable ignored) {
        }
    }
}
