package club.rainbowkitty.polymerdiagonalwalls.collision;

import java.util.List;

import net.minecraft.util.Mth;
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
 * @param columnHeight the height each column must reach; its lids open just far enough
 * @param slimeOffsets the centre of each ground slime, which pushes a player off the columns'
 *        corners
 * @param slimeWidth each slime's box width and height, in blocks
 */
public record ColumnLayout(
        List<Double> columnOffsets,
        double columnWidth,
        int shulkersPerColumn,
        double columnHeight,
        List<Double> slimeOffsets,
        double slimeWidth) {
    // A fully open lid, as a raw peek.
    private static final int OPEN_PEEK = 100;

    public ColumnLayout {
        columnOffsets = List.copyOf(columnOffsets);
        slimeOffsets = List.copyOf(slimeOffsets);
        if (shulkersPerColumn < 0) {
            throw new IllegalArgumentException(
                    "ColumnLayout.shulkersPerColumn must be >= 0, got " + shulkersPerColumn);
        }
        requireScale("columnWidth", columnWidth, EntityTypes.SHULKER);
        requireScale("slimeWidth", slimeWidth, EntityTypes.SLIME);
        double openStack = shulkersPerColumn * shulkerHeight(columnWidth, OPEN_PEEK);
        if (shulkersPerColumn > 0 && !(columnHeight > 0 && columnHeight <= openStack)) {
            throw new IllegalArgumentException("ColumnLayout.columnHeight must be in (0, "
                    + openStack + "], what the open shulkers reach, got " + columnHeight);
        }
    }

    /** Whether this layout puts nothing on a diagonal. */
    public boolean isEmpty() {
        return (columnOffsets.isEmpty() || shulkersPerColumn == 0) && slimeOffsets.isEmpty();
    }

    /** The scale attribute that makes a shulker {@link #columnWidth} wide. */
    public double shulkerScale() {
        return columnWidth / EntityTypes.SHULKER.getWidth();
    }

    /** The lowest raw peek, 0 to 100, at which a column reaches {@link #columnHeight}. */
    public int shulkerPeek() {
        for (int peek = 0; peek < OPEN_PEEK; peek++) {
            if (shulkersPerColumn * shulkerHeight(columnWidth, peek) >= columnHeight) {
                return peek;
            }
        }
        return OPEN_PEEK;
    }

    /** How much height one shulker adds to a column, and so the step between seats. */
    public double shulkerHeight() {
        return shulkerHeight(columnWidth, shulkerPeek());
    }

    /**
     * The scale attribute that makes a slime {@link #slimeWidth} wide. Riders keep the default
     * size, 1, whose box is the entity type's (AbstractCubeMob.java:75, :259).
     */
    public double slimeScale() {
        return slimeWidth / EntityTypes.SLIME.getWidth();
    }

    // A shulker's box height at a raw peek, in the client's float steps (Shulker.java:207, :264).
    private static double shulkerHeight(double width, int peek) {
        float size = (float) (width / EntityTypes.SHULKER.getWidth());
        float amount = peek * 0.01F;
        float physical = 0.5F - Mth.sin((0.5F + amount) * (float) Math.PI) * 0.5F;
        return size + (double) physical * size;
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
