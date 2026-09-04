package com.registrydumperdeluxe.dump;

import com.google.gson.*;
import com.registrydumperdeluxe.RegistryDumperDeluxe;
import com.registrydumperdeluxe.config.DumpConfig;
import com.registrydumperdeluxe.util.DumpHelper;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

public class RegistryDumper {

    public static void dumpAll(MinecraftServer server, ResourceManager rm, Path dir) {
        // --- Static registries (from BuiltInRegistries) ---
        safeDump("items",        () -> dumpRegistry(dir, "items",        "item"));
        safeDump("entities",     () -> dumpRegistry(dir, "entities",     "entity_type"));
        safeDump("sound_events", () -> dumpRegistry(dir, "sound_events", "sound_event"));
        safeDump("features",     () -> dumpRegistry(dir, "features",     "worldgen/feature", "feature"));

        // --- Dynamic registries (from server.registryAccess()) ---
        // Biomes and structures are NOT in BuiltInRegistries in 1.20.1;
        // they are datapack-driven and only available via the server's RegistryAccess.
        safeDump("biomes",     () -> dumpDynamicRegistry(dir, "biomes",     server, Registries.BIOME));
        safeDump("structures", () -> dumpDynamicRegistry(dir, "structures", server, Registries.STRUCTURE));

        // --- Resource-based dumps (from ResourceManager) ---
        // Tags, advancements, and loot_tables are datapack resources,
        // not vanilla registries. We discover them via ResourceManager.listResources().
        if (rm != null) {
            safeDump("tags",         () -> dumpResources(dir, "tags",         rm, "tags",         null));
            safeDump("advancements", () -> dumpResources(dir, "advancements", rm, "advancements", null));
            safeDump("loot_tables",  () -> dumpResources(dir, "loot_tables",  rm, "loot_tables",
                          rel -> !rel.startsWith("blocks/")));
        } else {
            RegistryDumperDeluxe.LOGGER.warn("ResourceManager is null - skipping tags, advancements, loot_tables");
        }
    }

    /* ===================== safe wrapper ===================== */

    private static void safeDump(String name, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            RegistryDumperDeluxe.LOGGER.error("Failed to dump {}", name, e);
        }
    }

    /* ===================== static registries ===================== */

    private static void dumpRegistry(Path dir, String fileName, String... possiblePaths) {
        Registry<?> registry = findRegistry(possiblePaths);
        if (registry == null) {
            RegistryDumperDeluxe.LOGGER.warn("Built-in registry not found for: {} (tried {})", fileName, Arrays.toString(possiblePaths));
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

    /* ===================== dynamic registries ===================== */

    /**
     * Dump a dynamic registry (biomes, structures) that is only available
     * through the server's RegistryAccess, NOT through BuiltInRegistries.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void dumpDynamicRegistry(Path dir, String fileName,
                                             MinecraftServer server,
                                             ResourceKey registryKey) {
        RegistryAccess access = server.registryAccess();
        Optional<Registry<?>> optReg;
        try {
            optReg = (Optional<Registry<?>>) (Optional) access.registry(registryKey);
        } catch (Exception e) {
            RegistryDumperDeluxe.LOGGER.warn("Failed to lookup dynamic registry {} (key: {}): {}",
                    fileName, registryKey.location(), e.getMessage());
            return;
        }

        if (optReg.isEmpty()) {
            RegistryDumperDeluxe.LOGGER.warn("Dynamic registry not found for: {} (key: {})",
                    fileName, registryKey.location());
            return;
        }

        Registry<?> registry = optReg.get();
        Map<String, List<String>> grouped = groupByNamespace(registry);

        if (DumpConfig.persistentTrackingVal) {
            mergeWithExisting(dir, fileName, grouped);
        }

        writeFile(dir, fileName, grouped);

        int total = grouped.values().stream().mapToInt(List::size).sum();
        RegistryDumperDeluxe.LOGGER.info("Dumped {} ({} namespaces, {} entries)", fileName, grouped.size(), total);
    }

    /* ===================== resource-based dumps ===================== */

    /**
     * Dump resources discovered via ResourceManager.listResources().
     * Used for tags, advancements, and loot_tables which are datapack resources.
     */
    private static void dumpResources(Path dir, String fileName, ResourceManager rm,
                                       String prefix, Predicate<String> pathFilter) {
        Map<String, List<String>> grouped = new TreeMap<>();

        // listResources returns Map<ResourceLocation, Resource>
        Map<ResourceLocation, ?> resourceMap = rm.listResources(prefix, p -> true);
        RegistryDumperDeluxe.LOGGER.info("listResources('{}') found {} resource paths", prefix, resourceMap.size());

        for (ResourceLocation rl : resourceMap.keySet()) {
            String fullPath = rl.getPath();
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
        RegistryDumperDeluxe.LOGGER.info("Dumped {} ({} namespaces, {} entries)", fileName, grouped.size(), total);
    }

    /* ===================== helpers ===================== */

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
