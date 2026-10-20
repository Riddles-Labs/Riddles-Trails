package com.riddleslabs.trails.quest;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/** Called exclusively on Paper's server thread. Never spawns an NPC or loads its chunk. */
final class CitizensTarget {
    private CitizensTarget() {}

    static Location location(int id) throws ReflectiveOperationException {
        Object registry = OptionalPluginAccess.call(
                OptionalPluginAccess.type("Citizens", "net.citizensnpcs.api.CitizensAPI"), "getNPCRegistry");
        Object npc = OptionalPluginAccess.call(registry, "getById", id);
        if (npc == null || !Boolean.TRUE.equals(OptionalPluginAccess.call(npc, "isSpawned"))) return null;
        Entity entity = (Entity) OptionalPluginAccess.call(npc, "getEntity");
        return entity == null || !entity.isValid() ? null : entity.getLocation();
    }
}
