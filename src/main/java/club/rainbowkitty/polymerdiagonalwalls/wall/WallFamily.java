package club.rainbowkitty.polymerdiagonalwalls.wall;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;

import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;
import club.rainbowkitty.polymerdiagonalwalls.collision.ColumnLayout;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalFamily;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalRule;
import club.rainbowkitty.polymerdiagonalwalls.display.ArmVisual;
import club.rainbowkitty.polymerdiagonalwalls.display.WallArmVisual;

/**
 * Walls as a diagonal family. Any two diagonal walls join, mixed materials included, because
 * vanilla joins every {@code #minecraft:walls} neighbour.
 */
public final class WallFamily implements DiagonalFamily {
    /**
     * The walls that take diagonals. It holds {@code #minecraft:walls}; a server opts a wall out
     * by removing it with a datapack.
     */
    public static final TagKey<Block> DIAGONAL_WALLS =
            TagKey.create(Registries.BLOCK, PolymerDiagonalWalls.id("diagonal_walls"));

    // Two 0.25 columns between the post corners at +-0.25, three open shulkers tall: the 1.5 of a
    // wall's collision. Each slime spans a post corner to the corner point.
    private static final ColumnLayout COLUMNS = new ColumnLayout(
            List.of(-0.125, 0.125), 0.25, 3, 1.5, List.of(-0.125, 0.125), 0.25);

    private final ArmVisual visual = new WallArmVisual();

    private WallFamily() {
    }

    /** Registers walls with {@link DiagonalRule}. */
    public static void register() {
        DiagonalRule.register(new WallFamily());
    }

    @Override
    public boolean admits(BlockState state) {
        return state.getBlock() instanceof WallBlock && state.is(DIAGONAL_WALLS);
    }

    @Override
    public boolean sideOpen(BlockState state, Direction side) {
        return state.getValue(WallBlock.PROPERTY_BY_DIRECTION.get(side)) == WallSide.NONE;
    }

    @Override
    public boolean joins(BlockState a, BlockState b) {
        return true;
    }

    @Override
    public boolean hasTallArms() {
        return true;
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
