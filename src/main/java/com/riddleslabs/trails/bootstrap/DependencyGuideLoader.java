package com.riddleslabs.trails.bootstrap;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;

/** Runs during discovery, before Paper rejects missing required server dependencies.
 * Deliberately independent of Core: this guidance must work when Core is absent.
 * This is an installation notice, not a claim that a dependency is missing.
 */
public final class DependencyGuideLoader implements PluginLoader {
    @Override
    public void classloader(PluginClasspathBuilder builder) {
        var logger = builder.getContext().getLogger();
        logger.info("[Installation requirements] Riddles Trails requires RiddlesLabsCore 0.6.18 or newer within 0.6.x on this Paper/Folia server.");
        logger.info("[Installation requirements] Core download: https://github.com/Riddles-Labs/Releases#downloads");
        logger.info("[Installation requirements] No economy plugin is required. PlaceholderAPI and quest integrations are optional.");
        logger.info("[Installation requirements] If the server reports a missing dependency or Core fails to enable, this plugin cannot start. Stop the server, install the required compatible JARs in plugins/, then restart. Website: https://riddleslabs.com");
    }
}
