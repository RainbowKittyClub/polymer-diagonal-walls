package club.rainbowkitty.polymerdiagonalwalls.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import club.rainbowkitty.polymerdiagonalwalls.lifecycle.SectionWrites;

// Axiom's single-block edits with updates off write straight into the section. Skipped without
// Axiom; required with it, so a moved call fails at boot, when Axiom loads this class.
@Pseudo
@Mixin(targets = "com.moulberry.axiom.packets.AxiomServerboundSetBlock")
abstract class AxiomSetBlockMixin {
    private static final String SET_WITHOUT_UPDATES = "setWithoutUpdates("
            + "Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/world/level/block/state/BlockState;)V";
    private static final String SET_BLOCK_STATE =
            "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState("
            + "IIILnet/minecraft/world/level/block/state/BlockState;Z)"
            + "Lnet/minecraft/world/level/block/state/BlockState;";

    // Marks the block if it changed; pos is the exact position, chunk the only LevelChunk local.
    @WrapOperation(method = SET_WITHOUT_UPDATES,
            at = @At(value = "INVOKE", target = SET_BLOCK_STATE))
    private static BlockState polymerdiagonalwalls$markWrite(LevelChunkSection section, int x,
            int y, int z, BlockState state, boolean checkThreading,
            Operation<BlockState> original, @Local(argsOnly = true) BlockPos pos,
            @Local LevelChunk chunk) {
        BlockState oldState = original.call(section, x, y, z, state, checkThreading);
        SectionWrites.written(chunk, pos, oldState, state);
        return oldState;
    }
}
