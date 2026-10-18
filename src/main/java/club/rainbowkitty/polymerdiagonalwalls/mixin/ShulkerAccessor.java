package club.rainbowkitty.polymerdiagonalwalls.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Shulker;

/**
 * Reaches the shulker's protected lid key, so a virtual shulker can be sent fully open. Its value
 * is the raw peek, 0 to 100.
 */
@Mixin(Shulker.class)
public interface ShulkerAccessor {
    /** {@code Shulker.DATA_PEEK_ID}, the synced lid opening. */
    @Accessor("DATA_PEEK_ID")
    static EntityDataAccessor<Byte> polymerdiagonalwalls$peek() {
        throw new AssertionError("ShulkerAccessor was not applied");
    }
}
