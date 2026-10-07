package com.astro.freecam.replay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.world.World;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.GZIPOutputStream;

public final class ReplayRecorder {
    private static final String MAGIC = "ASTROREPLAY";
    private static final int VERSION = 1;
    private static final int SAMPLE_RATE = 20;
    private static final ReplayFrame SENTINEL =
            new ReplayFrame(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);

    private final Minecraft mc;
    private final BlockingQueue<ReplayFrame> queue = new LinkedBlockingQueue<ReplayFrame>();

    private volatile boolean recording;
    private volatile Throwable failure;
    private volatile long ticks;

    private Path target;
    private Path temp;
    private Thread writerThread;

    public ReplayRecorder(Minecraft mc) {
        this.mc = mc;
    }

    public synchronized boolean isRecording() {
        return recording;
    }

    public long getRecordedTicks() {
        return ticks;
    }

    public synchronized Path start(Path file) throws IOException {
        if (recording) throw new IOException("Already recording");

        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);

        target = file;
        temp = file.resolveSibling(file.getFileName().toString() + ".part");
        Files.deleteIfExists(temp);
        queue.clear();

        final DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(
                        new GZIPOutputStream(Files.newOutputStream(temp))));

        out.writeUTF(MAGIC);
        out.writeInt(VERSION);
        out.writeInt(SAMPLE_RATE);
        out.writeUTF(readDimension());
        out.writeLong(System.currentTimeMillis());
        out.flush();

        failure = null;
        ticks = 0L;
        recording = true;

        writerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                writerLoop(out);
            }
        }, "AstroFreecam-ReplayWriter");
        writerThread.setDaemon(true);
        writerThread.start();

        return file;
    }

    public boolean capture(boolean cameraIsFreecam,
                           double cameraX, double cameraY, double cameraZ,
                           float cameraYaw, float cameraPitch) {
        if (!recording) return false;

        ClientPlayerEntity player = mc.player;
        if (player == null) return false;

        ReplayFrame frame = new ReplayFrame(
                player.getPosX(), player.getPosY(), player.getPosZ(),
                player.rotationYaw, player.rotationPitch,
                cameraX, cameraY, cameraZ,
                cameraYaw, cameraPitch,
                cameraIsFreecam
        );

        try {
            queue.offer(frame);
            ticks++;
            return true;
        } catch (Throwable t) {
            failure = t;
            return false;
        }
    }

    public synchronized Path stop() {
        if (!recording) return target;

        recording = false;

        try {
            queue.offer(SENTINEL);
            if (writerThread != null) writerThread.join(5000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            writerThread = null;
        }

        if (failure == null) {
            try {
                Files.move(temp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFailure) {
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception moveFailure) {
                    failure = moveFailure;
                }
            }
        }

        return target;
    }

    public Throwable getFailure() {
        return failure;
    }

    private void writerLoop(DataOutputStream out) {
        try {
            while (true) {
                ReplayFrame frame = queue.take();
                if (frame == SENTINEL) break;
                writeFrame(out, frame);
            }

            out.flush();
            out.close();
        } catch (Throwable t) {
            failure = t;
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void writeFrame(DataOutputStream out, ReplayFrame frame) throws IOException {
        out.writeDouble(frame.playerX);
        out.writeDouble(frame.playerY);
        out.writeDouble(frame.playerZ);
        out.writeFloat(frame.playerYaw);
        out.writeFloat(frame.playerPitch);

        out.writeDouble(frame.cameraX);
        out.writeDouble(frame.cameraY);
        out.writeDouble(frame.cameraZ);
        out.writeFloat(frame.cameraYaw);
        out.writeFloat(frame.cameraPitch);
        out.writeBoolean(frame.freecam);
    }

    private String readDimension() {
        try {
            World world = mc.world;
            return world == null || world.getDimensionKey() == null
                    ? "minecraft:unknown"
                    : String.valueOf(world.getDimensionKey());
        } catch (Throwable ignored) {
            return "minecraft:unknown";
        }
    }
}
