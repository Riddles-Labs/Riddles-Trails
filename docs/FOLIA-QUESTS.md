# Folia quest guidance — 1.6.5

## Current compatibility

Trails 1.6.6 / Core 0.6.18 expand the range to Paper 1.20–26.2 and Folia 1.20.1–26.2. The ownership and cancellation work below is retained. See [the current upgrade guide](COMPATIBILITY-1.6.6.md).

## Historical 1.6.5 upgrade

Replace Trails with `RiddlesTrails-1.6.5.jar` while the server is stopped. Keep Core
0.6.13 or later within 0.6.x; the installed Core 0.6.15 works with this API requirement.
No Core rebuild is needed. Trails still targets Paper/Folia 26.2 and Java 25.

Keep config.yml, destinations.yml, animations.yml, animation-preferences.yml and all
language files. Routes, NPC mappings, restore rules, particle settings, teleport indices,
preferences, permissions and API method signatures are unchanged. No data migration is
introduced. Existing custom translations are retained; only bundled unavailable-message
defaults drop the obsolete Paper requirement.

## What changed

- QuestTrailAPI and `/riddlesquesttrail` are available on Paper and Folia.
- Each active assignment runs on its player's Core entity scheduler. The scheduler
  follows that player through region changes and teleports. There is no global quest
  timer reading every player.
- API and console assignments are queued onto the target player. New requests supersede
  pending requests and login restoration. A delayed callback cannot restore an old
  player's session after a disconnect/rejoin or replace a newer assignment.
- BeautyQuests progress restoration runs on that player's scheduler, with the existing
  ten-attempt retry limit. Stored quest assignments are still not replayed blindly.
- Citizens entity handles are seeded during normal startup, then updated from its
  spawn/despawn/remove events. No runtime timer scans its mutable NPC registry.
  Position/validity reads run on the NPC's entity scheduler. Only copied locations
  cross between owners; despawned, replaced or stale observations pause guidance.
- Remote NPC refreshes remain bounded to one outstanding read per assignment. An
  extra refresh every 10 player ticks keeps snapshots fresh with long particle intervals.
  Observations older than two seconds are withheld if the target's region stalls.
- Collision and connector searches read loaded chunks only after checking region
  ownership, including a neighbouring chunk halo for collision shapes. Searches stop
  at unavailable terrain and display the safe prefix, expanding as the player moves.
  Trails does not load chunks or move NPCs/players to make a route available.
- Reloaded immutable config/routes become visible to each player's next update.
  Assignment replacement, logout, failed scheduling and shutdown cancel owned work.
  Shutdown also removes the adapter's optional Citizens event registrations.

## Optional integrations

Fixed-location quest guidance does not require Citizens or BeautyQuests. Citizens NPC
routing needs an enabled, compatible Citizens build. Automatic quest-progress restoration
needs an enabled, compatible BeautyQuests build. Use versions that support your server;
Trails does not override dependency compatibility checks or patch those plugins.

The Citizens bridge binds the default registry at normal startup. After replacing or
hot-reloading Citizens, restart the server to rebind it. Plugin hot-loading/reloading
is not an installation method for this release. Normal Trails configuration reload is
supported and keeps the current adapter.

## Health and reporting

`/rl health riddlestrails` now reports `quests | HEALTHY | AVAILABLE` when the quest
service is initialized on either platform. FastStats `quest_available` is 1 on Folia,
and the former unconditional `PAPER_ONLY` degraded signal is removed. This is service
availability, not proof that every configured NPC, quest ID or route exists.

Actual quest/restore failures still increment `quest_failures_total` and use Core's
optional scrubbed error reporting. Missing/despawned NPCs and temporarily inaccessible
terrain pause guidance and use the existing translated notices. None of those checks
changes saved data or enables telemetry without the existing consent controls.

## Evidence

Built with Java 25 against Paper API 26.2.build.133-stable. Static review covered player
ownership, NPC handoffs, block ownership, stale callbacks and resource cleanup. Build
logs and the previous JAR are retained locally under `.build/1.6.5/`.
No tests, test compilation, test servers, live databases or deployment were performed.
Runtime behavior remains for the owner's actual-server validation.

References: [Paper/Folia scheduling](https://docs.papermc.io/paper/dev/folia-support/),
[Citizens registry implementation](https://github.com/CitizensDev/Citizens2/blob/master/main/src/main/java/net/citizensnpcs/npc/CitizensNPCRegistry.java),
and [Citizens spawn events](https://github.com/CitizensDev/CitizensAPI/blob/master/src/main/java/net/citizensnpcs/api/event/NPCSpawnEvent.java).
