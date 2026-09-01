package dev.lopyluna.create_lnl.register;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateTagsProvider;
import dev.eriksonn.aeronautics.index.AeroBlocks;
import dev.eriksonn.aeronautics.index.AeroTags;
import dev.lopyluna.create_lnl.Lifts;
import dev.simulated_team.simulated.index.SimBlocks;
import net.createmod.catnip.lang.Lang;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.common.Tags;

import java.util.function.Function;
import java.util.stream.Stream;

import static dev.lopyluna.create_lnl.Lifts.MOD_ID;
import static dev.lopyluna.create_lnl.Lifts.REG;

@SuppressWarnings({"unused", "deprecation"})
public class LiftsTags {
    public static void addGenerators() {
        REG.addDataGenerator(ProviderType.FLUID_TAGS, LiftsTags::genFluidTags);
        REG.addDataGenerator(ProviderType.BLOCK_TAGS, LiftsTags::genBlockTags);
        REG.addDataGenerator(ProviderType.ITEM_TAGS, LiftsTags::genItemTags);
    }

    public static void genFluidTags(RegistrateTagsProvider<Fluid> provIn) {
        TagsProvider<Fluid> prov = new TagsProvider<>(provIn, Fluid::builtInRegistryHolder);

    }

    public static TagKey<Block> NODE_INPUT = block("node_input");
    public static TagKey<Block> NODE_OUTPUT = block("node_output");
    public static TagKey<Block> NODE_BOTH = block("node_both");

    public static void genBlockTags(RegistrateTagsProvider<Block> provIn) {
        TagsProvider<Block> prov = new TagsProvider<>(provIn, Block::builtInRegistryHolder);
        prov.tag(NODE_OUTPUT)
                .add(SimBlocks.THROTTLE_LEVER.get())
                .add(AllBlocks.ANALOG_LEVER.get())
                .add(AllBlocks.DESK_BELL.get())
                .add(Blocks.LEVER)
                .add(Blocks.DAYLIGHT_DETECTOR)
                .add(Blocks.TARGET)
                .add(Blocks.TRIPWIRE_HOOK)
                .add(Blocks.CALIBRATED_SCULK_SENSOR)
                .add(Blocks.SCULK_SENSOR)
                .add(Blocks.LECTERN)
                .addTag(net.minecraft.tags.BlockTags.BUTTONS)
                .addTag(net.minecraft.tags.BlockTags.PRESSURE_PLATES);

        var nodeIn = prov.tag(NODE_INPUT)
                .add(AeroBlocks.HOT_AIR_BURNER.get())
                .add(AeroBlocks.STEAM_VENT.get())
                .add(SimBlocks.REDSTONE_MAGNET.get())
                .add(AllBlocks.STEAM_WHISTLE.get())
                .add(AllBlocks.HAUNTED_BELL.get())
                .add(AllBlocks.PECULIAR_BELL.get())
                .add(Blocks.PISTON)
                .add(Blocks.STICKY_PISTON)
                .add(Blocks.DISPENSER)
                .add(Blocks.DROPPER)
                .add(Blocks.NOTE_BLOCK)
                .add(Blocks.REDSTONE_LAMP)
                .add(Blocks.BELL)
                .add(Blocks.CRAFTER)
                .add(Blocks.COPPER_BULB)
                .add(Blocks.EXPOSED_COPPER_BULB)
                .add(Blocks.WEATHERED_COPPER_BULB)
                .add(Blocks.OXIDIZED_COPPER_BULB)
                .add(Blocks.WAXED_COPPER_BULB)
                .add(Blocks.WAXED_EXPOSED_COPPER_BULB)
                .add(Blocks.WAXED_WEATHERED_COPPER_BULB)
                .add(Blocks.WAXED_OXIDIZED_COPPER_BULB)
                .addTag(net.minecraft.tags.BlockTags.DOORS)
                .addTag(net.minecraft.tags.BlockTags.TRAPDOORS)
                .addTag(net.minecraft.tags.BlockTags.FENCE_GATES);
        for (var dye : DyeColor.values()) nodeIn.addOptional(AllBlocks.NIXIE_TUBES.get(dye).getId());
        for (var dye : DyeColor.values()) nodeIn.addOptional(LiftsBlocks.THRUSTERS.get(dye).getId());

        var pickaxe = prov.tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE);
        for (var dye : DyeColor.values()) pickaxe.addOptional(LiftsBlocks.THRUSTERS.get(dye).getId());

