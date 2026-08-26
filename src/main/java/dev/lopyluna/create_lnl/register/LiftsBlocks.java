package dev.lopyluna.create_lnl.register;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllTags;
import com.simibubi.create.foundation.data.AssetLookup;
import com.simibubi.create.foundation.data.BlockStateGen;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.entry.BlockEntry;
import dev.lopyluna.create_lnl.Lifts;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.DockingLiftBlock;
import dev.lopyluna.create_lnl.content.blocks.contraption_lift.DockingLiftBlockItem;
import dev.lopyluna.create_lnl.content.blocks.node_link.NodeLinkBlock;
import dev.lopyluna.create_lnl.content.blocks.spring_shaft.SpringShaftBlock;
import dev.lopyluna.create_lnl.content.blocks.thruster.ThrusterBlock;
import dev.lopyluna.create_lnl.content.blocks.thruster.ThrusterStructureBlock;
import dev.lopyluna.create_lnl.content.blocks.wheel.WheelBlock;
import dev.lopyluna.create_lnl.content.configs.server.kinetics.LStress;
import dev.ryanhcode.offroad.Offroad;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.spring.SpringBlock;
import dev.simulated_team.simulated.index.SimItems;
import dev.simulated_team.simulated.index.SimTags;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.lang.Lang;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.Tags;

import java.util.function.Function;

import static com.simibubi.create.foundation.data.ModelGen.customItemModel;
import static com.simibubi.create.foundation.data.TagGen.axeOrPickaxe;
import static com.simibubi.create.foundation.data.TagGen.pickaxeOnly;
import static dev.lopyluna.create_lnl.Lifts.REG;

@SuppressWarnings({"removal", "unused"})
public class LiftsBlocks {

