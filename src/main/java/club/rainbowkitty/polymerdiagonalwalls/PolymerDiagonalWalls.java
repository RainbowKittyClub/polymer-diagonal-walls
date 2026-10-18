package club.rainbowkitty.polymerdiagonalwalls;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import club.rainbowkitty.kalic0re.common.command.CommandPermissions;
import club.rainbowkitty.kalic0re.common.text.FallbackText;
import club.rainbowkitty.polymerdiagonalwalls.command.DiagonalsCommand;
import club.rainbowkitty.polymerdiagonalwalls.display.ArmIndex;
import club.rainbowkitty.polymerdiagonalwalls.wall.WallFamily;

/** Mod entrypoint: diagonal arms between walls, fences and panes standing corner to corner. */
public class PolymerDiagonalWalls implements ModInitializer {
    public static final String MOD_ID = "polymerdiagonalwalls";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static CommandPermissions permissions;
    private static FallbackText text;

    /** {@code polymerdiagonalwalls:<path>} identifier. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** This mod's command gate: nodes {@code polymerdiagonalwalls.command.<path>}. */
    public static CommandPermissions permissions() {
        return permissions;
    }

    /** This mod's player-facing text, each piece carrying its English from {@code en_us.json}. */
    public static FallbackText text() {
        return text;
    }

    @Override
    public void onInitialize() {
        // Assigned here, not in static initialisers, so a missing lang file fails with the helper's
        // own message instead of an ExceptionInInitializerError wrapped around it.
        permissions = CommandPermissions.forMod(MOD_ID);
        text = FallbackText.load(MOD_ID);
        WallFamily.register();
        ArmIndex.register();
        DiagonalsCommand.register();
    }
}
