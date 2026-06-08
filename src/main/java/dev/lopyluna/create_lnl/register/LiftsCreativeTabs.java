package dev.lopyluna.create_lnl.register;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@SuppressWarnings("all")
public class LiftsCreativeTabs {

    //public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BASE_TAB = REGISTER.creativeTab().register("base_tab", () -> CreativeModeTab.builder()
    //        .title(Component.translatableWithFallback("itemGroup." + MOD_ID + ".base", NAME))
    //        .withTabsBefore(AllCreativeModeTabs.PALETTES_CREATIVE_TAB.getKey())
    //        .icon(AllItems.GOGGLES::asStack)
    //        .build());

    public static void register() {}

    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        //if (event.getTabKey().equals(LiftsCreativeTabs.BASE_TAB.getKey())) {
        //    //Hide unfinished blocks/items
        //    event.remove(GearsBlocks.PLANETARY_GEAR.asStack(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        //}
    }
}
