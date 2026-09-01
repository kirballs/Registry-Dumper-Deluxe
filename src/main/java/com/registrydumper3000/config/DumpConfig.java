package com.registrydumper3000.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.nio.file.Path;
import java.nio.file.Paths;

public class DumpConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue dumpBuiltinRegistries;
    public static final ForgeConfigSpec.BooleanValue dumpRuntimeRegistries;
    public static final ForgeConfigSpec.BooleanValue dumpTags;
    public static final ForgeConfigSpec.BooleanValue dumpRecipes;
    public static final ForgeConfigSpec.BooleanValue dumpResourceFolders;
    public static final ForgeConfigSpec.BooleanValue persistentTracking;
    public static final ForgeConfigSpec.BooleanValue includeClassNames;
    public static final ForgeConfigSpec.BooleanValue prettyPrint;
    public static final ForgeConfigSpec.EnumValue<OutputFormat> outputFormat;
    public static final ForgeConfigSpec.ConfigValue<String> outputFolderStr;

    // Cached values (loaded once per dump)
    public static Path outputFolder;
    public static OutputFormat format;
    public static boolean dumpBuiltinRegistriesVal;
    public static boolean dumpRuntimeRegistriesVal;
    public static boolean dumpTagsVal;
    public static boolean dumpRecipesVal;
    public static boolean dumpResourceFoldersVal;
    public static boolean persistentTrackingVal;
    public static boolean includeClassNamesVal;
    public static boolean prettyPrintVal;

    public enum OutputFormat {
        TXT, JSON
    }

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Registry Dumper 3000 Configuration")
               .push("general");

        dumpBuiltinRegistries = builder
                .comment("Dump all built-in registries (blocks, items, fluids, entity types, etc.)")
                .define("dumpBuiltinRegistries", true);

        dumpRuntimeRegistries = builder
                .comment("Dump all runtime registries from the server registry access.")
                .define("dumpRuntimeRegistries", true);

        dumpTags = builder
                .comment("Dump raw and expanded tags.")
                .define("dumpTags", true);

        dumpRecipes = builder
                .comment("Dump raw recipe JSONs grouped by type.")
                .define("dumpRecipes", true);

        dumpResourceFolders = builder
                .comment("Dump generic datapack resource folders (loot_tables, predicates, advancements, etc.)")
                .define("dumpResourceFolders", true);

        persistentTracking = builder
                .comment("Track all mods and their registry entries persistently across startups.",
                         "Entries from removed mods are NEVER deleted.",
                         "Entries from existing mods are never duplicated.")
                .define("persistentTracking", true);

        includeClassNames = builder
                .comment("Include Java class names for registry entries (useful for modded debugging).")
                .define("includeClassNames", true);

        prettyPrint = builder
                .comment("Pretty-print JSON output files.")
                .define("prettyPrint", true);

        outputFormat = builder
                .comment("Output format for registry dumps: TXT or JSON.")
                .defineEnum("outputFormat", OutputFormat.TXT);

        outputFolderStr = builder
                .comment("Output folder for all dumps (relative to the game/run directory).")
                .define("outputFolder", "dump");

        builder.pop();

        SPEC = builder.build();
    }

    /**
     * Reload cached values from the Forge config spec.
     * Call once at the start of each dump cycle.
     */
    public static void load() {
        outputFolder = Paths.get(outputFolderStr.get());
        format = outputFormat.get();
        dumpBuiltinRegistriesVal = dumpBuiltinRegistries.get();
        dumpRuntimeRegistriesVal = dumpRuntimeRegistries.get();
        dumpTagsVal = dumpTags.get();
        dumpRecipesVal = dumpRecipes.get();
        dumpResourceFoldersVal = dumpResourceFolders.get();
        persistentTrackingVal = persistentTracking.get();
        includeClassNamesVal = includeClassNames.get();
        prettyPrintVal = prettyPrint.get();
    }
}
