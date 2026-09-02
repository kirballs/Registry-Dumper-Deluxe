package com.registrydumper3000.dump;

import com.google.gson.*;
import com.registrydumper3000.RegistryDumper3000;
import com.registrydumper3000.config.DumpConfig;
import com.registrydumper3000.util.DumpHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraftforge.resource.ResourceManager;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dumps tags in two ways:
 * <ol>
 *   <li><b>Raw tag JSONs</b> - copied straight from the resource packs (data/[namespace]/tags/...).</li>
 *   <li><b>Expanded runtime tags</b> - the final merged tag contents after all
 *       datapacks / mods have been applied.</li>
 * </ol>
 */
public class TagDumper {

    // ------------------------------------------------------------------
    //  Public entry point
    // ------------------------------------------------------------------
    public static void dump(Path outputRoot, MinecraftServer server, ResourceManager resourceManager) {
        dumpRawTags(outputRoot, resourceManager);
        dumpExpandedTags(outputRoot, server);
    }

    // ==================================================================
    //  1. RAW TAG JSONs
    // ==================================================================
    private static void dumpRawTags(Path outputRoot, ResourceManager rm) {
        if (rm == null) {
            RegistryDumper3000.LOGGER.warn("ResourceManager is null — skipping raw tag dump.");
            return;
        }

        Path rawDir = outputRoot.resolve("tags");
        try {
            DumpHelper.ensureDir(rawDir);
        } catch (IOException e) {
            RegistryDumper3000.LOGGER.error("Failed to create tags dir", e);
            return;
        }

        int count = 0;

        // List all resources under data/*/tags/
        Collection<ResourceLocation> allResources = rm.listResources("tags", path -> true);

        // Build a summary grouped by namespace
        Map<String, List<ResourceLocation>> byNamespace = new TreeMap<>();
        for (ResourceLocation rl : allResources) {
            String ns = rl.getNamespace();
            byNamespace.computeIfAbsent(ns, k -> new ArrayList<>()).add(rl);
        }

        for (Map.Entry<String, List<ResourceLocation>> entry : byNamespace.entrySet()) {
            String ns = entry.getKey();
            for (ResourceLocation rl : entry.getValue()) {
                try {
                    var resources = rm.getResourceStack(rl);
                    if (resources.isEmpty()) continue;

                    // Use the first (top-priority) resource
                    var res = resources.get(0);
                    try (InputStream is = res.open()) {
                        // Preserve the folder structure: tags/<namespace>/<path>
                        Path outPath = rawDir.resolve(ns)
                                .resolve(rl.getPath().replace('/', '_') + ".json");
                        DumpHelper.copyStreamToFile(is, outPath);
                        count++;
                    }
                } catch (Exception e) {
                    RegistryDumper3000.LOGGER.debug("Could not copy tag {}: {}", rl, e.getMessage());
                }
            }
        }

        // Summary
        Path summaryFile = rawDir.resolve("_summary.txt");
        try (BufferedWriter w = Files.newBufferedWriter(summaryFile)) {
            w.write("Raw Tag Dump Summary\n");
            w.write("====================\n");
            w.write(String.format("Total tag files: %d%n", count));
            w.write("\nBy namespace:\n");
            for (Map.Entry<String, List<ResourceLocation>> entry : byNamespace.entrySet()) {
                w.write(String.format("  %s: %d tag files%n", entry.getKey(), entry.getValue().size()));
            }
        } catch (IOException e) {
            RegistryDumper3000.LOGGER.error("Failed to write raw tag summary", e);
        }

        RegistryDumper3000.LOGGER.info("Dumped {} raw tag files", count);
    }

    // ==================================================================
    //  2. EXPANDED RUNTIME TAGS
    // ==================================================================
    @SuppressWarnings("unchecked")
    private static void dumpExpandedTags(Path outputRoot, MinecraftServer server) {
        Path expDir = outputRoot.resolve("registry_tags_expanded");
        try {
            DumpHelper.ensureDir(expDir);
        } catch (IOException e) {
            RegistryDumper3000.LOGGER.error("Failed to create expanded tags dir", e);
            return;
        }

        var access = server.registryAccess();
        StringBuilder summary = new StringBuilder();
        int totalTags = 0;

        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            String registryName = registry.key().location().toString();

            // Collect all tags for this registry
            Map<TagKey<?>, ? extends Collection<Holder<?>>> tagMap;
            try {
                tagMap = registry.getTags().collect(Collectors.toMap(
                        named -> named.key(),
                        named -> named.value()
                ));
            } catch (Exception e) {
                RegistryDumper3000.LOGGER.debug("No tags for registry {}", registryName);
                continue;
            }

            int tagCount = tagMap.size();
            totalTags += tagCount;

            try {
                if (DumpConfig.format == DumpConfig.OutputFormat.TXT) {
                    dumpExpandedTxt(tagMap, registryName, expDir);
                } else {
                    dumpExpandedJson(tagMap, registryName, expDir);
                }
            } catch (Exception e) {
                RegistryDumper3000.LOGGER.error("Failed to dump expanded tags for {}", registryName, e);
            }

            summary.append(String.format("  %s: %d tags%n", registryName, tagCount));
        }

        Path summaryFile = expDir.resolve("_summary.txt");
        try (BufferedWriter w = Files.newBufferedWriter(summaryFile)) {
            w.write("Expanded Tag Dump Summary\n");
            w.write("==========================\n");
            w.write(String.format("Total tags: %d%n", totalTags));
            w.write("\nPer-registry:\n");
            w.write(summary.toString());
        } catch (IOException e) {
            RegistryDumper3000.LOGGER.error("Failed to write expanded tag summary", e);
        }

        RegistryDumper3000.LOGGER.info("Dumped {} expanded tag sets", totalTags);
    }

    private static void dumpExpandedTxt(
            Map<TagKey<?>, ? extends Collection<Holder<?>>> tagMap,
            String registryName, Path dir) throws IOException {
        Path file = dir.resolve(DumpHelper.safeFileName(registryName) + "_tags.txt");
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write(String.format("Expanded Tags for: %s%n", registryName));
            w.write(String.format("Tag sets: %d%n%n", tagMap.size()));

            for (var entry : new TreeMap<>(tagMap).entrySet()) {
                TagKey<?> tagKey = entry.getKey();
                w.write(String.format("[%s]%n", tagKey.location().toString()));
                for (Holder<?> holder : entry.getValue()) {
                    w.write(String.format("  %s%n",
                            holder.unwrapKey()
                                    .map(k -> k.location().toString())
                                    .orElse("<unknown>")));
                }
                w.write("\n");
            }
        }
    }

    private static void dumpExpandedJson(
            Map<TagKey<?>, ? extends Collection<Holder<?>>> tagMap,
            String registryName, Path dir) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("registry", registryName);
        root.addProperty("tagCount", tagMap.size());

        JsonObject tagsObj = new JsonObject();
        for (var entry : new TreeMap<>(tagMap).entrySet()) {
            TagKey<?> tagKey = entry.getKey();
            JsonArray values = new JsonArray();
            for (Holder<?> holder : entry.getValue()) {
                values.add(holder.unwrapKey()
                        .map(k -> k.location().toString())
                        .orElse("<unknown>"));
            }
            tagsObj.add(tagKey.location().toString(), values);
        }
        root.add("tags", tagsObj);

        Path file = dir.resolve(DumpHelper.safeFileName(registryName) + "_tags.json");
        DumpHelper.writeJson(file, root, DumpConfig.prettyPrintVal);
    }
}
