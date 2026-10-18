package club.rainbowkitty.polymerdiagonalwalls.collision;

import java.util.ArrayList;
import java.util.List;

import eu.pb4.polymer.virtualentity.api.elements.SimpleEntityElement;

import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;
import club.rainbowkitty.polymerdiagonalwalls.mixin.ShulkerAccessor;

/**
 * A diagonal's client collision: columns of invisible shulkers, which a vanilla client treats as
 * solid, standing where the family's {@link ColumnLayout} puts them and open as far as it says.
 */
public final class ShulkerColumns {
    // A free shulker snaps to (floor(x) + 0.5, floor(y + 0.5), floor(z) + 0.5), Shulker.java:334.
    // Spawned half a block low it lands in its seat's block, so the move onto the seat keeps the
    // lid open: the client closes it whenever that block changes after a tick (:337).
    private static final double SPAWN_DROP = 0.5;

    private ShulkerColumns() {
    }

    /**
     * The seats of every shulker in {@code diagonal}'s columns, bottom up per column, offset from
     * {@code origin}. {@code visible} shows the riders, for F3+B.
     */
    public static List<Seat> seats(Diagonal diagonal, Vec3 origin, boolean visible) {
        ColumnLayout layout = diagonal.family().columns();
        Vec3 corner = diagonal.corner();
        byte peek = (byte) layout.shulkerPeek();
        double step = layout.shulkerHeight();
        List<Seat> seats = new ArrayList<>();
        for (double offset : layout.columnOffsets()) {
            Vec3 base = corner.add(offset, 0, diagonal.dz() * offset);
            for (int index = 0; index < layout.shulkersPerColumn(); index++) {
                Vec3 seat = base.add(0, index * step, 0);
                seats.add(shulker(seat, origin, layout.shulkerScale(), peek, visible));
            }
        }
        return seats;
    }

    // One shulker on its seat, its lid at peek.
    private static Seat shulker(
            Vec3 seat, Vec3 origin, double scale, byte peek, boolean visible) {
        SimpleEntityElement shulker = new SimpleEntityElement(EntityTypes.SHULKER);
        shulker.getSyncedData().set(ShulkerAccessor.polymerdiagonalwalls$peek(), peek);
        return Seat.of(shulker, seat, seat.subtract(0, SPAWN_DROP, 0), origin, scale, visible);
    }
}