        prov.tag(NODE_BOTH)
                .add(Blocks.REDSTONE_TORCH)
                .add(Blocks.REDSTONE_WALL_TORCH);
    }

    public static TagKey<Item> NODE_CONNECTOR = item("node_connector");
    public static TagKey<Item> NODE_VIEWER = item("node_viewer");

    public static void genItemTags(RegistrateTagsProvider<Item> provIn) {
        TagsProvider<Item> prov = new TagsProvider<>(provIn, Item::builtInRegistryHolder);

        prov.tag(NODE_VIEWER)
                .addTag(NODE_CONNECTOR);

        var thrusters = prov.tag(ItemTags.THRUSTERS.tag)
                .add(LiftsBlocks.THRUSTER.asItem());
        for (var dye : DyeColor.values()) {
            var id = LiftsBlocks.THRUSTERS.get(dye).getId();
            thrusters.addOptional(id);
            prov.tag(item("thrusters/" + dye.getSerializedName())).addOptional(id);
        }

        prov.tag(ItemTags.SPRING_LIKE.tag)
                .add(Items.MAGMA_CREAM)
                .add(Items.WIND_CHARGE)
                .addTag(AeroTags.ItemTags.ENVELOPE)
                .addTag(Tags.Items.STORAGE_BLOCKS_SLIME)
                .addTag(Tags.Items.SLIME_BALLS)
                .addTag(Tags.Items.SLIMEBALLS);

    }

    public static <T> TagKey<T> optionalTag(Registry<T> registry, ResourceLocation id) {
        return TagKey.create(registry.key(), id);
    }
    public static <T> TagKey<T> commonTag(Registry<T> registry, String path) {
        return optionalTag(registry, ResourceLocation.fromNamespaceAndPath("c", path));
    }
    public static <T> TagKey<T> modTag(Registry<T> registry, String path) {
        return optionalTag(registry, Lifts.loc(path));
    }
    public static TagKey<Fluid> commonFluidTag(String path) {
        return commonTag(BuiltInRegistries.FLUID, path);
    }
    public static TagKey<Block> commonBlockTag(String path) {
        return commonTag(BuiltInRegistries.BLOCK, path);
    }
    public static TagKey<Item> commonItemTag(String path) {
        return commonTag(BuiltInRegistries.ITEM, path);
    }
    public static TagKey<Fluid> modFluidTag(String path) {
        return modTag(BuiltInRegistries.FLUID, path);
    }
    public static TagKey<Block> modBlockTag(String path) {
        return modTag(BuiltInRegistries.BLOCK, path);
    }
    public static TagKey<Item> modItemTag(String path) {
        return modTag(BuiltInRegistries.ITEM, path);
    }

    public static TagKey<Block> block(String name) { return TagKey.create(Registries.BLOCK, Lifts.loc(name)); }
    public static TagKey<Block> blockC(String name) { return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", name)); }
    public static TagKey<Block> blockMC(String name) { return TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace(name)); }
    public static TagKey<Item> item(String name) { return TagKey.create(Registries.ITEM, Lifts.loc(name)); }
    public static TagKey<Item> itemC(String name) { return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", name)); }
    public static TagKey<Item> itemMC(String name) { return TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace(name)); }
    public static TagKey<Fluid> fluid(String name) { return TagKey.create(Registries.FLUID, Lifts.loc(name)); }
    public static TagKey<Fluid> fluidC(String name) { return TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", name)); }
    public static TagKey<Fluid> fluidMC(String name) { return TagKey.create(Registries.FLUID, ResourceLocation.withDefaultNamespace(name)); }

    public enum NameSpace {
        MOD(MOD_ID, false, true),
        COMMON("c"),
        CREATE(Create.ID);

        public final String id;
        public final boolean optionalDefault;
        public final boolean alwaysDatagenDefault;
        NameSpace(String id) {
            this(id, true, false);
        }
        NameSpace(String id, boolean optionalDefault, boolean alwaysDatagenDefault) {
            this.id = id;
            this.optionalDefault = optionalDefault;
            this.alwaysDatagenDefault = alwaysDatagenDefault;
        }
    }

    public enum FluidTags {
        ;
        public final TagKey<Fluid> tag;
        public final boolean alwaysDatagen;

        FluidTags() { this(NameSpace.MOD); }
        FluidTags(NameSpace namespace) { this(namespace, namespace.optionalDefault, namespace.alwaysDatagenDefault); }
        FluidTags(NameSpace namespace, String path) { this(namespace, path, namespace.optionalDefault, namespace.alwaysDatagenDefault); }
        FluidTags(NameSpace namespace, boolean optional, boolean alwaysDatagen) { this(namespace, null, optional, alwaysDatagen); }
        FluidTags(NameSpace namespace, String path, boolean optional, boolean alwaysDatagen) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace.id, path == null ? Lang.asId(name()) : path);
            if (optional) tag = optionalTag(BuiltInRegistries.FLUID, id);
            else tag = net.minecraft.tags.FluidTags.create(id);
            this.alwaysDatagen = alwaysDatagen;
        }
        @SuppressWarnings("deprecation")
        public boolean is(Fluid fluid) { return fluid.builtInRegistryHolder().is(tag); }
        public boolean is(ItemStack stack) { return stack != null && stack.getItem() instanceof BucketItem bucket && is(bucket.content); }
        public boolean is(ItemLike item) { return item instanceof BucketItem bucket && is(bucket.content); }
        public boolean is(FluidState state) {return state.is(tag);}
        public boolean is(TagKey<Fluid> tag) {return tag==this.tag;}

        private static void init() {}
    }

    public enum BlockTags {
        ;
        public final TagKey<Block> tag;
        public final boolean alwaysDatagen;

        BlockTags() { this(NameSpace.MOD); }
        BlockTags(NameSpace namespace) { this(namespace, namespace.optionalDefault, namespace.alwaysDatagenDefault); }
        BlockTags(NameSpace namespace, String path) { this(namespace, path, namespace.optionalDefault, namespace.alwaysDatagenDefault); }
        BlockTags(NameSpace namespace, boolean optional, boolean alwaysDatagen) { this(namespace, null, optional, alwaysDatagen); }
        BlockTags(NameSpace namespace, String path, boolean optional, boolean alwaysDatagen) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace.id, path == null ? Lang.asId(name()) : path);
            if (optional) tag = optionalTag(BuiltInRegistries.BLOCK, id);
            else tag = net.minecraft.tags.BlockTags.create(id);
            this.alwaysDatagen = alwaysDatagen;
        }
        @SuppressWarnings("deprecation")
        public boolean is(Block block) { return block.builtInRegistryHolder().is(tag); }
        public boolean is(ItemStack stack) { return stack != null && stack.getItem() instanceof BlockItem blockItem && is(blockItem.getBlock()); }
        public boolean is(ItemLike item) { return item instanceof BlockItem blockItem && is(blockItem.getBlock()); }
        public boolean is(BlockState state) {return state.is(tag);}
        public boolean is(TagKey<Block> tag) {return tag==this.tag;}

        private static void init() {}
    }

    public enum ItemTags {
        SPRING_LIKE,
        THRUSTERS
        ;
        public final TagKey<Item> tag;
        public final boolean alwaysDatagen;

        ItemTags() { this(NameSpace.MOD); }
        ItemTags(NameSpace namespace) { this(namespace, namespace.optionalDefault, namespace.alwaysDatagenDefault); }
        ItemTags(NameSpace namespace, String path) { this(namespace, path, namespace.optionalDefault, namespace.alwaysDatagenDefault); }
        ItemTags(NameSpace namespace, boolean optional, boolean alwaysDatagen) { this(namespace, null, optional, alwaysDatagen); }
        ItemTags(NameSpace namespace, String path, boolean optional, boolean alwaysDatagen) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace.id, path == null ? Lang.asId(name()) : path);
            if (optional) tag = optionalTag(BuiltInRegistries.ITEM, id);
            else tag = net.minecraft.tags.ItemTags.create(id);
            this.alwaysDatagen = alwaysDatagen;
        }
        @SuppressWarnings("deprecation")
        public boolean matches(Item item) { return item.builtInRegistryHolder().is(tag); }
        public boolean matches(ItemStack stack) { return stack.is(tag); }
        private static void init() {}
    }

    public static void init() {
        FluidTags.init();
        BlockTags.init();
        ItemTags.init();
    }

    public static class TagsProvider<T> {

        private final RegistrateTagsProvider<T> provider;
        private final Function<T, ResourceKey<T>> keyExtractor;

        public TagsProvider(RegistrateTagsProvider<T> provider, Function<T, Holder.Reference<T>> refExtractor) {
            this.provider = provider;
            this.keyExtractor = refExtractor.andThen(Holder.Reference::key);
        }

        public TagAppender<T> tag(TagKey<T> tag) {
            TagBuilder tagbuilder = getOrCreateRawBuilder(tag);
            return new TagAppender<>(tagbuilder, keyExtractor);
        }

        public TagBuilder getOrCreateRawBuilder(TagKey<T> tag) {
            return provider.addTag(tag).getInternalBuilder();
        }

    }

    public static class TagAppender<T> extends net.minecraft.data.tags.TagsProvider.TagAppender<T> {

        private final Function<T, ResourceKey<T>> keyExtractor;

        public TagAppender(TagBuilder pBuilder, Function<T, ResourceKey<T>> pKeyExtractor) {
            super(pBuilder);
            this.keyExtractor = pKeyExtractor;
        }

        public TagAppender<T> add(T entry) {
            this.add(this.keyExtractor.apply(entry));
            return this;
        }

        @SafeVarargs
        public final TagAppender<T> add(T... entries) {
            Stream.of(entries)
                    .map(this.keyExtractor)
                    .forEach(this::add);
            return this;
        }
    }
}
