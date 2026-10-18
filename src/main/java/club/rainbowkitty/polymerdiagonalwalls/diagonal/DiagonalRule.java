package club.rainbowkitty.polymerdiagonalwalls.diagonal;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Which blocks join corner to corner. A joins B = A + (dx, 0, dz) when both belong to the same
 * family, the family joins them, and all four of their sides facing the two shared neighbours are
 * open. The rule reads only the two endpoint states: vanilla already folds every shared
 * neighbour that matters into those sides, so arms never cross.
 *
 * <p>Every method is side-effect-free and reads through a {@link BlockReader}, so an unloaded
 * partner means no diagonal and never a chunk load.
 */
public final class DiagonalRule {
    private static final int[] DZ_STEPS = {1, -1};
    // Centre-line strips from the cell centre to each corner, by the corner's x and z steps.
    private static final VoxelShape STRIP_SOUTH_EAST = cornerStrip(1, 1);
    private static final VoxelShape STRIP_NORTH_EAST = cornerStrip(1, -1);
    private static final VoxelShape STRIP_SOUTH_WEST = cornerStrip(-1, 1);
    private static final VoxelShape STRIP_NORTH_WEST = cornerStrip(-1, -1);

    private static volatile List<DiagonalFamily> families = List.of();

    private DiagonalRule() {
    }

    /** Adds a family; call from {@code onInitialize}, before any level loads. */
    public static synchronized void register(DiagonalFamily family) {
        List<DiagonalFamily> grown = new ArrayList<>(families);
        grown.add(family);
        families = List.copyOf(grown);
    }

    /** The first registered family that admits {@code state}, or {@code null}. */
    public static @Nullable DiagonalFamily familyOf(BlockState state) {
        for (DiagonalFamily family : families) {
            if (family.admits(state)) {
                return family;
            }
        }
        return null;
    }

    /** Whether some family admits {@code state}; the predicate for palette and section scans. */
    public static boolean isDiagonalBlock(BlockState state) {
        return familyOf(state) != null;
    }

    /**
     * Whether {@code a} joins {@code b} standing at {@code a + (dx, 0, dz)}. Only same-y pairs
     * are diagonals, like vanilla's cardinal joins.
     */
    public static boolean connects(BlockState a, BlockState b, int dx, int dz) {
        DiagonalFamily family = familyOf(a);
        if (family == null || !family.admits(b) || !family.joins(a, b)) {
            return false;
        }
        Direction alongX = dx > 0 ? Direction.EAST : Direction.WEST;
        Direction alongZ = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        return family.sideOpen(a, alongX) && family.sideOpen(a, alongZ)
                && family.sideOpen(b, alongX.getOpposite())
                && family.sideOpen(b, alongZ.getOpposite());
    }

    /** The diagonals {@code pos} owns in {@code level}'s loaded chunks: its (+1, ±1) ones. */
    public static List<Diagonal> owned(Level level, BlockPos pos) {
        return owned(BlockReader.loaded(level), pos);
    }

    /** The up to two diagonals {@code pos} owns, south-east first. */
    public static List<Diagonal> owned(BlockReader reader, BlockPos pos) {
        BlockState state = reader.stateAt(pos);
        DiagonalFamily family = state == null ? null : familyOf(state);
        if (family == null) {
            return List.of();
        }
        List<Diagonal> diagonals = new ArrayList<>(2);
        for (int dz : DZ_STEPS) {
            BlockState east = reader.stateAt(pos.offset(1, 0, dz));
            if (east != null && connects(state, east, 1, dz)) {
                diagonals.add(new Diagonal(
                        family, pos, dz, state, east, tall(reader, family, pos, dz)));
            }
        }
        return diagonals;
    }

    /** Every diagonal with an end at {@code pos} in {@code level}'s loaded chunks. */
    public static List<Diagonal> all(Level level, BlockPos pos) {
        return all(BlockReader.loaded(level), pos);
    }

    /**
     * The up to four diagonals with an end at {@code pos}: the ones it owns, then the ones owned by
     * its west-side partners.
     */
    public static List<Diagonal> all(BlockReader reader, BlockPos pos) {
        BlockState state = reader.stateAt(pos);
        if (state == null || familyOf(state) == null) {
            return List.of();
        }
        List<Diagonal> diagonals = new ArrayList<>(owned(reader, pos));
        for (int dz : DZ_STEPS) {
            BlockPos west = pos.offset(-1, 0, -dz);
            BlockState westState = reader.stateAt(west);
            if (westState != null && connects(westState, state, 1, dz)) {
                DiagonalFamily family = familyOf(westState);
                diagonals.add(new Diagonal(
                        family, west, dz, westState, state, tall(reader, family, west, dz)));
            }
        }
        return diagonals;
    }

    /**
     * Whether the diagonal {@code west} owns toward {@code dz} stands full height. That takes a
     * family with tall arms, and above both ends either the same family's blocks joined by the
     * stacked diagonal, or blocks whose down faces cover the arm's centre line, as vanilla
     * raises a wall side under a covering block ({@code WallBlock.makeWallState}).
     */
    public static boolean tall(BlockReader reader, DiagonalFamily family, BlockPos west, int dz) {
        if (!family.hasTallArms()) {
            return false;
        }
        BlockPos westAbove = west.above();
        BlockPos eastAbove = west.offset(1, 1, dz);
        BlockState westUpper = reader.stateAt(westAbove);
        BlockState eastUpper = reader.stateAt(eastAbove);
        if (westUpper == null || eastUpper == null) {
            return false;
        }
        if (family.admits(westUpper) && connects(westUpper, eastUpper, 1, dz)) {
            return true;
        }
        return covers(westUpper, westAbove, dz > 0 ? STRIP_SOUTH_EAST : STRIP_NORTH_EAST)
                && covers(eastUpper, eastAbove, dz > 0 ? STRIP_NORTH_WEST : STRIP_SOUTH_WEST);
    }

    // Whether upper's collision shape's down face holds all of strip, like WallBlock.isCovered.
    private static boolean covers(BlockState upper, BlockPos pos, VoxelShape strip) {
        if (upper.isAir()) {
            return false;
        }
        VoxelShape face = upper.getCollisionShape(EmptyBlockGetter.INSTANCE, pos)
                .getFaceShape(Direction.DOWN);
        return !face.isEmpty() && !Shapes.joinIsNotEmpty(strip, face, BooleanOp.ONLY_FIRST);
    }

    // 2 px squares stepped 1 px along the diagonal from the centre to the (stepX, stepZ) corner:
    // the diagonal counterpart of WallBlock's 2 px TEST_SHAPES_WALL.
    private static VoxelShape cornerStrip(int stepX, int stepZ) {
        VoxelShape strip = Shapes.empty();
        for (int step = 0; step < 8; step++) {
            double x = 8 + stepX * step;
            double z = 8 + stepZ * step;
            strip = Shapes.or(strip, Block.box(x - 1, 0, z - 1, x + 1, 16, z + 1));
        }
        return strip.optimize();
    }
}
