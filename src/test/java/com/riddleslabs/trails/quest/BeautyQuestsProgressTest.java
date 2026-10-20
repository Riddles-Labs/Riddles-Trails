package com.riddleslabs.trails.quest;

import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BeautyQuestsProgressTest {
    // Public fixtures mirror the published API shapes. No quest files are read or modified.
    public static final class Entry {
        public static Object getAPI() { return new Api(); }
    }
    public static final class Api {
        public Object getPlugin() { return new PluginApi(); }
        public Object getQuestsManager() { return new Quests(); }
    }
    public static final class PluginApi {
        public Object getPlayersManager() { return players; }
    }
    public static final class PlayersV2 {
        public Object getQuester(Player player) { return new Account(); }
    }
    public static final class PlayersV1 {
        public Object getAccount(Player player) { return new Account(); }
    }
    public static final class Account {}
    public static final class Quests {
        public Object getQuest(int id) { return id < 3 ? new Quest(id == 1) : null; }
    }
    public static final class Quest {
        private final boolean finished;
        public Quest(boolean finished) { this.finished = finished; }
        public boolean hasStarted(Account account) { return false; }
        public boolean hasFinished(Account account) { return finished; }
    }
    private static Object players;

    @Test void resolvesModernAndLegacyPlayerApiShapesAndFailsOnUnknownQuest() throws Exception {
        Player player = mock(Player.class);
        try (var access = mockStatic(OptionalPluginAccess.class, CALLS_REAL_METHODS)) {
            access.when(() -> OptionalPluginAccess.type("BeautyQuests", "fr.skytasul.quests.api.QuestsAPI"))
                    .thenReturn(Entry.class);
            List<Map<?, ?>> rules = List.of(Map.of("after-quest", 1, "until-quest", 2, "npc", 11));
            players = new PlayersV2();
            assertEquals(11, BeautyQuestsProgress.destination(player, rules));
            players = new PlayersV1();
            assertEquals(11, BeautyQuestsProgress.destination(player, rules));
            assertNull(BeautyQuestsProgress.destination(player, List.of(Map.of("after-quest", 2, "until-quest", 1, "npc", 11))));
            assertThrows(IllegalStateException.class, () -> BeautyQuestsProgress.destination(player,
                    List.of(Map.of("after-quest", 1, "until-quest", 99, "npc", 11))));
        }
    }
}
