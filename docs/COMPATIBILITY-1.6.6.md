# Riddles Trails 1.6.6 compatibility

Install **RiddlesTrails 1.6.6** with **RiddlesLabsCore 0.6.18**. Both remain native Paper plugins with Folia support. The compatibility adapter is built into Core; no extra adapter plugin is required.

## Versions

| Target | Java runtime | Evidence |
| --- | --- | --- |
| Paper 1.20 through 1.21.x | Java 21, or newer if supported by that server | Production compilation against 1.20; static API references inspected across representative versions |
| Folia 1.20.1 through 1.21.x | Java 21, or newer if required by that server | Production compilation against Folia 1.20.1; ownership boundaries reviewed |
| Paper 26.1.2 through 26.2 | Java 25 | Compilation against 26.2; static API inspection |
| Folia 26.1.2 through 26.2 | Java 25 | Compilation against Folia 26.2; static API inspection |

The JAR uses Java 21 bytecode. Java 17 is not supported, including on early Minecraft 1.20 servers. Use the Java runtime required by both the plugin and your server. Build with JDK 25. The range includes available server releases; it does not assert support for unpublished Folia 1.20.0 or 26.1.0 builds, every fork, or Spigot.

## Behavior and preservation

- All 47 built-in animations, route geometry, recording, teleport segments, commands, permissions and the 1.6.5 Folia quest work remain.
- Core owns command compatibility, entity scheduling, file coordination, translations, notifications, diagnostics and FastStats. Quest collision reads now use Core's chunk/halo ownership check; they still require loaded chunks and never read neighboring Folia regions without ownership.
- `DUST` and `REDSTONE` resolve to the appropriate server name. Other known renamed particles also resolve across versions.
- Native `TRAIL` data is constructed only when the running server provides it. On older servers, known newer simple particles use colored dust, with a warning naming each fallback once. Dust keeps the configured palette and size; the client controls its lifetime, so newer trail motion/duration is not emulated.
- Unknown particle names still fail validation. Custom emitters must provide the correct particle data for the server they run on.
- Existing configuration files are not rewritten to save a temporary particle fallback. Stored player particle names survive loading and later animation selections, including when another player's preferences are saved. An explicit style change/reset remains an explicit change.
- Route/preference schemas remain unchanged. Keep `destinations.yml`, `animation-preferences.yml`, `animations.yml`, `config.yml`, notification settings and all language files. No reset, conversion or re-pairing is required.
- The 26 Trails metrics, privacy controls and optional error reporting remain unchanged. Core's own metrics and optional Discord pairing remain unchanged.
- Citizens, BeautyQuests and custom animation plugins must themselves support the server and Java version in use. Trails cannot backport those external plugins. Fixed-location quest guidance needs neither Citizens nor BeautyQuests.

## Upgrade

Stop Minecraft normally. Replace Trails with `RiddlesTrails-1.6.6.jar` and Core with `RiddlesLabsCore-0.6.18.jar`. Retain the existing data folders and remove duplicate older JARs from the active plugins directory. The `-api.jar` is for developers, not a second server plugin.

Core 0.6.18 is an additive API update. Current TNE and RRM builds accepting Core 0.6.x do not need rebuilding for this addition. Their independent Minecraft/dependency requirements still apply.

## Evidence and limits

Production-only builds use `-Dmaven.test.skip=true -DskipITs=true`. Separate build directories prevent reusing classes from another API baseline. The final Trails JAR uses the Paper 1.20 baseline. Core is built against its documented 26.1.2 baseline with additional 26.2 compilation.

Static inspection covers direct server references against Paper 1.20, 1.20.1, 1.20.6, 1.21, 1.21.4, 1.21.11, 26.1.2 and 26.2, plus Folia 1.20.1 and 26.2. It checks missing members, class/interface changes, Java class versions, packaged resources and provided API exclusions. Source preservation is compared against snapshots of both working trees, including the uncommitted Folia quest and Core work already present.

No tests, test compilation, benchmarks, server runs, live-data access, publishing or deployment were performed. Runtime behavior and performance remain unverified; the owner performs actual-server validation.
