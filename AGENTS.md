# Riddles Trails owner instructions

Edit and build this project in F:/Plugins Coding/RiddlesTrails. The authoritative Core project is F:/Plugins Coding/RiddlesCore.

Do not run tests, test harnesses, benchmarks or Minecraft servers. The owner performs all testing. Compile/package only with -Dmaven.test.skip=true -DskipITs=true. Never claim runtime validation from compilation.

Use Riddles Trails branding, com.riddleslabs.trails packages, riddlestrails permissions and animation namespace. Keep old branding out of plugin source, metadata, commands and documentation. Do not rename the remote repository or publish without authorization.

RiddlesLabsCore is a required runtime dependency and its API uses provided Maven scope. Never shade Core or its APIs. Use an owned PaperModule for lifecycle, schedulers, listeners, command registration, configuration and diagnostics. Keep particle effects, route/quest geometry, gameplay policy and existing data formats in Trails. Do not impose a TNE/economy dependency on this plugin; it has no economy feature.

Preserve existing route and preference data. Do not touch the owner's live server or automatically migrate its files. Quest world/NPC integration was adapted to Folia in 1.6.5; preserve its entity/region ownership and stale-request guards.

The owner requested the Minecraft backport on 2026-10-23. Target Paper 1.20 through 26.2 and available Folia releases from 1.20.1 through 26.2, using Core 0.6.18 or newer within 0.6.x. Compile Java 21 bytecode with JDK 25 against Paper 1.20; also compile against current Paper/Folia APIs. Declare API 1.20. Preserve particle choices and use capability adapters for names/data; do not hard-link newer particle constants/classes. No extra adapter plugin is required. Runtime qualification remains with the owner.
