package club.rainbowkitty.polymerdiagonalwalls.fence;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;
import club.rainbowkitty.polymerdiagonalwalls.collision.ColumnLayout;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalFamily;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalRule;
import club.rainbowkitty.polymerdiagonalwalls.display.ArmVisual;
import club.rainbowkitty.polymerdiagonalwalls.display.CrossArmVisual;

/**
 * Fences as a diagonal family. Two fences join as {@link FenceBlock#connectsTo} joins them
 * cardinally, so wooden fences join each other and nether brick joins only nether brick.
 */
public final class FenceFamily implements DiagonalFamily {
    /**
     * The fences that take diagonals. It holds {@code #minecraft:fences}; a server opts a fence
     * out by removing it with a datapack.
     */
    public static final TagKey<Block> DIAGONAL_FENCES =
            TagKey.create(Registries.BLOCK, PolymerDiagonalWalls.id("diagonal_fences"));

    // No client collision yet.
    private static final ColumnLayout COLUMNS =
            new ColumnLayout(List.of(), 0.25, 0, 0, List.of(), 0.25);

    private final ArmVisual visual = CrossArmVisual.fence();

    private FenceFamily() {
    }

    /** Registers fences with {@link DiagonalRule}. */
    public static void register() {
        DiagonalRule.register(new FenceFamily());
    }

    @Override
    public boolean admits(BlockState state) {
        return state.getBlock() instanceof FenceBlock && state.is(DIAGONAL_FENCES);
    }

    @Override
    public boolean sideOpen(BlockState state, Direction side) {
        return !state.getValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(side));
    }

    // Not solid-face joining: b is a fence, never a solid neighbour, and the side only matters
    // to fence gates, which this family does not admit.
    @Override
    public boolean joins(BlockState a, BlockState b) {
        return ((FenceBlock) a.getBlock()).connectsTo(b, false, Direction.NORTH);
    }

    @Override
    public boolean hasTallArms() {
        return false;
    }

    @Override
    public ArmVisual visual() {
        return visual;
    }

    @Override
    public ColumnLayout columns() {
        return COLUMNS;
    }
}
