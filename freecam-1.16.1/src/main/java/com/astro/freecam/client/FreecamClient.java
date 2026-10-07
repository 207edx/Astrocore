package com.astro.freecam.client;

import com.astro.freecam.FreecamMod;
import com.astro.freecam.client.gui.ReplayManagerScreen;
import com.astro.freecam.config.FreecamConfig;
import com.astro.freecam.replay.ReplayManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapDoorBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.entity.player.RemoteClientPlayerEntity;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;

@Mod.EventBusSubscriber(modid = FreecamMod.MOD_ID, value = Dist.CLIENT)
public final class FreecamClient {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final TripodPose[] TRIPODS = new TripodPose[9];

    private static FreecamCamera camera;
    private static boolean enabled;
    private static boolean playerControl;
    private static boolean tripodActive;
    private static int activeTripod = -1;
    private static int oldPerspective;
    private static float lockedYaw, lockedPitch, lockedHeadYaw, lockedBodyYaw;
    private static Vector3d velocity = Vector3d.ZERO;
    private static Object dimension;
    private static int lockedHotbar = -1;
    private static boolean toggleHeld, toggleCombo, resetCombo;
    private static int lastNumberMask;
    private static boolean replayLeftHeld, replayRightHeld, replayUpHeld, replayDownHeld;

    private FreecamClient() {}

    public static boolean enabled() { return enabled; }
    public static FreecamCamera camera() { return camera; }

    public static void stopForReplay() {
        if (enabled) disable(true);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        handleKeys();
        ReplayManager.tickPlayback();

        if (ReplayManager.isPlaying()) return;

        if (!enabled) {
            ReplayManager.captureTick();
            return;
        }

        ClientPlayerEntity p = MC.player;
        ClientWorld w = MC.world;
        if (p == null || w == null || camera == null || MC.getRenderViewEntity() != camera || !p.isAlive()) {
            disable(true);
            return;
        }

        Object d = w.getDimensionKey();
        if (dimension != null && d != null && !dimension.equals(d)) {
            disable(true);
            return;
        }
        dimension = d;

        if (lockedHotbar >= 0) p.inventory.currentItem = MathHelper.clamp(lockedHotbar, 0, 8);

        if (!playerControl) {
            captureMouseRotation(p);
            restoreRotation(p);
        }

        moveCamera(w);
        ReplayManager.captureTick();
    }

    private static void captureMouseRotation(ClientPlayerEntity p) {
        if (playerControl || camera == null) return;
        float yawDelta = MathHelper.wrapDegrees(p.rotationYaw - lockedYaw);
        float pitchDelta = p.rotationPitch - lockedPitch;

        if (Math.abs(yawDelta) > 0.001F || Math.abs(pitchDelta) > 0.001F) {
            camera.rotationYaw = MathHelper.wrapDegrees(camera.rotationYaw + yawDelta);
            camera.rotationPitch = MathHelper.clamp(camera.rotationPitch + pitchDelta, -90F, 90F);
            camera.prevRotationYaw = camera.rotationYaw;
            camera.prevRotationPitch = camera.rotationPitch;
        }
    }

    @SubscribeEvent
    public static void inputUpdate(InputUpdateEvent e) {
        if (ReplayManager.isPlaying() && e.getMovementInput() != null) {
            e.getMovementInput().moveForward = 0F;
            e.getMovementInput().moveStrafe = 0F;
            e.getMovementInput().jump = false;
            e.getMovementInput().sneaking = false;
            return;
        }

        if (enabled && !playerControl && e.getMovementInput() != null) {
            e.getMovementInput().moveForward = 0F;
            e.getMovementInput().moveStrafe = 0F;
            e.getMovementInput().jump = false;
            e.getMovementInput().sneaking = false;
        }
    }

