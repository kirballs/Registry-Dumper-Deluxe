package com.registrydumper3000.dump;

import com.google.gson.*;
import com.registrydumper3000.RegistryDumper3000;
import com.registrydumper3000.config.DumpConfig;
import com.registrydumper3000.util.DumpHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dumps every built-in registry obtained from {@link BuiltInRegistries#REGISTRY}.
 * Supports both TXT and JSON output formats.
 */
public class BuiltinRegistryDumper {

    public static void dump(Path outputRoot) {
        Path dir = outputRoot.resolve("registry_builtin");
        try {
            DumpHelper.ensureDir(dir);
        } catch (IOException e) {
            RegistryDumper3000.LOGGER.error("Failed to create builtin registry dump dir", e);
            return;
        }

        StringBuilder summary = new StringBuilder();
        int totalRegistries = 0;
        int totalEntries = 0;

        // Iterate over every known built-in registry
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            ResourceKey<? extends Registry<?>> registryKey = registry.key();
            String registryName = registryKey.location().toString();
            totalRegistries++;

            int count = registry.size();
            totalEntries += count;

            try {
                if (DumpConfig.format == DumpConfig.OutputFormat.TXT) {
                    dumpTxt(registry, registryName, dir);
                } else {
                    dumpJson(registry, registryName, dir);
                }
            } catch (Exception e) {
                RegistryDumper3000.LOGGER.error("Failed to dump registry: {}", registryName, e);
            }

            summary.append(String.format("  %s: %d entries%n", registryName, count));
        }

        // Write summary
        Path summaryFile = dir.resolve("_summary.txt");
        try {
            BufferedWriter w = Files.newBufferedWriter(summaryFile);
            w.write("Built-in Registry Dump Summary\n");
            w.write("===============================\n");
            w.write(String.format("Total registries : %d%n", totalRegistries));
            w.write(String.format("Total entries     : %d%n", totalEntries));
            w.write("\nPer-registry breakdown:\n");
            w.write(summary.toString());
            w.close();
        } catch (IOException e) {
            RegistryDumper3000.LOGGER.error("Failed to write builtin summary", e);
        }

        RegistryDumper3000.LOGGER.info("Dumped {} built-in registries ({} total entries)",
                totalRegistries, totalEntries);
    }

    // ---------------------------------------------------------------
    //  TXT output
    // ---------------------------------------------------------------
    private static <T> void dumpTxt(Registry<T> registry, String registryName, Path dir) throws IOException {
        Path file = dir.resolve(DumpHelper.safeFileName(registryName) + ".txt");
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write(String.format("Registry: %s%n", registryName));
            w.write(String.format("Entries:  %d%n%n", registry.size()));

            for (Holder<T> holder : registry) {
                ResourceKey<T> key = holder.unwrapKey().orElse(null);
                if (key == null) continue;

                w.write(key.location().toString());

                if (DumpConfig.includeClassNamesVal && holder.value() != null) {
                    w.write(" -> ");
                    w.write(holder.value().getClass().getName());
                }
                w.write("\n");
            }
        }
    }

    // ---------------------------------------------------------------
    //  JSON output
    // ---------------------------------------------------------------
    private static <T> void dumpJson(Registry<T> registry, String registryName, Path dir) throws IOException {
        JsonArray entries = new JsonArray();

        for (Holder<T> holder : registry) {
            ResourceKey<T> key = holder.unwrapKey().orElse(null);
            if (key == null) continue;

            JsonObject entry = new JsonObject();
            entry.addProperty("id", key.location().toString());
            entry.addProperty("namespace", key.location().getNamespace());
            entry.addProperty("path", key.location().getPath());

            if (DumpConfig.includeClassNamesVal && holder.value() != null) {
                entry.addProperty("class", holder.value().getClass().getName());
            }

            entries.add(entry);
        }

        JsonObject root = new JsonObject();
        root.addProperty("registry", registryName);
        root.addProperty("count", registry.size());
        root.add("entries", entries);

        Path file = dir.resolve(DumpHelper.safeFileName(registryName) + ".json");
        DumpHelper.writeJson(file, root, DumpConfig.prettyPrintVal);
    }
}
