package club.rainbowkitty.polymerdiagonalwalls.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import club.rainbowkitty.polymerdiagonalwalls.lifecycle.SectionWrites;

// Axiom's pastes, fills, tools, undo and redo write each block straight into its section. Skipped
// without Axiom; required with it, so a moved call fails at boot, when Axiom loads this class.
@Pseudo
@Mixin(targets = "com.moulberry.axiom.packets.AxiomServerboundSetBuffer")
abstract class AxiomSetBufferMixin {
    private static final String APPLY = "applyBlockBufferServer("
            + "Lcom/moulberry/axiom/world_modification/BlockBuffer;"
            + "Lnet/minecraft/server/level/ServerLevel;"
            + "Lcom/moulberry/axiom/render/regions/ChunkedBooleanRegion;"
            + "Lnet/minecraft/server/level/ServerPlayer;)V";
    private static final String SET_BLOCK_STATE =
            "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState("
            + "IIILnet/minecraft/world/level/block/state/BlockState;Z)"
            + "Lnet/minecraft/world/level/block/state/BlockState;";

    // Marks each buffered block that changed; chunk is the only LevelChunk local in scope.
    @WrapOperation(method = APPLY, at = @At(value = "INVOKE", target = SET_BLOCK_STATE))
    private static BlockState polymerdiagonalwalls$markWrite(LevelChunkSection section, int x,
            int y, int z, BlockState state, boolean checkThreading,
            Operation<BlockState> original, @Local LevelChunk chunk) {
        BlockState oldState = original.call(section, x, y, z, state, checkThreading);
        SectionWrites.written(chunk, section, x, y, z, oldState, state);
        return oldState;
    }
}
