# Trail animations

Riddles Trails 1.5.0-SNAPSHOT gives each of the original 46 effects its own particle shape and adds `walking-ghost`, for 47 effects total. Hearts, stars, chevrons, clock faces, shoe outlines, hourglasses, gears, door leaves and humanoid figures move along the route. Colors, particle type, period, size, geometry scale and effect parameters come from `plugins/RiddlesTrails/animations.yml`. The built-ins are registered through the same registry used by extensions; custom plugins are not limited to the built-in names or geometries.

## Server configuration and commands

```yaml
default-animation: comet
fallback-animation: breathing
style:
  particle: DUST
  palette: ['#FFD166', '#F4F1DE', '#80CED7']
  period-seconds: 3.0
  scale: 1.0
  width: 0.8
  height: 1.0
  brightness: 1.0
  base-brightness: 0.08
  size: 0.8
  duration-ticks: 12
  parameters:
    markers: 5
    frequency: 3
    pulse-width: 0.12
    tail-length: 0.3
    ring-points: 12
    origin: 0.0
    wait-distance: 3.0
    look-ahead: 5.0
    shape-size: 0.85
    shape-points: 24
    detail-multiplier: 6.0
patterns:
  comet:
    palette: ['#FF8800', '#FFF0B3']
    period-seconds: 2.5
    parameters: {tail-length: 0.4}
  clockwork-moth:
    particle: DUST_COLOR_TRANSITION
    parameters: {wait-distance: 3.0, look-ahead: 6.0}
  'myplugin:aurora':
    palette: ['#B8F2E6', '#A9DEF9']
```

The shipped file lists every built-in profile. Profile values inherit from `style`; profiles can override any of those fields. Missing shipped settings and new profiles are added to existing files on startup/reload, preserving configured values and saved player preferences. `fallback-animation` must name a built-in. `default-animation` can name a custom animation once its plugin registers it; the fallback is used while it is unavailable. Reload both configuration files with `/riddlestrails reload`. An invalid animation reload reports an error and preserves the previous animation configuration.

```text
trailanimation list
trailanimation set <player> clockwork-moth
trailanimation set <player> walking-ghost
trailanimation set <player> riddlestrails:orbiting-guide
trailanimation set <player> myplugin:aurora
trailanimation reset <player>
```

Commands require `riddlestrails.animation.admin` (console/operators by default). Use the online player's exact name or UUID. Selection takes effect on the next render update and applies to that player's ordinary and quest trails. Selection does not start a destination by itself. Selections and programmatic style overrides are saved by player UUID in `plugins/RiddlesTrails/animation-preferences.yml` and survive logout, configuration reload and server restart. Restored quest trails use the saved choice. Only an explicit reset removes a preference; resetting selection does not reset a programmatic style override.

Changes are saved immediately. A failed save reports an error and keeps the previous preference. Back up `animation-preferences.yml` with your other plugin data; it is managed by the plugin, while `animations.yml` defines effects and defaults. A custom animation whose provider is unavailable temporarily uses the fallback without replacing the saved choice, and resumes when its provider registers again. Frame-local listener overrides are not saved. Choices already discarded by version 1.3.0 must be selected once after upgrading.

BeautyQuests can run these console actions consecutively:

```text
trailanimation set {PLAYER} clockwork-moth
questtrail show {PLAYER} npc 11
```

Particle types `DUST`, `DUST_COLOR_TRANSITION` and `TRAIL` support the configurable palette. Bukkit particles that require no data, such as `FLAME`, are also supported; their colors are determined by Minecraft, so brightness is approximated by emission density. Other data-requiring particles are available to custom animations through explicit particle/data emission. `duration-ticks` applies to `TRAIL`; dust and most other particle lifetimes are controlled by the client. Trail brightness is simulated through re-emission, not by editing particles already sent to the client.

`render.update-interval-ticks`, `render.particle-spacing`, `render.max-particles-per-update`, `render.max-ahead` and `render.height-offset` still come from `config.yml`. Lower update intervals make fast effects smoother at a higher rendering cost. Large effects are reduced by the per-frame particle budget. Width/height/scale affect decorative geometry around the already validated route; those decorative offsets are not themselves navigation or collision checks.

