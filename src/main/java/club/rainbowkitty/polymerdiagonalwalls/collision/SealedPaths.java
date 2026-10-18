package club.rainbowkitty.polymerdiagonalwalls.collision;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalRule;

/**
 * Keeps walking mobs from routing between two posts that a diagonal joins. Vanilla lets any mob
 * narrower than 0.5 step diagonally between two {@code FENCE} nodes, which {@link CornerBoxes}
 * would then hold it against.
 */
public final class SealedPaths {
    private SealedPaths() {
    }

    /**
     * Whether a diagonal step whose two side nodes are {@code first} and {@code second} crosses a
     * sealed gap on {@code server}. Reads states through {@code context}, which never loads a
     * chunk.
     */
    public static boolean crossesSealedGap(@Nullable MinecraftServer server,
            PathfindingContext context, Node first, Node second) {
        if (first.type != PathType.FENCE || second.type != PathType.FENCE || first.y != second.y
                || !GapSeal.paths(server)) {
            return false;
        }
        int dx = second.x - first.x;
        int dz = second.z - first.z;
        if (Math.abs(dx) != 1 || Math.abs(dz) != 1) {
            return false;
        }
        BlockState firstState = context.getBlockState(first.asBlockPos());
        BlockState secondState = context.getBlockState(second.asBlockPos());
        return DiagonalRule.connects(firstState, secondState, dx, dz);
    }
}
