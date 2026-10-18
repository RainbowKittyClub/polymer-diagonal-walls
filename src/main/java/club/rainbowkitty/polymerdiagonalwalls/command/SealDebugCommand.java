package club.rainbowkitty.polymerdiagonalwalls.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.PermissionLevel;

import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;
import club.rainbowkitty.polymerdiagonalwalls.collision.GapSeal;

/**
 * {@code /diagonals debug seal <on|boxes|off>}: seals diagonal gaps for non-players with corner
 * boxes and pathfinding ({@code on}), with corner boxes alone ({@code boxes}), or not at all, so
 * a mob check can run against each. Gated on {@code polymerdiagonalwalls.command.debug.seal}, op
 * level 2 as fallback; {@link DebugCommand} holds it.
 */
public final class SealDebugCommand {
    private static final String KEY = "commands.polymerdiagonalwalls.debug.seal.";

    private SealDebugCommand() {
    }

    /** The {@code seal} literal, gate included. */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("seal")
                .requires(PolymerDiagonalWalls.permissions()
                        .require("debug.seal", PermissionLevel.GAMEMASTERS))
                .then(mode("on", true, true))
                .then(mode("boxes", true, false))
                .then(mode("off", false, false));
    }

    // One mode literal under seal.
    private static LiteralArgumentBuilder<CommandSourceStack> mode(
            String name, boolean boxes, boolean paths) {
        return Commands.literal(name).executes(context -> run(context, name, boxes, paths));
    }

    // Sets both switches and tells ops too, since the change reaches every mob.
    private static int run(
            CommandContext<CommandSourceStack> context, String name, boolean boxes, boolean paths) {
        GapSeal.set(boxes, paths);
        context.getSource().sendSuccess(
                () -> PolymerDiagonalWalls.text().of(KEY + name), true);
        return 1;
    }
}
