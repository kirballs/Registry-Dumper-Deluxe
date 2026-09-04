package com.registrydumperdeluxe.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.nio.file.Path;
import java.nio.file.Paths;

public class DumpConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<String> outputFolderStr;
    public static final ForgeConfigSpec.BooleanValue persistentTracking;

    public static Path outputFolder;
    public static boolean persistentTrackingVal;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Registry Dumper Deluxe Configuration").push("general");

        outputFolderStr = builder
                .comment("Output folder for dumps (relative to game directory)")
                .define("outputFolder", "dump");

        persistentTracking = builder
                .comment("Track mods across startups.",
                         "Entries from removed mods are NEVER deleted.",
                         "Entries from currently-loaded mods are never duplicated.")
                .define("persistentTracking", true);

        builder.pop();
        SPEC = builder.build();
    }

    public static void load() {
        outputFolder = Paths.get(outputFolderStr.get());
        persistentTrackingVal = persistentTracking.get();
    }
}
