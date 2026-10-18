package club.rainbowkitty.polymerdiagonalwalls.display;

import java.util.List;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;

import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;

/**
 * The arms one owner draws, one per owned diagonal. Immutable once built: {@link ArmIndex}
 * replaces the holder instead of editing it, so a non-ticking holder never has to send a change.
 */
public final class ArmHolder extends ElementHolder {
    private final List<Diagonal> diagonals;

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
}