Each animation ID selects its own shape and motion together. `parameters.shape-size` scales symbol outlines, and `parameters.shape-points` controls their base contour detail (12–256, default 24). `parameters.detail-multiplier` (1–12, default 6) multiplies samples across all 47 effects, including fixed outlines, curves, footprints and humanoid models. With the default multiplier a 24-point contour requests 144 distinct positions. `ring-points` accepts 6–128 base samples. Width/height/scale remain available for all geometry. Outlines render before the optional dim centerline, so the baseline cannot consume their particle budget. Rigid symbols retain their shape at corners and endpoints.

The default budget is now 1,200 particles per viewer per update, configurable up to 6,000 in `config.yml`. Detail adapts down when a smaller budget is selected. Upgrading replaces the old stock 120 budget with 1,200 once, records `render.detail-version: 1`, and preserves other configured budgets. The new multiplier is added to existing `animations.yml` files without changing palettes or saved player selections. Set a per-pattern multiplier to tune individual effects. Additional samples trace real new positions, rather than stacking copies at the same coordinate. Clocks gain dial markings, gears gain hubs/spokes, moth wings gain veins, footprints gain treads, and the walking ghost gains filled head/torso surfaces and thicker limbs.

`walking-ghost` is a private particle humanoid, not a spawned Citizens NPC. Its cuboid head and torso face the route while opposing arms and legs swing through a walking gait. The figure advances and dissolves, then reforms ahead. Black particles fade through decreasing emission density as well as brightness. Its default dark palette is configurable:

```yaml
patterns:
  walking-ghost:
    particle: DUST
    palette: ['#050506', '#141418', '#050506']
    period-seconds: 6.0
    width: 1.0
    height: 1.0
    size: 0.45
    base-brightness: 0.0
    parameters:
      shape-size: 1.0
      look-ahead: 2.0
      ghost-distance: 8.0
      gait-cycles: 4.0
      stride: 0.38
```

`look-ahead` is the initial distance along the visible route; `ghost-distance` is its additional travel per cycle. `gait-cycles` and `stride` control walking steps. Movement stops at the visible section's endpoint, including elevator entrances, and resumes on the next section after teleporting.

## Built-in catalog

All IDs accept the `riddlestrails:` prefix. Short IDs below are aliases for that namespace.

| ID | Effect |
| --- | --- |
| `breathing` | Wireframe orbs expand, brighten and contract together. |
| `chase` | Pairs of forward chevrons travel along the route. |
| `comet` | A star-shaped head pulls a curved, tapered tail. |
| `wave` | A ribbon with two edges undulates along the route. |
| `ripple` | Concentric rings expand from a configurable origin. |
| `heartbeat` | Heart outlines enlarge with a double beat, then settle. |
| `twinkle` | Five-point stars swell and gently flicker. |
| `sparkle` | Eight-point starbursts flash and rotate. |
| `scan` | A rectangular scanning gate sweeps with a moving crossbar. |
| `sequential-fill` | Arrow outlines light up progressively, then fade together. |
| `dripping` | Outlined teardrops fall as they move forward. |
| `theater-chase` | Every third diamond lights up in a three-step sequence. |
| `stacking` | Rectangular blocks arrive and stack vertically, then fade. |
| `crossfade-chase` | Two ring-and-chevron guides crossfade while moving. |
| `tick-tock` | Rectangular clock marks with arrows alternate sides. |
| `clock-hands` | Rings carry rotating hands along the route. |
| `time-skip` | A lightning bolt freezes, disappears and reappears ahead with an afterimage. |
| `reverse-echo` | An outlined arrow advances while faint chevrons drift backward. |
| `clockwork-footsteps` | Alternating shoe outlines appear ahead and dissolve in place. |
| `unwinding-spring` | A coil stretches forward and reforms. |
| `steam-bursts` | Sequential curling jets rise from the path. |
| `fuse-burn` | A starburst tip burns along a fuse cord, leaving sparks. |
| `orbiting-guide` | Two streams spiral around the centerline. |
| `shattered-seconds` | Fragments assemble into an arrow and scatter. |
| `pendulum` | A rod with a circular bob swings across the trail as it advances. |
| `ink-reveal` | Irregular puddle outlines spread outward and evaporate. |
| `mechanical-relay` | Toothed gear outlines rotate and light up sequentially. |
| `ghost-of-tomorrow` | A particle silhouette dissolves and reforms farther ahead. |
| `escaping-seconds` | Triangular clock shards leave the viewer, pause, then accelerate toward the route. |
| `clockwork-moth` | Outlined wings flap around a body; the guide waits until the viewer approaches. |
| `broken-timeline` | Displaced, rotated arrow outlines align to reveal the route. |
| `pocket-watch-swing` | A watch face with a rotating hand swings on a particle chain. |
| `borrowed-footsteps` | Invisible-walker footprints sometimes vanish and redraw. |
| `mechanical-fireflies` | Small winged lights gather, drift and spread as a swarm. |
| `stitching-time` | A diamond needle advances, leaving fading X-shaped stitches. |
| `falling-hourglass` | Hourglass frames hold moving grains that fall through their waists. |
| `echo-doors` | Pairs of rectangular door leaves hinge open and dissolve. |
| `magnetic-shavings` | Scattered bars rotate and join into directional chevrons. |
| `clock-teeth` | Outlined gear teeth rise sequentially along the route. |
| `rewinding-ribbon` | Both edges of a ribbon coil backward and unwind forward. |
| `future-glimpse` | A far arrow appears first; markers then connect backward toward the viewer. |
| `pressure-leak` | A bulge runs along an outlined pipe and releases an expanding puff at a bend. |
| `orbit-collapse` | A ring collapses and shoots forward to the next orbit. |
| `unfinished-blueprint` | Dotted construction lines appear before the arrow, then fade. |
| `delayed-shadow` | Stationary steps of a guide catch up after a delay. |
| `second-hand-sweep` | A large clock dial at a bend carries a sweeping hand. |
| `walking-ghost` | A dark block-shaped humanoid walks forward with swinging limbs and dissolves. |

