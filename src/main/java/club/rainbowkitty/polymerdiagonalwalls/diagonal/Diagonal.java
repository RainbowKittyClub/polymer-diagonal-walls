package club.rainbowkitty.polymerdiagonalwalls.diagonal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One diagonal between two blocks of a family standing corner to corner, named by its west end.
 * The west end owns the diagonal, so every diagonal has exactly one owner.
 *
 * @param family the family both ends belong to
 * @param west the west end and owner
 * @param dz the east end's z step from {@code west}: +1 for south-east, -1 for north-east
 * @param westState the state at {@code west}
 * @param eastState the state at {@link #east()}
 * @param tall whether the arm stands full height, as a wall side does under a covering block;
 *        always false for a family without tall arms
 */
public record Diagonal(DiagonalFamily family, BlockPos west, int dz, BlockState westState,
        BlockState eastState, boolean tall) {
    public Diagonal {
        if (dz != 1 && dz != -1) {
            throw new IllegalArgumentException("Diagonal.dz must be +1 or -1, got " + dz);
        }
        west = west.immutable();
    }

    /** The east end: one block east of {@link #west()} and {@link #dz()} along z. */
    public BlockPos east() {
        return west.offset(1, 0, dz);
    }

    /** Whether this diagonal runs south-east from its owner rather than north-east. */
    public boolean southEast() {
        return dz > 0;
    }

    /** Whether {@code pos} is one of this diagonal's two ends. */
    public boolean hasEnd(BlockPos pos) {
        return west.equals(pos) || east().equals(pos);
    }
}
