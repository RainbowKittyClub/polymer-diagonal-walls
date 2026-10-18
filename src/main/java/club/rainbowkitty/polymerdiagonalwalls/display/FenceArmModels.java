package club.rainbowkitty.polymerdiagonalwalls.display;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.resourcepack.api.AssetPaths;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.resourcepack.extras.api.format.blockstate.BlockStateAsset;
import eu.pb4.polymer.resourcepack.extras.api.format.blockstate.StateModelVariant;
import eu.pb4.polymer.resourcepack.extras.api.format.item.ItemAsset;
import eu.pb4.polymer.resourcepack.extras.api.format.item.model.BasicItemModel;
import eu.pb4.polymer.resourcepack.extras.api.format.item.model.CompositeItemModel;
import eu.pb4.polymer.resourcepack.extras.api.format.item.model.ItemModel;
import eu.pb4.polymer.resourcepack.extras.api.format.model.ModelAsset;
import eu.pb4.polymer.resourcepack.extras.api.format.model.ModelElement;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;

/**
 * Post-less arms for fences that reach clients as themselves, generated into the Polymer pack from
 * each fence's own blockstate: one item definition per fence, a composite of the side parts its
 * arm state draws. An item definition can't turn a model the way a blockstate does, so a turned
 * part gets a copy of its model turned in place, uvlock included. Fences sent as a stand-in are
 * {@link BridgedArms}'.
 */
public final class FenceArmModels {
    private static final String PREFIX = "fence_arm/";
    // Model coordinates span one block in 16ths.
    private static final float BLOCK = 16f;
    // Deeper parent chains are taken as a loop.
    private static final int MAX_PARENTS = 16;

    // Written by the pack build, read on the server thread.
    private static volatile Set<Block> defined = Set.of();

    private FenceArmModels() {
    }

    /** Generates the arms into every main pack build. */
    public static void register() {
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT
                .register(FenceArmModels::build);
    }

    /** The item model of a fence's post-less arm: {@code fence_arm/<namespace>/<path>}. */
    public static Identifier itemModel(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return PolymerDiagonalWalls.id(PREFIX + id.getNamespace() + "/" + id.getPath());
    }

    /** Whether the last main pack built carries {@code block}'s post-less arm. Any thread. */
    public static boolean defines(Block block) {
        return defined.contains(block);
    }

