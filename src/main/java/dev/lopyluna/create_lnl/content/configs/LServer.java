package dev.lopyluna.create_lnl.content.configs;

import dev.lopyluna.create_lnl.content.configs.server.LKinetics;
import net.createmod.catnip.config.ConfigBase;

@SuppressWarnings("NullableProblems")
public class LServer extends ConfigBase {
    public final ConfigGroup server = group(0,
            "server", "Configs for the World");

    public final LKinetics kinetics = nested(0, LKinetics::new, "Parameters and abilities of Create: Desires 2 Dream's kinetic mechanisms");

    @Override
    public String getName() {
        return "server";
    }
}
