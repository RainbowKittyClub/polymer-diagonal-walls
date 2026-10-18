package club.rainbowkitty.polymerdiagonalwalls.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.PermissionLevel;

import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;
import club.rainbowkitty.polymerdiagonalwalls.display.ArmIndex;

/**
 * {@code /diagonals stats}: attached arm holders and arms in each level, then the totals. Returns
 * the total arm count. Gated on {@code polymerdiagonalwalls.command.stats}, op level 2 as fallback.
 */
public final class StatsCommand {
    private StatsCommand() {
    }

    /** The {@code stats} literal, gate included. */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("stats")
                .requires(PolymerDiagonalWalls.permissions()
                        .require("stats", PermissionLevel.GAMEMASTERS))
                .executes(StatsCommand::run);
    }

    // One line per level with arms, so an empty dimension adds no noise.
    private static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int holders = 0;
        int arms = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            ArmIndex.Counts counts = ArmIndex.counts(level);
            if (counts.holders() == 0) {
                continue;
            }
            holders += counts.holders();
            arms += counts.arms();
            String dimension = level.dimension().identifier().toString();
            source.sendSuccess(() -> PolymerDiagonalWalls.text().of(
                    "commands.polymerdiagonalwalls.stats.level", dimension,
                    counts.holders(), counts.arms()), false);
        }
        int totalHolders = holders;
        int totalArms = arms;
        source.sendSuccess(() -> PolymerDiagonalWalls.text().of(
                "commands.polymerdiagonalwalls.stats.total", totalHolders, totalArms), false);
        return arms;
    }
}
