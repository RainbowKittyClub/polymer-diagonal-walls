package club.rainbowkitty.polymerdiagonalwalls.diagonal;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.polymerdiagonalwalls.collision.ColumnLayout;
import club.rainbowkitty.polymerdiagonalwalls.display.ArmVisual;

/**
 * One kind of block that takes diagonals, such as walls. {@link DiagonalRule} asks each
 * registered family whether it admits a state and reads the state's sides through it, so a family
 * joins only what vanilla would join cardinally.
 */
public interface DiagonalFamily {
    /** Whether {@code state} belongs to this family and has not been opted out by its tag. */
    public boolean admits(BlockState state);

    /** Whether {@code state}, admitted by this family, has no connection toward {@code side}. */
    public boolean sideOpen(BlockState state, Direction side);

    /** Whether two admitted states would join each other, as vanilla joins them cardinally. */
    public boolean joins(BlockState a, BlockState b);

    /**
     * Whether this family's arms stand full height when covered from above, as wall sides do;
     * {@link DiagonalRule#tall} answers false for every diagonal of a family without them.
     */
    public boolean hasTallArms();

    /** How this family's arms are drawn. */
    public ArmVisual visual();

    /** Where this family's client collision stands along each diagonal. */
    public ColumnLayout columns();
}
