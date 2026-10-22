# Native Paper and Folia packaging

Current Trails build: 1.6.2 with Core 0.6.9. The historical migration below introduced native Paper command registration.
Replace Core and each installed consumer JAR while the server is stopped. Keep
existing configuration, language catalogs, data, rental state and pairing.

Both consumers now include only `paper-plugin.yml`, declare API 26.2 and Folia
support, and load POSTWORLD after Core. Required and optional integrations use
explicit server dependencies with classpath access. Commands and aliases register
through Core's Paper lifecycle API during onEnable. Permissions are preserved.
`commands.yml` is bundled command metadata, not a server-editable configuration.

RRM registers its commands before asynchronous storage startup. Until delegates
are ready, commands report startup status. Fallback activation and reload replace
existing delegates without registering duplicate lifecycle handlers. TNE/rental
logic and saved financial identities are unchanged. Existing data-folder migration
from the prior RRM name remains in place.

Trails keeps its route and preference formats. Ordinary trails retain Folia
scheduling. Its Citizens/BeautyQuests quest integration remains Paper-only; this
packaging change does not expand that feature's supported platforms.

Core 0.6.7 adds localized feedback to lifecycle command registration. Existing
Core command APIs remain available to other consumers.

Implementation references: [Paper plugin descriptors and dependencies](https://docs.papermc.io/paper/dev/getting-started/paper-plugins/)
and [Paper command lifecycle registration](https://docs.papermc.io/paper/dev/command-api/basics/registration/).

Validation is compilation and static source/JAR inspection only. No tests, test
compilation, server runs, live database access, publishing or deployment.
