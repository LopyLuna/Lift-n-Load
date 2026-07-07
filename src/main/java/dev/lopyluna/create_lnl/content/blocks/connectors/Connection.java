package dev.lopyluna.create_lnl.content.blocks.connectors;

import com.tterrag.registrate.util.nullness.NonNullSupplier;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class Connection {
    public static Static of(BlockEntity be, int rgb, @Nullable Type type) { return new Static(be, rgb, type); }
    public static Grabber of(BlockEntity be, NonNullSupplier<Integer> rgb, Supplier<Type> type) { return new Grabber(be, rgb, type); }
    public record Static(BlockEntity be, int rgb, @Nullable Type type) implements Self {}
    public record Grabber(BlockEntity be, NonNullSupplier<Integer> rgb, Supplier<Type> type) implements Self {}

    public static BlockEntity getBE(Self self) {
        return self instanceof Static s ? s.be : self instanceof Grabber g ? g.be : null;
    }
    public static int getRGB(Self self) {
        return self instanceof Static s ? s.rgb : self instanceof Grabber g ? g.rgb.get() : -1;
    }
    public static Type getType(Self self) {
        return self instanceof Static s ? s.type : self instanceof Grabber g ? g.type.get() : null;
    }

    public enum Type {
        IN,
        OUT,
        BOTH;

        @Nullable
        public static Type getType(Object obj) {
            return obj instanceof IConnection<?> c ? switch (c.getConnectType()) {
                case OUT -> OUT;
                case IN -> IN;
                case OTHER -> BOTH;
                case NONE -> null;
            }: null;
        }

        public <T> T choose(T in, T out, T both) {
            return choose(this, in, out, both);
        }
        public static <T> T choose(@Nullable Type type, T in, T out, T defaultValue) {
            return choose(type, in, out, defaultValue, defaultValue);
        }
        public static  <T> T choose(@Nullable Type type, T in, T out, T both, T defaultValue) {
            return type == null ? defaultValue : switch (type) {
                case IN -> in;
                case OUT -> out;
                case BOTH -> both;
            };
        }
    }
    public interface Self {}
}
