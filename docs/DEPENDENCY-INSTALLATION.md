# Dependency installation guidance

Riddles Trails requires RiddlesLabsCore 0.6.18 or newer within 0.6.x on this Paper/Folia server.

Core download: https://github.com/Riddles-Labs/Releases#downloads

No economy plugin is required. PlaceholderAPI and quest integrations are optional.

If the server reports a missing dependency or Core fails to enable, this plugin cannot start. Stop the server, install the required compatible JARs in plugins/, then restart. Website: https://riddleslabs.com

The loader prints this notice before dependency validation, including on successful starts. Paper still enforces every required dependency; no dependency was made optional, downloaded automatically or bypassed. Unsupported Java/API versions can be rejected even earlier by the server. No tests or runtime validation were performed.
