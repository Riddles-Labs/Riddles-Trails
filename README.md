# Riddles Trails

Personal particle navigation built on **RiddlesLabsCore**. Release **1.6.1**, packaged as a native Paper plugin with Folia support.

**Build target:** Java 25 / Paper API 26.2. Ordinary trails use Core's entity scheduler on Paper and Folia. The optional Citizens/BeautyQuests quest integration remains **Paper-only**. Core API >=0.6.7,<0.7.0 is required; no economy plugin or database is required.

## Install and upgrade

1. Stop the server and keep a backup of the previous plugin JAR and data folder.
2. Remove the previous Trails JAR. Install `RiddlesLabsCore-0.6.7.jar` and `RiddlesTrails-1.6.1.jar` together.
3. For an upgrade, copy the previous Trails data into `plugins/RiddlesTrails/`. The files remain `config.yml`, `destinations.yml`, `animations.yml` and `animation-preferences.yml`.
4. Update permissions to `riddlestrails.*` and any fully qualified built-in animation IDs to `riddlestrails:<effect>`. Short animation IDs still work. Keep third-party animation namespaces unchanged.
5. Start the server. Core must be enabled before Trails; Trails refuses to start without a compatible Core API.

No server files are automatically copied or rewritten by this source migration. See [upgrade details](docs/CORE-ADOPTION.md) before replacing an installed version. Do not install the API-only JAR on the server.

The plugin retains all 47 effects, route geometry and destination/preference file formats. It uses Core's lifecycle, task ownership, commands, listeners, configuration loader, service registry, placeholders, startup banners and FastStats. Particle rendering and quest logic remain in Trails.

Primary commands are `/riddlestrails`, `/riddlestrailanimation` and `/riddlesquesttrail`. Neutral aliases `/trail`, `/trails`, `/rt`, `/trailanimation`, `/rta`, `/questtrail` and `/rqt` remain available. Permission nodes and built-in animation IDs use only the Riddles name.

Animation settings come from `plugins/RiddlesTrails/animations.yml`. Missing new defaults are applied to a private configuration snapshot without overwriting operator edits. UUID selections and style overrides remain in `animation-preferences.yml`. See the [animation catalog](docs/ANIMATIONS.md).

## Build

Build in `F:/Plugins Coding/RiddlesTrails`, with Core's SDK installed from `F:/Plugins Coding/RiddlesLabCore`:

```text
mvn -B -ntp -Dmaven.test.skip=true -DskipITs=true clean package
```

The output is `target/RiddlesTrails-1.6.1.jar`. Core dependencies have Maven `provided` scope and are not bundled. The owner performs all testing on their server; this project defaults to skipping tests. No tests, benchmarks or servers were run for this migration.

## Quick waypoint

Stand at the destination and run:

```text
/riddlestrails set spawn
```

Walk away and run `/riddlestrails spawn`. This makes a direct guide to the waypoint. **It does not calculate a walkable path or avoid walls.** Use a recorded route for roads, stairs, bridges and paths around obstacles.

## Record a route

Stand at the start of a road/path:

```text
/riddlestrails record market
```

Walk the route on foot, then run `/riddlestrails save` at the destination. Players can now use `/riddlestrails market` from the same world. Their trail joins the closest segment and follows the recorded direction to the endpoint. The connection from the player to that segment is a straight line, so players should start near the recorded road. Avoid self-intersecting routes where possible.

