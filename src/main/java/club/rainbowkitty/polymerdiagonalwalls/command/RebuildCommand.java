package club.rainbowkitty.polymerdiagonalwalls.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.ChunkScan;
import club.rainbowkitty.polymerdiagonalwalls.display.ArmIndex;

/**
 * {@code /diagonals rebuild <radius>}: refreshes every owner in the loaded chunks that reach within
 * {@code radius} blocks of the source, plus every holder already indexed there. Returns the number
 * of blocks refreshed. Gated on {@code polymerdiagonalwalls.command.rebuild}, op level 2 as
 * fallback.
 */
public final class RebuildCommand {
    private static final int MAX_RADIUS = 512;

    private RebuildCommand() {
    }

    /** The {@code rebuild} literal, gate included. */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("rebuild")
                .requires(PolymerDiagonalWalls.permissions()
                        .require("rebuild", PermissionLevel.GAMEMASTERS))
                .then(Commands.argument("radius", IntegerArgumentType.integer(0, MAX_RADIUS))
                        .executes(RebuildCommand::run));
    }

    // Walks chunk by chunk and never loads one; an unloaded chunk has no arms to rebuild.
    private static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        int radius = IntegerArgumentType.getInteger(context, "radius");
        BlockPos centre = BlockPos.containing(source.getPosition());
        int minX = SectionPos.blockToSectionCoord(centre.getX() - radius);
        int maxX = SectionPos.blockToSectionCoord(centre.getX() + radius);
        int minZ = SectionPos.blockToSectionCoord(centre.getZ() - radius);
        int maxZ = SectionPos.blockToSectionCoord(centre.getZ() + radius);
        int chunks = 0;
        int blocks = 0;
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                chunks++;
                ArmIndex.refreshIndexed(level, chunk.getPos());
                blocks += ChunkScan.forEachMember(chunk, pos -> ArmIndex.refresh(level, pos));
            }
        }
        ArmIndex.Counts counts = ArmIndex.counts(level);
        int refreshed = blocks;
        int loaded = chunks;
        source.sendSuccess(() -> PolymerDiagonalWalls.text().of(
                "commands.polymerdiagonalwalls.rebuild.done", refreshed, loaded,
                counts.holders(), counts.arms()), true);
        return blocks;
    }
}
