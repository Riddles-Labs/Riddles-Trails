package com.riddleslabs.trails.api;

import java.util.UUID;
import org.bukkit.Location;

/**
 * Obtain through Bukkit's ServicesManager; do not construct an implementation.
 * Quest guidance currently requires Paper (the optional integrations are not enabled on Folia).
 * Mutations called off the server thread are queued. Locations are copied on submission.
 * One quest assignment per player; ordinary /riddlestrails navigation is independent.
 */
public interface QuestTrailAPI {
    /** Replace guidance with a Citizens NPC in the default registry. NPC IDs must be nonnegative. */
    void showToNpc(UUID playerId, int npcId);

    /** Replace guidance with a fixed destination. Connects to a matching configured location route. */
    void showToLocation(UUID playerId, Location destination);

    /** Remove guidance, including paused assignments. Safe for offline players. */
    void clear(UUID playerId);

    /** Whether guidance is assigned; also true while waiting for an NPC, world or safe route. */
    boolean hasTrail(UUID playerId);
}
