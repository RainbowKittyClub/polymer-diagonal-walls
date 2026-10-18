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
 * A wall diagonal's arm: one block display of the owner's material as a straight low bar, placed
 * by {@link ArmTransform}.
 */
public final class WallArmVisual implements ArmVisual {
    // 18 px: the shortest bar whose corners stay inside both posts is 1.082.
    private static final float LENGTH = 1.125f;

    @Override
    public List<VirtualElement> elements(Diagonal diagonal, Vec3 origin) {
        BlockDisplayElement arm = new BlockDisplayElement(barState(diagonal.westState()));
        ArmTransform.apply(arm, diagonal, origin, LENGTH, ArmTransform.LOW_HEIGHT);
        return List.of(arm);
    }

    // Two low sides along z: the side model has no south face, so the join at z 8 shows no seam.
    private static BlockState barState(BlockState owner) {
        return owner.getBlock().defaultBlockState()
                .setValue(WallBlock.UP, false)
                .setValue(WallBlock.NORTH, WallSide.LOW)
                .setValue(WallBlock.SOUTH, WallSide.LOW)
                .setValue(WallBlock.EAST, WallSide.NONE)
                .setValue(WallBlock.WEST, WallSide.NONE)
                .setValue(WallBlock.WATERLOGGED, false);
    }
}
