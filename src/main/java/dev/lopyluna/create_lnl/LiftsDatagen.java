package dev.lopyluna.create_lnl;

import com.google.gson.JsonElement;
import com.simibubi.create.api.data.recipe.ProcessingRecipeGen;
import com.simibubi.create.foundation.utility.FilesHelper;
import com.tterrag.registrate.providers.ProviderType;
import dev.lopyluna.create_lnl.content.datagen.DatagenTags;
import dev.lopyluna.create_lnl.content.datagen.gens.PolishingGen;
import dev.lopyluna.create_lnl.register.client.LiftKeys;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static dev.lopyluna.create_lnl.Lifts.MOD_ID;
import static dev.lopyluna.create_lnl.Lifts.NAME;

@SuppressWarnings("unused")
public class LiftsDatagen {
    static final List<ProcessingRecipeGen<?, ?, ?>> GENERATORS = new ArrayList<>();

    public static void gatherDataHighPriority(GatherDataEvent event) {
        if (event.getMods().contains(MOD_ID)) addExtraRegistrateData();
    }

    @SuppressWarnings("all")
    public static void gatherData(GatherDataEvent event) {
        if (!event.getMods().contains(MOD_ID)) return;
        var gen = event.getGenerator();
        var output = gen.getPackOutput();
        var provider = event.getLookupProvider();
        var helper = event.getExistingFileHelper();

        //gen.addProvider(event.includeClient(), GearsSoundEvents.provider(gen));

       //gen.addProvider(event.includeServer(), new VanillaRecipeGen(output, provider));
        //gen.addProvider(event.includeServer(), new MechanicalCraftingGen(output, provider));
        if (event.includeServer()) {
            GENERATORS.add(new PolishingGen(output, provider));

            gen.addProvider(true, new DataProvider() {
                @Override  public String getName() { return NAME + "'s Processing Recipes"; }
                @Override  public CompletableFuture<?> run(CachedOutput dc) { return CompletableFuture.allOf(GENERATORS.stream().map(gen -> gen.run(dc)).toArray(CompletableFuture[]::new)); }
            });
        }
    }


    private static void addExtraRegistrateData() {
        DatagenTags.addGenerators();
        Lifts.REG.addDataGenerator(ProviderType.LANG, provider -> {
            BiConsumer<String, String> langConsumer = provider::add;

            //provideDefaultLang("interface", langConsumer);
            //provideDefaultLang("tooltips", langConsumer);
            //GearsSoundEvents.provideLang(langConsumer);
            LiftKeys.provideLang(langConsumer);
        });
    }

    private static void provideDefaultLang(String fileName, BiConsumer<String, String> consumer) {
        var path = "assets/"+ MOD_ID +"/lang/default/" + fileName + ".json";
        var jsonElement = FilesHelper.loadJsonResource(path);
        if (jsonElement == null) throw new IllegalStateException(String.format("Could not find default lang file: %s", path));
        for (Map.Entry<String, JsonElement> entry : jsonElement.getAsJsonObject().entrySet()) consumer.accept(entry.getKey(), entry.getValue().getAsString());
    }
}
