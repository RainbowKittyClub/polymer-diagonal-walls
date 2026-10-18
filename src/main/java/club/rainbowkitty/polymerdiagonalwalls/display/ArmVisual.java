package club.rainbowkitty.polymerdiagonalwalls.display;

import java.util.List;

import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;

import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;

/**
 * How one family draws a diagonal's arm. Each family owns one, so the elements behind an arm
 * (block displays now, item displays for a modded block) can change per family without touching
 * {@link ArmHolder} or {@link ArmIndex}.
 */
public interface ArmVisual {
    /**
     * Fresh elements for {@code diagonal}'s arm, each offset from {@code origin}, the position of
     * the holder they join.
     */
    public List<VirtualElement> elements(Diagonal diagonal, Vec3 origin);
}
