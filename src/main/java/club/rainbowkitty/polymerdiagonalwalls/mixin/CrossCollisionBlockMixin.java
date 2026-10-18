package club.rainbowkitty.polymerdiagonalwalls.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import club.rainbowkitty.polymerdiagonalwalls.collision.CornerBoxes;
import club.rainbowkitty.polymerdiagonalwalls.collision.GapSeal;
import club.rainbowkitty.polymerdiagonalwalls.lifecycle.ArmUpdates;

// Seals fence and pane diagonal gaps and random-ticks them, as WallBlockMixin does for walls.
// Fences, panes and bars inherit all three; copper bars keep their own random tick.
@Mixin(CrossCollisionBlock.class)
abstract class CrossCollisionBlockMixin {
    // Adds the boxes of the block's diagonals for an entity GapSeal applies to.
    @ModifyReturnValue(method = "getCollisionShape", at = @At("RETURN"))
    private VoxelShape polymerdiagonalwalls$sealGaps(VoxelShape shape, BlockState state,
            BlockGetter level, BlockPos pos, CollisionContext context) {
        return GapSeal.appliesTo(context) ? CornerBoxes.seal(shape, state, level, pos) : shape;
    }

    /**
     * Random-ticks every state, so a fence, pane or bars some write set without
     * {@code LevelChunk.setBlockState} still gets its diagonals checked now and then.
     */
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    /** Marks the block for a diagonal check; the inherited random tick does nothing. */
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random) {
        ArmUpdates.recheck(level, pos, state);
    }
}