Use `/riddlestrails pause` and `/riddlestrails resume` for a break. Resume near the last recorded point. Same-world teleports keep recording and save a transition between the departure and destination points. Trails guide to the departure point, then continue on the other side after teleporting; they never draw a walking segment through the teleport gap. Manually paused recordings remain paused. Changing worlds or flying interrupts recording; return to the route before resuming. `/riddlestrails cancel` discards only the unsaved recording. Unsaved recordings are discarded on disconnect/restart. Saved destinations survive restart.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/riddlestrails <id>` | Follow a destination | `riddlestrails.use` |
| `/riddlestrails go <id>` | Explicit destination command | `riddlestrails.use` |
| `/riddlestrails list` | List destinations | `riddlestrails.use` |
| `/riddlestrails stop` | Stop your active trail | `riddlestrails.use` |
| `/riddlestrails help` | Show commands | `riddlestrails.use` |
| `/riddlestrails set <id>` | Save your location as a direct waypoint | `riddlestrails.admin` |
| `/riddlestrails record <id>` | Start recording a walking route | `riddlestrails.admin` |
| `/riddlestrails pause` | Pause recording | `riddlestrails.admin` |
| `/riddlestrails resume` | Resume recording near its last point | `riddlestrails.admin` |
| `/riddlestrails save` | Save the recording with your location as endpoint | `riddlestrails.admin` |
| `/riddlestrails cancel` | Discard the current recording | `riddlestrails.admin` |
| `/riddlestrails delete <id> DELETE` | Confirm deletion of a saved destination | `riddlestrails.admin` |
| `/riddlestrails reload` | Reload config/destinations and stop active trails | `riddlestrails.admin` |

Players have `riddlestrails.use` by default. Administrators/operators have `riddlestrails.admin` by default. Destination IDs are 1–48 lowercase letters, digits, hyphens or underscores, beginning with a letter/digit. Command names are reserved. Creation does not overwrite existing IDs; delete explicitly before replacing one.

## Storage and settings

Destinations and route points are saved in `plugins/RiddlesTrails/destinations.yml`, using atomic file replacement where supported. Example:

```yaml
destinations:
  spawn:
    world: world
    points:
      - x: 0.5
        y: 65.0
        z: 0.5
```

Edit the file while the server is stopped, or run `/riddlestrails reload` after edits. Keep a backup when editing routes manually. Invalid route data fails loading instead of replacing the current in-memory routes.

The config controls particle spacing, look-ahead distance, height above recorded feet positions, update rate, particle budget, trail timeout, arrival radius, and recording limits. Defaults allow up to 1,200 particles per player per update every 6 ticks, with a 20-block look-ahead and 180-second timeout. The configurable budget accepts 6–6,000 particles. All 47 built-ins use `animations.yml`'s `style.parameters.detail-multiplier` (default 6) to add contour, curve and model samples, scaling detail down for smaller budgets. Extra detail includes clock markings, gear hubs, moth wing veins, shoe treads and a surfaced ghost head/torso with thicker moving limbs.

On the first upgrade to 1.4.1, the old stock budget of 120 becomes 1,200 automatically. Other configured budgets are kept. `render.detail-version: 1` records that migration; subsequent edits to the budget are respected. Colors, animation selections and saved routes are preserved.

Trails continue after teleporting and rejoin the original route from the player's new position. Moving to another world pauses particles until the player returns to the destination's world; the existing timeout still applies and is not reset by teleporting. Recording also continues through same-world teleports, subject to the point limit. Trails stop on arrival, timeout, death, disconnect, plugin disable or explicit cancellation. Reloading stops ordinary trails. Tracing never loads chunks or changes blocks. The plugin does not create destinations automatically from NRM plots; it runs independently and does not alter NRM's existing trail feature.

## Build and verification

```text
mvn clean verify
```

Unit tests cover route joining/projection, corner following, duplicate points, invalid data, storage round-trips, deletion, failed reloads, immutable snapshots and config bounds. Actual particle visuals, player permissions, route recording and Paper/Folia lifecycle behavior should also be checked on a live server.

## Quest guidance: Citizens and BeautyQuests

Riddles Trails exposes private quest trails through Java and console commands. Nothing is tied to a particular server, NPC name or quest chain. Install compatible Citizens and BeautyQuests builds on Paper to use both integrations. Fixed-location guidance does not require either plugin. Existing configs can add the `quest-trails` section shown below; missing settings use defaults.

### Configure roads first

1. At the start of the road, run `/riddlestrails record village-to-guide`.
2. Walk the intended route around walls and buildings to the NPC, then `/riddlestrails save`.
3. Map the Citizens NPC ID to that recorded route in `config.yml`:

```yaml
quest-trails:
  connector-max-nodes: 768
  target-radius: 3.0
  npc-routes:
    '11': [village-to-guide]
    '13': [guide-to-merchant]
  location-routes: [village-to-guide]
  restore-rules: []
