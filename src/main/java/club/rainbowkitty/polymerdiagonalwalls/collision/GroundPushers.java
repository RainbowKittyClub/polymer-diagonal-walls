package club.rainbowkitty.polymerdiagonalwalls.collision;

import java.util.ArrayList;
import java.util.List;

import eu.pb4.polymer.virtualentity.api.elements.SimpleEntityElement;

import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;

/**
 * Invisible frogs on the ground beside a diagonal's columns. A frog is not solid to the client,
 * but each tick the client's frog pushes the local player out of its box (LivingEntity.java:3209,
 * EntitySelector.java:38, Entity.java:1882), which eases a player sliding along the arm off the
 * columns' corners. Frogs are allowed in Peaceful, so a Peaceful client builds them too
 * (EntityType.java:298).
 */
public final class GroundPushers {
    private GroundPushers() {
    }

    /**
     * The seats of {@code diagonal}'s pushers, standing on the diagonal's ground, offset from
     * {@code origin}. {@code visible} shows them, for F3+B.
     */
    public static List<Seat> seats(Diagonal diagonal, Vec3 origin, boolean visible) {
        ColumnLayout layout = diagonal.family().columns();
        Vec3 corner = diagonal.corner();
        List<Seat> seats = new ArrayList<>();
        for (double offset : layout.pusherOffsets()) {
            Vec3 seat = corner.add(offset, 0, diagonal.dz() * offset);
            SimpleEntityElement pusher = new SimpleEntityElement(EntityTypes.FROG);
            seats.add(Seat.of(pusher, seat, seat, origin, layout.pusherScale(), visible));
        }
        return seats;
    }
}
