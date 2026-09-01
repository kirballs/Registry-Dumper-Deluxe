# Registry Dumper Deluxe

A developer/debugging utility mod for **Minecraft Forge 1.20.1** (Forge 47.x) that dumps registries, tags, recipes, and resource folders to disk so you can inspect exactly what is loaded in your game or server. This is essentially a remastered version of [Registry Dumper 3000](https://www.curseforge.com/minecraft/mc-mods/registry-dumper-3000) that i made for my own modpack needs.

### Persistent Mod Tracking (extra feature)

Unlike the original, this version includes a **persistent registry tracker** that imprints every mod it detects at every startup **without ever removing** entries from mods that were removed and **without duplicating** entries that are already recorded.

**Example:**

1. Start with *Alex's Mobs* + *Croptopia* → both mods' registry entries are saved.
2. Remove *Croptopia*, add *Nether's Expansion* → *Alex's Mobs* entries stay, *Croptopia* entries **stay** (they are never deleted), *Nether's Expansion* entries are added.
3. No entry is ever written twice for the same mod.

The persistent history is stored in `dump/registry_persistent_history.json` alongside a human-readable summary at `dump/registry_persistent_summary.txt`.

---

## What It Dumps

| Section | Folder | Description |
|---|---|---|
| Built-in registries | `dump/registry_builtin/` | All registries from `BuiltInRegistries.REGISTRY` (blocks, items, fluids, entity types, etc.) |
| Runtime registries | `dump/registry_runtime/` | Final frozen state of all registries as seen by the server |
| Raw tags | `dump/tags/` | Raw tag JSON files from resource packs |
| Expanded tags | `dump/registry_tags_expanded/` | Final merged tag contents after all mods + datapacks are applied |
| Recipes | `dump/recipes/` | All recipes grouped by recipe type |
| Resource folders | `dump/data_raw/` | Loot tables, predicates, advancements, functions, worldgen, dimensions, damage types, etc. |
| Persistent history | `dump/registry_persistent_history.json` | Accumulated registry entries grouped by mod, never shrinks |
| Persistent summary | `dump/registry_persistent_summary.txt` | Human-readable summary of the persistent history |

Each section includes a `_summary.txt` file.

---

## Building

### Prerequisites

- **JDK 17**
- **Gradle 8.1.1+** (or use the included wrapper)

### Local build

```bash
# Generate the Gradle wrapper (first time only, if wrapper JAR is missing)
gradle wrapper --gradle-version 8.1.1

# Build the mod JAR
./gradlew build
```

The output JAR will be in `build/libs/`.

### GitHub Actions (manual build)

1. Go to the **Actions** tab in your GitHub repo.
2. Select the **"Build Mod"** workflow.
3. Click **"Run workflow"**.
4. Download the `RegistryDumper3000` artifact when it completes.

---

## Installation

1. Build the mod (see above) or download the JAR from GitHub Actions artifacts.
2. Copy the JAR into your Minecraft installation's `mods/` folder.
3. Launch the game or server.
4. After startup, check the `dump/` folder (inside your game/run directory).

---

## Configuration

A config file is automatically created at: `

config/registrydumper3000.toml`

### Options

| Option | Default | Description |
|---|---|---|
| `dumpBuiltinRegistries` | `true` | Dump all built-in registries |
| `dumpRuntimeRegistries` | `true` | Dump runtime/frozen registries |
| `dumpTags` | `true` | Dump raw and expanded tags |
| `dumpRecipes` | `true` | Dump all recipes |
| `dumpResourceFolders` | `true` | Dump generic datapack resource folders |
| `persistentTracking` | `true` | **Persistent mod tracking (the extra feature)** |
| `includeClassNames` | `true` | Include Java class names for each entry |
| `prettyPrint` | `true` | Pretty-print JSON output |
| `outputFormat` | `TXT` | Output format: `TXT` or `JSON` |
| `outputFolder` | `dump` | Output folder (relative to game directory) |

---

## How Persistent Tracking Works

On every server startup the mod:

1. **Loads** the existing `registry_persistent_history.json` (or creates a fresh one).
2. **Walks** every built-in registry and groups entries by their namespace (mod ID).
3. **Merges** into the history:
   - If a mod already has entries for a registry, only truly new IDs are appended.
   - If a mod is new to a registry, all its entries are added.
   - **Entries from mods that are no longer loaded are NEVER removed.**
4. **Records** the session timestamp and the list of loaded mods.
5. **Writes** everything back to disk.

### History JSON structure (simplified)

```json
{
  "version": 1,
  "sessions": [
    { "timestamp": "...", "loadedMods": ["minecraft", "forge", "alexsmobs", "croptopia"] },
    { "timestamp": "...", "loadedMods": ["minecraft", "forge", "alexsmobs", "netherexpansion"] }
  ],
  "registries": {
    "minecraft:block": {
      "minecraft": ["minecraft:dirt", "minecraft:stone", ...],
      "alexsmobs": ["alexsmobs:banana_slab", ...],
      "croptopia": ["croptopia:apple_tree", ...],
      "netherexpansion": ["netherexpansion:soul_stone", ...]
    },
    ...
  }
}
```

Note how `croptopia` entries persist even after the mod is removed.

---

## Compatibility

- **Minecraft:** 1.20.1
- **Forge:** 47.x (tested on 47.3.0)
- **Java:** 17
- **Side:** Server and client (runs on `ServerStartedEvent`)

---

## Who Is This For?

- Modpack developers debugging registry conflicts
- Datapack creators checking what IDs are actually loaded
- Mod developers inspecting cross-mod registry contents
- Technical players tracking which mods registered what over time

---

## License

MIT
