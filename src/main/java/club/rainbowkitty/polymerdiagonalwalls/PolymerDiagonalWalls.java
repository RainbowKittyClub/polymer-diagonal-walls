package club.rainbowkitty.polymerdiagonalwalls;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

/** Mod entrypoint: diagonal arms between walls, fences and panes standing corner to corner. */
public class PolymerDiagonalWalls implements ModInitializer {
    public static final String MOD_ID = "polymerdiagonalwalls";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** {@code polymerdiagonalwalls:<path>} identifier. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
    }
}
