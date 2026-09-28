package com.astro.freecam.client;

import com.astro.freecam.FreecamMod;
import com.astro.freecam.config.FreecamConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapDoorBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.entity.player.RemoteClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderTickEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = FreecamMod.MOD_ID, value = Dist.CLIENT)
public final class FreecamClient {
    private static final Minecraft MC = Minecraft.getInstance();
    private static final TripodPose[] TRIPODS = new TripodPose[9];

    private static FreecamCamera camera;
    private static boolean enabled, playerControl, tripodActive;
    private static int activeTripod = -1;
    private static int oldPerspective = 0;
    private static float lockedYaw, lockedPitch, lockedHeadYaw, lockedBodyYaw;
    private static Vector3d velocity = Vector3d.ZERO;
    private static Object dimension;
    private static int lockedHotbar = -1;
    private static boolean toggleHeld, toggleCombo;
    private static int lastNumberMask;
    private static boolean resetCombo;

    private FreecamClient() {}

    public static boolean enabled() { return enabled; }
    public static FreecamCamera camera() { return camera; }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        handleKeys();

        if (!enabled) return;
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
        if (!playerControl) restoreRotation(p);
        moveCamera(w);
    }

    @SubscribeEvent
    public static void renderTick(RenderTickEvent e) {
        if (!enabled || camera == null || MC.player == null || playerControl) return;
        ClientPlayerEntity p = MC.player;
        float yawDelta = MathHelper.wrapDegrees(p.rotationYaw - lockedYaw);
        float pitchDelta = p.rotationPitch - lockedPitch;
        if (Math.abs(yawDelta) > 0.001F || Math.abs(pitchDelta) > 0.001F) {
            camera.rotationYaw = MathHelper.wrapDegrees(camera.rotationYaw + yawDelta);
            camera.rotationPitch = MathHelper.clamp(camera.rotationPitch + pitchDelta, -90F, 90F);
            camera.prevRotationYaw = camera.rotationYaw;
            camera.prevRotationPitch = camera.rotationPitch;
            restoreRotation(p);
        }
    }

    @SubscribeEvent
    public static void inputUpdate(InputUpdateEvent e) {
        if (enabled && !playerControl && e.getMovementInput() != null) {
            e.getMovementInput().moveForward = 0F;
            e.getMovementInput().moveStrafe = 0F;
            e.getMovementInput().jump = false;
            e.getMovementInput().sneaking = false;
        }
    }

    @SubscribeEvent
    public static void click(InputEvent.ClickInputEvent e) {
        if (!enabled) return;
        if (!FreecamConfig.ALLOW_INTERACTION.get()) {
            e.setCanceled(true);
            return;
        }
        if (FreecamConfig.INTERACTION_MODE.get() == FreecamConfig.InteractionMode.PLAYER && MC.player != null && MC.gameRenderer != null) {
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
        if (enabled && !FreecamConfig.SHOW_PLAYER.get() && e.getPlayer() == MC.player) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void cameraSetup(EntityViewRenderEvent.CameraSetup e) {
        if (enabled && camera != null) {
            e.setYaw(camera.rotationYaw);
            e.setPitch(camera.rotationPitch);
        }
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload e) {
        if (MC.world == e.getWorld()) disable(true);
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (MC.player != null && e.getEntity() == MC.player) disable(true);
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggedOutEvent e) {
        disable(true);
    }

    private static void handleKeys() {
        if (MC.player == null || MC.world == null || FreecamKeybinds.TOGGLE == null) {
            toggleHeld = false;
            toggleCombo = false;
            resetCombo = false;
            lockedHotbar = -1;
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

        if ((down || resetDown) && lockedHotbar >= 0) MC.player.inventory.currentItem = lockedHotbar;

        if (FreecamKeybinds.PLAYER_CONTROL != null && FreecamKeybinds.PLAYER_CONTROL.isPressed()) {
            playerControl = !playerControl;
            if (!playerControl && MC.player != null) restoreRotation(MC.player);
            notify(playerControl ? "Player control enabled" : "Player control disabled", TextFormatting.AQUA);
        }

        if (!down && !resetDown) {
            if (resetCombo) resetCombo = false;
        }
    }

    private static void toggle() {
        if (enabled) disable(false); else enable(false, -1);
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
            camera.setPositionAndRotation(p.getPosX(), p.getPosYEye(), p.getPosZ(), p.rotationYaw, p.rotationPitch);
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
            } else notify("Freecam enabled", TextFormatting.AQUA);
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
            if (camera != null) try { camera.remove(); } catch (Throwable ignored) {}
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
        if (!enabled) enable(true, slot); else {
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
    }

    private static void savePose(int slot) {
        if (camera == null || slot < 0 || slot > 8) return;
        TRIPODS[slot] = new TripodPose(camera.getPosX(), camera.getPosY(), camera.getPosZ(), camera.rotationYaw, camera.rotationPitch);
    }

    private static void applyPose(TripodPose pose) {
        if (camera == null || pose == null) return;
        camera.setPositionAndRotation(pose.x, pose.y, pose.z, pose.yaw, pose.pitch);
        camera.prevPosX = pose.x; camera.prevPosY = pose.y; camera.prevPosZ = pose.z;
        camera.prevRotationYaw = pose.yaw; camera.prevRotationPitch = pose.pitch;
    }

    private static int currentNumberMask() {
        int mask = 0;
        for (int i = 0; i < 9; i++) if (numberDown(i)) mask |= 1 << i;
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
                case THIRD_PERSON: MC.gameSettings.thirdPersonView = 1; break;
                case THIRD_PERSON_MIRROR: MC.gameSettings.thirdPersonView = 2; break;
                default: MC.gameSettings.thirdPersonView = 0; break;
            }
        } catch (Throwable ignored) {
            MC.gameSettings.thirdPersonView = 0;
        }
    }

    private static void restoreRotation(ClientPlayerEntity p) {
        p.rotationYaw = lockedYaw; p.prevRotationYaw = lockedYaw;
        p.rotationPitch = lockedPitch; p.prevRotationPitch = lockedPitch;
        p.rotationYawHead = lockedHeadYaw; p.prevRotationYawHead = lockedHeadYaw;
        p.renderYawOffset = lockedBodyYaw; p.prevRenderYawOffset = lockedBodyYaw;
    }

    private static void moveCamera(ClientWorld w) {
        if (camera == null) return;
        camera.prevPosX = camera.getPosX(); camera.prevPosY = camera.getPosY(); camera.prevPosZ = camera.getPosZ();
        camera.prevRotationYaw = camera.rotationYaw; camera.prevRotationPitch = camera.rotationPitch;

        double x = 0, y = 0, z = 0;
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
            double sin = Math.sin(r), cos = Math.cos(r);
            input = new Vector3d(input.x * cos - input.z * sin, input.y, input.z * cos + input.x * sin);
        } else input = Vector3d.ZERO;

        double hs = safe(FreecamConfig.HORIZONTAL_SPEED.get(), 1);
        double vs = safe(FreecamConfig.VERTICAL_SPEED.get(), 1);
        if (FreecamConfig.FLIGHT_MODE.get() == FreecamConfig.FlightMode.CREATIVE) {
            velocity = velocity.scale(0.90).add(input.mul(hs * .25, vs * .25, hs * .25));
        } else {
            velocity = new Vector3d(input.x * hs, input.y * vs, input.z * hs);
        }

        Vector3d from = camera.getPositionVec(), to = from.add(velocity);
        if (!FreecamConfig.IGNORE_ALL_COLLISION.get()) to = collide(w, from, to);
        camera.setPosition(to.x, to.y, to.z);
        camera.motionX = camera.motionY = camera.motionZ = 0;
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
        int n = Math.max(1, (int)Math.ceil(d.length() / .20));
        Vector3d cur = a;
        for (int i=1;i<=n;i++) {
            Vector3d next = a.add(d.scale((double)i/n));
            if (blocked(w, next)) return cur;
            cur = next;
        }
        return cur;
    }

    private static boolean blocked(ClientWorld w, Vector3d c) {
        double r=.16;
        AxisAlignedBB box = new AxisAlignedBB(c.x-r,c.y-r,c.z-r,c.x+r,c.y+r,c.z+r);
        BlockPos min = new BlockPos(Math.floor(c.x-r),Math.floor(c.y-r),Math.floor(c.z-r));
        BlockPos max = new BlockPos(Math.floor(c.x+r),Math.floor(c.y+r),Math.floor(c.z+r));
        for(int x=min.getX();x<=max.getX();x++) for(int y=min.getY();y<=max.getY();y++) for(int z=min.getZ();z<=max.getZ();z++) {
            BlockPos pos=new BlockPos(x,y,z); BlockState s=w.getBlockState(pos);
            if(!collides(s)) continue;
            try {
                for(AxisAlignedBB shape:s.getCollisionShape(w,pos).toBoundingBoxList())
                    if(box.intersects(shape.offset(x,y,z))) return true;
            } catch(Throwable ignored) {}
        }
        return false;
    }

    private static boolean collides(BlockState s) {
        if(s == null || s.isAir()) return false;
        Block b=s.getBlock();
        if(FreecamConfig.IGNORE_OPENABLE.get() && (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock)) return false;
        return !FreecamConfig.IGNORE_TRANSPARENT.get() || s.getMaterial().isOpaque();
    }

    private static boolean down(KeyBinding k) { return k != null && k.isKeyDown(); }

    private static double safe(double v,double d) { return Double.isFinite(v) && v > 0 ? v : d; }

    private static void notify(String s, TextFormatting c) {
        try {
            if(FreecamConfig.NOTIFICATIONS.get() && MC.player != null)
                MC.player.sendStatusMessage(new StringTextComponent(c + "Freecam: " + s), true);
        } catch(Throwable ignored) {}
    }

    public static void clearTripods() { Arrays.fill(TRIPODS,null); }
}
