package club.rainbowkitty.polymerdiagonalwalls.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import club.rainbowkitty.polymerdiagonalwalls.collision.CornerBoxes;
import club.rainbowkitty.polymerdiagonalwalls.collision.GapSeal;

// Seals fence and pane diagonal gaps for non-players, as WallBlockMixin does for walls. Fences,
// panes and bars all inherit this method.
@Mixin(CrossCollisionBlock.class)
abstract class CrossCollisionBlockMixin {
    // Adds the boxes of the block's diagonals for an entity GapSeal applies to.
    @ModifyReturnValue(method = "getCollisionShape", at = @At("RETURN"))
    private VoxelShape polymerdiagonalwalls$sealGaps(VoxelShape shape, BlockState state,
            BlockGetter level, BlockPos pos, CollisionContext context) {
        return GapSeal.appliesTo(context) ? CornerBoxes.seal(shape, state, level, pos) : shape;
    }
}
