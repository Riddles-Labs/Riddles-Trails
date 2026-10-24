package com.riddleslabs.trails.quest;

import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;

/** Read-only bridge for the BeautyQuests 1.0.x and 2.x player-account API shapes. */
final class BeautyQuestsProgress {
    private BeautyQuestsProgress() {}

    static Integer destination(Player player, List<Map<?, ?>> rules) throws ReflectiveOperationException {
        if (!com.riddleslabs.paper.api.scheduler.PlatformThreads.owns(player))
            throw new IllegalStateException("Quest progress requires the player's entity scheduler");
        Object api = OptionalPluginAccess.call(
                OptionalPluginAccess.type("BeautyQuests", "fr.skytasul.quests.api.QuestsAPI"), "getAPI");
        Object plugin = OptionalPluginAccess.call(api, "getPlugin");
        Object players = OptionalPluginAccess.call(plugin, "getPlayersManager");
        Object account;
        try { account = OptionalPluginAccess.call(players, "getQuester", player); }
        catch (NoSuchMethodException oldApi) { account = OptionalPluginAccess.call(players, "getAccount", player); }
        if (account == null) throw new IllegalStateException("BeautyQuests player data is not loaded yet");
        Object quests = OptionalPluginAccess.call(api, "getQuestsManager");
        for (Map<?, ?> rule : rules) {
            int after = integer(rule, "after-quest"), until = integer(rule, "until-quest"), npc = integer(rule, "npc");
            Object previous = OptionalPluginAccess.call(quests, "getQuest", after);
            Object next = OptionalPluginAccess.call(quests, "getQuest", until);
            if (previous == null || next == null) throw new IllegalStateException("Unknown quest in restore rule: " + after + " / " + until);
            if (matches((boolean) OptionalPluginAccess.call(previous, "hasFinished", account),
                    (boolean) OptionalPluginAccess.call(next, "hasStarted", account),
                    (boolean) OptionalPluginAccess.call(next, "hasFinished", account))) return npc;
        }
        return null;
    }

    static boolean matches(boolean previousFinished, boolean nextStarted, boolean nextFinished) {
        return previousFinished && !nextStarted && !nextFinished;
    }

    static int integer(Map<?, ?> rule, String key) {
        Object value = rule.get(key);
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue() || number.intValue() < 0)
            throw new IllegalArgumentException("Restore rule requires a nonnegative integer: " + key);
        return number.intValue();
    }
}
