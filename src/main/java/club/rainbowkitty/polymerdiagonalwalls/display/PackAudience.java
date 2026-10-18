package club.rainbowkitty.polymerdiagonalwalls.display;

import java.util.function.Predicate;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/**
 * Which watchers one element of a fence arm is sent to, as its Polymer visibility predicate: the
 * post-less arm goes to those holding the main pack once that pack carries the fence's arm
 * ({@link FenceArmModels}), the block display to everyone else, so each watcher sees one of them.
 * Polymer asks it as a watcher starts watching, which follows the pack reply on join.
 *
 * @param block the fence the arm is drawn for
 * @param withPack true for the post-less arm, false for the block display
 */
public record PackAudience(Block block, boolean withPack) implements Predicate<ServerPlayer> {
    @Override
    public boolean test(ServerPlayer player) {
        boolean postless = FenceArmModels.defines(block)
                && PolymerResourcePackUtils.hasMainPack(player.connection);
        return postless == withPack;
    }
}
