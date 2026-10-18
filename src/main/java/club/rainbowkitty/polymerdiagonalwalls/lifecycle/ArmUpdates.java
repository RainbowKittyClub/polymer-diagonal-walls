package club.rainbowkitty.polymerdiagonalwalls.lifecycle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalRule;
import club.rainbowkitty.polymerdiagonalwalls.display.ArmIndex;

/**
 * Keeps arms in step with the world. A block change only marks its position, from whatever thread
 * made it; each level's end of tick drains the marks into {@link ArmIndex#refresh} on the server
 * thread, so nothing reads another chunk while a block is being set.
 */
public final class ArmUpdates {
    private static final Map<ServerLevel, PendingChanges> PENDING = new ConcurrentHashMap<>();

    private ArmUpdates() {
    }

    /** Drains every level at the end of its tick, and forgets a level's marks when it goes. */
    public static void register() {
        ServerTickEvents.END_LEVEL_TICK.register(ArmUpdates::drain);
        ServerLevelEvents.UNLOAD.register((server, level) -> PENDING.remove(level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    /**
     * Marks {@code pos} when its old or new state takes diagonals, and the block below it when
     * that one does, since what stands above a diagonal decides its height. Reads only
     * {@code chunk}, which holds both. Safe from any thread.
     */
    public static void blockChanged(ServerLevel level, LevelChunk chunk, BlockPos pos,
            BlockState oldState, BlockState newState) {
        boolean member = DiagonalRule.isDiagonalBlock(oldState)
                || DiagonalRule.isDiagonalBlock(newState);
        BlockPos below = pos.below();
        boolean memberBelow = DiagonalRule.isDiagonalBlock(chunk.getBlockState(below));
        if (!member && !memberBelow) {
            return;
        }
        PendingChanges pending = PENDING.computeIfAbsent(level, key -> new PendingChanges());
        if (member) {
            pending.markBlock(pos.asLong());
        }
        if (memberBelow) {
            pending.markBlock(below.asLong());
        }
    }

    // Refreshes every marked position as an owner, plus the two that may own a diagonal ending
    // there, each once however often it was marked.
    private static void drain(ServerLevel level) {
        PendingChanges pending = PENDING.get(level);
        LongSet blocks = pending == null ? null : pending.take();
        if (blocks == null) {
            return;
        }
        LongOpenHashSet owners = new LongOpenHashSet();
        blocks.forEach(pos -> addOwners(owners, pos));
        owners.forEach(owner -> ArmIndex.refresh(level, BlockPos.of(owner)));
    }

    // Adds pos and the positions one block west and one block along z either way.
    private static void addOwners(LongSet owners, long pos) {
        owners.add(pos);
        owners.add(BlockPos.offset(pos, -1, 0, 1));
        owners.add(BlockPos.offset(pos, -1, 0, -1));
    }

    // One level's marks waiting for its next drain. Any thread may mark; the drain takes them all.
    private static final class PendingChanges {
        private LongOpenHashSet blocks = new LongOpenHashSet();

        // Queues a changed block position.
        private synchronized void markBlock(long pos) {
            blocks.add(pos);
        }

        // Hands over every queued position and starts again empty; null when none are queued.
        private synchronized @Nullable LongSet take() {
            if (blocks.isEmpty()) {
                return null;
            }
            LongSet taken = blocks;
            blocks = new LongOpenHashSet();
            return taken;
        }
    }
}
