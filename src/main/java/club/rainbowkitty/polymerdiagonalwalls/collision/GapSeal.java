package club.rainbowkitty.polymerdiagonalwalls.collision;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;

/**
 * Whether diagonal gaps are sealed, and for whom. Sealing adds {@link CornerBoxes} for every
 * entity but players, and {@link SealedPaths} keeps walking mobs from routing into the boxes. A
 * debug switch can turn the routing half off alone.
 */
public final class GapSeal {
    private static volatile boolean paths = true;

    private GapSeal() {
    }

    /** Whether walking mobs stop routing between diagonal posts. */
    public static boolean paths() {
        return paths;
    }

    /** Sets the debug switch: whether pathfinding avoids sealed gaps. */
    public static void setPaths(boolean sealPaths) {
        paths = sealPaths;
    }

    /**
     * Whether corner boxes apply to the entity behind {@code context}: anything on a server but a
     * player or a vehicle carrying one, since a player's client predicts its moves against vanilla
     * shapes.
     */
    public static boolean appliesTo(CollisionContext context) {
        if (!(context instanceof EntityCollisionContext entityContext)) {
            return false;
        }
        Entity entity = entityContext.getEntity();
        return entity != null && !(entity instanceof Player)
                && !(entity.isVehicle() && entity.countPlayerPassengers() > 0)
                && entity.level() instanceof ServerLevel;
    }
}
