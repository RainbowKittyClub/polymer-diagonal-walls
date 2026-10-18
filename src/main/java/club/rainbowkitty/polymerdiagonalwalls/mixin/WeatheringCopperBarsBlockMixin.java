package club.rainbowkitty.polymerdiagonalwalls.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.WeatheringCopperBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.polymerdiagonalwalls.lifecycle.ArmUpdates;

// Copper bars that can still weather random-tick through their own override, which hides
// CrossCollisionBlockMixin's; fully oxidized ones never random-tick, so only the sweep covers them.
@Mixin(WeatheringCopperBarsBlock.class)
abstract class WeatheringCopperBarsBlockMixin {
    // Marks the bars for a diagonal check before they weather as usual.
    @Inject(method = "randomTick", at = @At("HEAD"))
    private void polymerdiagonalwalls$recheck(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random, CallbackInfo ci) {
        ArmUpdates.recheck(level, pos, state);
    }
}