    // One definition per fence the pack holds a blockstate for; a client mod's own fence has none.
    private static void build(ResourcePackBuilder builder) {
        Set<Block> built = new HashSet<>();
        Set<Identifier> turned = new HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            if (!(block instanceof FenceBlock)
                    || PolymerBlockUtils.getPolymerBlockState(state, null) != state) {
                continue;
            }
            try {
                if (addArm(builder, block, turned)) {
                    built.add(block);
                }
            } catch (RuntimeException e) {
                PolymerDiagonalWalls.LOGGER.warn("Arms of {} keep their post: unreadable assets",
                        BuiltInRegistries.BLOCK.getKey(block), e);
            }
        }
        defined = Set.copyOf(built);
        PolymerDiagonalWalls.LOGGER.info("Pack carries post-less arms for {} fences", built.size());
    }

    // Writes block's arm definition and any turned model it needs; false when it gets none.
    private static boolean addArm(
            ResourcePackBuilder builder, Block block, Set<Identifier> turned) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        String json = builder.getStringDataOrSource(
                "assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json");
        if (json == null) {
            return false;
        }
        BlockState state = block.defaultBlockState();
        List<ItemModel> models = new ArrayList<>();
        for (StateModelVariant part : Multipart.armParts(BlockStateAsset.fromJson(json),
                CrossArmVisual.barState(state, true), CrossArmVisual.barState(state, false))) {
            Identifier model = unturnedModel(builder, part, turned);
            if (model == null) {
                PolymerDiagonalWalls.LOGGER.warn("Arms of {} keep their post: no unturned copy "
                        + "of {} at x {}, y {}", id, part.model(), part.x(), part.y());
                return false;
            }
            models.add(new BasicItemModel(model));
        }
        if (models.isEmpty()) {
            PolymerDiagonalWalls.LOGGER.warn("Arms of {} keep their post: no side parts", id);
            return false;
        }
        ItemModel arm = models.size() == 1 ? models.getFirst() : new CompositeItemModel(models);
        builder.addData(AssetPaths.itemAsset(itemModel(block)), new ItemAsset(arm));
        return true;
    }

    // The model that draws part where its blockstate puts it, with no turn of its own: its own
    // model, or a half-turned copy written once. Null for any other turn; no vanilla side has one.
    private static @Nullable Identifier unturnedModel(
            ResourcePackBuilder builder, StateModelVariant part, Set<Identifier> turned) {
        if (part.x() != 0 || (part.y() != 0 && part.y() != 180)) {
            return null;
        }
        if (part.y() == 0) {
            return part.model();
        }
        Identifier model = part.model();
        Identifier copy = PolymerDiagonalWalls.id(PREFIX + model.getNamespace() + "/"
                + model.getPath() + (part.uvlock() ? "_y180_uvlock" : "_y180"));
        if (turned.contains(copy)) {
            return copy;
        }
        ModelAsset flat = flatten(builder, model);
        ModelAsset half = flat == null ? null : halfTurn(flat, part.uvlock());
        if (half == null) {
            return null;
        }
        builder.addData(AssetPaths.model(copy) + ".json", half);
        turned.add(copy);
        return copy;
    }

    // The model with its parents folded in: the nearest elements, every texture, child first.
    private static @Nullable ModelAsset flatten(ResourcePackBuilder builder, Identifier model) {
        Map<String, ModelAsset.TextureSlot> textures = new HashMap<>();
        List<ModelElement> elements = null;
        Identifier current = model;
        for (int depth = 0; current != null; depth++) {
            String json = builder.getStringDataOrSource(AssetPaths.model(current) + ".json");
            if (json == null || depth == MAX_PARENTS) {
                return null;
            }
            ModelAsset asset = ModelAsset.fromJson(json);
            asset.textures().forEach(textures::putIfAbsent);
            if (elements == null && asset.elements().isPresent()) {
                elements = asset.elements().get();
            }
            current = asset.parent().orElse(null);
        }
        if (elements == null) {
            return null;
        }
        return new ModelAsset(Optional.empty(), Optional.of(elements), textures);
    }

    // The model turned 180° about the block's vertical axis, as a blockstate's y 180 turns it.
    // Null when an element has a rotation of its own; no vanilla side has one.
    private static @Nullable ModelAsset halfTurn(ModelAsset model, boolean uvlock) {
        List<ModelElement> elements = new ArrayList<>();
        for (ModelElement element : model.elements().orElse(List.of())) {
            if (element.rotation().isPresent()) {
                return null;
            }
            Vec3 from = element.from();
            Vec3 to = element.to();
            Map<Direction, ModelElement.Face> faces = new EnumMap<>(Direction.class);
            element.faces().forEach((side, face) -> faces.put(turn(side),
                    new ModelElement.Face(turnUv(face.uv(), side, from, to, uvlock),
                            face.texture(), face.cullface().map(FenceArmModels::turn),
                            face.rotation(), face.tintIndex())));
            elements.add(new ModelElement(
                    new Vec3(BLOCK - to.x, from.y, BLOCK - to.z),
                    new Vec3(BLOCK - from.x, to.y, BLOCK - from.z),
                    faces, Optional.empty(), element.shade(), element.lightEmission()));
        }
        return new ModelAsset(Optional.empty(), Optional.of(elements), model.textures());
    }

    // A side's direction after the half turn: north and south swap, east and west swap.
    private static Direction turn(Direction side) {
        return side.getAxis() == Direction.Axis.Y ? side : side.getOpposite();
    }

    // A face's uv after the half turn. A side face's corners land on the matching corners of the
    // turned face, so its uv stays; a top or bottom face's corners land opposite, so its uv swaps
    // corners, and uvlock turns the texture back by mirroring both axes (BlockMath's face
    // transform for a y turn is a z turn of the uv).
    private static List<Float> turnUv(
            List<Float> uv, Direction side, Vec3 from, Vec3 to, boolean uvlock) {
        if (side.getAxis() != Direction.Axis.Y) {
            return uv;
        }
        List<Float> corners = uv.isEmpty() ? defaultUv(side, from, to) : uv;
        float u1 = corners.get(0);
        float v1 = corners.get(1);
        float u2 = corners.get(2);
        float v2 = corners.get(3);
        return uvlock
                ? List.of(BLOCK - u2, BLOCK - v2, BLOCK - u1, BLOCK - v1)
                : List.of(u2, v2, u1, v1);
    }

    // The uv vanilla gives a top or bottom face with none of its own (FaceBakery.defaultFaceUV).
    private static List<Float> defaultUv(Direction side, Vec3 from, Vec3 to) {
        return side == Direction.UP
                ? List.of((float) from.x, (float) from.z, (float) to.x, (float) to.z)
                : List.of((float) from.x, BLOCK - (float) to.z, (float) to.x,
                        BLOCK - (float) from.z);
    }
}
