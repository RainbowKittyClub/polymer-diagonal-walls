package club.rainbowkitty.polymerdiagonalwalls.display;

import java.util.List;

import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;

/**
 * A fence or pane diagonal's arm: the owner's state joined north and south, placed by
 * {@link ArmTransform} at full height. One block display draws it. A fence's model draws its post
 * under every state, so a fence arm also gets an item display of its sides alone from
 * {@link FenceArmModels}, and {@link PackAudience} sends each watcher one of the two. A block that
 * reaches clients as a Polymer stand-in draws only its side models instead ({@link BridgedArms}).
 */
public final class CrossArmVisual implements ArmVisual {
    // A fence bar end stays inside a 4 px post within 0.114 of its centre: 1.186 at least.
    private static final float FENCE_LENGTH = 1.1875f;
    // A pane end stays inside a 2 px post within 0.026 of its centre: 1.362 at least.
    private static final float PANE_LENGTH = 1.375f;
    // The post-less arm's parts are already where its blockstate draws them.
    private static final Quaternionfc NO_TURN = new Quaternionf();

    private final float length;
    private final boolean hasPost;

    // One visual per arm length, and whether its block display shows a post at the corner.
    private CrossArmVisual(float length, boolean hasPost) {
        this.length = length;
        this.hasPost = hasPost;
    }

    /** The arm of a fence diagonal, 1.1875 long. */
    public static CrossArmVisual fence() {
        return new CrossArmVisual(FENCE_LENGTH, true);
    }

    /** The arm of a pane or bars diagonal, 1.375 long. */
    public static CrossArmVisual pane() {
        return new CrossArmVisual(PANE_LENGTH, false);
    }

    /**
     * {@code owner} as a straight north-south run, or with {@code joined} false joined nowhere;
     * east and west stay open. A modded block keeps its own extra properties.
     */
    public static BlockState barState(BlockState owner, boolean joined) {
        return owner
                .setValue(CrossCollisionBlock.NORTH, joined)
                .setValue(CrossCollisionBlock.SOUTH, joined)
                .setValue(CrossCollisionBlock.EAST, false)
                .setValue(CrossCollisionBlock.WEST, false)
                .setValue(CrossCollisionBlock.WATERLOGGED, false);
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
        if (!hasPost) {
            return List.of(arm);
        }
        Block block = bar.getBlock();
        ItemStack stack = new ItemStack(Items.TRIAL_KEY);
        stack.set(DataComponents.ITEM_MODEL, FenceArmModels.itemModel(block));
        ItemDisplayElement sides = new ItemDisplayElement(stack);
        ArmTransform.applyPart(sides, diagonal, origin, length, ArmTransform.FULL_HEIGHT, NO_TURN,
                0, ArmTransform.VIEW_RANGE);
        sides.setVisibilityPredicate(new PackAudience(block, true));
        arm.setVisibilityPredicate(new PackAudience(block, false));
        return List.of(sides, arm);
    }
}
