package dev.lopyluna.create_lnl.content.configs;

import net.createmod.catnip.config.ConfigBase;

@SuppressWarnings("NullableProblems")
public class LClient extends ConfigBase {
    public final ConfigGroup client = group(0,
            "client", "Configs for the Client");

    @Override
    public String getName() {
        return "client";
    }
}
