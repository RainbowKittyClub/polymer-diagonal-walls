package club.rainbowkitty.polymerdiagonalwalls.command;

import java.util.function.Predicate;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.PermissionLevel;

import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;
import club.rainbowkitty.polymerdiagonalwalls.collision.ColumnDebug;

/**
 * {@code /diagonals debug}: {@code columns} shows or hides the collision columns' shulkers and
 * slimes for everyone, so F3+B can draw their boxes, and returns the holders changed;
 * {@code paths} is {@link PathsDebugCommand}. Each child is gated on its own
 * {@code polymerdiagonalwalls.command.debug.<child>} node, op level 2 as fallback.
 */
public final class DebugCommand {
    private static final String KEY = "commands.polymerdiagonalwalls.debug.columns.";

    private DebugCommand() {
    }

    /** The {@code debug} literal, open to anyone who may run one of its children. */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        Predicate<CommandSourceStack> columnsGate = PolymerDiagonalWalls.permissions()
                .require("debug.columns", PermissionLevel.GAMEMASTERS);
        LiteralArgumentBuilder<CommandSourceStack> paths = PathsDebugCommand.node();
        return Commands.literal("debug")
                .requires(columnsGate.or(paths.getRequirement()))
                .then(Commands.literal("columns")
                        .requires(columnsGate)
                        .executes(DebugCommand::toggleColumns))
                .then(paths);
    }

    // Tells the whole op channel, since the change shows for every player.
    private static int toggleColumns(CommandContext<CommandSourceStack> context) {
        int holders = ColumnDebug.toggle();
        String state = ColumnDebug.visible() ? "shown" : "hidden";
        context.getSource().sendSuccess(
                () -> PolymerDiagonalWalls.text().of(KEY + state, holders), true);
        return holders;
    }
}
