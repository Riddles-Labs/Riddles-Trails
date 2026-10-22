# Riddles Trails languages — 1.6.0

Install RiddlesLabsCore **0.6.5** and RiddlesTrails **1.6.0** together, with the server stopped. Replace the older JARs rather than keeping duplicate versions. Preserve the existing Trails data folder. These builds were compiled with Java 25; Trails keeps the Paper/Folia 26.2 baseline. Quest guidance remains Paper-only.

## What changes

All current Trails command replies, help, permission replies, recording notices, arrival/timeout messages, animation management replies and quest guidance notices now use Core's language service: **72 keys**, supplied in **English and Spanish**. Commands, destination IDs, animation IDs, world names and permission nodes stay stable. Source exception details remain in the console; players receive translated failure messages. Startup logs, technical diagnostics, configuration comments and API exception strings remain developer-facing English.

Existing routes, animations and preferences retain their existing formats. No live-server files were touched during development. The first startup adds these files when absent:

```
plugins/RiddlesTrails/languages/language.yml
plugins/RiddlesTrails/languages/en.yml
plugins/RiddlesTrails/languages/es.yml
```

## Owner controls

| Command | Purpose | Permission |
| --- | --- | --- |
| `/rtl list` | Available languages and current selection | `riddlestrails.use` |
| `/rtl es` / `/rtl en` | Save your language preference | `riddlestrails.use` |
| `/rtl auto` | Restore automatic selection | `riddlestrails.use` |
| `/rtl reload` | Validate and reload all enabled language files | `riddlestrails.admin` plus use |
| `/rtl status` | Revision and fallback coverage | `riddlestrails.admin` plus use |

`/rtl` is an alias of `/riddlestrailslanguage`. The existing `/rt` command is unchanged. `/rt reload` still reloads routes/configuration/animations; `/rtl reload` reloads languages without stopping active trails.

Edit message text in `languages/en.yml` or `languages/es.yml`, then run `/rtl reload`. Keep message keys and `<var_name>` placeholders intact. Color/style tags must be closed. Values inserted by the plugin cannot inject formatting. Use `''` or `[]` to suppress a particular message, or remove a custom override to inherit the bundled value. Reload failure keeps the previously working language set and logs the reason. Fix the file and reload again. Invalid files on startup prevent Trails from enabling until corrected.

The default `languages/language.yml` uses each player's Minecraft client language, falling back to English. To make Spanish the server fallback, set `server-locale: es`. To force server language for everyone, set **both** `use-client-locale: false` and `allow-player-override: false`. Console uses the server locale.

Player choices are stored through Core in Minecraft's player data and can be shared with other plugins that adopt the same Core service. Existing RRM/TNE language settings have not been migrated. This preference does not synchronize between separate servers.

## Add another language

1. Create `languages/fr.yml` with `schema-version: 1` and a `messages` section. Use an existing catalog as a reference.
2. Add `fr` to the `locales` list in `languages/language.yml`.
3. Translate text while keeping keys, commands and variables unchanged. A partial catalog is allowed; missing keys use the fallback chain.
4. Run `/rtl reload`, then `/rtl status` to see the number of keys still using fallback. Players can select `/rtl fr`.

The service understands language tags and regional/script fallback. A player using `es-MX` can use the bundled `es` translations. For custom regional catalogs, use canonical filenames such as `pt-BR.yml`.

Route-save messages demonstrate plural handling: the catalog defines the complete singular/plural phrases, and Core chooses the correct CLDR category. Numbers are formatted for the message's language. No translation API key, website account, database or Discord connection is needed.

## Developer adoption

`TrailMessages` is the product adapter: resource loading, product permissions, response scheduling and message keys. Core owns catalog validation, fallback, rendering, preferences, formatting and reload publication. `TrailService`, `AnimationCommand`, `QuestTrailCommand` and `QuestTrailService` now depend on this adapter. Quest notice deduplication includes the locale and catalog revision so a language change or reload does not permanently hide a notice.

Use the Core repository's `docs/LANGUAGES.md` for the shared API and format. The existing public Trails animation/quest API contracts remain unchanged. Core API classes and ICU are not bundled in Trails.

## Build scope

Compilation/package and static source/archive review only. No tests or test compilation, server runs, live data changes, pushes or deployment. Actual-server behavior remains for the owner to validate.

## Version 1.6.2

Existing root language settings move into languages automatically when there is no
conflict. If both policy files exist, both are preserved and the root policy stays active.
Core adds short managed headings and backs up changed catalogs. Translations stay intact.
Optional delivery settings are in notifications.yml; reload those with /riddlestrails reload.
