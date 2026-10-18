package club.rainbowkitty.polymerdiagonalwalls.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.PermissionLevel;

import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;
import club.rainbowkitty.polymerdiagonalwalls.collision.GapSeal;

/**
 * {@code /diagonals debug paths <on|off>}: whether walking mobs route around sealed gaps, so a
 * pathfinding check can run against sealed gaps both ways. Gated on
 * {@code polymerdiagonalwalls.command.debug.paths}, op level 2 as fallback; {@link DebugCommand}
 * holds it.
 */
public final class PathsDebugCommand {
    private static final String KEY = "commands.polymerdiagonalwalls.debug.paths.";

    private PathsDebugCommand() {
    }

    /** The {@code paths} literal, gate included. */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("paths")
                .requires(PolymerDiagonalWalls.permissions()
                        .require("debug.paths", PermissionLevel.GAMEMASTERS))
                .then(mode("on", true))
                .then(mode("off", false));
    }

    // One mode literal under paths.
    private static LiteralArgumentBuilder<CommandSourceStack> mode(String name, boolean paths) {
        return Commands.literal(name).executes(context -> run(context, name, paths));
    }

    // Sets the switch and tells ops too, since the change reaches every mob.
    private static int run(CommandContext<CommandSourceStack> context, String name,
            boolean paths) {
        GapSeal.setPaths(paths);
        context.getSource().sendSuccess(
                () -> PolymerDiagonalWalls.text().of(KEY + name), true);
        return 1;
    }
}
