package dev.lopyluna.create_lnl.register;

import dev.simulated_team.simulated.index.SimSoundEvents;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.common.util.DeferredSoundType;

@SuppressWarnings("all")
public class LiftsSoundTypes {
    public static final DeferredSoundType LIFT = new DeferredSoundType(
            0.9F,
            1.0F,
            () -> SoundEvents.NETHERITE_BLOCK_BREAK, //BREAK
            () -> SoundEvents.NETHERITE_BLOCK_STEP, //STEP
            SimSoundEvents.DOCKING_CONNECTOR_DOCKS::event, //PLACE
            () -> SoundEvents.NETHERITE_BLOCK_HIT, //HIT
            () -> SoundEvents.NETHERITE_BLOCK_FALL //FALL
    );
}
