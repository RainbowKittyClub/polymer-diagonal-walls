package club.rainbowkitty.polymerdiagonalwalls.lifecycle;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.LongConsumer;

import it.unimi.dsi.fastutil.longs.LongArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.BlockReader;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;
import club.rainbowkitty.polymerdiagonalwalls.display.ArmHolder;
import club.rainbowkitty.polymerdiagonalwalls.display.ArmIndex;

// Re-checks indexed holders in round robin, a fixed number per level per tick, and reports owners
// whose recorded end states no longer stand: the fallback for a write that bypassed
// LevelChunk.setBlockState at both ends, leaving no member for a random tick. Server thread only.
final class IndexSweep {
    // About 0.06 ms a tick, and a pass over S15's 32,640-holder wall lattice every 6.4 s.
    private static final int HOLDERS_PER_TICK = 256;

    private static final Map<ServerLevel, Cursor> CURSORS = new IdentityHashMap<>();

    private IndexSweep() {
    }

    // Checks level's next holders, at most one full pass per call, handing each stale owner on.
    static void sweep(ServerLevel level, LongConsumer staleOwner) {
        Cursor cursor = CURSORS.computeIfAbsent(level, key -> new Cursor());
        BlockReader reader = BlockReader.loaded(level);
        boolean restarted = false;
        int checked = 0;
        while (checked < HOLDERS_PER_TICK) {
            if (cursor.ownerIndex >= cursor.owners.size()) {
                if (cursor.chunkIndex >= cursor.chunks.size()) {
                    if (restarted) {
                        return;
                    }
                    restarted = true;
                    cursor.chunks = ArmIndex.chunkKeys(level);
                    cursor.chunkIndex = 0;
                    if (cursor.chunks.isEmpty()) {
                        return;
                    }
                }
                cursor.owners = ArmIndex.ownerKeys(level, cursor.chunks.getLong(cursor.chunkIndex));
                cursor.chunkIndex++;
                cursor.ownerIndex = 0;
                continue;
            }
            long owner = cursor.owners.getLong(cursor.ownerIndex);
            cursor.ownerIndex++;
            checked++;
            ArmHolder holder = ArmIndex.holderAt(level, BlockPos.of(owner));
            if (holder != null && stale(reader, holder)) {
                staleOwner.accept(owner);
            }
        }
    }

    // Drops level's cursor, for its unload.
    static void forget(ServerLevel level) {
        CURSORS.remove(level);
    }

    // Drops every cursor, for a server stop.
    static void clear() {
        CURSORS.clear();
    }

    // Whether a readable end of one of holder's diagonals holds another state than it recorded.
    private static boolean stale(BlockReader reader, ArmHolder holder) {
        for (Diagonal diagonal : holder.diagonals()) {
            if (differs(reader, diagonal.west(), diagonal.westState())
                    || differs(reader, diagonal.east(), diagonal.eastState())) {
                return true;
            }
        }
        return false;
    }

    // Whether pos is readable and holds another state than recorded.
    private static boolean differs(BlockReader reader, BlockPos pos, BlockState recorded) {
        BlockState state = reader.stateAt(pos);
        return state != null && state != recorded;
    }

    // Where one level's pass stands: a snapshot of its chunks, and of the current chunk's owners.
    private static final class Cursor {
        private LongArrayList chunks = new LongArrayList();
        private int chunkIndex;
        private LongArrayList owners = new LongArrayList();
        private int ownerIndex;
    }
}
