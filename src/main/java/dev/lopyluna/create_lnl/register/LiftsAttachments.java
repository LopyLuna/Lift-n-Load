package dev.lopyluna.create_lnl.register;

import dev.lopyluna.create_lnl.content.blocks.contraption_lift.LiftPlayerData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;

import static dev.lopyluna.create_lnl.Lifts.REGISTER;

@SuppressWarnings("unused")
public class LiftsAttachments {

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<LiftPlayerData>> LIFT_PLAYER_DATA =
            REGISTER.attachments().register("lift_player_data", () -> AttachmentType
                    .builder(() -> LiftPlayerData.EMPTY)
                    .serialize(LiftPlayerData.CODEC)
                    .copyOnDeath()
                    .build());

    public static void register() {}
}
