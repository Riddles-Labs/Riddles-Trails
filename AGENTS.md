# Riddles Trails owner instructions

Edit and build this project in F:/Plugins Coding/RiddlesTrails. The authoritative Core project is F:/Plugins Coding/RiddlesCore.

Do not run tests, test harnesses, benchmarks or Minecraft servers. The owner performs all testing. Compile/package only with -Dmaven.test.skip=true -DskipITs=true. Never claim runtime validation from compilation.

Use Riddles Trails branding, com.riddleslabs.trails packages, riddlestrails permissions and animation namespace. Keep old branding out of plugin source, metadata, commands and documentation. Do not rename the remote repository or publish without authorization.

RiddlesLabsCore is a required runtime dependency and its API uses provided Maven scope. Never shade Core or its APIs. Use an owned PaperModule for lifecycle, schedulers, listeners, command registration, configuration and diagnostics. Keep particle effects, route/quest geometry, gameplay policy and existing data formats in Trails. Do not impose a TNE/economy dependency on this plugin; it has no economy feature.

Preserve existing route and preference data. Do not touch the owner's live server or automatically migrate its files. Quest world/NPC integration remains Paper-only until explicitly adapted to Folia ownership.

The current server baseline is Folia 26.2. Compile against Paper API 26.2.build.133-stable and declare Minecraft API 26.2. Do not raise the minimum server version without an explicit request.
