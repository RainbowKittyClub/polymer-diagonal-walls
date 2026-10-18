package club.rainbowkitty.polymerdiagonalwalls;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import club.rainbowkitty.polymerdiagonalwalls.content.ExampleBlock;

/** Mod entrypoint. **/
public class PolymerDiagonalWalls implements ModInitializer {
    public static final String MOD_ID = "polymerdiagonalwalls";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** {@code polymerdiagonalwalls:<path>} identifier. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        // Example content demonstrating client-optional Polymer: a real block for clients running
        // this mod, a Polymer substitute for everyone else. Delete the content/ and polymer/
        // packages plus the example_block assets/data once you have your own content, or build on
        // it. See README "Polymer: client-optional content".
        if (FabricLoader.getInstance().isModLoaded("polymer-core")) {
            club.rainbowkitty.polymerdiagonalwalls.polymer.PolymerIntegration.register();
        } else {
            ExampleBlock.registerPlain();
        }
    }
}
