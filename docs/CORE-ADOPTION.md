# Core adoption and upgrade

## Runtime dependency and ownership

Riddles Trails requires RiddlesLabsCore API >=0.6.0,<0.7.0. Its descriptor declares a hard dependency. The Maven API is provided, so there is one shared Core API at runtime. Trails registers module ID `riddlestrails` and releases its owned resources on shutdown or failed startup.

| Shared facility | Trails use |
|---|---|
| Module lifecycle | Startup rollback and shutdown ownership for commands, services, listeners and tasks |
| Entity scheduler | Ordinary per-player particle trail updates |
| Global scheduler | Paper-only quest updates and delayed quest restoration; shared reload publication |
| Work executor | Configuration/animation/destination reload file work off gameplay threads |
| YAML configuration | Default installation, validated immutable config snapshots and reload handles |
| Commands and listeners | All three commands bound through Core; listener registrations owned by the module |
| Services | Animation API and Paper quest API published to Core and Bukkit for extension plugins |
| Startup banner | Riddles Trails logo and runtime/destination/animation/quest/metrics rows |
| FastStats | Per-plugin configured context; aggregate destinations, active trails, animations and recordings |
| Placeholders | Cached destination and active trail counts through Core's optional PlaceholderAPI bridge |

Route geometry, particles, recording rules, animation registry, player preferences, quest progress and their YAML file formats remain product-owned. Existing synchronous route/preference writes retain their original behavior; Core adoption does not change that persistence contract. Independent config files publish their own valid snapshots; a multi-file reload is not an all-files transaction.

The quest extension still requires Paper because Citizens/BeautyQuests access and loaded-world collision searches use its server-thread model. Core scheduling alone does not make those integrations Folia-compatible. No economy provider is used or required by Trails.

## Operator upgrade

With the server stopped, back up the previous JAR and data folder. Install the new Core and Trails JARs, removing the previous Trails JAR so both versions cannot load together.

Copy the previous data folder's contents into `plugins/RiddlesTrails/`. Keep your `destinations.yml` unchanged; route IDs, worlds, points and teleport indices have the same format. Keep UUID keys and style values in `animation-preferences.yml`.

Built-in animation namespace is now `riddlestrails:`. In copied `animations.yml`, change any fully qualified built-in default, fallback or profile IDs to that prefix. In copied `animation-preferences.yml`, update each built-in `players.<uuid>.animation` value the same way. For example, a built-in comet selection becomes `riddlestrails:comet`. Short IDs such as `comet` resolve to the new prefix. Do not rewrite third-party animation namespaces. An unknown selection uses the configured fallback until corrected.

Replace permission grants with:

- `riddlestrails.use`
- `riddlestrails.admin` (includes use)
- `riddlestrails.animation.admin`
- `riddlestrails.quest.admin`
- `riddlestrails.*` for all of the above

Primary commands are `/riddlestrails`, `/riddlestrailanimation`, `/riddlesquesttrail`; neutral aliases remain available. Any old plugin-qualified command prefix must be updated to `riddlestrails:`. Quest actions can continue using the neutral `/questtrail` alias or switch to `/riddlesquesttrail`.

Third-party extensions must recompile imports against `com.riddleslabs.trails.api`, use `com.riddleslabs:riddlestrails:1.5.0-SNAPSHOT:api`, and declare a dependency on `RiddlesTrails`. Old package names and permission aliases are not retained.

## FastStats and diagnostics

Add the following per-plugin entry to Core's existing config and supply the environment variable before starting the server. Enable the global switch only if the operator wants telemetry:

```yaml
faststats:
  enabled: true
  plugins:
    riddlestrails:
      enabled: true
      token-env: RIDDLES_TRAILS_FASTSTATS_TOKEN
      error-reporting: false
```

Merge this entry with existing plugins; do not replace their configuration. Core controls telemetry and startup layout. Trails has no bStats and does not send raw logs or player IDs in its metrics.

Core diagnostics include `/rl modules`, `/rl health riddlestrails` and `/rl tasks riddlestrails`. With PlaceholderAPI enabled, `%riddleslabs_riddlestrails__destinations%` and `%riddleslabs_riddlestrails__active%` expose the cached counts.

## Build record

Project: `F:/Plugins Coding/RiddlesTrails`. Core: `F:/Coding/RiddlesLabCore`. Build only with `-Dmaven.test.skip=true -DskipITs=true`. No tests, benchmarks, test servers or production-server changes are performed. The owner performs all testing.

## Foundation release 1.5.1

Publication note: Core 0.6.1 is compatible with the required API interval and is
the recommended shared installation alongside TNE 1.0.73. Trails has no economy
dependency. Minecraft 26.2 / Java 25 and the Paper-only quest restrictions remain.
This release is compiled and inspected only; actual-server validation is owner-run.

Use RiddlesLabsCore 0.6.0 with Trails 1.5.1. This change updates the shared API
requirement only; existing module ownership already covers shutdown. Trails
continues to target Minecraft 26.2 on Java 25 and is not advertised as 26.1
compatible because Core also supports that family. Paper-only quest safeguards,
route formats and preferences remain unchanged. This working copy was compiled
and packaged with test compilation and tests skipped; runtime validation was not
performed.
