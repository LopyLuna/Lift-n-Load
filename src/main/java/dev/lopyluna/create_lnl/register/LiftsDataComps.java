package dev.lopyluna.create_lnl.register;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.UUID;

import static dev.lopyluna.create_lnl.Lifts.REGISTER;

public class LiftsDataComps {

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> SUBLEVEL_UUID = REGISTER.components()
            .registerComponentType("sub_level_uuid", b -> b
                    .persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).cacheEncoding());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> FIRST_POS = REGISTER.components()
            .registerComponentType("first_pos", b -> b
                    .persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC).cacheEncoding());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Direction>> FIRST_DIR = REGISTER.components()
            .registerComponentType("first_dir", b -> b
                    .persistent(Direction.CODEC).networkSynchronized(Direction.STREAM_CODEC).cacheEncoding());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> STICKY = REGISTER.components()
            .registerComponentType("sticky", b -> b
                    .persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)).cacheEncoding());

    public static void register() {}
}
