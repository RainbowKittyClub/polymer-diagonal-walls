package club.rainbowkitty.polymerdiagonalwalls.display;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalRule;

/**
 * Every arm holder, per level, keyed by chunk and then by owner position. {@link #refresh} is the
 * one way arms change: whatever notices a block change or a chunk load calls it for the owners
 * that may be affected. Server thread only.
 */
public final class ArmIndex {
    private static final Map<ServerLevel, Long2ObjectMap<Long2ObjectMap<ArmHolder>>> LEVELS =
            new IdentityHashMap<>();

    private ArmIndex() {
    }

    /** Forgets a level's holders when it unloads, and every level's when the server stops. */
    public static void register() {
        ServerLevelEvents.UNLOAD.register((server, level) -> LEVELS.remove(level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LEVELS.clear());
    }

    /**
     * Recomputes the arms {@code pos} owns. With none, its holder goes; when they differ from the
     * holder's, or the holder has lost its attachment, the holder is destroyed and rebuilt rather
     * than edited. An owner whose chunk is not loaded has no arms.
     */
    public static void refresh(ServerLevel level, BlockPos pos) {
        long chunkKey = ChunkPos.pack(pos);
        long ownerKey = pos.asLong();
        Long2ObjectMap<ArmHolder> owners = chunks(level).get(chunkKey);
        ArmHolder existing = owners == null ? null : owners.get(ownerKey);
        LevelChunk chunk = level.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
        List<Diagonal> owned = chunk == null ? List.of() : DiagonalRule.owned(level, pos);

        if (existing != null && existing.isAttached() && existing.diagonals().equals(owned)) {
            return;
        }
        if (existing != null) {
            existing.destroy();
            owners.remove(ownerKey);
            if (owners.isEmpty()) {
                chunks(level).remove(chunkKey);
            }
        }
        if (owned.isEmpty()) {
            return;
        }
        Vec3 origin = Vec3.atBottomCenterOf(pos);
        ArmHolder holder = new ArmHolder(owned, origin);
        new ChunkAttachment(holder, chunk, origin, false);
        chunks(level).computeIfAbsent(chunkKey, key -> new Long2ObjectOpenHashMap<>())
                .put(ownerKey, holder);
    }

    /** Refreshes every owner indexed in {@code chunk}, so arms whose owner changed go too. */
    public static void refreshIndexed(ServerLevel level, ChunkPos chunk) {
        Long2ObjectMap<ArmHolder> owners = chunks(level).get(chunk.pack());
        if (owners == null) {
            return;
        }
        for (long ownerKey : new LongArrayList(owners.keySet())) {
            refresh(level, BlockPos.of(ownerKey));
        }
    }

    /** Forgets a chunk's holders, for its unload: Polymer has already destroyed them. */
    public static void dropChunk(ServerLevel level, ChunkPos chunk) {
        chunks(level).remove(chunk.pack());
    }

    /** The holder of the arms {@code pos} owns, or null when it owns none. */
    public static @Nullable ArmHolder holderAt(ServerLevel level, BlockPos pos) {
        Long2ObjectMap<ArmHolder> owners = chunks(level).get(ChunkPos.pack(pos));
        return owners == null ? null : owners.get(pos.asLong());
    }

    /** How many attached holders and arms {@code level} has. */
    public static Counts counts(ServerLevel level) {
        Long2ObjectMap<Long2ObjectMap<ArmHolder>> chunks = LEVELS.get(level);
        int holders = 0;
        int arms = 0;
        if (chunks != null) {
            for (Long2ObjectMap<ArmHolder> owners : chunks.values()) {
                for (ArmHolder holder : owners.values()) {
                    if (holder.isAttached()) {
                        holders++;
                        arms += holder.diagonals().size();
                    }
                }
            }
        }
        return new Counts(holders, arms);
    }

    // The level's chunk map, created on first use.
    private static Long2ObjectMap<Long2ObjectMap<ArmHolder>> chunks(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, key -> new Long2ObjectOpenHashMap<>());
    }

    /**
     * Totals for one level.
     *
     * @param holders attached arm holders, one per owner with at least one diagonal
     * @param arms diagonals drawn across those holders
     */
    public record Counts(int holders, int arms) {
    }
}
