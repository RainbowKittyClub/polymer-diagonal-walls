package club.rainbowkitty.polymerdiagonalwalls.display;

import java.util.ArrayList;
import java.util.List;

import eu.pb4.polymer.resourcepack.extras.api.format.blockstate.BlockStateAsset;
import eu.pb4.polymer.resourcepack.extras.api.format.blockstate.StateModelVariant;
import eu.pb4.polymer.resourcepack.extras.api.format.blockstate.StateMultiPartDefinition;
import eu.pb4.polymer.resourcepack.extras.api.format.blockstate.StateMultiPartDefinition.CombinedCondition;
import eu.pb4.polymer.resourcepack.extras.api.format.blockstate.StateMultiPartDefinition.Condition;
import eu.pb4.polymer.resourcepack.extras.api.format.blockstate.StateMultiPartDefinition.KeyValueCondition;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Reads an arm's model parts from a multipart blockstate file: the parts the arm state draws and
 * the same block joined nowhere does not, which leaves out a fence's post.
 */
public final class Multipart {
    private Multipart() {
    }

    /**
     * The first variant of every part of {@code asset} that applies to {@code armState} and not to
     * {@code postState}, in file order, or an empty list when the file has no multipart.
     *
     * @param armState the owner joined north and south, the state the arm draws
     * @param postState the owner joined nowhere, whose parts the arm leaves out
     */
    public static List<StateModelVariant> armParts(
            BlockStateAsset asset, BlockState armState, BlockState postState) {
        List<StateModelVariant> parts = new ArrayList<>();
        for (StateMultiPartDefinition part : asset.multipart().orElse(List.of())) {
            if (applies(part, armState) && !applies(part, postState)
                    && !part.apply().isEmpty()) {
                parts.add(part.apply().getFirst());
            }
        }
        return parts;
    }

    // Whether a multipart entry draws for state; a part with no condition always does.
    private static boolean applies(StateMultiPartDefinition part, BlockState state) {
        return part.when().isEmpty() || holds(part.when().get(), state);
    }

    // A multipart condition against a state; properties the block lacks pass, as in the patches.
    private static boolean holds(Condition condition, BlockState state) {
        return switch (condition) {
            case CombinedCondition combined -> combined.operation()
                    == CombinedCondition.Operation.OR
                    ? combined.terms().stream().anyMatch(term -> holds(term, state))
                    : combined.terms().stream().allMatch(term -> holds(term, state));
            case KeyValueCondition keyValue -> keyValue.tests().entrySet().stream()
                    .allMatch(test -> matches(state, test.getKey(), test.getValue()));
        };
    }

    // Whether the state's value of the named property is one of the terms' values.
    private static boolean matches(
            BlockState state, String name, KeyValueCondition.Terms terms) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) {
            return true;
        }
        String value = valueName(state, property);
        return terms.entries().stream()
                .anyMatch(term -> term.value().equals(value) != term.negated());
    }

    // The serialized name of the state's value of property, as blockstate files spell it.
    private static <T extends Comparable<T>> String valueName(
            BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
