package club.rainbowkitty.polymerdiagonalwalls.hedge;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;
import club.rainbowkitty.polymerdiagonalwalls.collision.ColumnLayout;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalFamily;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalRule;
import club.rainbowkitty.polymerdiagonalwalls.display.ArmVisual;
import club.rainbowkitty.polymerdiagonalwalls.display.CrossArmVisual;

/**
 * Hedges as a diagonal family: leafy {@link CrossCollisionBlock}s with a 6 px post and 6 px sides,
 * such as Blockus's. Any two diagonal hedges join, because a Blockus hedge joins every
 * {@code #blockus:hedges} neighbour.
 */
public final class HedgeFamily implements DiagonalFamily {
    /**
     * The hedges that take diagonals. It holds {@code #blockus:hedges} when present; a server opts
     * a hedge out by removing it with a datapack.
     */
    public static final TagKey<Block> DIAGONAL_HEDGES =
            TagKey.create(Registries.BLOCK, PolymerDiagonalWalls.id("diagonal_hedges"));

    // Two 0.3125 columns between the post corners at +-0.3125, three open shulkers tall: the 1.5 of
    // a hedge's collision. Each frog sits on a column, 1/16 wider on every side, as for walls.
    private static final ColumnLayout COLUMNS = new ColumnLayout(
            List.of(-0.15625, 0.15625), 0.3125, 3, 1.5, List.of(-0.15625, 0.15625), 0.4375);

    private final ArmVisual visual = CrossArmVisual.hedge();

    private HedgeFamily() {
    }

    /** Registers hedges with {@link DiagonalRule}. */
    public static void register() {
        DiagonalRule.register(new HedgeFamily());
    }

    @Override
    public boolean admits(BlockState state) {
        return state.getBlock() instanceof CrossCollisionBlock && state.is(DIAGONAL_HEDGES);
    }

    @Override
    public boolean sideOpen(BlockState state, Direction side) {
        return !state.getValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(side));
    }

    @Override
    public boolean joins(BlockState a, BlockState b) {
        return true;
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
