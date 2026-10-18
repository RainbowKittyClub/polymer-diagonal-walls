package club.rainbowkitty.polymerdiagonalwalls.command;

import java.util.List;
import java.util.function.Predicate;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * {@code /diagonals}, the root every subcommand hangs off. Each subcommand carries its own
 * permission gate, and the root shows to a source that passes any of them.
 */
public final class DiagonalsCommand {
    private DiagonalsCommand() {
    }

    /** Hooks command registration. */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            List<LiteralArgumentBuilder<CommandSourceStack>> subcommands = List.of(
                    ProbeCommand.node(),
                    RebuildCommand.node(),
                    StatsCommand.node(),
                    DebugCommand.node());
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("diagonals")
                    .requires(anyOf(subcommands));
            subcommands.forEach(root::then);
            dispatcher.register(root);
        });
    }

    // Passes a source that some subcommand would let in.
    private static Predicate<CommandSourceStack> anyOf(
            List<LiteralArgumentBuilder<CommandSourceStack>> subcommands) {
        return source -> subcommands.stream().anyMatch(sub -> sub.getRequirement().test(source));
    }
}
