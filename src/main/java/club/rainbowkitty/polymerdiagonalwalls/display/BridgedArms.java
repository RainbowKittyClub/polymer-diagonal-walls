package club.rainbowkitty.polymerdiagonalwalls.display;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import eu.pb4.polymer.core.api.block.PolymerBlockUtils;
import eu.pb4.polymer.resourcepack.extras.api.ResourcePackExtras;
import eu.pb4.polymer.resourcepack.extras.api.format.blockstate.BlockStateAsset;
import eu.pb4.polymer.resourcepack.extras.api.format.blockstate.StateModelVariant;
import eu.pb4.polymer.virtualentity.api.BlockWithElementHolder;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
//? if <26.3
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import club.rainbowkitty.polymerdiagonalwalls.PolymerDiagonalWalls;
import club.rainbowkitty.polymerdiagonalwalls.diagonal.Diagonal;

/**
 * Arms for modded walls, fences and bars that Polymer sends as a vanilla stand-in, drawn the way
 * the patch mods draw the block itself: one item display per side part of the block's own
 * multipart blockstate, showing the model the patch bridged as an item,
 * {@code <namespace>:-/block/<model>}. A block display of such a state would show the stand-in.
 * Server thread only.
 */
public final class BridgedArms {
    // Each patch mod's overlays share one view range, and only these bridge their models.
    private static final Map<String, Float> OVERLAY_VIEW_RANGES = Map.of(
            "polymer-patch-bundle", 3f,
            "enderscape-polymer-patch", 100f);
    // polymer-patch-bundle's block colours (Blockus's foliage hedges), or null without it.
    private static final @Nullable MethodHandle PATCH_COLOR = findPatchColor();

    private static final Map<BlockState, Optional<Arm>> ARMS = new IdentityHashMap<>();
    private static final Map<Block, Optional<BlockStateAsset>> BLOCKSTATES =
            new IdentityHashMap<>();
    private static final Set<Block> WARNED = new HashSet<>();

    private BridgedArms() {
    }

