package club.rainbowkitty.polymerdiagonalwalls.display;

import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;

/**
 * The placement every block-display arm shares: a model whose bar runs along z, turned 45° about
 * the corner point between the two posts and stretched along its length so both ends hide in the
 * posts. The geometry is the plan's "Reference: Arm geometry".
 */
public final class ArmTransform {
    /** Shrinks an arm across and lifts it, so no face is coplanar with a post's or the ground's. */
    public static final float EPSILON = 1f / 512f;
    /** The y scale of an arm whose model ends below the post top, such as a low wall side. */
    public static final float LOW_HEIGHT = 1 - EPSILON;
    /**
     * The y scale of an arm whose model runs to the post top. Its top ends ε above the post top,
     * where a stacked arm's bottom starts, so it neither z-fights the post nor leaves a gap.
     */
    public static final float FULL_HEIGHT = 1;

    // Toward the owner on each axis, so the entity's light cell is the owner, never opaque.
    private static final float NUDGE = 1f / 16f;
    private static final float CULL_WIDTH = 1.25f;
    // A full-height arm reaches 1 + ε.
    private static final float CULL_HEIGHT = 1.0625f;
    private static final float EIGHTH_TURN = (float) (Math.PI / 4);

    private ArmTransform() {
    }

    /**
     * Places {@code arm} on {@code diagonal}, offset from {@code origin}, the position of the
     * holder it joins.
     *
     * @param length the model's z scale, the arm's length in blocks
     * @param height the model's y scale: {@link #LOW_HEIGHT} or {@link #FULL_HEIGHT}
     */
    public static void apply(
            BlockDisplayElement arm, Diagonal diagonal, Vec3 origin, float length, float height) {
        int dz = diagonal.dz();
        Vec3 entityPosition = diagonal.corner().add(-NUDGE, 0, -dz * NUDGE);

        // p -> T + R(theta) S (p - c). JOML's rotationY sends +z to (sin, cos), so +45° runs
        // south-east and -45° along the north-east line.
        Quaternionf rotation = new Quaternionf().rotationY(dz * EIGHTH_TURN);
        Vector3f scale = new Vector3f(1 - EPSILON, height, length);
        Vector3f scaledCentre = new Vector3f(0.5f * scale.x, 0, 0.5f * scale.z);
        Vector3f translation = new Vector3f(NUDGE, EPSILON, dz * NUDGE)
                .sub(rotation.transform(scaledCentre));

        arm.setOffset(entityPosition.subtract(origin));
        arm.setTranslation(translation);
        arm.setLeftRotation(rotation);
        arm.setScale(scale);
        arm.setDisplaySize(CULL_WIDTH, CULL_HEIGHT);
    }
}
