package dev.lopyluna.create_lnl.mixins;

import com.tterrag.registrate.builders.ItemBuilder;
import com.tterrag.registrate.util.nullness.NonNullFunction;
import dev.lopyluna.create_lnl.content.blocks.overwrite.TireBlockItem;
import dev.lopyluna.create_lnl.content.blocks.wheel.WheelBE;
import dev.ryanhcode.offroad.index.OffroadItems;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(OffroadItems.class)
public class OffroadItemsMixin {

    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Ldev/simulated_team/simulated/registrate/SimulatedRegistrate;item(Ljava/lang/String;Lcom/tterrag/registrate/util/nullness/NonNullFunction;)Lcom/tterrag/registrate/builders/ItemBuilder;"))
    private static ItemBuilder<?, ?> lifts$replaceTireItem(SimulatedRegistrate registrate, String name, NonNullFunction<Item.Properties, ? extends Item> factory) {
        return switch (name) {
            case "small_tire" -> registrate.item(name, p -> new TireBlockItem(p, WheelBE.WheelType.SMALL, false));
            case "tire" -> registrate.item(name, p -> new TireBlockItem(p, WheelBE.WheelType.NORMAL, false));
            case "large_tire" -> registrate.item(name, p -> new TireBlockItem(p, WheelBE.WheelType.LARGE, false));
            case "monstrous_tire" -> registrate.item(name, p -> new TireBlockItem(p, WheelBE.WheelType.MONSTROUS, false));
            default -> registrate.item(name, factory);
        };
    }
}
