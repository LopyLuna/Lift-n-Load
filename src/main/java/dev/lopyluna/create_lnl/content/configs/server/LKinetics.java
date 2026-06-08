package dev.lopyluna.create_lnl.content.configs.server;

import dev.lopyluna.create_lnl.content.configs.server.kinetics.LStress;
import net.createmod.catnip.config.ConfigBase;

@SuppressWarnings("NullableProblems")
public class LKinetics extends ConfigBase {

    public final LStress stressValues = nested(1, LStress::new, "Fine tune the kinetic stats of individual components");

    @Override
    public String getName() {
        return "kinetics";
    }
}
