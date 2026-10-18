package club.rainbowkitty.polymerdiagonalwalls.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/** Datagen entrypoint; register providers here as the mod grows. **/
public final class PolymerDiagonalWallsDataGenerator implements DataGeneratorEntrypoint {
    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        FabricDataGenerator.Pack pack = generator.createPack();
        // pack.addProvider(ExampleProvider::new);
    }
}
