# Freecam 1.16.1

Client-only free camera for Minecraft 1.16.1 / Forge 32.x.

Default controls:
- F4: Toggle Freecam (release to toggle)
- Hold F4 + 1-9: save/load tripod camera slots
- Hold Reset Tripod + 1-9: clear tripod (Reset Tripod is unbound by default)
- Player Control: unbound by default; toggles real-player movement/rotation while the camera remains detached

Config file:
config/freecam-client.toml

Options:
- flightMode: DEFAULT or CREATIVE
- horizontalSpeed / verticalSpeed
- ignoreTransparentBlocks
- ignoreOpenableBlocks
- ignoreAllCollision
- initialPerspective: FIRST_PERSON, THIRD_PERSON, THIRD_PERSON_MIRROR, INSIDE
- showPlayer
- showHand
- interactionMode: CAMERA or PLAYER
- allowInteraction
- notifications

Tripods store X/Y/Z, yaw and pitch for nine session-local slots.

The mod has no server component, packets, Mixins, or third-party configuration GUI. It safely disables on missing player/world state, world unload, disconnect, dimension change, and local-player death.

Build with Java 8 using:
.gradlew.bat clean build
