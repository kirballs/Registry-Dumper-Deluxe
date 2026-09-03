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
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.resource.ResourceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/**
 * Dumps 9 specific registry/resource types as simple JSON files.
 * Each file groups IDs by namespace (mod id).
 *
 * Output files: items.json, entities.json, tags.json, biomes.json,
 * structures.json, features.json, advancements.json, sound_events.json, loot_tables.json
 */
public class RegistryDumper {

    // ==================================================================
    //  Public API
    // ==================================================================

    public static void dumpAll(MinecraftServer server, ResourceManager rm, Path dir) {
        // Registry-based (from BuiltInRegistries)
        dumpRegistry(dir, "items",        "item");
        dumpRegistry(dir, "entities",     "entity_type");
        dumpRegistry(dir, "sound_events", "sound_event");
        dumpRegistry(dir, "biomes",       "worldgen/biome", "biome");
        dumpRegistry(dir, "structures",   "structure", "worldgen/structure");
        dumpRegistry(dir, "features",     "worldgen/feature", "feature");

        // Resource-based (from ResourceManager / datapacks)
        if (rm != null) {
            dumpResources(dir, "tags",         rm, "tags",         null);
            dumpResources(dir, "advancements", rm, "advancements", null);
            dumpResources(dir, "loot_tables",  rm, "loot_tables",
                          rel -> !rel.startsWith("blocks/"));
        } else {
            RegistryDumper3000.LOGGER.warn("ResourceManager is null - skipping tags, advancements, loot_tables");
        }
    }

    // ==================================================================
    //  Registry dumping
    // ==================================================================

    private static void dumpRegistry(Path dir, String fileName, String... possiblePaths) {
        Registry<?> registry = findRegistry(possiblePaths);
        if (registry == null) {
            RegistryDumper3000.LOGGER.warn("Registry not found for: {}", fileName);
            return;
        }

        Map<String, List<String>> grouped = groupByNamespace(registry);

        if (DumpConfig.persistentTrackingVal) {
            mergeWithExisting(dir, fileName, grouped);
        }

        writeFile(dir, fileName, grouped);

        int total = grouped.values().stream().mapToInt(List::size).sum();
        RegistryDumper3000.LOGGER.info("Dumped {} ({} namespaces, {} entries)", fileName, grouped.size(), total);
    }

    // ==================================================================
    //  Resource dumping (tags, advancements, loot tables)
    // ==================================================================

    private static void dumpResources(Path dir, String fileName, ResourceManager rm,
                                       String prefix, Predicate<String> pathFilter) {
        Map<String, List<String>> grouped = new TreeMap<>();

        for (ResourceLocation rl : rm.listResources(prefix, p -> true)) {
            String fullPath = rl.getPath();
            // Get the part after the prefix, e.g. "blocks/stone" from "loot_tables/blocks/stone"
            String relative = fullPath.substring(prefix.length());
            if (relative.startsWith("/")) relative = relative.substring(1);

            if (pathFilter != null && !pathFilter.test(relative)) continue;

            String ns = rl.getNamespace();
            String id = ns + ":" + fullPath;
            grouped.computeIfAbsent(ns, k -> new ArrayList<>()).add(id);
        }

        if (DumpConfig.persistentTrackingVal) {
            mergeWithExisting(dir, fileName, grouped);
        }

        writeFile(dir, fileName, grouped);

        int total = grouped.values().stream().mapToInt(List::size).sum();
        RegistryDumper3000.LOGGER.info("Dumped {} ({} namespaces, {} entries)", fileName, grouped.size(), total);
    }

    // ==================================================================
    //  Registry lookup
    // ==================================================================

    /**
     * Finds a built-in registry trying multiple possible key paths.
     * For example, biome can be registered as "worldgen/biome" or just "biome".
     */
    private static Registry<?> findRegistry(String... possiblePaths) {
        for (Registry<?> reg : BuiltInRegistries.REGISTRY) {
            String path = reg.key().location().getPath();
            for (String target : possiblePaths) {
                if (path.equals(target)) return reg;
            }
        }
        // Fallback: match by suffix (e.g. "biome" matches "worldgen/biome")
        for (Registry<?> reg : BuiltInRegistries.REGISTRY) {
            String path = reg.key().location().getPath();
            for (String target : possiblePaths) {
                if (path.endsWith("/" + target)) return reg;
            }
        }
        return null;
    }

    // ==================================================================
    //  Grouping
    // ==================================================================

    private static Map<String, List<String>> groupByNamespace(Registry<?> registry) {
        Map<String, List<String>> grouped = new TreeMap<>();
        for (Holder<?> holder : registry) {
            ResourceKey<?> key = holder.unwrapKey().orElse(null);
            if (key == null) continue;
            String ns = key.location().getNamespace();
            String id = key.location().toString();
            grouped.computeIfAbsent(ns, k -> new ArrayList<>()).add(id);
        }
        return grouped;
    }

    // ==================================================================
    //  Persistent tracking
    // ==================================================================

    /**
     * Merges current data with existing file on disk.
     * - Namespaces from removed mods: all entries are preserved.
     * - Namespaces from current mods: existing entries kept, only new IDs appended.
     */
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
                    // Mod no longer loaded - preserve every entry
                    JsonArray arr = existing.getAsJsonArray(namespace);
                    List<String> entries = new ArrayList<>();
                    for (JsonElement e : arr) entries.add(e.getAsString());
                    current.put(namespace, entries);
                } else {
                    // Mod still loaded - keep old entries, append only new ones
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
            RegistryDumper3000.LOGGER.debug("Could not merge persistent data for {}", fileName);
        }
    }

    // ==================================================================
    //  File I/O
    // ==================================================================

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
            RegistryDumper3000.LOGGER.error("Failed to write {}", fileName, e);
        }
    }
}
