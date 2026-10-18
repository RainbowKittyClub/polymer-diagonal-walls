package club.rainbowkitty.polymerdiagonalwalls.display;

import java.util.List;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.HolderAttachment;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import org.jspecify.annotations.Nullable;

import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.collision.ColumnRange;
import club.rainbowkitty.polymerdiagonalwalls.collision.ColumnSite;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;

/**
 * The arms one owner draws, one per owned diagonal. Immutable once built: {@link ArmIndex}
 * replaces the holder instead of editing it, so a non-ticking holder never has to send a change.
 *
 * <p>While attached to a chunk it also stands for the arms' client collision, a
 * {@link ColumnSite} that {@link ColumnRange} serves to nearby players. Losing the attachment,
 * whether by {@link #destroy} or a chunk unload, removes the site and its columns.
 */
public final class ArmHolder extends ElementHolder {
    private final List<Diagonal> diagonals;
    private @Nullable ColumnSite columnSite;

    /** Builds every arm's elements around {@code origin}, the position the holder attaches at. */
    public ArmHolder(List<Diagonal> diagonals, Vec3 origin) {
        this.diagonals = List.copyOf(diagonals);
        for (Diagonal diagonal : this.diagonals) {
            for (VirtualElement element : diagonal.family().visual().elements(diagonal, origin)) {
                addElement(element);
            }
        }
    }

    /** The diagonals this holder draws, in the order {@code DiagonalRule.owned} gave them. */
    public List<Diagonal> diagonals() {
        return diagonals;
    }

    /** Whether the holder is still attached; Polymer detaches every holder of an unloaded chunk. */
    public boolean isAttached() {
        return getAttachment() != null;
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
}
