package com.riddleslabs.trails.animation;

import com.riddleslabs.trails.api.animation.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnimationEngineTest {
    @TempDir Path directory;
    AnimationEngine engine;JavaPlugin plugin;Plugin extension;Player viewer;PluginManager manager;
    MockedStatic<Bukkit> bukkit;
    final UUID uuid=new UUID(1,2);
    @BeforeEach void setup() throws Exception {
        Files.copy(Path.of("src/main/resources/animations.yml"),directory.resolve("animations.yml"));
        plugin=mock(JavaPlugin.class);extension=mock(Plugin.class);viewer=mock(Player.class);manager=mock(PluginManager.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());when(plugin.isEnabled()).thenReturn(true);
        when(extension.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("animations-test"));
        when(plugin.getResource("animations.yml")).thenAnswer(call->Files.newInputStream(Path.of("src/main/resources/animations.yml")));
        when(viewer.getUniqueId()).thenReturn(uuid);when(viewer.getLocation()).thenReturn(new Location(mock(World.class),0,64,0));
        bukkit=mockStatic(Bukkit.class);bukkit.when(Bukkit::getPluginManager).thenReturn(manager);bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        engine=new AnimationEngine(plugin);
    }
    @AfterEach void close() {engine.shutdown();bukkit.close();}
    void render(double seconds) {engine.render(viewer,TrailKind.QUEST,"npc:11",BuiltInAnimationsTest.path(),seconds,.8,12);}

    @Test void upgradeAddsShapeSettingsAndGhostProfileWithoutReplacingCustomValues() throws Exception {
        Path file=directory.resolve("animations.yml");
        var yaml=new org.bukkit.configuration.file.YamlConfiguration();yaml.load(file.toFile());
        yaml.set("patterns.walking-ghost",null);yaml.set("style.parameters.shape-size",null);
        yaml.set("style.palette",List.of("#AA3311"));yaml.set("patterns.comet.period-seconds",7.0);yaml.save(file.toFile());
        engine.select(uuid,"clockwork-moth");engine.reload();yaml.load(file.toFile());
        assertTrue(yaml.isConfigurationSection("patterns.walking-ghost"));
        assertEquals(.85,yaml.getDouble("style.parameters.shape-size"));
        assertEquals(List.of("#AA3311"),yaml.getStringList("style.palette"));
        assertEquals(7.0,engine.style("comet").periodSeconds());
        assertEquals("riddlestrails:clockwork-moth",engine.selected(uuid));
    }

    @Test void logoutKeepsPreferencesButClearsRenderState() {
        List<AnimationFrame> frames=new ArrayList<>();
        engine.register(extension,"example:state",(frame,sink)->frames.add(frame));
        var style=engine.style("comet").withPalette(List.of(Color.BLUE));
        engine.select(uuid,"example:state");engine.setStyle(uuid,style);render(0);
        PlayerQuitEvent quit=mock(PlayerQuitEvent.class);when(quit.getPlayer()).thenReturn(viewer);
        engine.quit(quit);render(1);
        assertEquals("example:state",engine.selected(uuid));
        assertEquals(style,frames.get(1).style());
        assertNotSame(frames.get(0).state(),frames.get(1).state());
    }

    @Test void selectionSurvivesReloadAndRestartIndependentlyForEachPlayer() {
        UUID other=UUID.randomUUID();
        String defaultAnimation=engine.selected(uuid);
        engine.select(uuid,"wave");engine.select(other,"clockwork-moth");
        engine.reload();assertEquals("riddlestrails:wave",engine.selected(uuid));
        engine.shutdown();engine=new AnimationEngine(plugin);
        assertEquals("riddlestrails:wave",engine.selected(uuid));
        assertEquals("riddlestrails:clockwork-moth",engine.selected(other));
        engine.reset(uuid);engine.shutdown();engine=new AnimationEngine(plugin);
        assertEquals(defaultAnimation,engine.selected(uuid));
        assertEquals("riddlestrails:clockwork-moth",engine.selected(other));
    }

    @Test void customSelectionAndCompleteStyleReturnAfterProviderRegistersOnRestart() {
        engine.register(extension,"example:custom",(frame,sink)->{});
        var style=new AnimationStyle(Particle.DUST_COLOR_TRANSITION,List.of(Color.BLUE,Color.RED),
                3.5,1.2,.6,.7,.8,.2,1.4f,35,Map.of("custom-speed",2.3));
        engine.select(uuid,"example:custom");engine.setStyle(uuid,style);
        engine.shutdown();engine=new AnimationEngine(plugin);
        assertEquals("riddlestrails:breathing",engine.selected(uuid)); // Provider has not enabled yet.
        List<AnimationFrame> frames=new ArrayList<>();
        engine.register(extension,"example:custom",(frame,sink)->frames.add(frame));render(0);
        assertEquals("example:custom",engine.selected(uuid));
        assertEquals(style,frames.getFirst().style());
        engine.resetStyle(uuid);engine.shutdown();engine=new AnimationEngine(plugin);
        engine.register(extension,"example:custom",(frame,sink)->frames.add(frame));render(0);
        assertEquals("example:custom",engine.selected(uuid));
        assertNotEquals(style,frames.getLast().style());
    }

    @Test void resettingSelectionKeepsStyleAndResettingBothStaysResetAfterRestart() {
        String defaultAnimation=engine.selected(uuid);
        var style=engine.style("comet").withPalette(List.of(Color.BLUE));
        engine.select(uuid,"comet");engine.setStyle(uuid,style);engine.reset(uuid);
        engine.shutdown();engine=new AnimationEngine(plugin);
        assertEquals(defaultAnimation,engine.selected(uuid));
        List<AnimationFrame> frames=new ArrayList<>();
        engine.register(extension,"example:style",(frame,sink)->frames.add(frame));
        engine.select(uuid,"example:style");render(0);assertEquals(style,frames.getFirst().style());
        engine.reset(uuid);engine.resetStyle(uuid);
        engine.shutdown();engine=new AnimationEngine(plugin);
        assertEquals(defaultAnimation,engine.selected(uuid));
        engine.register(extension,"example:style",(frame,sink)->frames.add(frame));
        engine.select(uuid,"example:style");render(0);assertNotEquals(style,frames.getLast().style());
    }

    @Test void selectionsAreSavedImmediatelyAndFailedWriteKeepsPreviousChoice() throws Exception {
        engine.select(uuid,"comet");
        Path file=directory.resolve("animation-preferences.yml");
        assertEquals("riddlestrails:comet",new AnimationPreferences(file).selection(uuid,"fallback"));
        // A nonempty directory at the file path makes replacement fail on every platform.
        Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("blocker"),"keep");
        assertThrows(IllegalStateException.class,()->engine.select(uuid,"wave"));
        assertEquals("riddlestrails:comet",engine.selected(uuid));
        assertEquals("keep",Files.readString(file.resolve("blocker")));
    }

    @Test void invalidPreferenceFileIsNotSilentlyReplaced() throws Exception {
        Path file=directory.resolve("animation-preferences.yml");
        String corrupt="version: 1\nplayers:\n  invalid-uuid:\n    animation: comet\n";
        Files.writeString(file,corrupt);
        assertThrows(IllegalStateException.class,()->new AnimationEngine(plugin));
        assertEquals(corrupt,Files.readString(file));
    }

    @Test void customCallbacksAreSelectableAndListenerCanOverrideSelection() {
        AtomicInteger a=new AtomicInteger(),b=new AtomicInteger();
        engine.register(extension,"example:one",(frame,sink)->a.incrementAndGet());
        engine.register(extension,"example:two",(frame,sink)->b.incrementAndGet());
        engine.select(uuid,"example:one");render(0);assertEquals(1,a.get());
        doAnswer(call->{((TrailAnimationSelectEvent)call.getArgument(0)).setAnimationId("example:two");return null;}).when(manager).callEvent(any());
        render(1);assertEquals(1,b.get());assertEquals(1,a.get());
        assertEquals("example:one",engine.selected(uuid)); // Event selection is frame-local.
        doAnswer(call->{((TrailAnimationSelectEvent)call.getArgument(0)).setCancelled(true);return null;}).when(manager).callEvent(any());
        render(2);assertEquals(1,b.get());
    }

    @Test void customEmissionsArePrivateBudgetedAndSinkCannotBeRetained() {
        ParticleSink[] retained=new ParticleSink[1];
        engine.register(extension,"example:burst",(frame,sink)->{
            retained[0]=sink;assertEquals(12,sink.remaining());
            assertTrue(sink.emit(Particle.FLAME,frame.point(.5,0,0),100,null));
            assertEquals(0,sink.remaining());assertFalse(sink.emit(Particle.FLAME,frame.point(.5,0,0),1,null));
        });
        engine.select(uuid,"example:burst");render(0);
        verify(viewer).spawnParticle(eq(Particle.FLAME),anyDouble(),anyDouble(),anyDouble(),eq(12),eq(0.0),eq(0.0),eq(0.0),eq(0.0),isNull());
        assertThrows(IllegalStateException.class,()->retained[0].remaining());
    }

    @Test void reusedDustDataPreservesColorsAndExplicitDataValidation() {
        var style=engine.style("comet");
        var point=new AnimationPoint(0,64,0);
        try(var sink=new ViewerParticleSink(viewer,style,5,73)) {
            sink.emit(point,.8,.3);sink.emit(point.add(1,0,0),.8,.3);sink.emit(point.add(2,0,0),.5,.3);
            assertThrows(IllegalArgumentException.class,()->sink.emit(Particle.DUST,point,1,null));
            assertEquals(2,sink.remaining());
        }
        var data=org.mockito.ArgumentCaptor.forClass(Particle.DustOptions.class);
        verify(viewer,times(3)).spawnParticle(eq(Particle.DUST),anyDouble(),anyDouble(),anyDouble(),eq(1),eq(0.0),eq(0.0),eq(0.0),eq(0.0),data.capture());
        assertSame(data.getAllValues().get(0),data.getAllValues().get(1));
        assertEquals(style.color(.3,.8),data.getAllValues().get(0).getColor());
        assertEquals(style.color(.3,.5),data.getAllValues().get(2).getColor());
    }

    @Test void ownershipAndPluginDisableCleanupProtectRegistrations() {
        engine.register(extension,"example:custom",(frame,sink)->{});
        assertThrows(IllegalArgumentException.class,()->engine.register(extension,"comet",(f,s)->{}));
        assertThrows(IllegalArgumentException.class,()->engine.register(extension,"example:custom",(f,s)->{}));
        assertThrows(IllegalArgumentException.class,()->engine.unregister(plugin,"example:custom"));
        engine.select(uuid,"example:custom");
        engine.disabled(new PluginDisableEvent(extension));
        assertFalse(engine.animations().contains("example:custom"));assertEquals("riddlestrails:breathing",engine.selected(uuid));
    }

    @Test void failingCustomAnimationFallsBackWithoutBeingCalledEveryFrame() {
        AtomicInteger calls=new AtomicInteger();
        engine.register(extension,"example:broken",(frame,sink)->{calls.incrementAndGet();throw new IllegalStateException("test error");});
        engine.select(uuid,"example:broken");render(0);render(.3);render(.6);assertEquals(1,calls.get());
        verify(viewer,atLeastOnce()).spawnParticle(eq(Particle.DUST),anyDouble(),anyDouble(),anyDouble(),eq(1),eq(0.0),eq(0.0),eq(0.0),eq(0.0),any(Particle.DustOptions.class));
    }

    @Test void styleOverridesReachCustomCallbacksAndStateIsIsolatedAndReset() throws Exception {
        List<AnimationFrame> frames=new ArrayList<>();
        engine.register(extension,"example:state",(frame,sink)->{frames.add(frame);frame.state().put("seen",true);});
        var blue=BuiltInAnimationsTest.config().common().withPalette(List.of(Color.BLUE));
        engine.select(uuid,"example:state");engine.setStyle(uuid,blue);render(0);render(1);
        assertSame(frames.get(0).state(),frames.get(1).state());assertEquals(blue,frames.get(1).style());
        engine.forget(uuid,TrailKind.QUEST);render(2);assertNotSame(frames.get(0).state(),frames.get(2).state());
        engine.resetStyle(uuid);render(3);assertNotEquals(blue,frames.get(3).style());
    }
}
