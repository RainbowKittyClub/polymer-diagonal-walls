package club.rainbowkitty.polymerdiagonalwalls.pane;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;
import club.rainbowkitty.polymerdiagonalwalls.collision.ColumnLayout;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalFamily;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalRule;
import club.rainbowkitty.polymerdiagonalwalls.display.ArmVisual;
import club.rainbowkitty.polymerdiagonalwalls.display.CrossArmVisual;

/**
 * Glass panes and bars, every {@link IronBarsBlock}, as a diagonal family. Two join as
 * {@link IronBarsBlock#attachsTo} joins them cardinally, which is any two, glass to iron included.
 */
public final class PaneFamily implements DiagonalFamily {
    /**
     * The panes and bars that take diagonals. It holds {@code #minecraft:bars}, the vanilla glass
     * panes and, when present, {@code #c:glass_panes}; a server opts one out with a datapack.
     */
    public static final TagKey<Block> DIAGONAL_PANES =
            TagKey.create(Registries.BLOCK, PolymerDiagonalWalls.id("diagonal_panes"));

    // No client collision yet.
    private static final ColumnLayout COLUMNS =
            new ColumnLayout(List.of(), 0.25, 0, 0, List.of(), 0.25);

    private final ArmVisual visual = CrossArmVisual.pane();

    private PaneFamily() {
    }

    /** Registers panes and bars with {@link DiagonalRule}. */
    public static void register() {
        DiagonalRule.register(new PaneFamily());
    }

    @Override
    public boolean admits(BlockState state) {
        return state.getBlock() instanceof IronBarsBlock && state.is(DIAGONAL_PANES);
    }

    @Override
    public boolean sideOpen(BlockState state, Direction side) {
        return !state.getValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(side));
    }

    // Not solid-face joining: b is a pane or bars, never a solid neighbour.
    @Override
    public boolean joins(BlockState a, BlockState b) {
        return ((IronBarsBlock) a.getBlock()).attachsTo(b, false);
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
