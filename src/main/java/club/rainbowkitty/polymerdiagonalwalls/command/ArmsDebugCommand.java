package club.rainbowkitty.polymerdiagonalwalls.command;

import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import eu.pb4.polymer.virtualentity.api.elements.AbstractElement;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.permissions.PermissionLevel;

import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;
import club.rainbowkitty.polymerdiagonalwalls.display.ArmHolder;
import club.rainbowkitty.polymerdiagonalwalls.display.ArmIndex;
import club.rainbowkitty.polymerdiagonalwalls.display.PackAudience;

/**
 * {@code /diagonals debug arms <pos>}: the elements that draw the arms {@code pos} owns, each a
 * block display with its state or an item display with its item model, its view range, and for a
 * fence arm whether players with the pack or without it are sent it.
 * Returns the element count. Gated on {@code polymerdiagonalwalls.command.debug.arms}, op level 2
 * as fallback; {@link DebugCommand} holds it.
 */
public final class ArmsDebugCommand {
    private static final String KEY = "commands.polymerdiagonalwalls.debug.arms.";

    private ArmsDebugCommand() {
    }

    /** The {@code arms} literal, gate included. */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("arms")
                .requires(PolymerDiagonalWalls.permissions()
                        .require("debug.arms", PermissionLevel.GAMEMASTERS))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ArmsDebugCommand::run));
    }

    // Reads the holder's own elements, so the output is what watchers are sent.
    private static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos pos = BlockPosArgument.getBlockPos(context, "pos");
        ArmHolder holder = ArmIndex.holderAt(source.getLevel(), pos);
        if (holder == null || !holder.isAttached()) {
            source.sendFailure(text("none", pos.getX(), pos.getY(), pos.getZ()));
            return 0;
        }
        List<VirtualElement> elements = holder.getElements();
        source.sendSuccess(() -> text("owns", pos.getX(), pos.getY(), pos.getZ(),
                holder.diagonals().size(), elements.size()), false);
        for (VirtualElement element : elements) {
            MutableComponent line = switch (element) {
                case ItemDisplayElement item -> text("item",
                        String.valueOf(item.getItem().get(DataComponents.ITEM_MODEL)),
                        item.getViewRange());
                case BlockDisplayElement block -> text("block",
                        block.getBlockState().toString(), block.getViewRange());
                default -> text("other", element.getClass().getSimpleName());
            };
            if (element instanceof AbstractElement abstractElement
                    && abstractElement.getVisibilityPredicate() instanceof PackAudience audience) {
                line.append(text(audience.withPack() ? "with_pack" : "without_pack"));
            }
            source.sendSuccess(() -> line, false);
        }
        return elements.size();
    }

    // This command's text under KEY, with its English as fallback.
    private static MutableComponent text(String key, Object... args) {
        return PolymerDiagonalWalls.text().of(KEY + key, args);
    }
}
