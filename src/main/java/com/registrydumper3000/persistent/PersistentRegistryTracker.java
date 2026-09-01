package com.registrydumper3000.persistent;

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
import net.minecraftforge.fml.ModList;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Persistent registry tracker — the key extra feature.
 *
 * <p>On every startup it:
 * <ol>
 *   <li>Loads the existing history from {@code registry_persistent_history.json}.</li>
 *   <li>Walks every built-in registry and groups entries by their namespace (mod id).</li>
 *   <li>Merges entries into the history <b>without removing any previous entries</b>
 *       and <b>without duplicating</b> entries that are already recorded for a mod.</li>
 *   <li>Appends a session record with timestamp and the set of loaded mods.</li>
 *   <li>Writes the updated history back to disk.</li>
 * </ol>
 *
 * <p>Result: if you start with Alex's Mobs + Croptopia, those entries are saved.
 * If you later remove Croptopia and add Nether's Expansion, Alex's Mobs entries stay,
 * Croptopia entries stay, and Nether's Expansion entries are added.  Nothing is ever
 * removed, and nothing is ever written twice for the same mod.</p>
 */
public class PersistentRegistryTracker {

    private static final String FILE_NAME = "registry_persistent_history.json";

    // ------------------------------------------------------------------
    //  Public entry point
    // ------------------------------------------------------------------
    public static void trackAndSave(Path outputRoot, MinecraftServer server) {
        Path historyFile = outputRoot.resolve(FILE_NAME);

        // 1. Load existing history (or create a fresh one)
        JsonObject history = loadHistory(historyFile);

        // Ensure top-level structure
        if (!history.has("version")) {
            history.addProperty("version", 1);
        }
        if (!history.has("sessions")) {
            history.add("sessions", new JsonArray());
        }
        if (!history.has("registries")) {
            history.add("registries", new JsonObject());
        }

        JsonObject registriesRoot = history.getAsJsonObject("registries");
        JsonArray sessions = history.getAsJsonArray("sessions");

        // 2. Record this session
        JsonObject session = new JsonObject();
        session.addProperty("timestamp", Instant.now().toString());
        JsonArray modList = new JsonArray();
        for (var modInfo : ModList.get().getMods()) {
            modList.add(modInfo.getModId());
        }
        session.add("loadedMods", modList);
        sessions.add(session);

        // 3. Walk every built-in registry
        int totalNewEntries = 0;
        int totalRegistries = 0;

        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            String registryName = registry.key().location().toString();
            totalRegistries++;

            // Get or create the per-registry object
            JsonObject registryObj;
            if (registriesRoot.has(registryName)) {
                registryObj = registriesRoot.getAsJsonObject(registryName);
            } else {
                registryObj = new JsonObject();
                registriesRoot.add(registryName, registryObj);
            }

            // Group CURRENT entries by namespace (mod id)
            Map<String, Set<String>> currentByMod = new LinkedHashMap<>();
            for (Holder<?> holder : registry) {
                ResourceKey<?> key = holder.unwrapKey().orElse(null);
                if (key == null) continue;

                String namespace = key.location().getNamespace();
                String fullId = key.location().toString();

                currentByMod
                        .computeIfAbsent(namespace, k -> new LinkedHashSet<>())
                        .add(fullId);
            }

            // Merge into persistent history
            for (Map.Entry<String, Set<String>> modEntry : currentByMod.entrySet()) {
                String modId = modEntry.getKey();

                if (registryObj.has(modId)) {
                    // Mod already exists — add only NEW entries, never duplicate
                    JsonArray existing = registryObj.getAsJsonArray(modId);
                    Set<String> existingSet = new HashSet<>();
                    for (JsonElement el : existing) {
                        existingSet.add(el.getAsString());
                    }

                    for (String newId : modEntry.getValue()) {
                        if (!existingSet.contains(newId)) {
                            existing.add(newId);
                            totalNewEntries++;
                        }
                    }
                } else {
                    // First time seeing this mod in this registry — add all entries
                    JsonArray entries = new JsonArray();
                    for (String id : modEntry.getValue()) {
                        entries.add(id);
                        totalNewEntries++;
                    }
                    registryObj.add(modId, entries);
                }
            }
        }

        // 4. Save
        try {
            DumpHelper.writeJson(historyFile, history, true); // always pretty-print history
        } catch (IOException e) {
            RegistryDumper3000.LOGGER.error("Failed to save persistent history", e);
            return;
        }

        // 5. Write a human-readable summary
        writeSummary(outputRoot, history, totalRegistries, totalNewEntries);

        RegistryDumper3000.LOGGER.info(
                "Persistent tracking: {} registries, {} new entries merged (history never shrinks)",
                totalRegistries, totalNewEntries);
    }

    // ------------------------------------------------------------------
    //  Load existing history
    // ------------------------------------------------------------------
    private static JsonObject loadHistory(Path file) {
        try {
            JsonElement el = DumpHelper.readJson(file);
            if (el != null && el.isJsonObject()) {
                return el.getAsJsonObject();
            }
        } catch (Exception e) {
            RegistryDumper3000.LOGGER.warn("Could not load persistent history, starting fresh", e);
        }
        return new JsonObject();
    }

    // ------------------------------------------------------------------
    //  Write a readable summary alongside the JSON
    // ------------------------------------------------------------------
    private static void writeSummary(Path outputRoot, JsonObject history,
                                     int registryCount, int newEntries) {
        Path summaryFile = outputRoot.resolve("registry_persistent_summary.txt");
        try (BufferedWriter w = Files.newBufferedWriter(summaryFile)) {
            w.write("Persistent Registry Tracking Summary\n");
            w.write("======================================\n");
            w.write(String.format("History version : %s%n",
                    history.has("version") ? history.get("version").getAsString() : "?"));
            w.write(String.format("Total sessions  : %d%n",
                    history.getAsJsonArray("sessions").size()));
            w.write(String.format("Registries      : %d%n", registryCount));
            w.write(String.format("New this session: %d%n", newEntries));
            w.write("\n");

            // Sessions log
            w.write("Session history:\n");
            for (JsonElement se : history.getAsJsonArray("sessions")) {
                JsonObject s = se.getAsJsonObject();
                w.write(String.format("  [%s] mods: %s%n",
                        s.has("timestamp") ? s.get("timestamp").getAsString() : "?",
                        s.has("loadedMods") ? s.getAsJsonArray("loadedMods").toString() : "?"));
            }
            w.write("\n");

            // Per-registry, per-mod breakdown
            JsonObject registries = history.getAsJsonObject("registries");
            w.write("Registry breakdown (entries never removed):\n");
            for (String regName : listSorted(registries.keySet())) {
                JsonObject reg = registries.getAsJsonObject(regName);
                int totalInReg = 0;
                for (JsonElement arr : reg.values()) {
                    totalInReg += arr.getAsJsonArray().size();
                }
                w.write(String.format("\n  [%s] — %d total entries, %d mods:%n",
                        regName, totalInReg, reg.size()));
                for (String modId : listSorted(reg.keySet())) {
                    int count = reg.getAsJsonArray(modId).size();
                    w.write(String.format("    %-30s %d entries%n", modId, count));
                }
            }
        } catch (IOException e) {
            RegistryDumper3000.LOGGER.error("Failed to write persistent summary", e);
        }
    }

    private static List<String> listSorted(Set<String> set) {
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }
}
