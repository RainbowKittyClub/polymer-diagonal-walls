package club.rainbowkitty.polymerdiagonalwalls.collision;

import java.util.List;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Where one family's client collision stands along a diagonal. Offsets are taken from the corner
 * point the two posts share, on each axis: an offset {@code t} on a diagonal running {@code dz}
 * along z is centred on {@code corner + (t, 0, dz * t)}.
 *
 * @param columnOffsets the centre of each shulker column; neighbours touch only at corners, and
 *        the outermost touch the post corners
 * @param columnWidth each shulker's box width, in blocks
 * @param shulkersPerColumn shulkers stacked in each column, each seated on the one below
 */
public record ColumnLayout(List<Double> columnOffsets, double columnWidth, int shulkersPerColumn) {
    // Open, a shulker's box is its scaled height times 1 + peek (Shulker.java:215, :264).
    private static final double OPEN_HEIGHT_FACTOR = 2;

    public ColumnLayout {
        columnOffsets = List.copyOf(columnOffsets);
        if (shulkersPerColumn < 0) {
            throw new IllegalArgumentException(
                    "ColumnLayout.shulkersPerColumn must be >= 0, got " + shulkersPerColumn);
        }
        requireScale("columnWidth", columnWidth, EntityTypes.SHULKER);
    }

    /** Whether this layout puts nothing on a diagonal. */
    public boolean isEmpty() {
        return columnOffsets.isEmpty() || shulkersPerColumn == 0;
    }

    /** The scale attribute that makes a shulker {@link #columnWidth} wide. */
    public double shulkerScale() {
        return columnWidth / EntityTypes.SHULKER.getWidth();
    }

    /** How much height one open shulker adds to a column, and so the step between seats. */
    public double shulkerHeight() {
        return shulkerScale() * EntityTypes.SHULKER.getHeight() * OPEN_HEIGHT_FACTOR;
    }

    // The scale attribute clamps silently, so a width outside its range would mis-size the box.
    private static void requireScale(String name, double width, EntityType<?> type) {
        double scale = width / type.getWidth();
        if (!(scale > 0) || Attributes.SCALE.value().sanitizeValue(scale) != scale) {
            throw new IllegalArgumentException("ColumnLayout." + name + " must give " + type
                    + " a scale inside the scale attribute's range, got width " + width);
        }
    }
}
