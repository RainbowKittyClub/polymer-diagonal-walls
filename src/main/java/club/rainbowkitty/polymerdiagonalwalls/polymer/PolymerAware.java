package club.rainbowkitty.polymerdiagonalwalls.polymer;

import eu.pb4.polymer.networking.api.PolymerNetworking;
import eu.pb4.polymer.networking.api.server.PolymerServerNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;

/**
 * Per-connection "does this client run this mod" flag, from Polymer's networking handshake.
 *
 * <p>{@link #register()} (called from both sides in {@code onInitialize}) adds {@link #HANDSHAKE}
 * to the packet set the client advertises during the handshake; the server then reports a version
 * {@code >= 0} for connections whose client also loaded this mod. {@link PolymerExampleBlock} uses
 * that to send real content vs a substitute. The packet carries nothing and is never sent — the id
 * exists only as a capability marker.
 */
public final class PolymerAware {
    /** Handshake capability id: {@code polymerdiagonalwalls:handshake}. */
    public static final CustomPacketPayload.Type<Payload> HANDSHAKE =
            PolymerNetworking.id(PolymerDiagonalWalls.MOD_ID, "handshake");

    private PolymerAware() {}

    /** Registers {@link #HANDSHAKE}. Call from {@code onInitialize} (runs on both sides). */
    public static void register() {
        PolymerNetworking.registerClientboundVersioned(
                HANDSHAKE, 1, StreamCodec.unit(new Payload()));
    }

    /** Whether this connection's client also loaded this mod. */
    public static boolean has(ServerGamePacketListenerImpl handler) {
        return PolymerServerNetworking.getSupportedVersion(handler, HANDSHAKE.id()) >= 0;
    }

    /** Whether the context's client also loaded this mod; {@code false} for a null context. */
    public static boolean has(@Nullable PacketContext context) {
        if (context == null) {
            return false;
        }
        return PolymerNetworking.getSupportedVersion(
                context.get(PacketContext.CONNECTION), HANDSHAKE.id()) >= 0;
    }

    /** Empty handshake marker payload. */
    public record Payload() implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return HANDSHAKE;
        }
    }
}
