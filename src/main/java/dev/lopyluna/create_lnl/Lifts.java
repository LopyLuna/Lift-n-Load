package dev.lopyluna.create_lnl;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import dev.lopyluna.create_lnl.content.utils.LiftsRegistry;
import dev.lopyluna.create_lnl.events.CommonEvents;
import dev.lopyluna.create_lnl.register.*;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import net.createmod.catnip.lang.FontHelper;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import javax.annotation.Nullable;

@SuppressWarnings("unused")
@Mod(Lifts.MOD_ID)
public class Lifts {
    public static final String NAME = "Lift n' Load";
    public static final String MOD_ID = "create_lnl";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static LiftsRegistry REGISTER = new LiftsRegistry(NAME, MOD_ID);
    public static CreateRegistrate REG = new SimulatedRegistrate(loc(MOD_ID), MOD_ID).defaultCreativeTab((ResourceKey<CreativeModeTab>) null);

    static {
        REG.setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE).andThen(TooltipModifier.mapNull(create(item))));
    }

    public Lifts(IEventBus modEventBus, ModContainer modContainer) {
        REGISTER.register(modEventBus);
        //LiftsCreativeTabs.register();
        var context = ModLoadingContext.get();
        REG.registerEventListeners(modEventBus);
        //REG.defaultCreativeTab(BASE_TAB, "base_tab");

        LiftsTags.addGenerators();
        LiftsDataComps.register();
        LiftsBlocks.register();
        LiftsBETypes.register();
        LiftsItems.register();
        LiftsPackets.register();

        LiftsConfigs.register(context, modContainer);

        SableEventPlatform.INSTANCE.onPhysicsTick(CommonEvents::onPhysicsTick);
        modEventBus.addListener(LiftsCreativeTabs::addCreative);
        modEventBus.addListener(EventPriority.HIGHEST, LiftsDatagen::gatherDataHighPriority);
        modEventBus.addListener(EventPriority.LOWEST, LiftsDatagen::gatherData);

        REG.addRawLang("create_lnl.simulated_section.create_lnl", NAME);
    }

    public static LangBuilder lang() {
        return new LangBuilder(MOD_ID);
    }
    public static ResourceLocation loc(String loc) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, loc);
    }
    public static ResourceLocation loc(String modID, String loc) {
        return ResourceLocation.fromNamespaceAndPath(modID, loc);
    }
    public static ResourceLocation mcLoc(String loc) {
        return ResourceLocation.withDefaultNamespace(loc);
    }
    public static ResourceLocation emptyLoc() {
        return loc("empty");
    }

    @Nullable
    public static KineticStats create(Item item) {
        if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof Block block)
            if (block instanceof IRotate) return new KineticStats(block);
        return null;
    }
}
