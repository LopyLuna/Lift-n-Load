package dev.lopyluna.create_lnl.register;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.data.AssetLookup;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.BlockEntry;
import dev.lopyluna.create_lnl.Lifts;
import dev.lopyluna.create_lnl.content.blocks.lift.LiftBlock;
import dev.lopyluna.create_lnl.content.blocks.lift.LiftBlockItem;
import dev.simulated_team.simulated.index.SimItems;
import dev.simulated_team.simulated.index.SimTags;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.client.model.generators.ModelFile;

import java.util.function.Function;

import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;
import static dev.lopyluna.create_lnl.Lifts.REG;

@SuppressWarnings("removal")
public class LiftsBlocks {

    public static final BlockEntry<LiftBlock> CONTRAPTION_LIFT = REG.block("contraption_lift", LiftBlock::new)
            .properties(p -> p
                    .noOcclusion().dynamicShape()
                    .mapColor(MapColor.COLOR_GRAY).sound(LiftsSoundTypes.LIFT)
                    .strength(-1, 960000).pushReaction(PushReaction.BLOCK)
            ).addLayer(() -> RenderType::cutout)
            .transform(pickaxeOnly())
            .blockstate((c, p) -> p.simpleBlock(c.getEntry(),
                    p.models().withExistingParent("block/" + c.getName(), Lifts.loc("block/"+c.getName()+"/bottom"))))
            .loot((lt, block) -> lt.dropOther(block, Items.AIR))
            .recipe((c, p) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get(), 1)
                    .pattern("BBB").pattern(" S ").pattern("IEI")
                    .define('B', LiftsTags.itemC("plates/brass"))
                    .define('I', AllBlocks.INDUSTRIAL_IRON_BLOCK)
                    .define('S', SimItems.SPRING)
                    .define('E', SimItems.ENGINE_ASSEMBLY)
                    .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(LiftsTags.itemC("plates/brass")))
                    .save(p))
            .tag(SimTags.Blocks.NON_MOVABLE)
            .item(LiftBlockItem::new)
            .tag(LiftsTags.ItemTags.SPRING_LIKE.tag)
            .model((c, p) -> {})
            .build()
            .register();



    protected static String getItemName(ItemLike pItemLike) {
        return BuiltInRegistries.ITEM.getKey(pItemLike.asItem()).getPath();
    }

    public static <T extends Block> Function<BlockState, ModelFile> getBlockModel(boolean customItem, DataGenContext<Block, T> c, RegistrateBlockstateProvider p) {
        return $ -> customItem ? AssetLookup.partialBaseModel(c, p) : AssetLookup.standardModel(c, p);
    }

    private static boolean never(BlockState state, BlockGetter blockGetter, BlockPos pos) {
        return false;
    }

    public static void register() {}
}