    public static final BlockEntry<DockingLiftBlock> CONTRAPTION_LIFT = REG.block("contraption_lift", DockingLiftBlock::new)
            .properties(p -> p
                    .noOcclusion().dynamicShape()
                    .mapColor(MapColor.COLOR_GRAY).sound(LiftsSoundTypes.LIFT)
                    .strength(0.4f, 960000).pushReaction(PushReaction.BLOCK)
            ).addLayer(() -> RenderType::cutout)
            .blockstate((c, p) -> {
                var name = c.getName();
                var text = Lifts.loc("block/lift");
                for (var clr : DyeColor.values()) {
                    var clrName = clr.getSerializedName();
                    var textRe = Lifts.loc("block/lifts/" + clrName);

                    p.models().withExistingParent("block/"+name+"/"+clrName+"/block",
                            Lifts.loc("block/"+name+"/block")).texture("0", textRe);
                    p.models().withExistingParent("block/"+name+"/"+clrName+"/bottom",
                            Lifts.loc("block/"+name+"/bottom")).texture("0", textRe);
                    p.models().withExistingParent("block/"+name+"/"+clrName+"/top",
                            Lifts.loc("block/"+name+"/top")).texture("0", textRe);

                    for (var dir : Iterate.horizontalDirections) {
                        p.models().withExistingParent("block/"+name+"/"+clrName+"/animation/"+Lang.asId(dir.name())+"_flap",
                                Lifts.loc("block/"+name+"/animation/"+Lang.asId(dir.name())+"_flap")).texture("0", textRe);
                        p.models().withExistingParent("block/"+name+"/"+clrName+"/animation/"+Lang.asId(dir.name())+"_foot",
                                Lifts.loc("block/"+name+"/animation/"+Lang.asId(dir.name())+"_foot")).texture("0", textRe);
                    }
                }

                p.simpleBlock(c.getEntry(), p.models().withExistingParent("block/"+name, Lifts.loc("block/"+name+"/bottom")));
            })
            .loot((lt, block) -> lt.dropOther(block, Items.AIR))
            .recipe((c, p) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get(), 1)
                    .pattern("BBB").pattern(" S ").pattern("IEI")
                    .define('B', LiftsTags.itemC("plates/brass"))
                    .define('I', AllBlocks.INDUSTRIAL_IRON_BLOCK)
                    .define('S', SimItems.SPRING)
                    .define('E', SimItems.ENGINE_ASSEMBLY)
                    .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(LiftsTags.itemC("ingots/brass")))
                    .save(p))
            .tag(SimTags.Blocks.NON_MOVABLE)
            .item(DockingLiftBlockItem::new)
            .properties(p -> p.stacksTo(1))
            .tag(LiftsTags.ItemTags.SPRING_LIKE.tag)
            .model((c, p) -> {
                for (var clr : DyeColor.values())
                    p.withExistingParent("item/" + c.getName() + "/" + clr.getSerializedName(), Lifts.loc("item/" + c.getName()))
                            .texture("0", Lifts.loc("block/lifts/" + clr.getSerializedName()));
            })
            .build()
            .register();

    public static final BlockEntry<ThrusterBlock> THRUSTER = REG.block("thruster", ThrusterBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_GRAY).sound(SoundType.NETHERITE_BLOCK))
            .addLayer(() -> RenderType::cutout)
            .transform(pickaxeOnly())
            .blockstate((c, p) ->
                    p.directionalBlock(c.getEntry(), s -> p.models().getExistingFile(Lifts.loc("block/"+c.getName()+"/block"))))
            .recipe((c, p) -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, c.get(), 1)
                    .pattern("IAS").pattern("BR ").pattern("IAS")
                    .define('B', LiftsTags.itemC("ingots/brass"))
                    .define('S', LiftsTags.itemC("plates/brass"))
                    .define('I', AllBlocks.INDUSTRIAL_IRON_BLOCK)
                    .define('A', AllItems.ANDESITE_ALLOY)
                    .define('R', LiftsTags.itemC("dusts/redstone"))
                    .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(LiftsTags.itemC("ingots/brass")))
                    .save(p))
            .simpleItem()
            .register();

    public static final BlockEntry<ThrusterStructureBlock> THRUSTER_STRUCTURAL = REG.block("thruster_structure", ThrusterStructureBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .clientExtension(() -> ThrusterStructureBlock.RenderProperties::new)
            .properties(p -> p.noOcclusion().mapColor(MapColor.COLOR_GRAY).sound(SoundType.NETHERITE_BLOCK))
            .blockstate((c, p) -> p.getVariantBuilder(c.get()).forAllStatesExcept(BlockStateGen.mapToAir(p), ThrusterStructureBlock.FACING))
            .transform(pickaxeOnly())
            .lang("Thruster")
            .register();


    public static final BlockEntry<WheelBlock> WHEEL = REG.block("wheel", WheelBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.noOcclusion().sound(SoundType.WOOL).mapColor(MapColor.COLOR_GRAY))
            .transform(axeOrPickaxe())
            .transform(LStress.setNoImpact())
            .blockstate((c, p) -> {
                p.simpleBlock(c.get(), p.models().withExistingParent("block/"+c.getName(), Lifts.mcLoc("block/barrier")).texture("particle", Lifts.loc("create", "block/belt")));
                var models = p.itemModels();
                models.withExistingParent("item/monstrous_slime_tire/monstrous_slime_tire", Offroad.path("item/monstrous_tire/monstrous_tire")).texture("tire_0", Lifts.loc("block/tire/slime_0")).texture("tire_1", Lifts.loc("block/tire/slime_1"));
                models.withExistingParent("item/large_slime_tire/large_slime_tire", Offroad.path("item/large_tire/large_tire")).texture("tire_0", Lifts.loc("block/tire/slime_0"));
                models.withExistingParent("item/slime_tire/slime_tire", Offroad.path("item/tire/tire")).texture("tire_0", Lifts.loc("block/tire/slime_0"));
                models.withExistingParent("item/small_slime_tire/small_slime_tire", Offroad.path("item/small_tire/small_tire")).texture("tire_0", Lifts.loc("block/tire/slime_0"));

                models.withExistingParent("item/monstrous_slime_tire/item", Offroad.path("item/monstrous_tire/item")).texture("tire_0", Lifts.loc("block/tire/slime_0")).texture("tire_1", Lifts.loc("block/tire/slime_1"));
                models.withExistingParent("item/large_slime_tire/item", Offroad.path("item/large_tire/item")).texture("tire_0", Lifts.loc("block/tire/slime_0"));
                models.withExistingParent("item/slime_tire/item", Offroad.path("item/tire/item")).texture("tire_0", Lifts.loc("block/tire/slime_0"));
                models.withExistingParent("item/small_slime_tire/item", Offroad.path("item/small_tire/item")).texture("tire_0", Lifts.loc("block/tire/slime_0"));

                models.withExistingParent("item/monstrous_slime_tire/block", Offroad.path("item/monstrous_tire/block"));
                models.withExistingParent("item/large_slime_tire/block", Offroad.path("item/large_tire/block"));
                models.withExistingParent("item/slime_tire/block", Offroad.path("item/tire/block"));
                models.withExistingParent("item/small_slime_tire/block", Offroad.path("item/small_tire/block"));
            }).register();

    public static final BlockEntry<SpringShaftBlock> SPRING_SHAFT = REG.block("spring_shaft", SpringShaftBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .transform(pickaxeOnly())
            .blockstate((c, p) -> p.directionalBlock(c.getEntry(), s -> p.models().getExistingFile(
                    Simulated.path("block/spring/" + (s.getValue(SpringBlock.SIZE) == SpringBlock.Size.MEDIUM ? "" : (s.getValue(SpringBlock.SIZE).getSerializedName() + "_")) + "block"))))
            .tag(AllTags.AllBlockTags.SAFE_NBT.tag, AllTags.AllBlockTags.BRITTLE.tag, AllTags.AllBlockTags.NON_MOVABLE.tag, SimTags.Blocks.LIGHT)
            .loot((tables, block) -> tables.add(block, tables.createSingleItemTable(LiftsItems.SPRING_SHAFT)))
            .register();

    public static final BlockEntry<NodeLinkBlock> NODE_LINK = REG.block("node_link", NodeLinkBlock::new)
            .initialProperties(SharedProperties::softMetal)
            .properties(p -> p.sound(SoundType.NETHERITE_BLOCK).mapColor(MapColor.TERRACOTTA_BROWN).forceSolidOn())
            .transform(axeOrPickaxe())
            .tag(AllTags.AllBlockTags.BRITTLE.tag, AllTags.AllBlockTags.SAFE_NBT.tag)
            .blockstate((c, p) -> p.getVariantBuilder(c.get()).forAllStates(state -> {
                var receiver = state.getValue(NodeLinkBlock.RECEIVER);
                var facing = state.getValue(NodeLinkBlock.FACING);
                return ConfiguredModel.builder()
                        .modelFile(p.models().getExistingFile(p.modLoc("block/node_link/" + ((receiver ? "receiver" : "transmitter") + (facing.getAxis().isHorizontal() ? "_vertical" : "")))))
                        .rotationX((facing == Direction.UP ? 0 : facing == Direction.DOWN ? 180 : 270 + 360) % 360)
                        .rotationY((facing.getAxis().isVertical() ? 180 : horizontalAngle(facing) + 360) % 360)
                        .build();
            })).addLayer(() -> RenderType::cutoutMipped)
            .recipe((c, p) -> ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, c.get(), 2)
                    .requires(LiftsItems.NODE_PLUG).requires(AllBlocks.INDUSTRIAL_IRON_BLOCK).requires(Tags.Items.DUSTS_REDSTONE)
                    .unlockedBy("has_ingredient", RegistrateRecipeProvider.has(LiftsItems.NODE_PLUG))
                    .save(p))
            .item()
            .tag(LiftsTags.NODE_VIEWER)
            .transform(customItemModel("_", "transmitter"))
            .register();


    protected static String getItemName(ItemLike pItemLike) {
        return BuiltInRegistries.ITEM.getKey(pItemLike.asItem()).getPath();
    }

    public static <T extends Block> Function<BlockState, ModelFile> getBlockModel(boolean customItem, DataGenContext<Block, T> c, RegistrateBlockstateProvider p) {
        return $ -> customItem ? AssetLookup.partialBaseModel(c, p) : AssetLookup.standardModel(c, p);
    }
    public static int horizontalAngle(Direction direction) {
        if (direction.getAxis().isVertical()) return 0;
        return (int) direction.toYRot();
    }

    private static boolean never(BlockState state, BlockGetter blockGetter, BlockPos pos) {
        return false;
    }

    public static void register() {}
}
