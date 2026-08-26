package dev.lopyluna.create_lnl.register;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.recipe.CommonMetal;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.ItemEntry;
import dev.lopyluna.create_lnl.content.blocks.overwrite.TireBlockItem;
import dev.lopyluna.create_lnl.content.blocks.spring_shaft.SpringShaftItem;
import dev.lopyluna.create_lnl.content.blocks.wheel.WheelBE;
import dev.lopyluna.create_lnl.content.items.physic_welder.PhysicWelderItem;
import dev.ryanhcode.offroad.Offroad;
import dev.ryanhcode.offroad.content.components.TireLike;
import dev.ryanhcode.offroad.index.OffroadDataComponents;
import dev.simulated_team.simulated.index.SimItems;
import dev.simulated_team.simulated.index.SimTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.Tags;

import static dev.lopyluna.create_lnl.Lifts.REG;

@SuppressWarnings("unused")
public class LiftsItems {
    public static final ItemEntry<Item> LUNAR_DIAMOND = REG.item("lunar_diamond", Item::new).recipe((c, p) -> ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, c.get(), 2)
            .requires(Tags.Items.GEMS_DIAMOND).requires(Tags.Items.GEMS_LAPIS).requires(Tags.Items.GEMS_LAPIS).requires(Tags.Items.GEMS_LAPIS).requires(Tags.Items.GEMS_LAPIS).requires(Tags.Items.GEMS_LAPIS).requires(Tags.Items.GEMS_LAPIS).requires(Tags.Items.GEMS_LAPIS).requires(Tags.Items.GEMS_LAPIS)
            .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(Tags.Items.GEMS_DIAMOND)).save(p)).register();
    public static final ItemEntry<Item> POLISHED_LUNAR_DIAMOND = REG.item("polished_lunar_diamond", Item::new).register();
    public static final ItemEntry<Item> NODE_PLUG = REG.item("node_plug", Item::new).recipe((c, p) -> ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, c.get(), 1)
            .requires(POLISHED_LUNAR_DIAMOND.get()).requires(CommonMetal.IRON.plates)
            .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(POLISHED_LUNAR_DIAMOND.get())).save(p))
            .tag(LiftsTags.NODE_VIEWER).register();

    public static final ItemEntry<SpringShaftItem> SPRING_SHAFT = REG.item("spring_shaft", SpringShaftItem::new)
            .recipe((ctx, prov) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ctx.get(), 2)
                    .pattern("S").pattern("N").pattern("S").define('S', SimItems.SPRING).define('N', AllBlocks.SHAFT)
                    .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(SimItems.SPRING)).save(prov))
            .tag(SimTags.Items.SPRING_ADJUSTER)
            .register();

    public static final ItemEntry<PhysicWelderItem> PHYSIC_WELDER = REG.item("physic_welder", PhysicWelderItem::new)
            .recipe((c, p) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get(), 1)
                    .pattern("AB ").pattern(" TE").pattern("AB ")
                    .define('B', LiftsTags.itemC("ingots/brass"))
                    .define('A', AllItems.ANDESITE_ALLOY)
                    .define('T', AllItems.TRANSMITTER)
                    .define('E', Tags.Items.ENDER_PEARLS)
                    .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(LiftsTags.itemC("ingots/brass")))
                    .save(p))
            .model((c, p) -> {})
            .tag(Tags.Items.ENCHANTABLES, ItemTags.DURABILITY_ENCHANTABLE)
            .register();

    public static final ItemEntry<TireBlockItem> SLIME_MONSTROUS_TIRE = REG.item("monstrous_slime_tire", p -> new TireBlockItem(p, WheelBE.WheelType.MONSTROUS, true))
            .properties(p -> p.component(OffroadDataComponents.TIRE, TireLike.MONSTROUS_TIRE).component(LiftsDataComps.STICKY, Unit.INSTANCE))
            .recipe((c, p) -> ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, c.get(), 1)
                    .requires(Tags.Items.STORAGE_BLOCKS_SLIME)
                    .requires(Tags.Items.STORAGE_BLOCKS_SLIME)
                    .requires(BuiltInRegistries.ITEM.get(Offroad.path("monstrous_tire")))
                    .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(Tags.Items.SLIME_BALLS))
                    .save(p))
            .model(AssetLookup.itemModelWithPartials())
            .register();
    public static final ItemEntry<TireBlockItem> SLIME_LARGE_TIRE = REG.item("large_slime_tire", p -> new TireBlockItem(p, WheelBE.WheelType.LARGE, true))
            .properties(p -> p.component(OffroadDataComponents.TIRE, TireLike.LARGE_TIRE).component(LiftsDataComps.STICKY, Unit.INSTANCE))
            .recipe((c, p) -> ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, c.get(), 1)
                    .requires(Tags.Items.STORAGE_BLOCKS_SLIME)
                    .requires(BuiltInRegistries.ITEM.get(Offroad.path("large_tire")))
                    .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(Tags.Items.SLIME_BALLS))
                    .save(p))
            .model(AssetLookup.itemModelWithPartials())
            .register();
    public static final ItemEntry<TireBlockItem> SLIME_TIRE = REG.item("slime_tire", p -> new TireBlockItem(p, WheelBE.WheelType.NORMAL, true))
            .properties(p -> p.component(OffroadDataComponents.TIRE, TireLike.TIRE).component(LiftsDataComps.STICKY, Unit.INSTANCE))
            .recipe((c, p) -> ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, c.get(), 1)
                    .requires(Tags.Items.SLIME_BALLS)
                    .requires(Tags.Items.SLIME_BALLS)
                    .requires(BuiltInRegistries.ITEM.get(Offroad.path("tire")))
                    .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(Tags.Items.SLIME_BALLS))
                    .save(p))
            .model(AssetLookup.itemModelWithPartials())
            .register();
    public static final ItemEntry<TireBlockItem> SLIME_SMALL_TIRE = REG.item("small_slime_tire", p -> new TireBlockItem(p, WheelBE.WheelType.SMALL, true))
            .properties(p -> p.component(OffroadDataComponents.TIRE, TireLike.SMALL_TIRE).component(LiftsDataComps.STICKY, Unit.INSTANCE))
            .recipe((c, p) -> ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, c.get(), 1)
                    .requires(Tags.Items.SLIME_BALLS)
                    .requires(BuiltInRegistries.ITEM.get(Offroad.path("small_tire")))
                    .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(Tags.Items.SLIME_BALLS))
                    .save(p))
            .model(AssetLookup.itemModelWithPartials())
            .register();


    protected static String getItemName(ItemLike pItemLike) {
        return BuiltInRegistries.ITEM.getKey(pItemLike.asItem()).getPath();
    }

    public static void register() {}
}
