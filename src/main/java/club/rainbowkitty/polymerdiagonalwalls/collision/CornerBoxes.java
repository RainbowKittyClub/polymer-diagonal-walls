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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalFamily;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalRule;

/**
 * The boxes that close a diagonal's gap for non-players: the part of each of the family's
 * {@link ColumnLayout} column squares that lies in an end's own cell, as tall as that end's
 * collision. With the two posts they form a chain of boxes that touch only at corners, which no
 * box of any width passes, and they match what a client collides with.
 */
public final class CornerBoxes {
    // Each mask bit's step to the partner: south-east, north-east, south-west, north-west.
    private static final int[] STEP_X = {1, 1, -1, -1};
    private static final int[] STEP_Z = {1, -1, 1, -1};
    private static final int BITS = STEP_X.length;

    // Per family, vanilla shape to its union with each mask's boxes. VoxelShape compares by
    // identity, and each block hands out one shape per state.
    private static final Map<DiagonalFamily, Map<VoxelShape, AtomicReferenceArray<VoxelShape>>>
            SEALED = new ConcurrentHashMap<>();

    private CornerBoxes() {
    }

    /**
     * {@code vanilla} plus the boxes of every diagonal with an end at {@code pos}. Reads the
     * partners through {@code level} without loading a chunk; an unreadable partner adds nothing.
     */
    public static VoxelShape seal(
            VoxelShape vanilla, BlockState state, BlockGetter level, BlockPos pos) {
        DiagonalFamily family = DiagonalRule.familyOf(state);
        if (family == null) {
            return vanilla;
        }
        int mask = mask(family, state, level, pos);
        if (mask == 0) {
            return vanilla;
        }
        AtomicReferenceArray<VoxelShape> byMask = SEALED
                .computeIfAbsent(family, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(vanilla, shape -> new AtomicReferenceArray<>(1 << BITS));
        VoxelShape sealed = byMask.get(mask);
        if (sealed == null) {
            // Threads that race here compute equal shapes, so the last write is as good as any.
            sealed = Shapes.or(vanilla,
                    boxes(family.columns(), mask, vanilla.max(Direction.Axis.Y)));
            byMask.set(mask, sealed);
        }
        return sealed;
    }

    // Bit i set when pos joins its partner at STEP_X[i], STEP_Z[i], tested as DiagonalRule.all
    // tests it: the west end of each pair goes first.
    private static int mask(
            DiagonalFamily family, BlockState state, BlockGetter level, BlockPos pos) {
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

    // The column squares, cut to the cell, toward each partner in mask, from 0 to height.
    private static VoxelShape boxes(ColumnLayout layout, int mask, double height) {
        VoxelShape union = Shapes.empty();
        double half = layout.columnWidth() / 2;
        for (int bit = 0; bit < BITS; bit++) {
            if ((mask & 1 << bit) == 0) {
                continue;
            }
            // The diagonal runs (1, dz) west to east, so dz flips for an east end.
            int dz = STEP_X[bit] > 0 ? STEP_Z[bit] : -STEP_Z[bit];
            double cornerX = STEP_X[bit] > 0 ? 1 : 0;
            double cornerZ = STEP_Z[bit] > 0 ? 1 : 0;
            for (double offset : layout.columnOffsets()) {
                double centreX = cornerX + offset;
                double centreZ = cornerZ + dz * offset;
                double minX = Math.max(0, centreX - half);
                double maxX = Math.min(1, centreX + half);
                double minZ = Math.max(0, centreZ - half);
                double maxZ = Math.min(1, centreZ + half);
                if (minX < maxX && minZ < maxZ && height > 0) {
                    union = Shapes.or(union, Shapes.box(minX, 0, minZ, maxX, height, maxZ));
                }
            }
        }
        return union;
    }
}
