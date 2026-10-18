package club.rainbowkitty.polymerdiagonalwalls.display;

import java.util.List;

import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;

/**
 * A wall diagonal's arm: one block display of the owner's material as a straight low bar, turned
 * 45° about the corner point between the two posts and stretched so both ends hide in the posts.
 * The geometry is the plan's "Reference: Arm geometry".
 */
public final class WallArmVisual implements ArmVisual {
    // 18 px: the shortest bar whose corners stay inside both posts is 1.082.
    private static final float LENGTH = 1.125f;
    // Shrinks the bar across and lifts it, so no face is coplanar with a post's top or bottom.
    private static final float EPSILON = 1f / 512f;
    // Toward the owner on each axis, so the entity's light cell is the owner, never opaque.
    private static final float NUDGE = 1f / 16f;
    // 128 blocks at 100% entity distance.
    private static final float VIEW_RANGE = 2f;
    private static final float CULL_WIDTH = 1.25f;
    private static final float CULL_HEIGHT = 1f;
    private static final float EIGHTH_TURN = (float) (Math.PI / 4);

    @Override
    public List<VirtualElement> elements(Diagonal diagonal, Vec3 origin) {
        int dz = diagonal.dz();
        Vec3 corner = Vec3.atLowerCornerOf(diagonal.west()).add(1, 0, dz > 0 ? 1 : 0);
        Vec3 entityPosition = corner.add(-NUDGE, 0, -dz * NUDGE);

        // p -> T + R(theta) S (p - c). JOML's rotationY sends +z to (sin, cos), so +45° runs
        // south-east and -45° along the north-east line.
        Quaternionf rotation = new Quaternionf().rotationY(dz * EIGHTH_TURN);
        Vector3f scale = new Vector3f(1 - EPSILON, 1 - EPSILON, LENGTH);
        Vector3f scaledCentre = new Vector3f(0.5f * scale.x, 0, 0.5f * scale.z);
        Vector3f translation = new Vector3f(NUDGE, EPSILON, dz * NUDGE)
                .sub(rotation.transform(scaledCentre));

        BlockDisplayElement arm = new BlockDisplayElement(barState(diagonal.westState()));
        arm.setOffset(entityPosition.subtract(origin));
        arm.setTranslation(translation);
        arm.setLeftRotation(rotation);
        arm.setScale(scale);
        arm.setDisplaySize(CULL_WIDTH, CULL_HEIGHT);
        arm.setViewRange(VIEW_RANGE);
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
