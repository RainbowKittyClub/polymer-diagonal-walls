package club.rainbowkitty.polymerdiagonalwalls.collision;

import java.util.List;

import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;

/**
 * Where one owner's diagonals could have client collision. Its {@link ColumnHolder} is built only
 * while some player is near, since every rider costs memory and a client tick; {@link ColumnRange}
 * builds and drops it. Server thread only.
 */
public final class ColumnSite {
    private final ServerLevel level;
    private final LevelChunk chunk;
    private final Vec3 origin;
    private final List<Diagonal> diagonals;
    private @Nullable ColumnHolder holder;

    // A site at origin, in chunk of level, for the diagonals of one owner.
    ColumnSite(ServerLevel level, LevelChunk chunk, Vec3 origin, List<Diagonal> diagonals) {
        this.level = level;
        this.chunk = chunk;
        this.origin = origin;
        this.diagonals = List.copyOf(diagonals);
    }

    /** The level the site stands in. */
    public ServerLevel level() {
        return level;
    }

    /** The chunk its holder attaches to: the owner's. */
    public LevelChunk chunk() {
        return chunk;
    }

    /** Its holder's position, the owner's bottom centre. */
    public Vec3 origin() {
        return origin;
    }

    /** The holder, if one is built now. */
    public @Nullable ColumnHolder holder() {
        return holder;
    }

    // The holder, built and attached first if there is none; attaching hands it to every player
    // tracking the chunk that ColumnRange admits.
    ColumnHolder build() {
        if (holder == null) {
            holder = new ColumnHolder(diagonals, origin);
            new ChunkAttachment(holder, chunk, origin, false);
        }
        return holder;
    }

    // Destroys the holder, if there is one, removing every rider from its watchers.
    void drop() {
        if (holder != null) {
            holder.destroy();
            holder = null;
        }
    }
}
