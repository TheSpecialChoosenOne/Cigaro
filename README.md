<div align="center">

# Cigaro

**From the first seed to the last puff.**

A tobacco-growing, crafting and roleplay plugin for Paper servers.

[English](README.md) · [Русский](README.ru.md) · [Download beta](https://github.com/TheSpecialChoosenOne/Cigaro/releases/tag/v1.1.0) · [Addon API](docs/ADDON_API.md)

</div>

> **Beta:** Cigaro is playable, but long-running multiplayer use and compatibility with other plugins still need wider testing. Back up your world and plugin data before installing or updating.

## Make it your own

Grow different tobacco varieties, learn how to process them and make something worth sharing. Experiment with drying, cutting, flavors and blends—or build an automated workshop to handle the work for you.

- **Grow and explore.** Find ordinary seeds in grass, look for ancient seeds through archaeology and exploration, and keep an eye on wandering traders for Caravan tobacco.
- **Craft and experiment.** Dry and cut leaves, combine tobaccos, add flavors and create cigarettes, cigars, pipes or hookahs. Different choices affect the finished product.
- **Build a workshop.** Use dedicated stations and automation, with custom models and interfaces from the resource pack.
- **Give characters a history.** Smoking affects personal statistics, tolerance and addiction. Learned habits can lead to occasional contextual reminders; nicotine patches suppress withdrawal symptoms temporarily.
- **Make it fit your server.** Configure effects, drop chances and other mechanics, or edit the included English and Russian language files.

## Requirements

| | |
|---|---|
| Server | Paper **26.3** |
| Java | **25** |
| Client | Minecraft **26.3** with the Cigaro resource pack |
| Release status | Beta; addon API is experimental |

Other server implementations and Minecraft versions have not been verified as supported.

## Installation

1. Download `Cigaro-1.1.0.jar` and `Cigaro-ResourcePack-1.1.0.zip` from the [release page](https://github.com/TheSpecialChoosenOne/Cigaro/releases/tag/v1.1.0).
2. Place the JAR in your server's `plugins` folder and restart the server.
3. Install the ZIP as a client resource pack, or configure your server to distribute it. The pack supplies the custom models, textures and interfaces.
4. Review `plugins/Cigaro/config.yml` and the language files in `plugins/Cigaro/lang/`. The default language is English; set `language: "ru_ru"` for Russian.

If your server already uses a resource pack, test and merge overlapping item definitions and fonts as needed. Distributing multiple packs does not automatically resolve conflicts between them.

## First steps

1. Break grass to look for tobacco seeds. Different varieties are associated with different biomes.
2. Plant the seeds on farmland and harvest the mature plant.
3. Craft a drying rack, dry the leaves and use a cutting table to prepare tobacco.
4. Experiment with flavors and blending, then use a rolling machine to make your first products.

Try combinations and discover what works for your character. Keep in mind that smoking can lead to dependence and withdrawal; a nicotine patch temporarily suppresses symptoms but does not remove the underlying addiction.

## Commands

| Command | Purpose |
|---|---|
| `/cigaro` | Show command help |
| `/cigaro stats` | View your smoking statistics |
| `/cigaro achievements` | View your achievements |
| `/cigaro top` | View the smokers leaderboard |
| `/cigaro give` | List available items and give syntax; admin |
| `/cigaro debug` | Show debugging options; admin |
| `/cigaro reload` | Reload configuration and language files; admin |
| `/cigaro analytics` | View plugin analytics; admin |

Use tab completion for item types and command options. `cigaro.admin` defaults to server operators. `cigaro.use` defaults to all players and is checked by some features; it should not be relied on as a complete restriction on all plugin interactions.

## Configuration and data

The configuration includes farming and ingredient drop chances, drying and rolling settings, smoking effects, addiction, tolerance and medical settings. Restart or use `/cigaro reload` after editing it. Back up the server world and the entire `plugins/Cigaro/` directory together, with the server stopped, before updates or migrations.

## Developers

The [addon API guide](docs/ADDON_API.md) and [example addon](examples/addon/) cover registration and events. The API is **experimental**: compatibility across versions is not guaranteed, and removing an addon whose content exists in the world is not supported.

Build the plugin and resource pack with Java 25:

```bash
./gradlew build
```

The JAR is placed in `build/libs/`; the resource-pack ZIP is placed in `build/distributions/`.

---

Made for Minecraft servers that enjoy a little more atmosphere.
