# Trails 1.6.2 / Core 0.6.9

## Upgrade

Stop the server, replace only Trails with `target/RiddlesTrails-1.6.2.jar`, and retain
Core 0.6.9. Keep the existing data folder. Do not install the `-api.jar` on the server.
Paper/Folia 26.2 and Java 25 remain the baseline. Core is not bundled or modified.

## Data and saving

Route geometry, all 47 animations, permissions, external animation namespaces and
existing route/preference schemas are retained. `destinations.yml` and
`animation-preferences.yml` stay in their current locations. No database is introduced.

Core provides a bounded, single-worker Trails persistence lane (256 queued operations)
and revision-checked atomic file replacements. Each mutation reads the latest committed
in-memory state on that lane, saves it, then publishes it. Commands acknowledge success
only after saving. Recording edits pause while their save is pending; a failed save
retains the recording for retry. Disconnecting does not cancel an accepted save.

Files are not rewritten just to upgrade. Core rejects writes if a file was changed
externally since loading; it does not overwrite that edit. Reload routes after correcting
an external edit; restart to reload animation preferences. An invalid reload retains the
previous route snapshot and its old revision, so it cannot authorize an overwrite of the
invalid file. Core's `files.max-bytes` applies (default 2 MiB; configurable up to 16 MiB).
An oversized or malformed file is retained and reported, never truncated or reset.

On shutdown, successful saves are already durable. Queued operations may fail if the
module closes before Core admits their file operation; no success response is sent for
those failures. Existing unsaved recordings still have their original disconnect/restart
lifetime.

## Language files

A sole root `language.yml` moves into `languages/language.yml` without replacement.
If both paths exist, both are preserved and the original root policy stays active;
the console explains the conflict. Locale filenames and translated values do not change.
Core adds short section headings and one editing/placeholder guide on startup and
`/rtl reload`, with originals saved in `languages/.documentation-backups/`.
Personal comments outside managed blocks remain intact. Edited managed blocks are left
unchanged. The new `record.saving` message falls back to bundled text when absent.

## Notifications

`notifications.yml` is installed only when absent. Default delivery stays chat with no
sound. Override existing language keys, for example:

```yaml
schema-version: 1
overrides:
  trail.arrived:
    delivery: action-bar
    sound:
      key: minecraft:entity.player.levelup
      volume: 0.5
      pitch: 1.0
```

Delivery options: `chat`, `action-bar`, `title`, `silent`. Title timing uses
`fade-in-ms`, `stay-ms`, `fade-out-ms`. Text remains in the language catalogs.
Player delivery uses Core's entity scheduler; console replies remain text.
Reload notification/configuration settings with `/riddlestrails reload`; reload
language catalogs with `/rtl reload`. Invalid notification reloads retain the last
accepted profiles; invalid startup profiles fall back to chat. Neither rewrites settings.

## Public animation API

The existing `select`, `reset`, `setStyle` and `resetStyle` signatures remain. They wait
for durable saving and can block their caller. New `selectAsync`, `resetAsync`,
`setStyleAsync` and `resetStyleAsync` methods return completion stages; Trails commands
use these. Success means the mutation is saved and visible. Completion runs off player
threads; consumers schedule any subsequent player/world work on its owning scheduler.

Synchronous mutation calls from Trails' own shared Core file workers are explicitly
rejected before admission to prevent a circular wait. Use async methods there, without
joining. Existing synchronous callers on other threads retain save-before-return behavior.
No API signatures or events were removed.

## Other Core adoption

- Core argument helpers parse route identifiers, particle enums and suitable numeric
  inputs. Existing coordinate exponent/hex syntax remains; nonfinite values are rejected.
- Health reports distinguish route files, preferences, notification configuration and
  Paper-only quest availability: `/rl health riddlestrails`.
- Platform detection now uses Core's platform snapshot.
- Existing Core scheduling, commands, listeners, configuration, FastStats, languages and
  lifecycle remain in use. No menu, profile lookup or dialog feature was added without
  a current Trails use case. Quest guidance remains Paper-only.

## Evidence

Compilation/package and static source/archive inspection only. No tests, test compilation,
server runs, live data access, publishing or deployment. Runtime validation belongs to the
owner. Build log and artifact hashes are under `.build/`.
