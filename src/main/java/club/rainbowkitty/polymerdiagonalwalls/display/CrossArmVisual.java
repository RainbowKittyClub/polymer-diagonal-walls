package club.rainbowkitty.polymerdiagonalwalls.display;

import java.util.List;

import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;

import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;

/**
 * A fence or pane diagonal's arm: one block display of the owner's state joined north and south,
 * placed by {@link ArmTransform} at full height. A fence's model draws its post under every
 * state, so a fence arm shows one more post at the corner point.
 */
public final class CrossArmVisual implements ArmVisual {
    // A fence bar end stays inside a 4 px post within 0.114 of its centre: 1.186 at least.
    private static final float FENCE_LENGTH = 1.1875f;
    // A pane end stays inside a 2 px post within 0.026 of its centre: 1.362 at least.
    private static final float PANE_LENGTH = 1.375f;

    private final float length;

    // One visual per arm length.
    private CrossArmVisual(float length) {
        this.length = length;
    }

    /** The arm of a fence diagonal, 1.1875 long. */
    public static CrossArmVisual fence() {
        return new CrossArmVisual(FENCE_LENGTH);
    }

    /** The arm of a pane or bars diagonal, 1.375 long. */
    public static CrossArmVisual pane() {
        return new CrossArmVisual(PANE_LENGTH);
    }

    @Override
    public List<VirtualElement> elements(Diagonal diagonal, Vec3 origin) {
        BlockDisplayElement arm = new BlockDisplayElement(barState(diagonal.westState()));
        ArmTransform.apply(arm, diagonal, origin, length, ArmTransform.FULL_HEIGHT);
        return List.of(arm);
    }

    // A straight north-south run: both sides along z joined, the other two open.
    private static BlockState barState(BlockState owner) {
        return owner.getBlock().defaultBlockState()
                .setValue(CrossCollisionBlock.NORTH, true)
                .setValue(CrossCollisionBlock.SOUTH, true)
                .setValue(CrossCollisionBlock.EAST, false)
                .setValue(CrossCollisionBlock.WEST, false)
                .setValue(CrossCollisionBlock.WATERLOGGED, false);
    }
}
