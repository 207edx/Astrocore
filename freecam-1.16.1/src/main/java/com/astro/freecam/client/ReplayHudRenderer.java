package com.astro.freecam.client;

import com.astro.freecam.replay.ReplayKeyframe;
import com.astro.freecam.replay.ReplayManager;
import com.astro.freecam.replay.ReplayMarker;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = "freecam", value = Dist.CLIENT)
public final class ReplayHudRenderer extends AbstractGui {
    private static final Minecraft MC = Minecraft.getInstance();
    private static boolean visible = true;

    private ReplayHudRenderer() {}

    public static void toggle() { visible = !visible; }

    @SubscribeEvent
    public static void render(RenderGameOverlayEvent.Post e) {
        if (!visible || !ReplayManager.isPlaying() || e.getType() != RenderGameOverlayEvent.ElementType.ALL || MC.currentScreen != null) return;

        MatrixStack m = e.getMatrixStack();
        int w = MC.getMainWindow().getScaledWidth();
        int h = MC.getMainWindow().getScaledHeight();
        int left = 12, right = w - 12, top = 8;
        int barTop = top + 29;

        fill(m, left, top, right, top + 61, 0xC80A0F14);
        button(m, left + 7, top + 6, left + 34, top + 24, ReplayManager.isPaused() ? "▶" : "Ⅱ", 0xE8E8E8);
        button(m, left + 39, top + 6, left + 95, top + 24, String.format(Locale.ROOT, "%.2fx", ReplayManager.getSpeed()), 0xD3DEE7);
        button(m, left + 100, top + 6, left + 202, top + 24, ReplayManager.viewName(), 0xA5D7F4);
        button(m, left + 207, top + 6, left + 239, top + 24, "KEY", 0x6FE08F);
        button(m, left + 244, top + 6, left + 279, top + 24, "M", 0xFFB36E);
        button(m, left + 284, top + 6, left + 322, top + 24, "IMG", 0xE4D88C);
        button(m, left + 327, top + 6, left + 356, top + 24, "HUD", 0xBFC7CF);
        drawString(m, MC.fontRenderer, "Replay Studio", right - 83, top + 11, 0x9BA7B0);

        fill(m, left + 8, barTop, right - 8, barTop + 12, 0xFF252C33);
        int total = Math.max(1, ReplayManager.getFrameCount() - 1);
        int posX = left + 9 + (int)((right - left - 18) * (ReplayManager.getFrameIndex() / (double)total));
        fill(m, left + 9, barTop + 1, Math.max(left + 9, posX), barTop + 11, 0xFF4D778F);
        fill(m, posX - 1, barTop - 3, posX + 2, barTop + 15, 0xFFFFDF4D);

        for (ReplayMarker marker : ReplayManager.markers()) {
            int x = left + 9 + (int)((right - left - 18) * (marker.getFrame() / (double)total));
            fill(m, x - 1, barTop - 7, x + 2, barTop + 1, 0xFFFF8A52);
        }
        for (ReplayKeyframe k : ReplayManager.keyframes()) {
            int x = left + 9 + (int)((right - left - 18) * (k.getFrame() / (double)total));
            fill(m, x - 1, barTop + 12, x + 2, barTop + 19, 0xFF55E089);
        }

        String now = time(ReplayManager.getFrameIndex() / 20D);
        String end = time(ReplayManager.getDurationSeconds());
        drawString(m, MC.fontRenderer, now, left + 8, top + 46, 0xA6B0B7);
        drawString(m, MC.fontRenderer, end, right - MC.fontRenderer.getStringWidth(end) - 8, top + 46, 0xA6B0B7);

        String controls = "P Pause • O POV • M Marker • K Keyframe • T Image • H HUD • ←/→ Seek • ↑/↓ Speed";
        drawCenteredString(m, MC.fontRenderer, new StringTextComponent(controls), w / 2, h - 14, 0xD2D8DC);
    }

    private static void button(MatrixStack m, int x1, int y1, int x2, int y2, String label, int color) {
        fill(m, x1, y1, x2, y2, 0xFF232A31);
        drawCenteredString(m, MC.fontRenderer, new StringTextComponent(label), (x1+x2)/2, y1+6, color);
    }

    @SubscribeEvent
    public static void mouse(InputEvent.MouseInputEvent e) {
        if (!visible || !ReplayManager.isPlaying() || MC.currentScreen != null || e.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || e.getAction() != GLFW.GLFW_PRESS) return;

        double[] xs = new double[1], ys = new double[1];
        GLFW.glfwGetCursorPos(MC.getMainWindow().getHandle(), xs, ys);
        int sw = MC.getMainWindow().getWidth(), sh = MC.getMainWindow().getHeight();
        int gw = MC.getMainWindow().getScaledWidth(), gh = MC.getMainWindow().getScaledHeight();
        int gx = (int)(xs[0] * gw / Math.max(1, sw));
        int gy = (int)(ys[0] * gh / Math.max(1, sh));
        int left = 12, right = gw - 12, top = 8;

        if (box(gx, gy, left + 7, top + 6, left + 34, top + 24)) ReplayManager.togglePause();
        else if (box(gx, gy, left + 39, top + 6, left + 95, top + 24)) ReplayManager.setSpeed(ReplayManager.getSpeed() + 0.25D);
        else if (box(gx, gy, left + 100, top + 6, left + 202, top + 24)) ReplayManager.cycleView();
        else if (box(gx, gy, left + 207, top + 6, left + 239, top + 24)) ReplayManager.addKeyframe();
        else if (box(gx, gy, left + 244, top + 6, left + 279, top + 24)) ReplayManager.addMarker();
        else if (box(gx, gy, left + 284, top + 6, left + 322, top + 24)) ReplayManager.captureThumbnail();
        else if (box(gx, gy, left + 327, top + 6, left + 356, top + 24)) toggle();
        else if (gy >= top + 20 && gy <= top + 52 && gx >= left + 8 && gx <= right - 8) {
            double pct = (gx - (left + 9)) / (double)Math.max(1, right - left - 18);
            ReplayManager.seekToPercent(Math.max(0D, Math.min(1D, pct)));
        }
    }

    private static boolean box(int x,int y,int x1,int y1,int x2,int y2){return x>=x1&&x<=x2&&y>=y1&&y<=y2;}
    private static String time(double seconds){int s=Math.max(0,(int)seconds);return String.format(Locale.ROOT,"%02d:%02d",s/60,s%60);}
}