The end of the currently visible walking section is the animation's forward direction. Long routes are still shown through the look-ahead window, and elevator gaps are never treated as continuous walking paths. No animations teleport players or spawn real NPC/entity silhouettes.

## API selection

Use the `riddlestrails` **1.5.0-SNAPSHOT** `api` classifier with Maven `provided` scope, and `depend: [RiddlesTrails]` (or `softdepend` plus a missing-service check). Do not shade the API or install the API-only JAR as a server plugin. The API includes all animation interfaces and event classes without implementation classes.

```java
import com.riddleslabs.trails.api.animation.TrailAnimationAPI;
import com.riddleslabs.trails.api.QuestTrailAPI;

TrailAnimationAPI animations = getServer().getServicesManager().load(TrailAnimationAPI.class);
QuestTrailAPI quests = getServer().getServicesManager().load(QuestTrailAPI.class);
if (animations == null) return;

animations.select(player.getUniqueId(), "riddlestrails:clockwork-moth");
if (quests != null) quests.showToNpc(player.getUniqueId(), 11);

// Later, switch an already-running trail:
animations.select(player.getUniqueId(), "riddlestrails:comet");
animations.reset(player.getUniqueId());
```

`animations.animations()` returns registered IDs, including third-party IDs. Registration and preference access are thread-safe. Preference mutations serialize disk writes before publishing an immutable snapshot and throw `IllegalStateException` if saving fails. Set persistent preferences when they change, rather than on every animation frame; use the selection event for frame-local changes. Rendering itself always stays on the ordinary trail's owning entity scheduler, or the quest service's Paper server thread. The animation service is available for ordinary trails on Folia; the Citizens/BeautyQuests quest service still requires Paper.

For programmatic appearance, pass an immutable `AnimationStyle` to `setStyle(UUID, style)`. Read a configured profile with `animations.style("comet")`, then use helpers `withPalette`, `withParticle` and `withPeriod` to derive a style (or use the full constructor). For example: `animations.setStyle(playerId, animations.style("comet").withPeriod(5.0))`. `resetStyle(UUID)` returns to configuration. Per-player styles override profile configuration; listener styles override both for that frame.

## Listener selection

```java
import com.riddleslabs.trails.api.animation.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class QuestEffects implements Listener {
    @EventHandler
    public void chooseAnimation(TrailAnimationSelectEvent event) {
        if (event.getKind() == TrailKind.QUEST && event.getSource().equals("npc:11")) {
            event.setAnimationId("riddlestrails:echo-doors");
        }
        // Optional frame-local appearance override:
        // event.setStyle(event.getStyle().withPalette(List.of(Color.AQUA, Color.WHITE)));
        // event.setCancelled(true); // suppress only this frame
    }
}
```