    /**
     * Item displays drawing {@code diagonal}'s arm from the parts that {@code armState} shows and
     * {@code postState} does not, or an empty list when the owner reaches clients as itself, its
     * overlay comes from no patch mod listed here, or its blockstate can't be read.
     *
     * @param armState the owner joined north and south, the state the arm draws
     * @param postState the owner joined nowhere, whose parts (its post) the arm leaves out, or null
     *        to keep every part
     * @param length the arm's length in blocks
     * @param height the arm's y scale, as {@link ArmTransform#apply} takes it
     */
    public static List<VirtualElement> elements(Diagonal diagonal, Vec3 origin,
            BlockState armState, @Nullable BlockState postState, float length, float height) {
        Optional<Arm> arm = ARMS.computeIfAbsent(armState, key -> resolve(armState, postState));
        if (arm.isEmpty()) {
            return List.of();
        }
        List<VirtualElement> elements = new ArrayList<>();
        List<Part> parts = arm.get().parts();
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            ItemDisplayElement element = new ItemDisplayElement(part.stack().copy());
            ArmTransform.applyPart(element, diagonal, origin, length, height, part.rotation(), i,
                    arm.get().viewRange());
            elements.add(element);
        }
        return elements;
    }

    /**
     * Tints the item displays of {@code diagonal}'s arm as polymer-patch-bundle tints its owner's
     * own overlay: by the owner's colour at its position in {@code chunk}. Blocks without one stay
     * as they are.
     */
    public static void tint(List<VirtualElement> elements, LevelChunk chunk, Diagonal diagonal) {
        if (PATCH_COLOR == null) {
            return;
        }
        int color;
        try {
            color = (int) PATCH_COLOR.invokeExact(diagonal.westState(), chunk, diagonal.west());
        } catch (Throwable e) {
            warnOnce(diagonal.westState().getBlock(), "its patch colour failed: " + e);
            return;
        }
        if (color == -1) {
            return;
        }
        for (VirtualElement element : elements) {
            if (element instanceof ItemDisplayElement display) {
                display.setItem(tinted(display.getItem(), color));
            }
        }
    }

    // A copy of stack drawn in color. A 26.2 bridged model tints from map_color, a 26.3 one from
    // custom_model_data; 26.2 sets both, as the patch does, for 26.3 clients through Via.
    private static ItemStack tinted(ItemStack stack, int color) {
        ItemStack copy = stack.copy();
        //? if <26.3
        copy.set(DataComponents.MAP_COLOR, new MapItemColor(color));
        copy.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(), List.of(color)));
        return copy;
    }

    // ColorProviderRegistry.getColor(BlockState, LevelChunk, BlockPos), -1 for an untinted block.
    private static @Nullable MethodHandle findPatchColor() {
        if (!FabricLoader.getInstance().isModLoaded("polymer-patch-bundle")) {
            return null;
        }
        try {
            Class<?> registry = Class.forName("me.drex.ppb.color.ColorProviderRegistry");
            return MethodHandles.publicLookup().findStatic(registry, "getColor",
                    MethodType.methodType(int.class, BlockState.class, LevelChunk.class,
                            BlockPos.class));
        } catch (ReflectiveOperationException e) {
            PolymerDiagonalWalls.LOGGER.warn(
                    "polymer-patch-bundle has no block colours to read; tinted arms stay grey", e);
            return null;
        }
    }

    // The arm for armState, or empty when the block shows as itself or isn't a patch's.
    private static Optional<Arm> resolve(BlockState armState, @Nullable BlockState postState) {
        Block block = armState.getBlock();
        if (PolymerBlockUtils.getPolymerBlockState(armState, null) == armState) {
            return Optional.empty();
        }
        Float viewRange = overlayViewRange(armState);
        if (viewRange == null) {
            warnOnce(block, "its overlay comes from no patch mod that bridges its models");
            return Optional.empty();
        }
        Optional<BlockStateAsset> asset =
                BLOCKSTATES.computeIfAbsent(block, BridgedArms::readBlockState);
        if (asset.isEmpty() || asset.get().multipart().isEmpty()) {
            warnOnce(block, "its blockstate has no readable multipart");
            return Optional.empty();
        }
        List<Part> parts = new ArrayList<>();
        for (StateModelVariant variant : Multipart.armParts(asset.get(), armState, postState)) {
            parts.add(Part.of(variant));
        }
        if (parts.isEmpty()) {
            warnOnce(block, "its blockstate has no side parts for " + armState);
            return Optional.empty();
        }
        return Optional.of(new Arm(List.copyOf(parts), viewRange));
    }

    // The view range of the patch mod whose class draws this block's overlay, if it is one.
    private static @Nullable Float overlayViewRange(BlockState state) {
        BlockWithElementHolder overlay = BlockWithElementHolder.get(state);
        if (overlay == null) {
            return null;
        }
        String classFile = overlay.getClass().getName().replace('.', '/') + ".class";
        for (Map.Entry<String, Float> entry : OVERLAY_VIEW_RANGES.entrySet()) {
            Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(entry.getKey());
            if (mod.isPresent() && mod.get().findPath(classFile).isPresent()) {
                return entry.getValue();
            }
        }
        return null;
    }

    // The block's own blockstate file, from the first mod that ships it, as the patches read it.
    private static Optional<BlockStateAsset> readBlockState(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        String file = "assets/" + id.getNamespace() + "/blockstates/" + id.getPath() + ".json";
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            for (Path root : mod.getRootPaths()) {
                Path path = root.resolve(file);
                if (Files.exists(path)) {
                    try {
                        return Optional.of(BlockStateAsset.fromJson(
                                Files.readString(path, StandardCharsets.UTF_8)));
                    } catch (IOException | RuntimeException e) {
                        PolymerDiagonalWalls.LOGGER.warn("Could not read {} from {}", file,
                                mod.getMetadata().getId(), e);
                        return Optional.empty();
                    }
                }
            }
        }
        return Optional.empty();
    }

    // Logs once per block why its arms fall back to a block display of its stand-in.
    private static void warnOnce(Block block, String reason) {
        if (WARNED.add(block)) {
            PolymerDiagonalWalls.LOGGER.warn("Arms of {} show its Polymer stand-in: {}",
                    BuiltInRegistries.BLOCK.getKey(block), reason);
        }
    }

    // A resolved arm: its parts in blockstate order and the overlay view range they match.
    private record Arm(List<Part> parts, float viewRange) {
    }

    // One model part: the item that shows it and its rotation, as the overlays pass them.
    private record Part(ItemStack stack, Quaternionfc rotation) {
        // A uvlock part shows the patch's pre-rotated copy unturned, any other its model turned.
        private static Part of(StateModelVariant variant) {
            Identifier model = variant.model();
            Quaternionf rotation = new Quaternionf();
            if (variant.uvlock()) {
                model = model.withSuffix("_uvlock_" + variant.x() + "_" + variant.y());
            } else {
                rotation.rotateY((float) Math.toRadians(-variant.y()))
                        .rotateX((float) Math.toRadians(variant.x()));
            }
            ItemStack stack = new ItemStack(Items.TRIAL_KEY);
            stack.set(DataComponents.ITEM_MODEL, ResourcePackExtras.bridgeModel(model));
            return new Part(stack, rotation);
        }
    }
}
