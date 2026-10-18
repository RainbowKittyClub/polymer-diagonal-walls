package club.rainbowkitty.polymerdiagonalwalls.diagonal;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Where {@link DiagonalRule} reads block states from; never loads a chunk. */
@FunctionalInterface
public interface BlockReader {
    /** The state at {@code pos}, or {@code null} when it cannot be read without a chunk load. */
    public @Nullable BlockState stateAt(BlockPos pos);

    /**
     * Reads {@code level} through its fully loaded chunks only. Off the server thread every read
     * is {@code null}, because {@code ServerChunkCache.getChunkNow} answers only on that thread.
     */
    public static BlockReader loaded(Level level) {
        return pos -> {
            LevelChunk chunk = level.getChunkSource().getChunkNow(
                    SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ()));
            return chunk == null ? null : chunk.getBlockState(pos);
        };
    }
}