```

Record every referenced route, then run `/riddlestrails reload`. These NPC IDs and route names are examples; replace them with your own. Multiple routes per NPC allow different starting areas. The nearest route entrances are tried first, preferring a complete walkable connection. Routes are directional. A single `/riddlestrails set` waypoint can also be mapped; guidance then uses the bounded local walking search to approach it.

Players can receive guidance away from the recorded road. A show command/API call schedules its first render for the next server tick. A bounded walking search connects the player to a recorded section using loaded terrain, including detours around walls. The old `join-radius` setting is ignored. The route endpoint must still be within `target-radius` (0.5–8 blocks) of the destination. An NPC's live position is refreshed each render update; if it leaves that endpoint radius, configure an appropriate route or move it back.

Connections search up to 64 blocks at a time, with a configurable total node budget of 96–3072 across up to three candidate entrances. The default is 768. A partial connection guides toward a distant route and refreshes as the player walks. Plans are cached for up to two seconds and invalidated after a teleport, route/config change, target movement or substantial deviation. This is a local connector, not unlimited world navigation: inaccessible or unloaded terrain, large mazes, special movement and hazards may require additional recorded approaches. It never loads chunks, spawns NPCs or teleports players. The connector checks collision clearance and supporting surfaces; it is not a hazard-avoidance system.

Stair and slab samples are lifted to nearby step surfaces (up to approximately one block). A later obstruction truncates the displayed trail at that point instead of hiding the entire visible section. The trail still uses `render.max-ahead` (20 blocks by default) and the particle budget; it does not draw the entire route at once.

### Elevators and existing recordings

Record a route normally, use the elevator, then keep walking and save. Riddles Trails captures the same-world Bukkit teleport as an explicit transition, stored as destination point indices in `destinations.<id>.teleports`. Quest guidance marks the entrance and asks the player to use the elevator/teleport. It recalculates from the exit on the next tick. Standalone trails also stop drawing at the entrance and rejoin after teleporting.

[Simple Elevators](https://www.spigotmc.org/resources/simple-elevators-1-8-26-2.44462/) uses Space to go up and Shift to go down. Riddles Trails relies on the resulting normal teleport event, so no Simple Elevators dependency or special config is required. Live compatibility with the installed elevator build should still be checked on a server.

For older recordings, near-vertical gaps over 1.25 blocks with less than one block of horizontal displacement are treated as elevator transitions automatically. Other old teleport gaps need to be rerecorded to capture explicit transition metadata. This heuristic can also classify near-vertical ladder/drop segments as transitions; record a walking alternative when appropriate. Existing configs work without changes: `connector-max-nodes` defaults to 768 and `join-radius` no longer restricts guidance. Replace the older Riddles Trails JAR with 1.4.2 and restart; the new `animations.yml` is created automatically.

### Console commands

Run these as console rewards/actions in your quest plugin, without a leading slash:

```text
questtrail show <player> npc <npcId>
questtrail show <player> location <world> <x> <y> <z>
questtrail clear <player>
questtrail status <player>
```

Use an online player's exact name or UUID. Commands require `riddlestrails.quest.admin` (operators and console by default). Normal players do not need that permission to receive guidance. Fixed locations select from `location-routes` using the same endpoint matching and walking connections.

For BeautyQuests, choose **console** execution and use its player placeholder:

```text
# Previous quest completion action:
questtrail show {PLAYER} npc 11

# Next quest start action:
questtrail clear {PLAYER}