Register the listener with Bukkit normally. The event runs before **every frame**, so keep it quick and avoid disk/network work. Source IDs are saved destination IDs for ordinary trails, `npc:<id>` for NPC quests, and `location:<world>:<x>:<y>:<z>` for location quests. Changing only the animation ID selects that profile's style automatically; explicitly setting a style takes precedence. Unknown listener-selected IDs fall back to the configured fallback.

On Folia the event is marked asynchronous and fires on the viewer's owning entity thread. Access only that player's region and thread-safe data. It is not an invitation to touch other players or arbitrary regions. Paper events use the main thread. Listener selection is frame-local and does not change the persistent per-player API selection.

## Register a new animation

```java
import com.riddleslabs.trails.api.animation.*;

TrailAnimationAPI animations = getServer().getServicesManager().load(TrailAnimationAPI.class);
if (animations == null) return;

animations.register(this, "myplugin:aurora", (frame, particles) -> {
    double phase = frame.phase();
    for (double u = 0; u <= 1 && particles.remaining() > 0; u += 0.04) {
        double side = Math.sin((u * 3 - phase) * Math.PI * 2) * 0.5;
        AnimationPoint position = frame.point(u, side, 0.4);
        // Configured particle, palette and brightness; always private to the viewer.
        particles.emit(position, 0.8, u);
    }
});
```

`frame.path()` is an immutable snapshot of the current visible section. `frame.point(u, lateral, vertical)` uses normalized progress and style-scaled local offsets. For rigid custom shapes use `frame.localPoint(u, forward, lateral, vertical)`: it anchors the shape to the route's heading without bending its outline around corners or flattening it at endpoints. Forward/lateral coordinates use width and scale; vertical coordinates use height and scale. `path.at(distance)` uses distances in blocks. `frame.playerPosition()` is a snapshot, not a live Bukkit entity. `elapsedSeconds`, `seed`, `spacing`, `budget` and `style` are available. `frame.state()` is scratch storage isolated by player, trail kind, source and selected animation; it is reset on selection changes, teleport, trail cleanup and configuration reload. Use it for waiting guides or particle histories. Do not store unbounded histories.

For many vertices at the same anchor, compute `AnimationTransform transform = frame.localTransform(0.5)` once and call `transform.point(forward, lateral, vertical)` inside the vertex loop. The immutable transform avoids repeating route interpolation and heading calculations for every particle. Create a new transform for the next frame when the visible path changes. Built-in effects use this path and bounded, immutable unit-shape caches without reducing their particle detail.

For a custom Bukkit particle with explicit data:

```java
particles.emit(Particle.FLAME, frame.point(0.5, 0, 0.2), 1, null);
particles.emit(Particle.DUST, frame.point(0.6, 0, 0.2), 1,
    new Particle.DustOptions(frame.style().color(0.6, 1), frame.style().size()));
```

The emitter validates particle data types and counts, caps output to the remaining budget, and sends only to the assigned viewer. It is valid only during the render callback and only on that callback's thread. Retaining it or emitting asynchronously throws an exception. Positive counts are required; zero-count directional emission is not exposed. For custom movement, update positions using the frame clock.

Use a unique namespace such as `myplugin:aurora`; `riddlestrails:` is reserved. IDs are case-insensitive and underscores normalize to hyphens. Duplicate registrations are rejected. Only the registering plugin may unregister its animation with `unregister(owner, id)`. Registrations are automatically removed when that plugin disables. Render exceptions are isolated to that animation/player state and switch subsequent frames to the fallback, without repeatedly invoking the broken callback. Plugin callbacks must still finish promptly: particle budgets cannot stop arbitrary slow plugin code.

## Preview and verification

Run `mvn clean verify`, then open `target/animation-preview.html` to play all 47 effects, filter by name, pause/restart and switch between route and shape close-up views. `target/animation-atlas.png` shows whole-route snapshots; `target/animation-shapes.png` shows enlarged details. The black ghost preview uses a light background for contrast. These use emitted coordinates and configured colors captured from the real Java callbacks. They do not simulate Minecraft textures, lingering particles or client performance. Confirm final appearance in game with the chosen particle type and update interval.
