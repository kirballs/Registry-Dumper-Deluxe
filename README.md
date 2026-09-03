# Registry Dumper Deluxe

A debugging utility mod for **Minecraft Forge 1.20.1** (Forge 47.x) that dumps 9 key registry and resource types as JSON files, with **persistent mod tracking** across startups.

### Persistent Mod Tracking

Entries are accumulated across startups and never removed:

1. Start with *Alex's Mobs* + *Croptopia* - both mods' entries are saved.
2. Remove *Croptopia*, add *Nether's Expansion* - Alex's Mobs entries stay, Croptopia entries **stay**, Nether's Expansion entries are added.
3. No entry is ever written twice for the same mod.

---

## What Gets Dumped

On every server startup, 9 JSON files are created (or updated) in the `dump/` folder:

| File | Source | Details |
|---|---|---|
| `items.json` | `minecraft:item` registry | All registered items |
| `entities.json` | `minecraft:entity_type` registry | All registered entity types |
| `sound_events.json` | `minecraft:sound_event` registry | All registered sound events |
| `biomes.json` | `minecraft:worldgen/biome` registry | All registered biomes |
| `structures.json` | `minecraft:structure` registry | All registered structures |
| `features.json` | `minecraft:worldgen/feature` registry | Configured features (e.g. ore veins, ant hills) |
| `tags.json` | Resource manager (`tags/`) | All tag resource locations |
| `advancements.json` | Resource manager (`advancements/`) | All advancement resource locations |
| `loot_tables.json` | Resource manager (`loot_tables/`) | Chest, entity, gameplay loot tables (blocks excluded) |

### Output Format

Each file groups IDs by their namespace (mod ID), pretty-printed:

```json
{
  "minecraft": [
    "minecraft:stone",
    "minecraft:dirt",
    "minecraft:diamond_ore"
  ],
  "alexsmobs": [
    "alexsmobs:crocodile",
    "alexsmobs:anteater"
  ],
  "croptopia": [
    "croptopia:apple"
  ]
}
```

---

## Building

### Prerequisites

- **JDK 17**
- **Gradle 8.1.1+** (or use the included wrapper)

### Local build

```bash
# Generate wrapper (first time only, if missing)
gradle wrapper --gradle-version 8.1.1

# Build
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
4. Check the `dump/` folder for the 9 JSON files.

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
