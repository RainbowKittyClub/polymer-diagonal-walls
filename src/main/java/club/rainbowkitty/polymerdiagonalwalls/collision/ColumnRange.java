package club.rainbowkitty.polymerdiagonalwalls.collision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;
import club.rainbowkitty.polymerdiagonalwalls.settings.DiagonalSettings;

/**
 * Which players get which owner's columns. A client ticks every shulker and slime it holds as a
 * mob, so a player gets a {@link ColumnSite}'s columns only while it tracks the site's chunk
 * within {@link DiagonalSettings#COLLISION_RANGE} of it. Once a second each level's players are
 * offered the sites near them, building their holders, and released from those they have left; a
 * holder nobody watches any more is dropped. Server thread only.
 */
public final class ColumnRange {
    // A watcher keeps its columns this much farther out, so one standing at the edge sees no churn.
    private static final double RELEASE_MARGIN = 2;
    private static final int RECHECK_TICKS = 20;

    private static final Map<ServerLevel, Long2ObjectMap<Set<ColumnSite>>> SITES =
            new IdentityHashMap<>();
    private static int ticksSinceRecheck;

    private ColumnRange() {
    }

    /** Hooks the once-a-second recheck, and forgets a level's sites when it unloads. */
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ColumnRange::tick);
        ServerLevelEvents.UNLOAD.register((server, level) -> SITES.remove(level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SITES.clear());
    }

    /**
     * Indexes the site of an owner's diagonals, attached at {@code origin} in {@code chunk}.
     *
     * @return the site, to hand back to {@link #removeSite}; {@code null} when the diagonals'
     *         families put nothing on them
     */
    public static @Nullable ColumnSite addSite(
            ServerLevel level, LevelChunk chunk, Vec3 origin, List<Diagonal> diagonals) {
        if (diagonals.stream().allMatch(diagonal -> diagonal.family().columns().isEmpty())) {
            return null;
        }
        ColumnSite site = new ColumnSite(level, chunk, origin, diagonals);
        SITES.computeIfAbsent(level, key -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(chunk.getPos().pack(),
                        key -> Collections.newSetFromMap(new IdentityHashMap<>()))
                .add(site);
        return site;
    }

    /** Forgets a site and drops its holder, when its owner's arms go. */
    public static void removeSite(ColumnSite site) {
        site.drop();
        Long2ObjectMap<Set<ColumnSite>> chunks = SITES.get(site.level());
        if (chunks == null) {
            return;
        }
        long chunkKey = site.chunk().getPos().pack();
        Set<ColumnSite> inChunk = chunks.get(chunkKey);
        if (inChunk != null && inChunk.remove(site) && inChunk.isEmpty()) {
            chunks.remove(chunkKey);
        }
    }

    /** Whether {@code player} is near enough {@code holder} to be given its columns. */
    public static boolean admits(ServerPlayer player, ColumnHolder holder) {
        HolderAttachment attachment = holder.getAttachment();
        return attachment != null && within(player, attachment.getWorld(), holder.getPos(),
                DiagonalSettings.collisionRange(attachment.getWorld().getServer()));
    }

    /** Runs {@code action} on every built holder, and returns how many there were. */
    public static int forEachBuilt(Consumer<ColumnHolder> action) {
        List<ColumnHolder> holders = new ArrayList<>();
        for (Long2ObjectMap<Set<ColumnSite>> chunks : SITES.values()) {
            for (Set<ColumnSite> inChunk : chunks.values()) {
                for (ColumnSite site : inChunk) {
                    if (site.holder() != null) {
                        holders.add(site.holder());
                    }
                }
            }
        }
        holders.forEach(action);
        return holders.size();
    }

    // Counts server ticks, rechecking every level once RECHECK_TICKS have passed.
    private static void tick(MinecraftServer server) {
        if (++ticksSinceRecheck < RECHECK_TICKS) {
            return;
        }
        ticksSinceRecheck = 0;
        for (ServerLevel level : server.getAllLevels()) {
            Long2ObjectMap<Set<ColumnSite>> chunks = SITES.get(level);
            if (chunks != null && !chunks.isEmpty()) {
                recheck(level, chunks);
            }
        }
    }

    // Releases watchers that have moved off, drops unwatched holders, then serves each player.
    private static void recheck(ServerLevel level, Long2ObjectMap<Set<ColumnSite>> chunks) {
        double range = DiagonalSettings.collisionRange(level.getServer());
        double releaseRange = range + RELEASE_MARGIN;
        for (Set<ColumnSite> inChunk : chunks.values()) {
            for (ColumnSite site : inChunk) {
                ColumnHolder holder = site.holder();
                if (holder == null) {
                    continue;
                }
                List<ServerGamePacketListenerImpl> watchers =
                        List.copyOf(holder.getWatchingPlayers());
                for (ServerGamePacketListenerImpl watcher : watchers) {
                    if (!within(watcher.getPlayer(), level, site.origin(), releaseRange)) {
                        holder.stopWatching(watcher);
                    }
                }
                if (holder.getWatchingPlayers().isEmpty()) {
                    site.drop();
                }
            }
        }
        for (ServerPlayer player : level.players()) {
            // Polymer stops a dying player watching; serving it would undo that.
            if (!player.isDeadOrDying()) {
                serveNearby(player, chunks, range);
            }
        }
    }

    // Gives player the columns of every site within range blocks whose chunk it tracks.
    private static void serveNearby(
            ServerPlayer player, Long2ObjectMap<Set<ColumnSite>> chunks, double range) {
        int minX = SectionPos.blockToSectionCoord(Mth.floor(player.getX() - range));
        int maxX = SectionPos.blockToSectionCoord(Mth.floor(player.getX() + range));
        int minZ = SectionPos.blockToSectionCoord(Mth.floor(player.getZ() - range));
        int maxZ = SectionPos.blockToSectionCoord(Mth.floor(player.getZ() + range));
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                Set<ColumnSite> inChunk = chunks.get(ChunkPos.pack(chunkX, chunkZ));
                if (inChunk == null) {
                    continue;
                }
                for (ColumnSite site : List.copyOf(inChunk)) {
                    if (within(player, site.level(), site.origin(), range)
                            && VirtualEntityUtils.isPlayerTracking(player, site.chunk())) {
                        // A new holder takes every admitted tracker as it attaches.
                        site.build().startWatching(player.connection);
                    }
                }
            }
        }
    }

    // Whether player stands in level within range blocks of position.
    private static boolean within(
            ServerPlayer player, ServerLevel level, Vec3 position, double range) {
        return player.level() == level
                && player.position().distanceToSqr(position) <= range * range;
    }
}
