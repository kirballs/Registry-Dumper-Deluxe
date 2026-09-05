package com.registrydumperdeluxe.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DumpConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<String> outputFolderStr;
    public static final ForgeConfigSpec.BooleanValue persistentTracking;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> blacklistStr;

    public static Path outputFolder;
    public static boolean persistentTrackingVal;
    public static Set<String> blacklist;

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

        blacklistStr = builder
                .comment("Mod IDs to exclude from ALL dumps.",
                         "Entries from blacklisted mods are wiped from JSON files on startup.",
                         "Current entries from these mods are also skipped.",
                         "Example: [\"alexsmobs\", \"create\"]")
                .defineList("blacklist", Collections.emptyList(), obj -> obj instanceof String);

        builder.pop();
        SPEC = builder.build();
    }

    public static void load() {
        outputFolder = Paths.get(outputFolderStr.get());
        persistentTrackingVal = persistentTracking.get();
        blacklist = new HashSet<>(blacklistStr.get());
    }

    /** Check if a namespace (mod ID) is blacklisted. */
    public static boolean isBlacklisted(String namespace) {
        return blacklist.contains(namespace);
    }
}
