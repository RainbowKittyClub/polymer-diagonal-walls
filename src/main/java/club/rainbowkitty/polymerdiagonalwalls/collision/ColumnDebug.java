package club.rainbowkitty.polymerdiagonalwalls.collision;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Whether column riders are drawn. A client draws no F3+B box for an invisible entity
 * ({@code EntityHitboxDebugRenderer.java:38}), so showing them is how an op sees the columns.
 * Server-wide, and off again on every server start. Server thread only.
 */
public final class ColumnDebug {
    private static boolean visible;

    private ColumnDebug() {
    }

    /** Hides the riders again whenever the server stops. */
    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> visible = false);
    }

    /** Whether riders are currently drawn. */
    public static boolean visible() {
        return visible;
    }

    /**
     * Flips whether riders are drawn, for every built holder and every one built later.
     *
     * @return the holders changed
     */
    public static int toggle() {
        visible = !visible;
        return ColumnRange.forEachBuilt(holder -> holder.setVisible(visible));
    }
}
