package club.rainbowkitty.polymerdiagonalwalls.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import club.rainbowkitty.polymerdiagonalwalls.collision.CornerBoxes;
import club.rainbowkitty.polymerdiagonalwalls.collision.GapSeal;

// Seals diagonal gaps for non-players. Players and the empty context keep vanilla's shape, so the
// server's movement check agrees with what a client predicts.
@Mixin(WallBlock.class)
abstract class WallBlockMixin {
    // Adds the corner boxes of the wall's diagonals for an entity GapSeal applies to.
    @ModifyReturnValue(method = "getCollisionShape", at = @At("RETURN"))
    private VoxelShape polymerdiagonalwalls$sealGaps(VoxelShape shape, BlockState state,
            BlockGetter level, BlockPos pos, CollisionContext context) {
        return GapSeal.appliesTo(context) ? CornerBoxes.seal(shape, state, level, pos) : shape;
    }
}
