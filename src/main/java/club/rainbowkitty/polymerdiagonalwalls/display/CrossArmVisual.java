package club.rainbowkitty.polymerdiagonalwalls.display;

import java.util.List;

import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;

import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;

/**
 * A fence or pane diagonal's arm: the owner's state joined north and south, placed by
 * {@link ArmTransform} at full height. One block display draws it, and a fence's model draws its
 * post under every state, so a fence arm shows one more post at the corner point. A block that
 * reaches clients as a Polymer stand-in draws only its side models instead ({@link BridgedArms}),
 * with no post.
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
        BlockState bar = barState(diagonal.westState(), true);
        List<VirtualElement> bridged = BridgedArms.elements(diagonal, origin, bar,
                barState(diagonal.westState(), false), length, ArmTransform.FULL_HEIGHT);
        if (!bridged.isEmpty()) {
            return bridged;
        }
        BlockDisplayElement arm = new BlockDisplayElement(bar);
        ArmTransform.apply(arm, diagonal, origin, length, ArmTransform.FULL_HEIGHT);
        return List.of(arm);
    }

    // A straight north-south run, or with joined false no side at all; east and west stay open.
    // Starts from the owner's state, so a modded block keeps its own extra properties.
    private static BlockState barState(BlockState owner, boolean joined) {
        return owner
                .setValue(CrossCollisionBlock.NORTH, joined)
                .setValue(CrossCollisionBlock.SOUTH, joined)
                .setValue(CrossCollisionBlock.EAST, false)
                .setValue(CrossCollisionBlock.WEST, false)
                .setValue(CrossCollisionBlock.WATERLOGGED, false);
    }
}
