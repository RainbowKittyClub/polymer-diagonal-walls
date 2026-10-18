package club.rainbowkitty.polymerdiagonalwalls.lifecycle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.ChunkScan;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalRule;
import club.rainbowkitty.polymerdiagonalwalls.display.ArmIndex;

/**
 * Keeps arms in step with the world. Block changes and chunk loads and unloads only mark
 * positions, from whatever thread raised them; each level's end of tick drains the marks into
 * {@link ArmIndex} on the server thread. Nothing here reads another chunk while a block is being
 * set, or touches level storage from a chunk event, which can fire inside a level's constructor.
 */
public final class ArmUpdates {
    // Drains a loaded chunk may wait for getChunkNow to return it before it is given up.
    private static final int CHUNK_WAIT_DRAINS = 20;

    private static final Map<ServerLevel, PendingChanges> PENDING = new ConcurrentHashMap<>();

    private ArmUpdates() {
    }

    /**
     * Marks chunks as they load, become readable again or unload, drains every level at the end
     * of its tick, and forgets a level's marks when it goes.
     */
    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register(
                (level, chunk, generated) -> pending(level).markLoaded(chunk.getPos().pack()));
        ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE.register(ArmUpdates::statusChanged);
        ServerChunkEvents.CHUNK_UNLOAD.register(
                (level, chunk) -> pending(level).markUnloaded(chunk.getPos().pack()));
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
        PendingChanges pending = pending(level);
        if (member) {
            pending.markBlock(pos.asLong());
        }
        if (memberBelow) {
            pending.markBlock(below.asLong());
        }
    }

    // Marks a chunk that becomes readable again: one dropped below FULL keeps its completed
    // future, so coming back raises no CHUNK_LOAD.
    private static void statusChanged(ServerLevel level, LevelChunk chunk,
            FullChunkStatus oldStatus, FullChunkStatus newStatus) {
        if (oldStatus == FullChunkStatus.INACCESSIBLE
                && newStatus.isOrAfter(FullChunkStatus.FULL)) {
            pending(level).markLoaded(chunk.getPos().pack());
        }
    }

    // The level's marks, created on first use; touches nothing but the map.
    private static PendingChanges pending(ServerLevel level) {
        return PENDING.computeIfAbsent(level, key -> new PendingChanges());
    }

    // Forgets unloaded chunks first, so a chunk that unloaded and loaded again is rebuilt. Then
    // refreshes every member of each loaded chunk and every marked block as an owner, plus the two
    // positions that may own a diagonal ending there, each once however often it was marked.
    private static void drain(ServerLevel level) {
        PendingChanges pending = PENDING.get(level);
        Batch batch = pending == null ? null : pending.take();
        if (batch == null) {
            return;
        }
        batch.unloaded().forEach(chunk -> ArmIndex.dropChunk(level, ChunkPos.unpack(chunk)));
        LongOpenHashSet owners = new LongOpenHashSet();
        for (Long2IntMap.Entry entry : batch.loaded().long2IntEntrySet()) {
            long chunkKey = entry.getLongKey();
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
            if (chunk != null) {
                ChunkScan.forEachMember(chunk, pos -> addOwners(owners, pos.asLong()));
            } else if (entry.getIntValue() < CHUNK_WAIT_DRAINS) {
                pending.waitFor(chunkKey, entry.getIntValue() + 1);
            }
        }
        batch.blocks().forEach(pos -> addOwners(owners, pos));
        owners.forEach(owner -> ArmIndex.refresh(level, BlockPos.of(owner)));
    }

    // Adds pos and the positions one block west and one block along z either way.
    private static void addOwners(LongSet owners, long pos) {
        owners.add(pos);
        owners.add(BlockPos.offset(pos, -1, 0, 1));
        owners.add(BlockPos.offset(pos, -1, 0, -1));
    }

    // Everything one drain takes: marked blocks, loaded chunks with the drains each has waited,
    // and unloaded chunks.
    private record Batch(LongSet blocks, Long2IntMap loaded, LongSet unloaded) {
    }

    // One level's marks waiting for its next drain. Any thread may mark; the drain takes them all.
    private static final class PendingChanges {
        private LongOpenHashSet blocks = new LongOpenHashSet();
        private Long2IntOpenHashMap loaded = new Long2IntOpenHashMap();
        private LongOpenHashSet unloaded = new LongOpenHashSet();

        // Queues a changed block position.
        private synchronized void markBlock(long pos) {
            blocks.add(pos);
        }

        // Queues a chunk that loaded or became readable, with no drains waited yet.
        private synchronized void markLoaded(long chunk) {
            loaded.put(chunk, 0);
        }

        // Queues a chunk a drain could not read yet, unless something marked it since.
        private synchronized void waitFor(long chunk, int drainsWaited) {
            loaded.putIfAbsent(chunk, drainsWaited);
        }

        // Queues an unloaded chunk, which makes a load of it still waiting moot.
        private synchronized void markUnloaded(long chunk) {
            loaded.remove(chunk);
            unloaded.add(chunk);
        }

        // Hands over everything queued and starts again empty; null when nothing is queued.
        private synchronized @Nullable Batch take() {
            if (blocks.isEmpty() && loaded.isEmpty() && unloaded.isEmpty()) {
                return null;
            }
            Batch batch = new Batch(blocks, loaded, unloaded);
            blocks = new LongOpenHashSet();
            loaded = new Long2IntOpenHashMap();
            unloaded = new LongOpenHashSet();
            return batch;
        }
    }
}
