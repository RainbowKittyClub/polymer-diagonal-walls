package club.rainbowkitty.polymerdiagonalwalls.display;

import java.util.List;

import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;

import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;

/**
 * A wall diagonal's arm: a straight bar of the owner's material, low or tall as
 * {@link Diagonal#tall()} says, placed by {@link ArmTransform}. A wall that reaches clients as
 * a Polymer stand-in draws it from its own side models ({@link BridgedArms}), any other as one
 * block display.
 */
public final class WallArmVisual implements ArmVisual {
    // 18 px: the shortest bar whose corners stay inside both posts is 1.082.
    private static final float LENGTH = 1.125f;

    @Override
    public List<VirtualElement> elements(Diagonal diagonal, Vec3 origin) {
        boolean tall = diagonal.tall();
        float height = tall ? ArmTransform.FULL_HEIGHT : ArmTransform.LOW_HEIGHT;
        BlockState bar = barState(diagonal.westState(), tall ? WallSide.TALL : WallSide.LOW);
        List<VirtualElement> bridged = BridgedArms.elements(diagonal, origin, bar,
                barState(diagonal.westState(), WallSide.NONE), LENGTH, height);
        if (!bridged.isEmpty()) {
            return bridged;
        }
        BlockDisplayElement arm = new BlockDisplayElement(bar);
        ArmTransform.apply(arm, diagonal, origin, LENGTH, height);
        return List.of(arm);
    }

    // Two sides along z: the side model has no south face, so the join at z 8 shows no seam.
    // Starts from the owner's state, so a modded wall keeps its own extra properties.
    private static BlockState barState(BlockState owner, WallSide side) {
        return owner
                .setValue(WallBlock.UP, false)
                .setValue(WallBlock.NORTH, side)
                .setValue(WallBlock.SOUTH, side)
                .setValue(WallBlock.EAST, WallSide.NONE)
                .setValue(WallBlock.WEST, WallSide.NONE)
                .setValue(WallBlock.WATERLOGGED, false);
    }
}
