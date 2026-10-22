package com.riddleslabs.trails.animation;

import com.riddleslabs.trails.api.animation.*;
import com.riddleslabs.api.config.ReloadableConfiguration;
import com.riddleslabs.paper.api.config.YamlConfigurations;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class AnimationEngine implements TrailAnimationAPI, Listener {
    private static final java.util.regex.Pattern VALID_ID=java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9/.-]+");
    private record Registration(Plugin owner,TrailAnimation animation) {}
    private record Key(UUID player,TrailKind kind) {}
    private static final class FrameState {
        final String source,id;
        final Registration registration;
        final double started;
        final Map<String,Object> data=new ConcurrentHashMap<>();
        volatile double lastElapsed;
        boolean failed;
        FrameState(String source,String id,Registration registration,double elapsed) {
            this.source=source;this.id=id;this.registration=registration;started=elapsed;lastElapsed=elapsed;
        }
    }
    private final JavaPlugin plugin;
    private final com.riddleslabs.trails.TrailMetrics metrics;
    private final Map<String,Registration> registry=new ConcurrentHashMap<>();
    private final AnimationPreferences preferences;
    private final com.riddleslabs.api.files.FileCoordinator persistence;
    private final Map<Key,FrameState> states=new ConcurrentHashMap<>();
    private volatile AnimationConfig config;
    private final ReloadableConfiguration<AnimationConfig> configuration;
    private volatile boolean closed;

    public AnimationEngine(JavaPlugin plugin, YamlConfigurations configurations, com.riddleslabs.api.files.FileCoordinator persistence) {
        this(plugin, configurations, persistence, null);
    }
    public AnimationEngine(JavaPlugin plugin, YamlConfigurations configurations, com.riddleslabs.api.files.FileCoordinator persistence, com.riddleslabs.trails.TrailMetrics metrics) {
        this.plugin=plugin;
        this.metrics=metrics;
        this.persistence=persistence;
        preferences=persistence.blocking(() -> new AnimationPreferences(persistence));
        BuiltInAnimations.all().forEach((id,animation)->registry.put(id,new Registration(plugin,animation)));
        try (var input=Objects.requireNonNull(plugin.getResource("animations.yml"));
             var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            YamlConfiguration defaults=new YamlConfiguration();
            defaults.load(reader);
            configuration=configurations.open("animations.yml", candidate -> {
                // Merge new shipped settings into the private candidate without overwriting operator files.
                defaults.getValues(true).forEach((key,value) -> {
                    if (!(value instanceof org.bukkit.configuration.ConfigurationSection) && !candidate.contains(key))
                        candidate.set(key,value);
                });
                AnimationConfig next=AnimationConfig.read(candidate);
                if(!registry.containsKey(next.fallback())||registry.get(next.fallback()).owner!=plugin)
                    throw new IllegalArgumentException("Fallback must be a built-in animation: "+next.fallback());
                return next;
            });
            config=configuration.snapshot().value();
        } catch(Exception ex) { throw new IllegalArgumentException("Could not load animations.yml: "+ex.getMessage(),ex); }
    }
    public synchronized void reload() {
        open();
        try {
            config=configuration.reload().value();
            states.clear();
        } catch(Exception ex) { throw new IllegalArgumentException("Could not reload animations.yml: "+ex.getMessage(),ex); }
    }
    public static String id(String value) {
        String id=Objects.requireNonNull(value).trim().toLowerCase(Locale.ROOT).replace('_','-');
        if(!id.contains(":")) id="riddlestrails:"+id;
        if(!VALID_ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid animation ID: "+value);
        return id;
    }
    private void open() { if(closed) throw new IllegalStateException("Animation API is disabled"); }
    @Override public void register(Plugin owner,String name,TrailAnimation animation) {
        open();Objects.requireNonNull(owner);Objects.requireNonNull(animation);
        String id=id(name);
        if(owner!=plugin && id.startsWith("riddlestrails:")) throw new IllegalArgumentException("Custom animations must use their own namespace");
        if(!owner.isEnabled()) throw new IllegalStateException("Animation owner is not enabled");
        if(registry.putIfAbsent(id,new Registration(owner,animation))!=null) throw new IllegalArgumentException("Animation already registered: "+id);
    }
    @Override public boolean unregister(Plugin owner,String name) {
        open();String id=id(name); Registration registration=registry.get(id);
        if(registration==null) return false;
        if(registration.owner!=owner) throw new IllegalArgumentException("Only the registering owner may unregister "+id);
        if(registration.owner==plugin) throw new IllegalArgumentException("Built-in animations cannot be unregistered");
        boolean removed=registry.remove(id,registration);
        if(removed) states.entrySet().removeIf(e->e.getValue().id.equals(id));
        return removed;
    }
    public long customCount() { return registry.values().stream().filter(r -> r.owner != plugin).count(); }
    @Override public Set<String> animations() { return Collections.unmodifiableSet(new TreeSet<>(registry.keySet())); }
    @Override public void select(UUID playerId,String name) {
        open();String id=id(name);
        if(!registry.containsKey(id)) throw new IllegalArgumentException("Unknown animation: "+id);
        persistence.blocking(() -> { preferences.select(playerId,id); clearStates(playerId); return null; });
    }
    @Override public void reset(UUID playerId) { open();persistence.blocking(() -> { preferences.select(playerId,null);clearStates(playerId);return null; }); }
    @Override public String selected(UUID playerId) {
        String requested=preferences.selection(playerId,config.selected());
        return registry.containsKey(requested)?requested:config.fallback();
    }
    @Override public void setStyle(UUID playerId,AnimationStyle style) { open();Objects.requireNonNull(style);persistence.blocking(() -> { preferences.setStyle(playerId,style);return null; }); }
    @Override public AnimationStyle style(String animationId) { return config.style(id(animationId)); }
    @Override public void resetStyle(UUID playerId) { open();persistence.blocking(() -> { preferences.setStyle(playerId,null);return null; }); }
    @Override public java.util.concurrent.CompletionStage<Void> selectAsync(UUID player, String name) {
        return persistence.submit(() -> { select(player, name); return null; });
    }
    @Override public java.util.concurrent.CompletionStage<Void> resetAsync(UUID player) {
        return persistence.submit(() -> { reset(player); return null; });
    }
    @Override public java.util.concurrent.CompletionStage<Void> setStyleAsync(UUID player, AnimationStyle style) {
        return persistence.submit(() -> { setStyle(player, style); return null; });
    }
    @Override public java.util.concurrent.CompletionStage<Void> resetStyleAsync(UUID player) {
        return persistence.submit(() -> { resetStyle(player); return null; });
    }
    private void clearStates(UUID id) { states.keySet().removeIf(key->key.player.equals(id)); }
    public void forget(UUID player,TrailKind kind) { states.remove(new Key(player,kind)); }

    public void render(Player player,TrailKind kind,String source,AnimationPath path,double elapsed,double spacing,int budget) {
        if(closed) return;
        UUID uuid=player.getUniqueId();AnimationConfig snapshot=config;
        String requested=selected(uuid);
        AnimationStyle style=preferences.style(uuid,snapshot.style(requested));
        TrailAnimationSelectEvent event=new TrailAnimationSelectEvent(player,kind,source,requested,style);
        Bukkit.getPluginManager().callEvent(event);
        if(event.isCancelled()) return;
        String selected;
        try { selected=requested.equals(event.getAnimationId())?requested:id(event.getAnimationId()); } catch(IllegalArgumentException invalid) { selected=snapshot.fallback(); }
        if(!registry.containsKey(selected)) selected=snapshot.fallback();
        if(event.isStyleChanged()) style=event.getStyle();
        else if(!selected.equals(requested)) style=preferences.style(uuid,snapshot.style(selected));
        Registration registration=registry.get(selected);
        if(registration==null) return;
        Key key=new Key(uuid,kind);
        FrameState state=states.get(key);
        if(state==null||!state.source.equals(source)||!state.id.equals(selected)||state.registration!=registration||elapsed<state.lastElapsed) {
            state=new FrameState(source,selected,registration,elapsed);states.put(key,state);
        }
        state.lastElapsed=elapsed;
        if(state.failed) { registration=registry.get(snapshot.fallback()); style=snapshot.style(snapshot.fallback()); }
        if(registration==null) return;
        var location=player.getLocation();
        AnimationFrame frame=new AnimationFrame(uuid,kind,source,path,
                new AnimationPoint(location.getX(),location.getY(),location.getZ()),Math.max(0,elapsed-state.started),
                uuid.getMostSignificantBits()^uuid.getLeastSignificantBits()^source.hashCode(),spacing,budget,style,state.data);
        try(ViewerParticleSink sink=new ViewerParticleSink(player,style,budget,(long)(elapsed*20)^frame.seed())) {
            registration.animation.render(frame,sink);
        } catch(RuntimeException|LinkageError ex) {
            if (!state.failed && metrics != null) metrics.failure(com.riddleslabs.trails.TrailMetrics.Counter.ANIMATION_FAILURES, ex);
            if(!state.failed) plugin.getLogger().warning("Animation "+selected+" failed; using fallback: "+ex.getMessage());
            state.failed=true;state.data.clear();
        }
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        clearStates(event.getPlayer().getUniqueId());
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.MONITOR)
    public void teleported(PlayerTeleportEvent event) { clearStates(event.getPlayer().getUniqueId()); }
    @EventHandler public void disabled(PluginDisableEvent event) {
        if(event.getPlugin()==plugin) return;
        registry.entrySet().removeIf(e->e.getValue().owner==event.getPlugin());
        states.entrySet().removeIf(e->e.getValue().registration.owner==event.getPlugin());
    }
    public void shutdown() { closed=true;states.clear();registry.clear(); }
}
