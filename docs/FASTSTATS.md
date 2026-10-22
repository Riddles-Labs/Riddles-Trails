# Trails FastStats — 1.6.4

Requires RiddlesLabsCore 0.6.13 (API below 0.7.0), Java 25 and Paper/Folia 26.2.
Core owns FastStats SDK registration, the project token, transport, opt-outs, shutdown and error scrubbing. Trails supplies only bounded aggregate values and fixed error categories. No separate FastStats dependency is bundled.

## Enable reporting

Keep the existing settings in `plugins/RiddlesLabsCore/config.yml`. To opt in to Trails metrics and error reports, merge these switches into the existing FastStats section; do not replace other plugin entries:

```yaml
faststats:
  enabled: true
  plugins:
    riddlestrails:
      enabled: true
      error-reporting: true
```

The key is `riddlestrails`. Core's own error-reporting switch does not enable Trails errors. Existing false values, `-Dfaststats.enabled=false`, and the shared `plugins/faststats/config.properties` opt-outs remain respected. No server configuration was changed by this update. Restart after changing switches.

FastStats project error tracking is enabled. The project and dashboards belong to **Riddles Labs**:

- [Trails Overview](https://www.faststats.dev/project/riddles-trails/dashboards/trails-overview)
- [Trails Health](https://www.faststats.dev/project/riddles-trails/dashboards/trails-health)
- [Error Tracking](https://www.faststats.dev/project/riddles-trails/errors)
- The existing Minecraft Plugin dashboard retains platform, version and server statistics.

## Reading charts

Latest-snapshot widgets deduplicate by server before summing. They describe reporting servers in the selected time range, not necessarily servers currently online. Failure and outcome counters reset on plugin restart; do not sum repeated reports or treat them as daily event counts. Failure categories can overlap, so adding categories does not give unique incidents.

The activity line averages reports within each time bucket and leaves missing observations as gaps. FastStats widgets can display zero when no values exist: check Data Sources coverage and Event Explorer before interpreting a zero as healthy. Newly registered fields are not proof of delivery. The error page being empty is not proof that error delivery has been verified.

## Metrics

All 26 fields are numeric. Counts and flags are nonnegative integers; uptime permits decimals.

| Field | Meaning |
| --- | --- |
| destinations | Saved destination count (existing field) |
| active_trails | Current ordinary trail sessions (existing field) |
| animations | Registered built-in and external animations (existing field) |
| recordings | Current recordings, including paused/saving sessions (existing field) |
| route_points | Total points in saved routes |
| teleport_segments | Total recorded teleport boundaries |
| custom_animations | Registered external animations; subset of animations |
| languages | Loaded catalogs, not player language preferences |
| quest_available | 1 when the Paper quest service exists; 0 on Folia; does not prove NPC availability |
| active_quest_trails | Current quest assignments, including temporarily unavailable targets; 0 on Folia |
| runtime_ready | 1 after initialization; 0 during startup or recorded startup failure |
| health_degraded | Any product signal DEGRADED/UNAVAILABLE; excludes metrics health; includes the Folia quest limitation |
| health_unknown | Empty product health or any UNKNOWN signal |
| uptime_seconds | Monotonic time since metrics initialization |
| trails_started_total | Successfully scheduled ordinary sessions, including replacements |
| trails_arrived_total | Sessions reaching the arrival radius; already-there requests excluded |
| trails_timed_out_total | Sessions ending at the configured timeout |
| trail_failures_total | Unexpected ordinary trail tick failures |
| animation_failures_total | First render failure per animation session; repeated fallback frames excluded |
| file_failures_total | Failed Core file health observations, not unique damaged files |
| reload_failures_total | Failed/rejected configuration or language reload operations |
| command_failures_total | Exceptions caught by the ordinary trail command handler |
| quest_failures_total | First unavailable-target warning per quest session or exhausted restore attempt |
| notification_failures_total | Failed notification delivery or initial notification configuration |
| startup_failures_total | Startup failures after Core metrics initialization |
| sampling_failures_total | Failed aggregate collection/publication observations |

Arrival and timeout counters do not cover manual stops, replacements, disconnects or entity retirement. They are not a completion-rate denominator. Metrics do not count every particle or inspect world/player state from the asynchronous sampler. Route snapshots are immutable; session and animation registry counts use concurrent collections.

## Error reports

Explicit boundaries cover startup, ordinary trail commands/ticks, the first animation failure per session, configuration/language reloads, notification failures, exhausted quest restore and first target-unavailable warnings, telemetry collection, and failed saves observed by the command completion helper. Core file health observations increment a counter; that callback has no throwable. Direct external API failures without one of these boundaries are not claimed as captured.

Reports are limited to one per fixed category per five minutes; counters still accumulate. Core discards exception messages, causes and suppressed errors and bounds stack information. No player UUIDs/names, world/route names, coordinates, command arguments, preference content or raw logs are custom metrics. This is explicit error reporting, not a global console-log collector or crash recorder. Very early startup failures or abrupt JVM termination may not upload anything.

## Timing and upgrade

A startup snapshot is published immediately, then a complete snapshot immediately after successful initialization. Collection repeats after 10 seconds and then every 30 seconds. Core's SDK upload cadence is independent (currently an initial 30-second delay and 30-minute periodic uploads). Sampling reads cached counts only; no filesystem or database work is introduced into rendering or metric suppliers.

Stop the server, replace only the Trails JAR with `RiddlesTrails-1.6.4.jar`, retain Core 0.6.13 and all current data/configuration, then restart. No file reset or re-pairing is required. `/rl health riddlestrails` should show metrics `AGGREGATES_READY` when collection succeeds; this confirms local collection, not receipt by FastStats.

## Validation

Compilation and static archive/source review only. No tests, test compilation, Minecraft servers, live data changes, deployment or push. Actual-server delivery of the new version remains for the owner to verify after installation.
