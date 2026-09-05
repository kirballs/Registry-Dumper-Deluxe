# Registry Dumper Deluxe

A debugging utility mod for **Minecraft Forge 1.20.1** (Forge 47.x) that dumps key registry and resource types as files, with **persistent mod tracking** across startups. Inspired by [Registry Dumper 3000](https://www.curseforge.com/minecraft/mc-mods/registry-dumper-3000) but amplified for my modpack needs. I made this public so that it can help other people with what i think is one of the most obvious issue when making modpacks: **knowing what exists in the game.**

### Persistent Mod Tracking

Registry entries are accumulated across startups and never removed:

1. Start with *Alex's Mobs* + *Croptopia* — both mods' entries are saved.
2. Remove *Croptopia*, add *Nether's Expansion* — Alex's Mobs entries stay, Croptopia entries **stay**, Nether's Expansion entries are added.
3. No entry is ever written twice for the same mod.

> **Note:** The `mods.txt` file is **not** persistent — it is overwritten fresh each session with only currently loaded mods.

---

## What Gets Dumped

On every server startup, the following files are created (or updated) in the `dump/` folder:

| File | Source | Details |
|---|---|---|
| `mods.txt` | Forge mod list | Display names of all loaded mods (non-persistent) |
| `items.json` | `minecraft:item` registry | All registered items |
| `entities.json` | `minecraft:entity_type` registry | All registered entity types |
| `sound_events.json` | `minecraft:sound_event` registry | All registered sound events |
| `biomes.json` | `minecraft:worldgen/biome` registry | All registered biomes (dynamic registry) |
| `structures.json` | `minecraft:structure` registry | All registered structures (dynamic registry) |
| `features.json` | `minecraft:worldgen/feature` registry | Configured features (e.g. ore veins, ant hills) |
| `advancements.json` | Resource manager (`advancements/`) | All advancement resource locations |

### Tags Subfolder

Tags are split into a `tags/` subfolder with six separate files:

| File | Paths included | Details |
|---|---|---|
| `tags/entity_types.json` | `tags/entity_types/*` | Entity type tags |
| `tags/blocks.json` | `tags/blocks/*` | Block tags |
| `tags/items.json` | `tags/items/*` | Item tags |
| `tags/worldgen_biome.json` | `tags/worldgen/biome/*` | Biome tags |
| `tags/worldgen_structure.json` | `tags/worldgen/structure/*` | Structure tags |
| `tags/misc.json` | Everything else | Fluid, function, game event, and other tags |

### Loot Tables Subfolder

Loot tables are split into a `loot_tables/` subfolder with three separate files:

| File | Paths included | Details |
|---|---|---|
| `loot_tables/entity.json` | `loot_tables/entities/*` | Entity drop tables |
| `loot_table/chest.json` | `loot_tables/chests/*` | Chest loot tables |
| `loot_table/misc.json` | Everything else except `blocks/` | Gameplay, custom, and other loot tables |

Block loot tables (`loot_tables/blocks/`) are excluded entirely since every block drops itself — that information is redundant.

### Output Format

**Registry files** (`.json`) use a flat list format with a total count header:

```
Total Elements: 1234
"minecraft:apple",
"minecraft:acacia_boat",
"alexsmobs:crocodile_egg",
...
```

**`mods.txt`** is plain text — one mod display name per line, sorted alphabetically:

```
Alex's Mobs
Create
Farmer's Delight
Minecraft
...
```

---

## Building

### Prerequisites

- **JDK 17**
- **Gradle 8.1.1+** (or use the included wrapper)

### Local build

```bash
./gradlew build
```

Output JAR: `build/libs/`

### GitHub Actions

Go to **Actions** tab, select **Build Mod** workflow, click **Run workflow**. Download the artifact when done.

---

## Installation

1. Build or download the JAR.
2. Copy into your `mods/` folder.
3. Launch the game or server.
4. Check the `dump/` folder for the output files.

---

## Configuration

Auto-generated at `config/registrydumperdeluxe.toml`:

| Option | Default | Description |
|---|---|---|
| `outputFolder` | `dump` | Output folder (relative to game directory) |
| `persistentTracking` | `true` | Keep entries from removed mods, never duplicate |

---

## Compatibility

- **Minecraft:** 1.20.1
- **Forge:** 47.x (tested on 47.4.23)
- **Java:** 17
- **Side:** Server and client (runs on `ServerStartedEvent`)

---

## License

MIT
