package dev.lopyluna.create_lnl.register.client;

import com.simibubi.create.Create;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.eriksonn.aeronautics.Aeronautics;
import dev.lopyluna.create_lnl.Lifts;
import dev.ryanhcode.offroad.Offroad;
import dev.simulated_team.simulated.Simulated;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.lang.Lang;
import net.minecraft.core.Direction;

import java.util.EnumMap;
import java.util.Map;

@SuppressWarnings("unused")
public class LiftsPartialModels {
    public static final PartialModel
            LIFT_ANIM_BASE = block("contraption_lift/animation/base"),

            SLIME_MONSTROUS_TIRE = item("monstrous_slime_tire/monstrous_slime_tire"),
            SLIME_LARGE_TIRE = item("large_slime_tire/large_slime_tire"),
            SLIME_TIRE = item("slime_tire/slime_tire"),
            SLIME_SMALL_TIRE = item("small_slime_tire/small_slime_tire"),

            MONSTROUS_TIRE = itemO("monstrous_tire/monstrous_tire"),
            LARGE_TIRE = itemO("large_tire/large_tire"),
            TIRE = itemO("tire/tire"),
            SMALL_TIRE = itemO("small_tire/small_tire"),

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

    private static PartialModel block(String path) { return PartialModel.of(Lifts.loc("block/" + path)); }
    private static PartialModel blockC(String path) { return PartialModel.of(Create.asResource("block/" + path)); }
    private static PartialModel blockS(String path) { return PartialModel.of(Simulated.path("block/" + path)); }
    private static PartialModel blockO(String path) { return PartialModel.of(Offroad.path("block/" + path)); }
    private static PartialModel blockA(String path) { return PartialModel.of(Aeronautics.path("block/" + path)); }

    private static PartialModel item(String path) { return PartialModel.of(Lifts.loc("item/" + path)); }
    private static PartialModel itemC(String path) { return PartialModel.of(Create.asResource("item/" + path)); }
    private static PartialModel itemS(String path) { return PartialModel.of(Simulated.path("item/" + path)); }
    private static PartialModel itemO(String path) { return PartialModel.of(Offroad.path("item/" + path)); }
    private static PartialModel itemA(String path) { return PartialModel.of(Aeronautics.path("item/" + path)); }

    public static void init() {
    }
}
