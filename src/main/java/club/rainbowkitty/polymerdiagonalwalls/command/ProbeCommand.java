package club.rainbowkitty.polymerdiagonalwalls.command;

import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.BlockReader;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.DiagonalRule;

/**
 * {@code /diagonals probe <pos>}: prints the diagonals the block at {@code pos} owns, read from
 * loaded chunks only. Returns how many it owns. Gated on
 * {@code polymerdiagonalwalls.command.probe}, op level 2 as fallback.
 */
public final class ProbeCommand {
    private static final String KEY = "commands.polymerdiagonalwalls.probe.";

    private ProbeCommand() {
    }

    /** The {@code probe} literal, gate included. */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("probe")
                .requires(PolymerDiagonalWalls.permissions()
                        .require("probe", PermissionLevel.GAMEMASTERS))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ProbeCommand::run));
    }

    // Reports the owner's state first, so a refusal says which of the rule's inputs was missing.
    private static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        BlockState state = BlockReader.loaded(level).stateAt(pos);
        if (state == null) {
            source.sendFailure(text("unloaded", pos.getX(), pos.getY(), pos.getZ()));
            return 0;
        }
        String block = blockId(state);
        if (!DiagonalRule.isDiagonalBlock(state)) {
            source.sendFailure(text("not_member", block, pos.getX(), pos.getY(), pos.getZ()));
            return 0;
        }
        List<Diagonal> owned = DiagonalRule.owned(level, pos);
        if (owned.isEmpty()) {
            source.sendSuccess(
                    () -> text("owns_none", block, pos.getX(), pos.getY(), pos.getZ()), false);
            return 0;
        }
        source.sendSuccess(() -> text("owns", block, pos.getX(), pos.getY(), pos.getZ(),
                owned.size()), false);
        for (Diagonal diagonal : owned) {
            BlockPos east = diagonal.east();
            String direction = diagonal.southEast() ? "south_east" : "north_east";
            source.sendSuccess(() -> text(direction, blockId(diagonal.eastState()),
                    east.getX(), east.getY(), east.getZ()), false);
        }
        return owned.size();
    }

    // This command's text under KEY, with its English as fallback.
    private static MutableComponent text(String key, Object... args) {
        return PolymerDiagonalWalls.text().of(KEY + key, args);
    }

    // The registry id, which tells mixed materials apart where display names might not.
    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }
}
