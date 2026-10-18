package club.rainbowkitty.polymerdiagonalwalls.polymer;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;
import club.rainbowkitty.polymerdiagonalwalls.content.ExampleBlock;

/**
 * Everything that touches Polymer. The mod initializer calls this only when polymer-core is
 * loaded, so no Polymer class is referenced on a Polymer-less server. polymer-resource-pack and
 * polymer-blocks ride along inside polymer-bundled; split the {@code isModLoaded} guard if a mod
 * ever depends on them separately.
 */
public final class PolymerIntegration {
    private PolymerIntegration() {}

    /** Registers the Polymer example block, its fallback item, and the mod's pack assets. */
    public static void register() {
        PolymerAware.register();

        // A pool block state whose model the generated resource pack overrides with
        // block/example_block. Null only if the FULL_BLOCK pool is exhausted.
        BlockState clientState = PolymerBlockResourceUtils.requestBlock(
                BlockModelType.FULL_BLOCK,
                PolymerBlockModel.of(PolymerDiagonalWalls.id("block/example_block")));
        if (clientState == null) {
            clientState = Blocks.STONE.defaultBlockState();
            PolymerDiagonalWalls.LOGGER.warn(
                    "polymer-blocks FULL_BLOCK pool exhausted; example_block shows as stone");
        }

        final BlockState fallback = clientState;
        Block block = ExampleBlock.registerBlock(
                props -> new PolymerExampleBlock(props, fallback));
        // Vanilla clients see a stone item rather than an item id they don't know.
        ExampleBlock.registerItem(
                new PolymerBlockItem(block, ExampleBlock.itemSettings(), Items.STONE, true));

        // Ship this mod's assets into Polymer's generated pack. Deliberately NOT markAsRequired():
        // clients that decline just see the fallback block, and clients running this mod never need
        // the pack. Whether the pack is pushed or required is the server's autohost config.
        PolymerResourcePackUtils.addModAssets(PolymerDiagonalWalls.MOD_ID);
    }
}
