package club.rainbowkitty.polymerdiagonalwalls.lifecycle;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Hands {@link ArmUpdates} the block writes that go straight into a chunk section and so never
 * reach {@code LevelChunk.setBlockState}: Axiom's buffer writes and its single writes without
 * updates.
 */
public final class SectionWrites {
    // Index of the section last found; checked by identity before use, so a stale or racing value
    // only costs a search.
    private static int lastSectionIndex;

    private SectionWrites() {
    }

    /**
     * Marks a write at local {@code x, y, z} of {@code section}, one of {@code chunk}'s sections,
     * when it replaced {@code oldState} with a different state.
     */
    public static void written(LevelChunk chunk, LevelChunkSection section, int x, int y, int z,
            BlockState oldState, BlockState newState) {
        if (oldState == newState) {
            return;
        }
        int sectionIndex = sectionIndex(chunk.getSections(), section);
        if (sectionIndex < 0) {
            return;
        }
        ChunkPos chunkPos = chunk.getPos();
        int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex);
        BlockPos pos = new BlockPos(chunkPos.getMinBlockX() + x,
                SectionPos.sectionToBlockCoord(sectionY, y), chunkPos.getMinBlockZ() + z);
        written(chunk, pos, oldState, newState);
    }

    /** Marks a write at {@code pos} in {@code chunk} when it replaced a different state. */
    public static void written(LevelChunk chunk, BlockPos pos, BlockState oldState,
            BlockState newState) {
        if (oldState != newState && chunk.getLevel() instanceof ServerLevel level) {
            ArmUpdates.blockChanged(level, chunk, pos, oldState, newState);
        }
    }

    // Index of section in sections, the last match tried first; -1 when it is not among them.
    private static int sectionIndex(LevelChunkSection[] sections, LevelChunkSection section) {
        int last = lastSectionIndex;
        if (last < sections.length && sections[last] == section) {
            return last;
        }
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] == section) {
                lastSectionIndex = i;
                return i;
            }
        }
        return -1;
    }
}
