package dev.lopyluna.create_lnl.content.blocks.contraption_lift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record LiftPlayerData(Optional<UUID> heldSubLevel, Optional<GlobalPos> lift, int rotation, int color) {
    public static final LiftPlayerData EMPTY = new LiftPlayerData(Optional.empty(), Optional.empty(), 0, -1);

    public static final Codec<LiftPlayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("held_sub_level").forGetter(LiftPlayerData::heldSubLevel),
            GlobalPos.CODEC.optionalFieldOf("lift").forGetter(LiftPlayerData::lift),
            Codec.INT.optionalFieldOf("rotation", 0).forGetter(LiftPlayerData::rotation),
            Codec.INT.optionalFieldOf("color", -1).forGetter(LiftPlayerData::color)
    ).apply(i, LiftPlayerData::new));

    public static final StreamCodec<ByteBuf, LiftPlayerData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), LiftPlayerData::heldSubLevel,
            ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), LiftPlayerData::lift,
            ByteBufCodecs.VAR_INT, LiftPlayerData::rotation,
            ByteBufCodecs.VAR_INT, LiftPlayerData::color,
            LiftPlayerData::new
    );

    public boolean isHolding() { return heldSubLevel.isPresent(); }
    public LiftPlayerData withHeld(Optional<UUID> held) { return new LiftPlayerData(held, lift, held.isPresent() ? rotation : 0, color); }
    public LiftPlayerData withLift(Optional<GlobalPos> pos) { return new LiftPlayerData(heldSubLevel, pos, rotation, color); }
    public LiftPlayerData withRotation(int value) { return new LiftPlayerData(heldSubLevel, lift, Math.floorMod(value, LiftPlacement.ROTATION_STEPS), color); }
    public LiftPlayerData withColor(int value) { return new LiftPlayerData(heldSubLevel, lift, rotation, value); }
}
