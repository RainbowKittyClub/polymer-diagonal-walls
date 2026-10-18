package club.rainbowkitty.polymerdiagonalwalls.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import club.rainbowkitty.polymerdiagonalwalls.collision.SealedPaths;

// Extends NodeEvaluator to reach its mob and currentContext fields. AmphibiousNodeEvaluator
// inherits this.
@Mixin(WalkNodeEvaluator.class)
abstract class WalkNodeEvaluatorMixin extends NodeEvaluator {
    // Refuses a diagonal step vanilla allows between two posts whose gap a diagonal seals.
    @ModifyReturnValue(method = "isDiagonalValid("
            + "Lnet/minecraft/world/level/pathfinder/Node;"
            + "Lnet/minecraft/world/level/pathfinder/Node;"
            + "Lnet/minecraft/world/level/pathfinder/Node;)Z", at = @At("RETURN"))
    private boolean polymerdiagonalwalls$avoidSealedGaps(
            boolean valid, Node pos, @Nullable Node ew, @Nullable Node ns) {
        return valid && !(ew != null && ns != null && SealedPaths.crossesSealedGap(
                this.mob.level().getServer(), this.currentContext, ew, ns));
    }
}
