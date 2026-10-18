package club.rainbowkitty.polymerdiagonalwalls.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import club.rainbowkitty.polymerdiagonalwalls.lifecycle.ArmUpdates;

// Every block write on a loaded chunk ends here, WorldEdit's included, unlike Level.setBlock.
@Mixin(LevelChunk.class)
abstract class LevelChunkMixin {
    // A non-null return is the replaced state, so only a real change gets marked.
    @Inject(method = "setBlockState(Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;I)"
            + "Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"))
    private void polymerdiagonalwalls$markChanged(BlockPos pos, BlockState state, int flags,
            CallbackInfoReturnable<BlockState> cir) {
        BlockState oldState = cir.getReturnValue();
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (oldState != null && chunk.getLevel() instanceof ServerLevel level) {
            ArmUpdates.blockChanged(level, chunk, pos, oldState, state);
        }
    }
}
