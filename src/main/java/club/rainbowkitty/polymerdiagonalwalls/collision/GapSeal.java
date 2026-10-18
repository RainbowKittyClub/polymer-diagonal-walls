package club.rainbowkitty.polymerdiagonalwalls.collision;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;

/**
 * Whether diagonal gaps are sealed, and for whom. Sealing adds {@link CornerBoxes} for every
 * entity but players, and {@link SealedPaths} keeps walking mobs from routing into the boxes.
 * Both switches are debug toggles until a server setting replaces them.
 */
public final class GapSeal {
    private static volatile boolean boxes = true;
    private static volatile boolean paths = true;

    private GapSeal() {
    }

    /** Whether non-players collide with corner boxes. */
    public static boolean boxes() {
        return boxes;
    }

    /** Whether walking mobs stop routing between diagonal posts; never while boxes are off. */
    public static boolean paths() {
        return boxes && paths;
    }

    /**
     * Sets both switches.
     *
     * @param sealBoxes whether non-players collide with corner boxes
     * @param sealPaths whether pathfinding avoids sealed gaps, when {@code sealBoxes} holds
     */
    public static void set(boolean sealBoxes, boolean sealPaths) {
        boxes = sealBoxes;
        paths = sealPaths;
    }

    /**
     * Whether corner boxes apply to the entity behind {@code context}: anything but a player or
     * a vehicle carrying one, since a player's client predicts its moves against vanilla shapes.
     */
    public static boolean appliesTo(CollisionContext context) {
        if (!boxes || !(context instanceof EntityCollisionContext entityContext)) {
            return false;
        }
        Entity entity = entityContext.getEntity();
        return entity != null && !(entity instanceof Player)
                && !(entity.isVehicle() && entity.countPlayerPassengers() > 0);
    }
}
