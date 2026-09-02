package com.registrydumper3000.dump;

import com.google.gson.*;
import com.registrydumper3000.RegistryDumper3000;
import com.registrydumper3000.config.DumpConfig;
import com.registrydumper3000.util.DumpHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Dumps the final server-side state of all registries via
 * {@link MinecraftServer#registryAccess()}.  This includes mod-added
 * runtime registries and the frozen state seen by the server.
 */
public class RuntimeRegistryDumper {

    /**
     * Well-known registry keys we can ask the RegistryAccess for.
     * We also collect every key from BuiltInRegistries.REGISTRY so that
     * modded registries registered there are included.
     */
    @SuppressWarnings("unchecked")
    public static void dump(Path outputRoot, MinecraftServer server) {
        Path dir = outputRoot.resolve("registry_runtime");
        try {
            DumpHelper.ensureDir(dir);
        } catch (IOException e) {
            RegistryDumper3000.LOGGER.error("Failed to create runtime registry dump dir", e);
            return;
        }

        // Collect every registry key we know about
        Set<ResourceKey<? extends Registry<?>>> keys = new LinkedHashSet<>();

        // Standard vanilla registries
        for (Registry<?> reg : BuiltInRegistries.REGISTRY) {
            keys.add(reg.key());
        }

        var access = server.registryAccess();

        StringBuilder summary = new StringBuilder();
        int totalRegistries = 0;
        int totalEntries = 0;

        for (ResourceKey<? extends Registry<?>> rk : keys) {
            Optional<? extends Registry<?>> optReg = access.registry(rk);
            if (optReg.isEmpty()) continue;

            Registry<?> registry = optReg.get();
            String registryName = rk.location().toString();
            int count = registry.size();
            totalRegistries++;
            totalEntries += count;

            try {
                if (DumpConfig.format == DumpConfig.OutputFormat.TXT) {
                    dumpTxt(registry, registryName, dir);
                } else {
                    dumpJson(registry, registryName, dir);
                }
            } catch (Exception e) {
                RegistryDumper3000.LOGGER.error("Failed to dump runtime registry: {}", registryName, e);
            }

            summary.append(String.format("  %s: %d entries%n", registryName, count));
        }

        // Summary
        Path summaryFile = dir.resolve("_summary.txt");
        try (BufferedWriter w = Files.newBufferedWriter(summaryFile)) {
            w.write("Runtime Registry Dump Summary\n");
            w.write("==============================\n");
            w.write(String.format("Total registries : %d%n", totalRegistries));
            w.write(String.format("Total entries     : %d%n", totalEntries));
            w.write("\nPer-registry breakdown:\n");
            w.write(summary.toString());
        } catch (IOException e) {
            RegistryDumper3000.LOGGER.error("Failed to write runtime summary", e);
        }

        RegistryDumper3000.LOGGER.info("Dumped {} runtime registries ({} total entries)",
                totalRegistries, totalEntries);
    }

    private static <T> void dumpTxt(Registry<T> registry, String registryName, Path dir) throws IOException {
        Path file = dir.resolve(DumpHelper.safeFileName(registryName) + ".txt");
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write(String.format("Registry: %s  (runtime / frozen)%n", registryName));
            w.write(String.format("Entries:  %d%n%n", registry.size()));

            for (ResourceLocation id : registry.keySet()) {
                T value = registry.get(id);
                w.write(id.toString());
                if (DumpConfig.includeClassNamesVal && value != null) {
                    w.write(" -> ");
                    w.write(value.getClass().getName());
                }
                w.write("\n");
            }
        }
    }

    private static <T> void dumpJson(Registry<T> registry, String registryName, Path dir) throws IOException {
        JsonArray entries = new JsonArray();
        for (ResourceLocation id : registry.keySet()) {
            T value = registry.get(id);
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id.toString());
            entry.addProperty("namespace", id.getNamespace());
            entry.addProperty("path", id.getPath());
            if (DumpConfig.includeClassNamesVal && value != null) {
                entry.addProperty("class", value.getClass().getName());
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
