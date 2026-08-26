package dev.lopyluna.create_lnl;

import dev.lopyluna.create_lnl.content.blocks.contraption_lift.LiftHolding;
import dev.lopyluna.create_lnl.register.LiftsBlocks;
import dev.lopyluna.create_lnl.register.client.LiftsPartialModels;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import static dev.lopyluna.create_lnl.Lifts.MOD_ID;

@Mod(value = MOD_ID, dist = Dist.CLIENT)
public class LiftsClient {

    public LiftsClient(IEventBus modEventBus) {
        modEventBus.addListener(LiftsClient::clientInit);
    }

    public static void clientInit(final FMLClientSetupEvent event) {
        LiftsPartialModels.init();
        event.enqueueWork(() -> ItemProperties.register(LiftsBlocks.CONTRAPTION_LIFT.asItem(), Lifts.loc("color"),
                (stack, level, entity, seed) -> entity instanceof Player player ?
                        (LiftHolding.color(player) + 1) : 0)
        );
    }
}
