package com.registrydumper3000.dump;

import com.google.gson.*;
import com.registrydumper3000.RegistryDumper3000;
import com.registrydumper3000.config.DumpConfig;
import com.registrydumper3000.util.DumpHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dumps all recipes from the server's {@link RecipeManager}.
 * Recipes are grouped by their parsed {@link RecipeType}.
 */
public class RecipeDumper {

    public static void dump(Path outputRoot, MinecraftServer server) {
        Path dir = outputRoot.resolve("recipes");
        try {
            DumpHelper.ensureDir(dir);
        } catch (IOException e) {
            RegistryDumper3000.LOGGER.error("Failed to create recipes dump dir", e);
            return;
        }

        RecipeManager recipeManager = server.getRecipeManager();
        Collection<Recipe<?>> allRecipes = recipeManager.getRecipes();

        // Group by recipe type
        Map<String, List<Recipe<?>>> byType = new TreeMap<>();
        for (Recipe<?> recipe : allRecipes) {
            String typeName = recipe.getType().toString();
            byType.computeIfAbsent(typeName, k -> new ArrayList<>()).add(recipe);
        }

        // Dump each group
        for (Map.Entry<String, List<Recipe<?>>> entry : byType.entrySet()) {
            String typeName = entry.getKey();
            List<Recipe<?>> recipes = entry.getValue();
            String safeName = DumpHelper.safeFileName(typeName);

            try {
                if (DumpConfig.format == DumpConfig.OutputFormat.TXT) {
                    dumpTypeTxt(typeName, recipes, dir);
                } else {
                    dumpTypeJson(typeName, recipes, dir);
                }
            } catch (Exception e) {
                RegistryDumper3000.LOGGER.error("Failed to dump recipe type: {}", typeName, e);
            }
        }

        // Summary
        Path summaryFile = dir.resolve("_summary.txt");
        try (BufferedWriter w = Files.newBufferedWriter(summaryFile)) {
            w.write("Recipe Dump Summary\n");
            w.write("====================\n");
            w.write(String.format("Total recipes: %d%n", allRecipes.size()));
            w.write(String.format("Recipe types : %d%n", byType.size()));
            w.write("\nPer-type breakdown:\n");
            for (Map.Entry<String, List<Recipe<?>>> entry : byType.entrySet()) {
                w.write(String.format("  %s: %d recipes%n", entry.getKey(), entry.getValue().size()));
            }
        } catch (IOException e) {
            RegistryDumper3000.LOGGER.error("Failed to write recipe summary", e);
        }

        RegistryDumper3000.LOGGER.info("Dumped {} recipes in {} types",
                allRecipes.size(), byType.size());
    }

    private static void dumpTypeTxt(String typeName, List<Recipe<?>> recipes, Path dir) throws IOException {
        String safeName = DumpHelper.safeFileName(typeName);
        Path file = dir.resolve(safeName + ".txt");
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write(String.format("Recipe Type: %s%n", typeName));
            w.write(String.format("Count: %d%n%n", recipes.size()));
            for (Recipe<?> recipe : recipes) {
                ResourceLocation id = recipe.getId();
                w.write(String.format("%s  (group=%s, serializer=%s)%n",
                        id.toString(),
                        recipe.getGroup().isEmpty() ? "<none>" : recipe.getGroup(),
                        recipe.getSerializer().toString()));
            }
        }
    }

    private static void dumpTypeJson(String typeName, List<Recipe<?>> recipes, Path dir) throws IOException {
        String safeName = DumpHelper.safeFileName(typeName);
        JsonArray arr = new JsonArray();
        for (Recipe<?> recipe : recipes) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", recipe.getId().toString());
            obj.addProperty("group", recipe.getGroup().isEmpty() ? null : recipe.getGroup());
            obj.addProperty("serializer", recipe.getSerializer().toString());
            obj.addProperty("type", typeName);
            arr.add(obj);
        }
        JsonObject root = new JsonObject();
        root.addProperty("recipeType", typeName);
        root.addProperty("count", recipes.size());
        root.add("recipes", arr);
        Path file = dir.resolve(safeName + ".json");
        DumpHelper.writeJson(file, root, DumpConfig.prettyPrintVal);
    }
}
