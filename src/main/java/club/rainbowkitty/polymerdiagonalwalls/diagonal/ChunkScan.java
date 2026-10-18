package club.rainbowkitty.polymerdiagonalwalls.diagonal;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Finds the blocks of a chunk that some {@link DiagonalFamily} admits. */
public final class ChunkScan {
    private ChunkScan() {
    }

    /**
     * Hands every admitted block in {@code chunk} to {@code action}, as an immutable position.
     * Sections whose palette holds no admitted state are skipped without reading their blocks.
     *
     * @return how many blocks were handed over
     */
    public static int forEachMember(LevelChunk chunk, Consumer<BlockPos> action) {
        LevelChunkSection[] sections = chunk.getSections();
        ChunkPos chunkPos = chunk.getPos();
        int found = 0;
        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section.hasOnlyAir() || !section.maybeHas(DiagonalRule::isDiagonalBlock)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            for (int y = 0; y < SectionPos.SECTION_SIZE; y++) {
                for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
                    for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (DiagonalRule.isDiagonalBlock(state)) {
                            action.accept(new BlockPos(
                                    chunkPos.getBlockX(x), baseY + y, chunkPos.getBlockZ(z)));
                            found++;
                        }
                    }
                }
            }
        }
        return found;
    }
}
