package club.rainbowkitty.polymerdiagonalwalls.collision;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalFamily;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalRule;

/**
 * The boxes that close a wall diagonal's gap: a 4×4 px column as tall as the post in the corner
 * of each end's cell that faces the other end. With the two posts they form a chain of boxes that
 * touch only at corners, which no box of any width passes.
 */
public final class CornerBoxes {
    // Each mask bit's step to the partner: south-east, north-east, south-west, north-west.
    private static final int[] STEP_X = {1, 1, -1, -1};
    private static final int[] STEP_Z = {1, -1, 1, -1};
    private static final int BITS = STEP_X.length;
    // WallBlock's collision post height, in pixels.
    private static final double POST_TOP = 24;
    // The corner box's width: from the post's edge to the cell's.
    private static final double CORNER = 4;

    private static final VoxelShape[] BOXES_BY_MASK = boxesByMask();
    // Vanilla shape to its union with each mask's boxes. VoxelShape compares by identity, and
    // WallBlock hands out one shape per state.
    private static final Map<VoxelShape, AtomicReferenceArray<VoxelShape>> SEALED =
            new ConcurrentHashMap<>();

    private CornerBoxes() {
    }

    /**
     * {@code vanilla} plus the corner box of every diagonal with an end at {@code pos}. Reads the
     * partners through {@code level} without loading a chunk; an unreadable partner adds nothing.
     */
    public static VoxelShape seal(
            VoxelShape vanilla, BlockState state, BlockGetter level, BlockPos pos) {
        int mask = mask(state, level, pos);
        if (mask == 0) {
            return vanilla;
        }
        AtomicReferenceArray<VoxelShape> byMask =
                SEALED.computeIfAbsent(vanilla, shape -> new AtomicReferenceArray<>(1 << BITS));
        VoxelShape sealed = byMask.get(mask);
        if (sealed == null) {
            // Threads that race here compute equal shapes, so the last write is as good as any.
            sealed = Shapes.or(vanilla, BOXES_BY_MASK[mask]);
            byMask.set(mask, sealed);
        }
        return sealed;
    }

    // Bit i set when pos joins its partner at STEP_X[i], STEP_Z[i], tested as DiagonalRule.all
    // tests it: the west end of each pair goes first.
    private static int mask(BlockState state, BlockGetter level, BlockPos pos) {
        DiagonalFamily family = DiagonalRule.familyOf(state);
        if (family == null) {
            return 0;
        }
        BlockPos.MutableBlockPos partnerPos = new BlockPos.MutableBlockPos();
        int mask = 0;
        for (int bit = 0; bit < BITS; bit++) {
            int stepX = STEP_X[bit];
            int stepZ = STEP_Z[bit];
            // Skips the partner read for the sides vanilla already joined, which is most of them.
            if (!family.sideOpen(state, stepX > 0 ? Direction.EAST : Direction.WEST)
                    || !family.sideOpen(state, stepZ > 0 ? Direction.SOUTH : Direction.NORTH)) {
                continue;
            }
            BlockState partner = stateAt(level, partnerPos.setWithOffset(pos, stepX, 0, stepZ));
            if (partner == null) {
                continue;
            }
            boolean joined = stepX > 0
                    ? DiagonalRule.connects(state, partner, 1, stepZ)
                    : DiagonalRule.connects(partner, state, 1, -stepZ);
            if (joined) {
                mask |= 1 << bit;
            }
        }
        return mask;
    }

    // The state at pos, or null where reading it would load a chunk or wrap inside one.
    private static @Nullable BlockState stateAt(BlockGetter level, BlockPos pos) {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        if (level instanceof CollisionGetter collisions) {
            BlockGetter chunk = collisions.getChunkForCollisions(chunkX, chunkZ);
            return chunk == null ? null : chunk.getBlockState(pos);
        }
        if (level instanceof ChunkAccess chunk) {
            // A chunk reads any position by its local coordinates, so a neighbour's is wrong.
            boolean inside = chunk.getPos().x() == chunkX && chunk.getPos().z() == chunkZ;
            return inside ? chunk.getBlockState(pos) : null;
        }
        return level.getBlockState(pos);
    }

    // The union of corner boxes for each of the sixteen masks.
    private static VoxelShape[] boxesByMask() {
        VoxelShape[] corners = new VoxelShape[BITS];
        for (int bit = 0; bit < BITS; bit++) {
            double minX = STEP_X[bit] > 0 ? 16 - CORNER : 0;
            double minZ = STEP_Z[bit] > 0 ? 16 - CORNER : 0;
            corners[bit] = Block.box(minX, 0, minZ, minX + CORNER, POST_TOP, minZ + CORNER);
        }
        VoxelShape[] byMask = new VoxelShape[1 << BITS];
        for (int mask = 0; mask < byMask.length; mask++) {
            VoxelShape union = Shapes.empty();
            for (int bit = 0; bit < BITS; bit++) {
                if ((mask & 1 << bit) != 0) {
                    union = Shapes.or(union, corners[bit]);
                }
            }
            byMask[mask] = union;
        }
        return byMask;
    }
}
