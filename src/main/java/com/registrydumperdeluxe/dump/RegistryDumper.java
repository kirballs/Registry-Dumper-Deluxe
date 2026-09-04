package com.registrydumperdeluxe.dump;

import com.registrydumperdeluxe.RegistryDumperDeluxe;
import com.registrydumperdeluxe.config.DumpConfig;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Predicate;

public class RegistryDumper {

    public static void dumpAll(MinecraftServer server, ResourceManager rm, Path dir) {
        // --- Mod list (non-persistent, overwritten every session) ---
        safeDump("mods", () -> dumpModList(dir));

        // --- Static registries (from BuiltInRegistries) ---
        safeDump("items",        () -> dumpRegistry(dir, "items",        "item"));
        safeDump("entities",     () -> dumpRegistry(dir, "entities",     "entity_type"));
        safeDump("sound_events", () -> dumpRegistry(dir, "sound_events", "sound_event"));
        safeDump("features",     () -> dumpRegistry(dir, "features",     "worldgen/feature", "feature"));

        // --- Dynamic registries (from server.registryAccess()) ---
        safeDump("biomes",     () -> dumpDynamicRegistry(dir, "biomes",     server, Registries.BIOME));
        safeDump("structures", () -> dumpDynamicRegistry(dir, "structures", server, Registries.STRUCTURE));

        // --- Resource-based dumps (from ResourceManager) ---
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

    /* ===================== mod list (non-persistent) ===================== */

    private static void dumpModList(Path dir) {
        List<String> names = new ArrayList<>();
        for (var mod : ModList.get().getMods()) {
            names.add(mod.getDisplayName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);

        // mods.txt: plain names, one per line, no commas/quotes
        Path file = dir.resolve("mods.txt");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, String.join("\n", names) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            RegistryDumperDeluxe.LOGGER.error("Failed to write mods", e);
        }

        RegistryDumperDeluxe.LOGGER.info("Dumped mods ({} entries)", names.size());
    }

    /* ===================== static registries ===================== */

    private static void dumpRegistry(Path dir, String fileName, String... possiblePaths) {
        Registry<?> registry = findRegistry(possiblePaths);
        if (registry == null) {
            RegistryDumperDeluxe.LOGGER.warn("Built-in registry not found for: {} (tried {})",
                    fileName, Arrays.toString(possiblePaths));
            return;
        }

        Set<String> ids = collectIds(registry);
        if (DumpConfig.persistentTrackingVal) {
            mergeWithExisting(dir, fileName, ids);
        }
        writeRegistryFile(dir, fileName, ids);

        RegistryDumperDeluxe.LOGGER.info("Dumped {} ({} entries)", fileName, ids.size());
    }

    /* ===================== dynamic registries ===================== */

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
        Set<String> ids = collectIds(registry);
        if (DumpConfig.persistentTrackingVal) {
            mergeWithExisting(dir, fileName, ids);
        }
        writeRegistryFile(dir, fileName, ids);

        RegistryDumperDeluxe.LOGGER.info("Dumped {} ({} entries)", fileName, ids.size());
    }

    /* ===================== resource-based dumps ===================== */

    private static void dumpResources(Path dir, String fileName, ResourceManager rm,
                                       String prefix, Predicate<String> pathFilter) {
        Set<String> ids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        Map<ResourceLocation, ?> resourceMap = rm.listResources(prefix, p -> true);
        RegistryDumperDeluxe.LOGGER.info("listResources('{}') found {} resource paths", prefix, resourceMap.size());

        for (ResourceLocation rl : resourceMap.keySet()) {
            String fullPath = rl.getPath();
            String relative = fullPath.substring(prefix.length());
            if (relative.startsWith("/")) relative = relative.substring(1);

            if (pathFilter != null && !pathFilter.test(relative)) continue;

            ids.add(rl.getNamespace() + ":" + fullPath);
        }

        if (DumpConfig.persistentTrackingVal) {
            mergeWithExisting(dir, fileName, ids);
        }
        writeRegistryFile(dir, fileName, ids);

        RegistryDumperDeluxe.LOGGER.info("Dumped {} ({} entries)", fileName, ids.size());
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

    private static Set<String> collectIds(Registry<?> registry) {
        Set<String> ids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ResourceLocation location : registry.keySet()) {
            ids.add(location.toString());
        }
        return ids;
    }

    /**
     * Write a registry dump file in the format:
     *   Total Elements: 1234
     *   "minecraft:apple",
     *   "minecraft:acacia_boat",
     *   ...
     */
    private static void writeRegistryFile(Path dir, String fileName, Set<String> ids) {
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);

        StringBuilder sb = new StringBuilder();
        sb.append("Total Elements: ").append(sorted.size()).append("\n");
        for (String id : sorted) {
            sb.append("\"").append(id).append("\",\n");
        }

        Path file = dir.resolve(fileName + ".json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            RegistryDumperDeluxe.LOGGER.error("Failed to write {}", fileName, e);
        }
    }

    /**
     * Persistent merge: read existing .json file, extract IDs from
     * quoted lines, and add any that aren't in the current set.
     * This keeps entries from removed mods.
     */
    private static void mergeWithExisting(Path dir, String fileName, Set<String> current) {
        Path file = dir.resolve(fileName + ".json");
        if (!Files.exists(file)) return;

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int added = 0;
            for (String line : lines) {
                line = line.trim();
                // Skip header line and empty lines
                if (line.startsWith("Total") || line.isEmpty()) continue;
                // Strip quotes and comma: "minecraft:apple", -> minecraft:apple
                if (line.startsWith("\"") && line.endsWith("\",")) {
                    String id = line.substring(1, line.length() - 2);
                    if (current.add(id)) added++;
                }
            }
            if (added > 0) {
                RegistryDumperDeluxe.LOGGER.info("Persisted {} old entries for {}", added, fileName);
            }
        } catch (IOException e) {
            RegistryDumperDeluxe.LOGGER.debug("Could not read existing file for {}", fileName);
        }
    }
}
