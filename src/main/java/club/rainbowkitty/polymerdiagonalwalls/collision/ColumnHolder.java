package club.rainbowkitty.polymerdiagonalwalls.collision;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import eu.pb4.polymer.virtualentity.api.ElementHolder;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;

/**
 * The client collision of one owner's diagonals: every seat {@link ShulkerColumns} gives them.
 * Immutable once built; its {@link ColumnSite} builds and drops it. Only players
 * {@link ColumnRange} admits watch it.
 */
public final class ColumnHolder extends ElementHolder {
    private final List<Seat> seats;

    /** Builds every seat of {@code diagonals} around {@code origin}, the holder's position. */
    public ColumnHolder(List<Diagonal> diagonals, Vec3 origin) {
        List<Seat> built = new ArrayList<>();
        for (Diagonal diagonal : diagonals) {
            built.addAll(ShulkerColumns.seats(diagonal, origin, false));
        }
        this.seats = List.copyOf(built);
        for (Seat seat : this.seats) {
            addElement(seat.vehicle());
            addElement(seat.rider());
        }
    }

    // Polymer offers every player tracking the chunk; ColumnRange offers the rest later.
    @Override
    public boolean startWatching(ServerGamePacketListenerImpl connection) {
        if (!ColumnRange.admits(connection.getPlayer(), this)) {
            return false;
        }
        return super.startWatching(connection);
    }

    // After every spawn and data packet, so each vehicle exists before its rider boards.
    @Override
    protected void startWatchingExtraPackets(ServerGamePacketListenerImpl connection,
            Consumer<Packet<ClientGamePacketListener>> packets) {
        for (Seat seat : seats) {
            packets.accept(seat.scalePacket());
            packets.accept(seat.seatPacket());
        }
    }
}
