package club.rainbowkitty.polymerdiagonalwalls.settings;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import club.rainbowkitty.kalic0re.common.config.ModConfig;
import club.rainbowkitty.kalic0re.common.config.Setting;
import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;

/**
 * This mod's settings. Each server setting lives in the rkcore-common settings hub, or in a game
 * rule of the same id where the hub is absent; {@link #SHOW_ARMS} is each player's own. Every one
 * is read where it is used, never cached.
 */
public final class DiagonalSettings {
    // Blocks a display's view range of 1 covers at 100% entity distance (Display.java:403).
    private static final float BLOCKS_PER_VIEW_RANGE = 64f;

    private static final ModConfig CONFIG = ModConfig.create(PolymerDiagonalWalls.MOD_ID);

    /** Whether diagonals exist at all: arms, columns and sealed gaps. */
    public static final Setting<Boolean> ENABLED = toggle("enabled", true);

    /** Whether non-players collide with diagonal gaps, and walking mobs route around them. */
    public static final Setting<Boolean> SEAL_GAPS = toggle("seal_gaps", true);

    /** Blocks within which a client draws an arm, at 100% entity distance. */
    public static final Setting<Integer> VIEW_RANGE = range("view_range", 128, 64, 512);

    /** Blocks within which a player gets a diagonal's collision columns. */
    public static final Setting<Integer> COLLISION_RANGE = range("collision_range", 8, 4, 64);

    /** Whether this player is sent arms; their collision stays either way. */
    public static final Setting<Boolean> SHOW_ARMS = CONFIG.toggle("show_arms", true).player();

    private DiagonalSettings() {
    }

    /**
     * Declares every setting and registers them with the hub or as game rules. Call once from the
     * initializer, before the game-rule registry freezes.
     */
    public static void register() {
        CONFIG.register();
    }

    /** Whether diagonals exist on {@code server}. */
    public static boolean enabled(MinecraftServer server) {
        return ENABLED.get(server);
    }

    /** Whether diagonal gaps are sealed on {@code server}; never while diagonals are off. */
    public static boolean sealsGaps(MinecraftServer server) {
        return ENABLED.get(server) && SEAL_GAPS.get(server);
    }

    /** {@link #VIEW_RANGE} as a display's view range. */
    public static float displayViewRange(MinecraftServer server) {
        return VIEW_RANGE.get(server) / BLOCKS_PER_VIEW_RANGE;
    }

    /** {@link #COLLISION_RANGE} in blocks. */
    public static double collisionRange(MinecraftServer server) {
        return COLLISION_RANGE.get(server);
    }

    /** Whether {@code player} is sent arms. */
    public static boolean showsArms(ServerPlayer player) {
        return SHOW_ARMS.get(player);
    }

    // Declares a true/false server setting, with a game rule of its id where the hub is absent.
    private static Setting<Boolean> toggle(String key, boolean defaultValue) {
        return CONFIG.toggle(key, defaultValue).server().fallbackRule();
    }

    // Declares a whole-number server setting in [min, max], with a game rule as toggle's has.
    private static Setting<Integer> range(String key, int defaultValue, int min, int max) {
        return CONFIG.range(key, defaultValue, min, max).server().fallbackRule();
    }
}
