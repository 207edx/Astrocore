package com.astro.freecam.client.gui;

import com.astro.freecam.replay.ReplayManager;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.StringTextComponent;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class ReplayManagerScreen extends Screen {
    private final Minecraft mc = Minecraft.getInstance();

    private List<Path> files;
    private int selected = -1;
    private String message = "";
    private int messageTicks;

    public ReplayManagerScreen() {
        super(new StringTextComponent("Astro Freecam Replay Manager"));
    }

    @Override
    protected void init() {
        int cx = width / 2;

        addButton(new Button(
                cx - 155, 34, 100, 20,
                new StringTextComponent("Record"),
                b -> record()));

        addButton(new Button(
                cx - 50, 34, 100, 20,
                new StringTextComponent("Stop"),
                b -> stopRecording()));

        addButton(new Button(
                cx + 55, 34, 100, 20,
                new StringTextComponent("Refresh"),
                b -> refresh()));

        addButton(new Button(
                cx - 155, height - 34, 100, 20,
                new StringTextComponent("Play"),
                b -> playSelected()));

        addButton(new Button(
                cx - 50, height - 34, 100, 20,
                new StringTextComponent("Delete"),
                b -> deleteSelected()));

        addButton(new Button(
                cx + 55, height - 34, 100, 20,
                new StringTextComponent("Close"),
                b -> closeReplayScreen()));

        refresh();
    }

    private void refresh() {
        files = ReplayManager.listReplays();

        if (selected >= files.size()) {
            selected = files.isEmpty() ? -1 : files.size() - 1;
        }

        message = files.isEmpty()
                ? "No replay files yet."
                : "Click a replay, then Play.";
        messageTicks = 80;
    }

    private void record() {
        if (ReplayManager.isPlaying()) {
            message = "Stop playback before recording.";
        } else if (ReplayManager.isRecording()) {
            message = "Already recording.";
        } else {
            ReplayManager.startRecording("");
            message = "Recording started.";
        }
        messageTicks = 80;
    }

    private void stopRecording() {
        if (ReplayManager.isRecording()) {
            ReplayManager.stopRecording();
            refresh();
            message = "Recording saved.";
        } else {
            message = "Nothing is recording.";
        }
        messageTicks = 80;
    }

    private void playSelected() {
        Path file = getSelected();

        if (file == null) {
            message = "Select a replay first.";
            messageTicks = 80;
            return;
        }

        closeReplayScreen();
        ReplayManager.startPlayback(file);
    }

    private void deleteSelected() {
        Path file = getSelected();

        if (file == null) {
            message = "Select a replay first.";
            messageTicks = 80;
            return;
        }

        try {
            ReplayManager.delete(file);
            refresh();
            message = "Deleted " + file.getFileName();
        } catch (Throwable t) {
            message = "Delete failed: " + safeMessage(t);
        }

        messageTicks = 100;
    }

    private Path getSelected() {
        return files != null
                && selected >= 0
                && selected < files.size()
                ? files.get(selected)
                : null;
    }

    private void closeReplayScreen() {
        mc.displayGuiScreen(null);
    }

    @Override
    public void tick() {
        if (messageTicks > 0) messageTicks--;
        super.tick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int top = 70;
        int rowHeight = 22;

        if (mouseY >= top && mouseY < height - 46) {
            int index = (int) ((mouseY - top) / rowHeight);

            if (files != null && index >= 0 && index < files.size()) {
                selected = index;
                message = "Selected: " + files.get(index).getFileName();
                messageTicks = 60;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            closeReplayScreen();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(MatrixStack matrix, int mouseX, int mouseY, float partialTicks) {
        renderBackground(matrix);

        drawCenteredString(
                matrix, font,
                "§bAstro Freecam Replay Manager",
                width / 2, 8, 0xFFFFFF);

        drawCenteredString(
                matrix, font,
                "Lightweight movement + camera replay files",
                width / 2, 20, 0x909090);

        if (ReplayManager.isRecording()) {
            drawString(matrix, font, "§c● RECORDING",
                    8, 10, 0xFFFFFF);
        }

        fill(
                matrix,
                width / 2 - 165, 58,
                width / 2 + 165, height - 42,
                0xAA10151C);

        drawString(
                matrix, font,
                "Saved .replay files",
                width / 2 - 155, 62, 0xFFFFFF);

        if (files == null || files.isEmpty()) {
            drawCenteredString(
                    matrix, font,
                    "No replay files found",
                    width / 2, 115, 0x909090);
        } else {
            int y = 82;

            for (int i = 0;
                 i < files.size() && y < height - 52;
                 i++, y += 22) {

                int background =
                        i == selected
                                ? 0xFF284A65
                                : 0x66242A30;

                fill(
                        matrix,
                        width / 2 - 155, y - 2,
                        width / 2 + 155, y + 18,
                        background);

                String name = files.get(i)
                        .getFileName().toString();

                if (name.length() > 44) {
                    name = name.substring(0, 41) + "...";
                }

                drawString(
                        matrix, font, name,
                        width / 2 - 148, y + 3,
                        0xE8E8E8);
            }
        }

        if (ReplayManager.isPlaying()) {
            String state =
                    ReplayManager.isPaused()
                            ? "Paused"
                            : "Playing";

            String view =
                    ReplayManager.isFreecamView()
                            ? "Freecam"
                            : "Player POV";

            String controls =
                    state + " | " + view + " | "
                    + String.format(
                            Locale.ROOT,
                            "%.2fx",
                            ReplayManager.getSpeed());

            drawCenteredString(
                    matrix, font, controls,
                    width / 2, height - 52,
                    0xA0E0A0);
        } else if (messageTicks > 0) {
            drawCenteredString(
                    matrix, font, message,
                    width / 2, height - 52,
                    0xD0D0D0);
        }

        drawCenteredString(
                matrix, font,
                "P pause • O POV • ←/→ seek • ↑/↓ speed",
                width / 2, height - 8, 0x808080);

        super.render(matrix, mouseX, mouseY, partialTicks);
    }

    private static String safeMessage(Throwable t) {
        return t == null || t.getMessage() == null
                ? "unknown error"
                : t.getMessage();
    }
}
