package club.rainbowkitty.polymerdiagonalwalls.content;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;

/**
 * Reference example — a plain, Polymer-free block with a real shape and hitbox. Its Polymer face
 * ({@code polymer.PolymerExampleBlock}) is a subclass used only when polymer-core is present, so
 * nothing here drags a Polymer class onto a Polymer-less server.
 *
 * <p>Delete this package, {@code polymer.PolymerExampleBlock}, the branch in the mod initializer,
 * and the {@code example_block} assets/data once you have your own content — or rename and build
 * on it.
 */
public class ExampleBlock extends Block {
    /** {@code polymerdiagonalwalls:example_block}. */
    public static final Identifier ID = PolymerDiagonalWalls.id("example_block");
    /** Registry key the block is registered and given its settings under. */
    public static final ResourceKey<Block> KEY = ResourceKey.create(Registries.BLOCK, ID);

    private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, ID);

    public ExampleBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** A fresh stone-like {@code Properties} carrying this block's id. */
    public static BlockBehaviour.Properties blockSettings() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).setId(KEY);
    }

    /** A fresh {@code Item.Properties} for this block's item. */
    public static Item.Properties itemSettings() {
        return new Item.Properties().useBlockDescriptionPrefix().setId(ITEM_KEY);
    }

    /** Registers the block; {@code factory} picks the concrete class per environment. */
    public static Block registerBlock(Function<BlockBehaviour.Properties, Block> factory) {
        return Registry.register(BuiltInRegistries.BLOCK, KEY, factory.apply(blockSettings()));
    }

    /** Registers this block's item. */
    public static void registerItem(Item item) {
        Registry.register(BuiltInRegistries.ITEM, ITEM_KEY, item);
    }

    /** Polymer-less path: a real block and a plain block item, invisible to unmodded clients. */
    public static void registerPlain() {
        Block block = registerBlock(ExampleBlock::new);
        registerItem(new BlockItem(block, itemSettings()));
    }
}
