package com.riddleslabs.trails.quest;

import com.riddleslabs.trails.api.QuestTrailAPI;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class QuestTrailCommandTest {
    @Test void consoleCommandsAssignAndClearOnlyRequestedPlayer() {
        QuestTrailAPI api = mock(QuestTrailAPI.class);
        CommandSender console = mock(CommandSender.class);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(console.hasPermission("riddlestrails.quest.admin")).thenReturn(true);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Alex");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayerExact("Alex")).thenReturn(player);
            QuestTrailCommand command = new QuestTrailCommand(api);
            command.onCommand(console, null, "questtrail", new String[]{"show", "Alex", "npc", "11"});
            verify(api).showToNpc(id, 11);
            command.onCommand(console, null, "questtrail", new String[]{"clear", "Alex"});
            verify(api).clear(id);
        }
    }

    @Test void deniedOrMalformedCommandsNeverChangeAssignments() {
        QuestTrailAPI api = mock(QuestTrailAPI.class);
        CommandSender sender = mock(CommandSender.class);
        QuestTrailCommand command = new QuestTrailCommand(api);
        command.onCommand(sender, null, "questtrail", new String[]{"clear", "Alex"});
        verifyNoInteractions(api);
        when(sender.hasPermission("riddlestrails.quest.admin")).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            Player player = mock(Player.class);
            bukkit.when(() -> Bukkit.getPlayerExact("Alex")).thenReturn(player);
            command.onCommand(sender, null, "questtrail", new String[]{"show", "Alex", "npc", "bad-id"});
            command.onCommand(sender, null, "questtrail", new String[]{"show", "offline", "npc", "11"});
            verifyNoInteractions(api);
        }
    }
}