# That quest's completion action:
questtrail show {PLAYER} npc 13
```

Put the clear action on quest cancellation/reset paths where appropriate too. Only paste the command lines, not the comments. Command rewards are documented by the [BeautyQuests implementation](https://github.com/SkytAsul/BeautyQuests/blob/2.1.0/core/src/main/java/fr/skytasul/quests/rewards/CommandReward.java). No separate Java integration is needed for these triggers.

### Restore from quest progress on login

Quest assignments are cleared on disconnect and plugin shutdown. To reconstruct guidance after login or server restart, configure rules using **BeautyQuests quest IDs**, which are separate from Citizens NPC IDs:

```yaml
quest-trails:
  # Keep the route mappings and radius settings from above here as well.
  restore-rules:
    - {after-quest: 101, until-quest: 102, npc: 13}
    - {after-quest: 100, until-quest: 101, npc: 11}
```

The first matching rule wins: `after-quest` must be completed and `until-quest` must be neither active nor completed. Put later steps first. Replace all example IDs with your server's IDs. This handles guidance **between quests**; it does not infer arbitrary in-quest stages or party progress. For other quest systems or stage-specific logic, use the Java API when that system finishes loading the player's progress.

Restoration waits one second and retries unavailable progress for up to ten attempts. A newer show/clear command always takes precedence over pending restoration. Missing or incompatible BeautyQuests APIs leave guidance unassigned and produce a console warning. Empty rules disable restoration. Quest state is read only; Riddles Trails does not edit BeautyQuests data or persist old trail assignments. Configure both the live command actions and the login rules.

The bridge targets the [BeautyQuests 2.1.0 public API](https://github.com/SkytAsul/BeautyQuests/tree/2.1.0/api/src/main/java/fr/skytasul/quests/api), with a fallback for the 1.0.x account API. Reflection keeps both plugins optional. This Core adaptation has been compiled only; the owner handles runtime compatibility on their server.

### Java API

The `api` classifier JAR contains only the public interface. Run `mvn -Dmaven.test.skip=true -DskipITs=true install` locally to make it available as a Maven dependency (it is not published to a public Maven repository):

```xml
<dependency>
  <groupId>com.riddleslabs</groupId>
  <artifactId>riddlestrails</artifactId>
  <version>1.5.0-SNAPSHOT</version>
  <classifier>api</classifier>
  <scope>provided</scope>
</dependency>
```

Add `depend: [RiddlesTrails]` to your plugin's `plugin.yml` (or `softdepend` with a missing-service check). Do not shade the interface into your plugin and do not install the API-only JAR on the server. Obtain the service during `onEnable`:

```java
import com.riddleslabs.trails.api.QuestTrailAPI;

QuestTrailAPI trails = getServer().getServicesManager().load(QuestTrailAPI.class);
if (trails == null) {
    getLogger().warning("Quest trails are unavailable on this server.");
    return;
}

trails.showToNpc(player.getUniqueId(), 11);
trails.showToLocation(player.getUniqueId(), destination);
boolean assigned = trails.hasTrail(player.getUniqueId());
trails.clear(player.getUniqueId());
```

One quest assignment exists per player; each show call replaces it. Calls made off the Paper server thread are queued, so `hasTrail` may not reflect a queued mutation immediately. Locations are copied and must contain finite coordinates and a world. Negative NPC IDs are rejected. Offline players are not assigned guidance; restore it after login. Using an API instance after plugin shutdown throws `IllegalStateException` for mutations.

`hasTrail` means an assignment exists, including when paused for missing/despawned NPCs, another world, a missing/blocked route, death or arrival. At arrival particles stop; the assignment remains until clear/disconnect, allowing a moving NPC to be followed again. Quest guidance does not use the ordinary trail timeout. Ordinary `/riddlestrails` sessions are independent; `questtrail clear` only clears quest guidance. Already emitted particles expire naturally; most particle lifetimes are client-controlled, while `TRAIL` uses the duration in `animations.yml`.

### Build status

This migration is compiled and packaged with all tests skipped. No test server, benchmark, simulation or production-server action was run. The owner performs all testing. Historical fixture sources do not establish behavior of the new Core integration.

## Languages

Version 1.6.0 uses Core for all 72 player-message keys, with English and Spanish catalogs. Use `/rtl list`, `/rtl es`, `/rtl auto`, `/rtl reload` and `/rtl status`. See [the owner guide](docs/LANGUAGES.md).
