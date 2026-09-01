package com.registrydumper3000.util;

import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class DumpHelper {

    public static final Gson PRETTY_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    public static final Gson COMPACT_GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    /**
     * Converts a ResourceLocation-style string into a safe filename.
     * e.g. "minecraft:block" -> "minecraft__block.txt"
     */
    public static String safeFileName(String input) {
        return input.replace(':', '_').replace('/', '_');
    }

    /**
     * Ensures a directory exists, creating it (and parents) if needed.
     */
    public static void ensureDir(Path dir) throws IOException {
        Files.createDirectories(dir);
    }

    /**
     * Writes a string to a file using UTF-8.
     */
    public static void writeString(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Writes a JSON object to a file.
     */
    public static void writeJson(Path file, JsonElement json, boolean pretty) throws IOException {
        Gson gson = pretty ? PRETTY_GSON : COMPACT_GSON;
        writeString(file, gson.toJson(json));
    }

    /**
     * Reads a JSON file. Returns null if the file does not exist.
     */
    public static JsonElement readJson(Path file) throws IOException {
        if (!Files.exists(file)) {
            return null;
        }
        String content = Files.readString(file, StandardCharsets.UTF_8);
        return JsonParser.parseString(content);
    }

    /**
     * Reads a raw resource file from the classpath as a string.
     * Returns null if not found.
     */
    public static String readResource(String path) {
        try (InputStream is = DumpHelper.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Copies an InputStream to a file.
     */
    public static void copyStreamToFile(InputStream is, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream os = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        }
    }
}
