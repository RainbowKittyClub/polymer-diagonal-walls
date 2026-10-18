package club.rainbowkitty.polymerdiagonalwalls.polymer;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.core.api.utils.PolymerClientDecoded;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import club.rainbowkitty.polymerdiagonalwalls.content.ExampleBlock;

/**
 * The Polymer face of {@link ExampleBlock}, instantiated only when polymer-core is present.
 *
 * <p>Per client: one running this mod (see {@link PolymerAware}) is sent the real block state and
 * renders it from the mod's own assets; everyone else is sent {@code clientState}, a polymer-blocks
 * pool state that the generated resource pack retextures to {@code block/example_block} with the
 * same full-cube collision. {@link PolymerClientDecoded} tells a Polymer client to keep the real
 * block rather than expect a virtual one.
 */
public class PolymerExampleBlock extends ExampleBlock
        implements PolymerTexturedBlock, PolymerClientDecoded {
    private final BlockState clientState;

    public PolymerExampleBlock(BlockBehaviour.Properties properties, BlockState clientState) {
        super(properties);
        this.clientState = clientState;
    }

    @Override
    public BlockState getPolymerBlockState(BlockState state, @Nullable PacketContext context) {
        return PolymerAware.has(context) ? state : clientState;
    }
}
