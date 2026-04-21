package dev.lopyluna.create_lnl.register.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.lopyluna.create_lnl.Lifts;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.lang.Lang;
import net.minecraft.core.Direction;

import java.util.EnumMap;
import java.util.Map;

@SuppressWarnings("unused")
public class LiftsPartialModels {
    public static final PartialModel
            LIFT_ANIM_BASE = block("contraption_lift/animation/base"),

            LIFT_BOTTOM = block("contraption_lift/bottom"),
            LIFT_MIDDLE = block("contraption_lift/middle"),
            LIFT_TOP = block("contraption_lift/top"),
            LIFT = block("contraption_lift/block")
    ;

    public static final Map<Direction, PartialModel> LIFT_ANIM_FLAPS = new EnumMap<>(Direction.class);
    public static final Map<Direction, PartialModel> LIFT_ANIM_FOOTS = new EnumMap<>(Direction.class);

    static {
        for (var d : Iterate.horizontalDirections) {
            LIFT_ANIM_FLAPS.put(d, block("contraption_lift/animation/" + Lang.asId(d.name()) + "_flap"));
            LIFT_ANIM_FOOTS.put(d, block("contraption_lift/animation/" + Lang.asId(d.name()) + "_foot"));
        }
    }

    private static PartialModel block(String path) {
        return PartialModel.of(Lifts.loc("block/" + path));
    }

    public static void init() {
    }
}
