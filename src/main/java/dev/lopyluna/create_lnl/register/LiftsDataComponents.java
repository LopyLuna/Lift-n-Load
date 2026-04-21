package dev.lopyluna.create_lnl.register;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.UUID;

import static dev.lopyluna.create_lnl.Lifts.REGISTER;

public class LiftsDataComponents {

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> SUBLEVEL_UUID = REGISTER.components()
            .registerComponentType("sub_level_uuid", b -> b
                    .persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).cacheEncoding());

    public static void register() {}
}
