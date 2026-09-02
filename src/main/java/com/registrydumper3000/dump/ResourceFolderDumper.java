package com.registrydumper3000.dump;

import com.registrydumper3000.RegistryDumper3000;
import com.registrydumper3000.config.DumpConfig;
import com.registrydumper3000.util.DumpHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Dumps generic datapack resource folders (loot_tables, predicates, advancements,
 * functions, worldgen, dimension, damage_type, etc.) by copying raw files from
 * the ResourceManager.
 */
public class ResourceFolderDumper {

    /**
     * Default folders to dump.  Each entry is the top-level data folder name
     * (e.g. "loot_tables" matches data/[namespace]/loot_tables/...).
     */
    private static final List<String> DEFAULT_FOLDERS = List.of(
            "loot_tables",
            "predicates",
            "item_modifiers",
            "advancements",
            "functions",
            "structures",
            "worldgen",
            "dimension",
            "dimension_type",
            "damage_type",
            "chat_type",
            "trim_material",
            "trim_pattern",
            "painting_variant",
            "instrument",
            "jukebox_song"
    );

    public static void dump(Path outputRoot, ResourceManager rm) {
        if (rm == null) {
            RegistryDumper3000.LOGGER.warn("ResourceManager is null — skipping resource folder dump.");
            return;
        }

        Path dir = outputRoot.resolve("data_raw");
        try {
            DumpHelper.ensureDir(dir);
        } catch (IOException e) {
            RegistryDumper3000.LOGGER.error("Failed to create data_raw dir", e);
            return;
        }

        StringBuilder summary = new StringBuilder();
        int totalFiles = 0;

        for (String folder : DEFAULT_FOLDERS) {
            int count = dumpFolder(rm, dir, folder);
            totalFiles += count;
            summary.append(String.format("  %s: %d files%n", folder, count));
        }

        // Summary
        Path summaryFile = dir.resolve("_summary.txt");
        try (BufferedWriter w = Files.newBufferedWriter(summaryFile)) {
            w.write("Resource Folder Dump Summary\n");
            w.write("==============================\n");
            w.write(String.format("Total folders scanned: %d%n", DEFAULT_FOLDERS.size()));
            w.write(String.format("Total files dumped  : %d%n", totalFiles));
            w.write("\nPer-folder:\n");
            w.write(summary.toString());
        } catch (IOException e) {
            RegistryDumper3000.LOGGER.error("Failed to write resource folder summary", e);
        }

        RegistryDumper3000.LOGGER.info("Dumped {} resource files from {} folders",
                totalFiles, DEFAULT_FOLDERS.size());
    }

    /**
     * Dumps all resources under a given top-level data folder.
     * Uses the highest-priority resource for each path.
     */
    private static int dumpFolder(ResourceManager rm, Path outputDir, String folder) {
        // List all resources under this folder
        Set<ResourceLocation> resources = rm.listResources(folder, path -> true).keySet();

        int count = 0;
        for (ResourceLocation rl : resources) {
            try {
                var stack = rm.getResourceStack(rl);
                if (stack.isEmpty()) continue;

                // Use the top-priority resource
                var res = stack.get(0);
                try (InputStream is = res.open()) {
                    // Preserve namespace and path in output: <folder>/<namespace>_<path>
                    String relativePath = rl.getNamespace() + "_" +
                            rl.getPath().replace('/', '_');
                    Path outPath = outputDir.resolve(folder).resolve(relativePath);
                    DumpHelper.copyStreamToFile(is, outPath);
                    count++;
                }
            } catch (Exception e) {
                RegistryDumper3000.LOGGER.debug("Could not copy resource {}: {}", rl, e.getMessage());
            }
        }
        return count;
    }
}
