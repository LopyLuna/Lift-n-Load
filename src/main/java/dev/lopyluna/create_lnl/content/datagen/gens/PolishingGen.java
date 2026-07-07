package dev.lopyluna.create_lnl.content.datagen.gens;

import com.simibubi.create.api.data.recipe.PolishingRecipeGen;
import dev.lopyluna.create_lnl.register.LiftsItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;

import java.util.concurrent.CompletableFuture;

import static dev.lopyluna.create_lnl.Lifts.MOD_ID;

@SuppressWarnings("unused")
public class PolishingGen extends PolishingRecipeGen {
    GeneratedRecipe ROSE_QUARTZ = create(LiftsItems.LUNAR_DIAMOND::get, b -> b.output(LiftsItems.POLISHED_LUNAR_DIAMOND.get()));
    public PolishingGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, MOD_ID);
    }
}
