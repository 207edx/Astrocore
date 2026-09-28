package com.astro.freecam.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.entity.player.RemoteClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

public final class FreecamCamera extends RemoteClientPlayerEntity {
    public FreecamCamera(ClientWorld world, GameProfile profile) {
        super(world, profile);
        noClip = true;
        setNoGravity(true);
        setInvulnerable(true);
        setSilent(true);
    }

    @Override public void tick() {}
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean canBePushed() { return false; }
}
