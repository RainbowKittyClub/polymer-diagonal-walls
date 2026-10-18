package club.rainbowkitty.polymerdiagonalwalls.display;

import java.util.List;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import org.jspecify.annotations.Nullable;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.collision.ColumnRange;
import club.rainbowkitty.polymerdiagonalwalls.collision.ColumnSite;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;
import club.rainbowkitty.polymerdiagonalwalls.settings.DiagonalSettings;

/**
 * The arms one owner draws, one per owned diagonal. Immutable once built apart from its view
 * range: {@link ArmIndex} replaces the holder instead of editing it. Players whose
 * {@code show_arms} is off never watch it.
 *
 * <p>While attached to a chunk it also stands for the arms' client collision, a
 * {@link ColumnSite} that {@link ColumnRange} serves to nearby players. Losing the attachment,
 * whether by {@link #destroy} or a chunk unload, removes the site and its columns.
 */
public final class ArmHolder extends ElementHolder {
    private final List<Diagonal> diagonals;
    private @Nullable ColumnSite columnSite;

    /**
     * Builds every arm's elements around {@code origin}, the position the holder attaches at.
     *
     * @param viewRange each display's view range, in units of 64 blocks
     */
    public ArmHolder(List<Diagonal> diagonals, Vec3 origin, float viewRange) {
        this.diagonals = List.copyOf(diagonals);
        for (Diagonal diagonal : this.diagonals) {
            for (VirtualElement element : diagonal.family().visual().elements(diagonal, origin)) {
                addElement(element);
            }
        }
        applyViewRange(viewRange);
    }

    /** The diagonals this holder draws, in the order {@code DiagonalRule.owned} gave them. */
    public List<Diagonal> diagonals() {
        return diagonals;
    }

    /** Whether the holder is still attached; Polymer detaches every holder of an unloaded chunk. */
    public boolean isAttached() {
        return getAttachment() != null;
    }

    /**
     * Sets every display's view range and sends it to the current watchers.
     *
     * @param viewRange the view range, in units of 64 blocks
     */
    public void setViewRange(float viewRange) {
        applyViewRange(viewRange);
        // A non-ticking holder sends element data only from tick(), which moves nothing here.
        tick();
    }

    // Polymer offers every player tracking the chunk; a player who hides arms is refused.
    @Override
    public boolean startWatching(ServerGamePacketListenerImpl connection) {
        return DiagonalSettings.showsArms(connection.getPlayer())
                && super.startWatching(connection);
    }

    @Override
    protected void onAttachmentSet(HolderAttachment attachment,
            @Nullable HolderAttachment oldAttachment) {
        if (columnSite == null && attachment instanceof ChunkAttachment chunkAttachment) {
            columnSite = ColumnRange.addSite(attachment.getWorld(), chunkAttachment.getChunk(),
                    attachment.getPos(), diagonals);
        }
    }

    @Override
    protected void onAttachmentRemoved(HolderAttachment oldAttachment) {
        if (columnSite != null) {
            ColumnRange.removeSite(columnSite);
            columnSite = null;
        }
    }

    // Sets the view range on every display element, sending nothing.
    private void applyViewRange(float viewRange) {
        for (VirtualElement element : getElements()) {
            if (element instanceof DisplayElement display) {
                display.setViewRange(viewRange);
            }
        }
    }
}
