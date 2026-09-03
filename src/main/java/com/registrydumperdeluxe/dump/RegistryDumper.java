package com.registrydumperdeluxe.dump;

import com.google.gson.*;
import com.registrydumperdeluxe.RegistryDumperDeluxe;
import com.registrydumperdeluxe.config.DumpConfig;
import com.registrydumperdeluxe.util.DumpHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class RegistryDumper {

    public static void dumpAll(MinecraftServer server, Path dir) {
        dumpRegistry(dir, "items",        "item");
        dumpRegistry(dir, "entities",     "entity_type");
        dumpRegistry(dir, "sound_events", "sound_event");
        dumpRegistry(dir, "biomes",       "worldgen/biome", "biome");
        dumpRegistry(dir, "structures",   "structure", "worldgen/structure");
        dumpRegistry(dir, "features",     "worldgen/feature", "feature");
    }

    private static void dumpRegistry(Path dir, String fileName, String... possiblePaths) {
        Registry<?> registry = findRegistry(possiblePaths);
        if (registry == null) {
            RegistryDumperDeluxe.LOGGER.warn("Registry not found for: {}", fileName);
            return;
        }

        Map<String, List<String>> grouped = groupByNamespace(registry);

        if (DumpConfig.persistentTrackingVal) {
            mergeWithExisting(dir, fileName, grouped);
        }

        writeFile(dir, fileName, grouped);

        int total = grouped.values().stream().mapToInt(List::size).sum();
        RegistryDumperDeluxe.LOGGER.info("Dumped {} ({} namespaces, {} entries)", fileName, grouped.size(), total);
    }

    private static Registry<?> findRegistry(String... possiblePaths) {
        for (Registry<?> reg : BuiltInRegistries.REGISTRY) {
            String path = reg.key().location().getPath();
            for (String target : possiblePaths) {
                if (path.equals(target)) return reg;
            }
        }
        for (Registry<?> reg : BuiltInRegistries.REGISTRY) {
            String path = reg.key().location().getPath();
            for (String target : possiblePaths) {
                if (path.endsWith("/" + target)) return reg;
            }
        }
        return null;
    }

    private static Map<String, List<String>> groupByNamespace(Registry<?> registry) {
        Map<String, List<String>> grouped = new TreeMap<>();
        for (ResourceLocation location : registry.keySet()) {
            String ns = location.getNamespace();
            String id = location.toString();
            grouped.computeIfAbsent(ns, k -> new ArrayList<>()).add(id);
        }
        return grouped;
    }

    @SuppressWarnings("unchecked")
    private static void mergeWithExisting(Path dir, String fileName,
                                          Map<String, List<String>> current) {
        Path file = dir.resolve(fileName + ".json");
        if (!Files.exists(file)) return;

        try {
            JsonElement el = DumpHelper.readJson(file);
            if (el == null || !el.isJsonObject()) return;
            JsonObject existing = el.getAsJsonObject();

            for (String namespace : existing.keySet()) {
                if (!current.containsKey(namespace)) {
                    JsonArray arr = existing.getAsJsonArray(namespace);
                    List<String> entries = new ArrayList<>();
                    for (JsonElement e : arr) entries.add(e.getAsString());
                    current.put(namespace, entries);
                } else {
                    JsonArray arr = existing.getAsJsonArray(namespace);
                    Set<String> existingIds = new HashSet<>();
                    List<String> merged = new ArrayList<>();
                    for (JsonElement e : arr) {
                        String id = e.getAsString();
                        existingIds.add(id);
                        merged.add(id);
                    }
                    for (String id : current.get(namespace)) {
                        if (!existingIds.contains(id)) {
                            merged.add(id);
                        }
                    }
                    current.put(namespace, merged);
                }
            }
        } catch (Exception e) {
            RegistryDumperDeluxe.LOGGER.debug("Could not merge persistent data for {}", fileName);
        }
    }

    private static void writeFile(Path dir, String fileName, Map<String, List<String>> grouped) {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, List<String>> entry : grouped.entrySet()) {
            JsonArray arr = new JsonArray();
            for (String id : entry.getValue()) {
                arr.add(id);
            }
            root.add(entry.getKey(), arr);
        }
        try {
            DumpHelper.writeJson(dir.resolve(fileName + ".json"), root, true);
        } catch (IOException e) {
            RegistryDumperDeluxe.LOGGER.error("Failed to write {}", fileName, e);
        }
    }
}