    @SubscribeEvent
    public static void click(InputEvent.ClickInputEvent e) {
        if (ReplayManager.isPlaying()) {
            e.setCanceled(true);
            return;
        }
        if (!enabled) return;

        if (!FreecamConfig.ALLOW_INTERACTION.get()) {
            e.setCanceled(true);
            return;
        }

        if (FreecamConfig.INTERACTION_MODE.get() == FreecamConfig.InteractionMode.PLAYER
                && MC.player != null && MC.gameRenderer != null) {
            Entity old = MC.getRenderViewEntity();
            try {
                MC.setRenderViewEntity(MC.player);
                MC.gameRenderer.getMouseOver(1F);
            } catch (Throwable ignored) {
            } finally {
                if (old != null) MC.setRenderViewEntity(old);
            }
        }
    }

    @SubscribeEvent
    public static void renderHand(RenderHandEvent e) {
        if (enabled && !FreecamConfig.SHOW_HAND.get()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void renderPlayer(RenderPlayerEvent.Pre e) {
        if (ReplayManager.isPlaying() && e.getPlayer() == MC.player) {
            e.setCanceled(true);
            return;
        }
        if (enabled && !FreecamConfig.SHOW_PLAYER.get() && e.getPlayer() == MC.player) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void cameraSetup(EntityViewRenderEvent.CameraSetup e) {
        if (ReplayManager.isPlaying()) {
            Entity view = MC.getRenderViewEntity();
            if (view != null) {
                e.setYaw(view.rotationYaw);
                e.setPitch(view.rotationPitch);
            }
            return;
        }

        if (enabled && camera != null) {
            e.setYaw(camera.rotationYaw);
            e.setPitch(camera.rotationPitch);
        }
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload e) {
        if (MC.world == e.getWorld()) {
            disable(true);
            ReplayManager.lifecycleReset();
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (MC.player != null && e.getEntity() == MC.player) disable(true);
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggedOutEvent e) {
        disable(true);
        ReplayManager.lifecycleReset();
    }

    private static void handleKeys() {
        if (MC.player == null || MC.world == null || FreecamKeybinds.TOGGLE == null) {
            toggleHeld = false;
            toggleCombo = false;
            resetCombo = false;
            lockedHotbar = -1;
            replayLeftHeld = replayRightHeld = replayUpHeld = replayDownHeld = false;
            return;
        }

        if (FreecamKeybinds.REPLAY_RECORD != null && FreecamKeybinds.REPLAY_RECORD.isPressed()) {
            if (ReplayManager.isRecording()) ReplayManager.stopRecording();
            else ReplayManager.startRecording("");
        }

        if (FreecamKeybinds.REPLAY_MANAGER != null && FreecamKeybinds.REPLAY_MANAGER.isPressed()) {
            if (ReplayManager.isPlaying()) ReplayManager.stopPlayback();
            else if (MC.currentScreen == null) MC.displayGuiScreen(new ReplayManagerScreen());
        }

        if (FreecamKeybinds.REPLAY_PLAY_PAUSE != null && FreecamKeybinds.REPLAY_PLAY_PAUSE.isPressed()) {
            ReplayManager.togglePause();
        }

        if (FreecamKeybinds.REPLAY_POV != null && FreecamKeybinds.REPLAY_POV.isPressed()) {
            ReplayManager.toggleView();
        }

        if (ReplayManager.isPlaying()) {
            handleReplayArrowKeys();
            return;
        }

        boolean down = FreecamKeybinds.TOGGLE.isKeyDown();
        if (down && !toggleHeld) {
            toggleHeld = true;
            toggleCombo = false;
            lockedHotbar = MC.player.inventory.currentItem;
        } else if (!down && toggleHeld) {
            toggleHeld = false;
            if (!toggleCombo) toggle();
            toggleCombo = false;
            lockedHotbar = -1;
        }

        boolean resetDown = FreecamKeybinds.RESET_TRIPOD != null && FreecamKeybinds.RESET_TRIPOD.isKeyDown();

        for (int i = 0; i < 9; i++) {
            if (!numberDown(i)) continue;
            int bit = 1 << i;
            if ((lastNumberMask & bit) != 0) continue;

            if (down) {
                toggleCombo = true;
                if (lockedHotbar < 0) lockedHotbar = MC.player.inventory.currentItem;
                enterTripod(i);
            }

            if (resetDown) {
                resetCombo = true;
                if (lockedHotbar < 0) lockedHotbar = MC.player.inventory.currentItem;
                TRIPODS[i] = null;

                if (enabled && tripodActive && activeTripod == i) {
                    tripodActive = false;
                    activeTripod = -1;
                }
                notify("Tripod " + (i + 1) + " reset", TextFormatting.LIGHT_PURPLE);
            }
        }

        lastNumberMask = currentNumberMask();

        if ((down || resetDown) && lockedHotbar >= 0) {
            MC.player.inventory.currentItem = lockedHotbar;
        }

        if (FreecamKeybinds.PLAYER_CONTROL != null && FreecamKeybinds.PLAYER_CONTROL.isPressed()) {
            playerControl = !playerControl;
            if (!playerControl && MC.player != null) restoreRotation(MC.player);
            notify(playerControl ? "Player control enabled" : "Player control disabled", TextFormatting.AQUA);
        }

        if (!down && !resetDown && resetCombo) resetCombo = false;
    }

    private static void handleReplayArrowKeys() {
        try {
            long handle = MC.getMainWindow().getHandle();
            boolean left = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS;
            boolean right = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS;
            boolean up = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS;
            boolean down = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS;

            if (left && !replayLeftHeld) ReplayManager.seek(-20);
            if (right && !replayRightHeld) ReplayManager.seek(20);
            if (up && !replayUpHeld) ReplayManager.setSpeed(ReplayManager.getSpeed() + 0.25D);
            if (down && !replayDownHeld) ReplayManager.setSpeed(ReplayManager.getSpeed() - 0.25D);

            replayLeftHeld = left;
            replayRightHeld = right;
            replayUpHeld = up;
            replayDownHeld = down;
        } catch (Throwable ignored) {
        }
    }

    private static void toggle() {
        if (enabled) disable(false);
        else enable(false, -1);
    }

    private static void enable(boolean tripod, int slot) {
        ClientPlayerEntity p = MC.player;
        ClientWorld w = MC.world;
        if (p == null || w == null) return;

        try {
            oldPerspective = pPerspective();
            lockedYaw = p.rotationYaw;
            lockedPitch = p.rotationPitch;
            lockedHeadYaw = p.rotationYawHead;
            lockedBodyYaw = p.renderYawOffset;
            dimension = w.getDimensionKey();
            velocity = Vector3d.ZERO;
            playerControl = false;
            tripodActive = tripod;
            activeTripod = tripod ? slot : -1;
            lockedHotbar = -1;

            camera = new FreecamCamera(w, p.getGameProfile());
            camera.setPositionAndRotation(
                    p.getPosX(), p.getPosYEye(), p.getPosZ(),
                    p.rotationYaw, p.rotationPitch
            );
            camera.prevPosX = camera.getPosX();
            camera.prevPosY = camera.getPosY();
            camera.prevPosZ = camera.getPosZ();
            camera.prevRotationYaw = camera.rotationYaw;
            camera.prevRotationPitch = camera.rotationPitch;

            MC.setRenderViewEntity(camera);
            applyInitialPerspective();
            enabled = true;

            if (tripod && TRIPODS[slot] != null) applyPose(TRIPODS[slot]);

            if (tripod) {
                if (TRIPODS[slot] == null) savePose(slot);
                notify("Tripod " + (slot + 1) + " active", TextFormatting.LIGHT_PURPLE);
            } else {
                notify("Freecam enabled", TextFormatting.AQUA);
            }
        } catch (Throwable ignored) {
            disable(true);
        }
    }

    private static void disable(boolean lifecycle) {
        ClientPlayerEntity p = MC.player;
        try {
            if (p != null) {
                if (tripodActive && activeTripod >= 0 && camera != null) savePose(activeTripod);
                restoreRotation(p);
            }

            if (MC.player != null) {
                MC.setRenderViewEntity(MC.player);
                MC.gameSettings.thirdPersonView = oldPerspective;
            }

            if (!lifecycle) notify("Freecam disabled", TextFormatting.AQUA);
        } catch (Throwable ignored) {
        } finally {
            if (camera != null) {
                try { camera.remove(); } catch (Throwable ignored) {}
            }
            camera = null;
            enabled = false;
            playerControl = false;
            tripodActive = false;
            activeTripod = -1;
            velocity = Vector3d.ZERO;
            lockedHotbar = -1;
            dimension = null;
        }
    }

    private static void enterTripod(int slot) {
        if (slot < 0 || slot > 8) return;

        if (!enabled) {
            enable(true, slot);
            return;
        }

        if (tripodActive && activeTripod >= 0 && activeTripod != slot) savePose(activeTripod);
        tripodActive = true;
        activeTripod = slot;

        if (TRIPODS[slot] == null) {
            savePose(slot);
            notify("Tripod " + (slot + 1) + " saved", TextFormatting.LIGHT_PURPLE);
        } else {
            applyPose(TRIPODS[slot]);
            notify("Tripod " + (slot + 1) + " loaded", TextFormatting.LIGHT_PURPLE);
        }
    }

    private static void savePose(int slot) {
        if (camera == null || slot < 0 || slot > 8) return;
        TRIPODS[slot] = new TripodPose(
                camera.getPosX(), camera.getPosY(), camera.getPosZ(),
                camera.rotationYaw, camera.rotationPitch
        );
    }

    private static void applyPose(TripodPose pose) {
        if (camera == null || pose == null) return;
        camera.setPositionAndRotation(pose.x, pose.y, pose.z, pose.yaw, pose.pitch);
        camera.prevPosX = pose.x;
        camera.prevPosY = pose.y;
        camera.prevPosZ = pose.z;
        camera.prevRotationYaw = pose.yaw;
        camera.prevRotationPitch = pose.pitch;
    }

    private static int currentNumberMask() {
        int mask = 0;
        for (int i = 0; i < 9; i++) {
            if (numberDown(i)) mask |= 1 << i;
        }
        return mask;
    }

    private static boolean numberDown(int slot) {
        try {
            long handle = MC.getMainWindow().getHandle();
            return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_1 + slot) == GLFW.GLFW_PRESS;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int pPerspective() {
        int v = MC.gameSettings.thirdPersonView;
        return v == 1 || v == 2 ? v : 0;
    }

    private static void applyInitialPerspective() {
        try {
            switch (FreecamConfig.INITIAL_PERSPECTIVE.get()) {
                case THIRD_PERSON:
                    MC.gameSettings.thirdPersonView = 1;
                    break;
                case THIRD_PERSON_MIRROR:
                    MC.gameSettings.thirdPersonView = 2;
                    break;
                default:
                    MC.gameSettings.thirdPersonView = 0;
                    break;
            }
        } catch (Throwable ignored) {
            MC.gameSettings.thirdPersonView = 0;
        }
    }

    private static void restoreRotation(ClientPlayerEntity p) {
        p.rotationYaw = lockedYaw;
        p.prevRotationYaw = lockedYaw;
        p.rotationPitch = lockedPitch;
        p.prevRotationPitch = lockedPitch;
        p.rotationYawHead = lockedHeadYaw;
        p.prevRotationYawHead = lockedHeadYaw;
        p.renderYawOffset = lockedBodyYaw;
        p.prevRenderYawOffset = lockedBodyYaw;
    }

    private static void moveCamera(ClientWorld w) {
        if (camera == null) return;

        camera.prevPosX = camera.getPosX();
        camera.prevPosY = camera.getPosY();
        camera.prevPosZ = camera.getPosZ();
        camera.prevRotationYaw = camera.rotationYaw;
        camera.prevRotationPitch = camera.rotationPitch;

        double x = 0;
        double y = 0;
        double z = 0;

        if (down(MC.gameSettings.keyBindLeft)) x--;
        if (down(MC.gameSettings.keyBindRight)) x++;
        if (down(MC.gameSettings.keyBindForward)) z++;
        if (down(MC.gameSettings.keyBindBack)) z--;
        if (down(MC.gameSettings.keyBindJump)) y++;
        if (down(MC.gameSettings.keyBindSneak)) y--;

        Vector3d input = new Vector3d(x, y, z);

        if (input.lengthSquared() > 0) {
            input = input.normalize();
            double r = Math.toRadians(camera.rotationYaw);
            double sin = Math.sin(r);
            double cos = Math.cos(r);
            input = new Vector3d(
                    input.x * cos - input.z * sin,
                    input.y,
                    input.z * cos + input.x * sin
            );
        } else {
            input = Vector3d.ZERO;
        }

        double hs = safe(FreecamConfig.HORIZONTAL_SPEED.get(), 1D);
        double vs = safe(FreecamConfig.VERTICAL_SPEED.get(), 1D);

        if (FreecamConfig.FLIGHT_MODE.get() == FreecamConfig.FlightMode.CREATIVE) {
            velocity = velocity.scale(0.90D)
                    .add(input.mul(hs * 0.25D, vs * 0.25D, hs * 0.25D));
        } else {
            velocity = new Vector3d(input.x * hs, input.y * vs, input.z * hs);
        }

        Vector3d from = camera.getPositionVec();
        Vector3d to = from.add(velocity);

        if (!FreecamConfig.IGNORE_ALL_COLLISION.get()) {
            to = collide(w, from, to);
        }

        camera.setPosition(to.x, to.y, to.z);
    }

    private static Vector3d collide(ClientWorld w, Vector3d from, Vector3d to) {
        Vector3d out = from;
        out = moveAxis(w, out, new Vector3d(to.x, out.y, out.z));
        out = moveAxis(w, out, new Vector3d(out.x, out.y, to.z));
        out = moveAxis(w, out, new Vector3d(out.x, to.y, out.z));
        return out;
    }

    private static Vector3d moveAxis(ClientWorld w, Vector3d a, Vector3d b) {
        Vector3d d = b.subtract(a);
        int n = Math.max(1, (int) Math.ceil(d.length() / 0.20D));
        Vector3d cur = a;

        for (int i = 1; i <= n; i++) {
            Vector3d next = a.add(d.scale((double) i / (double) n));
            if (blocked(w, next)) return cur;
            cur = next;
        }
        return cur;
    }

    private static boolean blocked(ClientWorld w, Vector3d c) {
        double r = 0.16D;
        AxisAlignedBB box = new AxisAlignedBB(
                c.x - r, c.y - r, c.z - r,
                c.x + r, c.y + r, c.z + r
        );

        BlockPos min = new BlockPos(Math.floor(c.x - r), Math.floor(c.y - r), Math.floor(c.z - r));
        BlockPos max = new BlockPos(Math.floor(c.x + r), Math.floor(c.y + r), Math.floor(c.z + r));

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = w.getBlockState(pos);
                    if (!collides(state)) continue;

                    try {
                        for (AxisAlignedBB shape : state.getCollisionShape(w, pos).toBoundingBoxList()) {
                            if (box.intersects(shape.offset(x, y, z))) return true;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return false;
    }

    private static boolean collides(BlockState state) {
        if (state == null || state.isAir()) return false;
        Block block = state.getBlock();

        if (FreecamConfig.IGNORE_OPENABLE.get()
                && (block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock)) {
            return false;
        }

        return !FreecamConfig.IGNORE_TRANSPARENT.get() || state.getMaterial().isOpaque();
    }

    private static boolean down(KeyBinding key) {
        return key != null && key.isKeyDown();
    }

    private static double safe(double value, double fallback) {
        return Double.isFinite(value) && value > 0D ? value : fallback;
    }

    private static void notify(String message, TextFormatting color) {
        try {
            if (FreecamConfig.NOTIFICATIONS.get() && MC.player != null) {
                MC.player.sendStatusMessage(
                        new StringTextComponent(color + "Freecam: " + message), true);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void clearTripods() {
        Arrays.fill(TRIPODS, null);
    }
}
