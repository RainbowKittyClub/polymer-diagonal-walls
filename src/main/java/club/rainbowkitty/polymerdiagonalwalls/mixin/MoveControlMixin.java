package club.rainbowkitty.polymerdiagonalwalls.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

// MoveControl jumps a mob whose own block stands above its feet, unless it's a door or a fence. A
// rabbit touching a wall post stands in the post's cell, so walls bounced rabbits onto their tops.
@Mixin(MoveControl.class)
abstract class MoveControlMixin {
    // Exempts walls where fences are exempt; the jump toward a path node above is left alone.
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is("
                    + "Lnet/minecraft/tags/TagKey;)Z"))
    private boolean polymerdiagonalwalls$exemptWalls(BlockState state, TagKey<Block> tag,
            Operation<Boolean> original) {
        return original.call(state, tag)
                || tag == BlockTags.FENCES && state.is(BlockTags.WALLS);
    }
}
