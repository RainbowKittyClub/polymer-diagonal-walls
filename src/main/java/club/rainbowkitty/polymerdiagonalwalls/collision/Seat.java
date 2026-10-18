package club.rainbowkitty.polymerdiagonalwalls.collision;

import java.util.List;

import eu.pb4.polymer.virtualentity.api.VirtualEntityUtils;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.GenericEntityElement;

import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * One client-only mob riding its own empty block display, which pins it exactly at the seat: a
 * passenger sits at a display's position, and nothing moves either one. Polymer sends no
 * attributes, so the seat carries the rider's scale as a packet of its own.
 *
 * @param vehicle the empty display at the seat
 * @param rider the mob on it
 * @param seatPacket seats {@code rider} on {@code vehicle}
 * @param scalePacket gives {@code rider} its scale
 */
public record Seat(
        BlockDisplayElement vehicle,
        GenericEntityElement rider,
        ClientboundSetPassengersPacket seatPacket,
        ClientboundUpdateAttributesPacket scalePacket) {
    /**
     * Seats {@code rider} at {@code seat}. It spawns at {@code spawn} and moves to the seat on the
     * client's first tick of it; both positions are absolute, the elements' offsets taken from
     * {@code origin}, their holder's position.
     */
    public static Seat of(GenericEntityElement rider, Vec3 seat, Vec3 spawn, Vec3 origin,
            double scale, boolean visible) {
        rider.setOffset(spawn.subtract(origin));
        rider.setInvisible(!visible);
        rider.setSilent(true);
        rider.setNoGravity(true);
        // The vehicle places it from then on, so no move of the rider's own is ever wanted.
        rider.ignorePositionUpdates();

        BlockDisplayElement vehicle = new BlockDisplayElement();
        vehicle.setOffset(seat.subtract(origin));
        vehicle.ignorePositionUpdates();

        AttributeInstance scaleAttribute = new AttributeInstance(Attributes.SCALE, changed -> {
        });
        scaleAttribute.setBaseValue(scale);
        int riderId = rider.getEntityId();
        return new Seat(vehicle, rider,
                VirtualEntityUtils.createClientboundSetPassengersPacket(
                        vehicle.getEntityId(), new int[] {riderId}),
                new ClientboundUpdateAttributesPacket(riderId, List.of(scaleAttribute)));
    }
}
