package com.astro.freecam.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class FreecamConfig {
    public enum FlightMode { DEFAULT, CREATIVE }
    public enum Perspective { FIRST_PERSON, THIRD_PERSON, THIRD_PERSON_MIRROR, INSIDE }
    public enum InteractionMode { CAMERA, PLAYER }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<FlightMode> FLIGHT_MODE;
    public static final ForgeConfigSpec.DoubleValue HORIZONTAL_SPEED;
    public static final ForgeConfigSpec.DoubleValue VERTICAL_SPEED;
    public static final ForgeConfigSpec.BooleanValue IGNORE_TRANSPARENT;
    public static final ForgeConfigSpec.BooleanValue IGNORE_OPENABLE;
    public static final ForgeConfigSpec.BooleanValue IGNORE_ALL_COLLISION;
    public static final ForgeConfigSpec.EnumValue<Perspective> INITIAL_PERSPECTIVE;
    public static final ForgeConfigSpec.BooleanValue SHOW_PLAYER;
    public static final ForgeConfigSpec.BooleanValue SHOW_HAND;
    public static final ForgeConfigSpec.EnumValue<InteractionMode> INTERACTION_MODE;
    public static final ForgeConfigSpec.BooleanValue ALLOW_INTERACTION;
    public static final ForgeConfigSpec.BooleanValue NOTIFICATIONS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("movement");
        FLIGHT_MODE = b.defineEnum("flightMode", FlightMode.DEFAULT);
        HORIZONTAL_SPEED = b.defineInRange("horizontalSpeed", 1.0D, 0.01D, 100.0D);
        VERTICAL_SPEED = b.defineInRange("verticalSpeed", 1.0D, 0.01D, 100.0D);
        b.pop();
        b.push("collision");
        IGNORE_TRANSPARENT = b.define("ignoreTransparentBlocks", true);
        IGNORE_OPENABLE = b.define("ignoreOpenableBlocks", true);
        IGNORE_ALL_COLLISION = b.define("ignoreAllCollision", true);
        b.pop();
        b.push("visual");
        INITIAL_PERSPECTIVE = b.defineEnum("initialPerspective", Perspective.INSIDE);
        SHOW_PLAYER = b.define("showPlayer", true);
        SHOW_HAND = b.define("showHand", false);
        b.pop();
        b.push("interaction");
        INTERACTION_MODE = b.defineEnum("interactionMode", InteractionMode.CAMERA);
        ALLOW_INTERACTION = b.define("allowInteraction", false);
        b.pop();
        b.push("notifications");
        NOTIFICATIONS = b.define("notifications", true);
        b.pop();
        SPEC = b.build();
    }

    private FreecamConfig() {}
}
