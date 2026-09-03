package com.registrydumper3000.util;

import com.google.gson.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class DumpHelper {

    public static final Gson PRETTY_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public static final Gson COMPACT_GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    public static void writeJson(Path file, JsonElement json, boolean pretty) throws IOException {
        Files.createDirectories(file.getParent());
        String content = pretty ? PRETTY_GSON.toJson(json) : COMPACT_GSON.toJson(json);
        Files.writeString(file, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static JsonElement readJson(Path file) throws IOException {
        if (!Files.exists(file)) return null;
        String content = Files.readString(file, StandardCharsets.UTF_8);
        return JsonParser.parseString(content);
    }
}
