# Historical performance work in 1.4.2

This update keeps the 1,200-particle default, all 47 effects, colors, animation timing and saved player preferences. It reduces work needed to construct the same particle geometry and validate walking routes.

## Measurements

Local Windows/JDK 25 measurements against 1.4.1 commit `0621a96`, using the same opt-in benchmark and Java Flight Recorder settings before and after. Values are medians of seven batches after warm-up.

| Workload | Before | After | Change |
| --- | ---: | ---: | ---: |
| 5,640 animation frames: all 47 effects, 1,200 budget, 20-block bent path | 381.866 ms | 134.974 ms | 64.7% less CPU time |
| Allocations for those animation frames | 290.961 MiB | 62.508 MiB | 78.5% less allocation |
| 1,000 nearest-position queries on a 5,000-point route | 50.161 ms | 0.595 ms | 98.8% less CPU time |
| Allocations for those route queries | <0.001 MiB | 0.038 MiB | Small per-query search state |

These are offline geometry benchmarks, not live Minecraft TPS/FPS measurements. The animation sink consumes positions without sending packets. Real gains depend on route layout, player count, other plugins, networking and clients. Packet counts and client particle load are intentionally unchanged. The route workload favors spatial pruning; heavily overlapping routes can require scanning most segments.

## Changes

- Compute a shape's route anchor and heading once and reuse its transform for every vertex. Reuse centerline normals for adjacent emissions at the same progress.
- Walk outline edges in one forward pass, hoist constant trigonometry, and reuse bounded immutable circle/heart templates. Dynamic ink outlines use private copies.
- Reuse dust data for consecutive emissions with identical appearance. Skip unused color work for non-colorable particles; retain custom particle-data validation and per-viewer emission.
- Build an immutable bounding hierarchy for routes with at least 64 points. Nearest queries prune distant segment groups and preserve the original earliest-segment tie behavior. Short paths use a linear scan. Index construction adds work when a path is created, rather than repeatedly scanning long paths each update.
- Cache loaded-chunk checks and world-space collision boxes within one synchronous render or connector search, including its candidate entrances. Limit the block cache to 16,384 entries. Discard it after the operation; the next update sees changed blocks and unloaded chunks. No terrain is loaded or checked asynchronously.
- Reuse quest progress calculations within a frame, cache optional-plugin method discovery by class lifetime, and avoid normalizing an animation ID again when the event leaves it unchanged.

The corridor regression workload (101 samples across a 20-block flat route) verifies over 75% fewer block queries, then checks new obstacles and unloaded chunks with a fresh snapshot. Stairs, fences, low ceilings, wall detours and teleport sections remain covered by the existing tests.

## Current build policy

The measurements above are historical results from the earlier 1.4.2 work; they do not establish performance of this Core adaptation. The owner performs all testing on their server. No tests or benchmarks are run as part of this migration, and Maven defaults to skipping them.
