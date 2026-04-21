package dev.lopyluna.create_lnl.content.datagen;

import com.simibubi.create.foundation.data.TagGen;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateTagsProvider;
import dev.lopyluna.create_lnl.register.LiftsTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import static dev.lopyluna.create_lnl.Lifts.REG;

@SuppressWarnings("deprecation")
public class DatagenTags {
    public static void addGenerators() {
        REG.addDataGenerator(ProviderType.BLOCK_TAGS, DatagenTags::genBlockTags);
        REG.addDataGenerator(ProviderType.ITEM_TAGS, DatagenTags::genItemTags);
    }
    private static void genItemTags(RegistrateTagsProvider<Item> provIn) {
        var prov = new TagGen.CreateTagsProvider<>(provIn, Item::builtInRegistryHolder);

        for (var tag : LiftsTags.ItemTags.values()) if (tag.alwaysDatagen) prov.getOrCreateRawBuilder(tag.tag);
    }

    private static void genBlockTags(RegistrateTagsProvider<Block> provIn) {
        var prov = new TagGen.CreateTagsProvider<>(provIn, Block::builtInRegistryHolder);

        for (var tag : LiftsTags.BlockTags.values()) if (tag.alwaysDatagen) prov.getOrCreateRawBuilder(tag.tag);
    }
}
