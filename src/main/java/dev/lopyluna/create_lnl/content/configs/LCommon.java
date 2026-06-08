package dev.lopyluna.create_lnl.content.configs;

import net.createmod.catnip.config.ConfigBase;

@SuppressWarnings("NullableProblems")
public class LCommon extends ConfigBase {
    public final ConfigGroup common = group(0,
            "common", "Configs for the General Game");


    @Override
    public String getName() {
        return "common";
    }
}
