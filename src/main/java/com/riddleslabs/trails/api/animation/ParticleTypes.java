package com.riddleslabs.trails.api.animation;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;

/** Resolves appearance across Minecraft versions without rewriting the configured particle name. */
public final class ParticleTypes {
    private static final Particle DUST = find("DUST", "REDSTONE");
    private static final Particle TRAIL = find("TRAIL", null);
    private static final Constructor<?> TRAIL_DATA = trailConstructor();
    private static final List<List<String>> ALIASES = List.of(
            List.of("DUST", "REDSTONE"), List.of("POOF", "EXPLOSION_NORMAL"),
            List.of("EXPLOSION", "EXPLOSION_LARGE"), List.of("EXPLOSION_EMITTER", "EXPLOSION_HUGE"),
            List.of("FIREWORK", "FIREWORKS_SPARK"), List.of("BUBBLE", "WATER_BUBBLE"),
            List.of("SPLASH", "WATER_SPLASH"), List.of("FISHING", "WATER_WAKE"),
            List.of("UNDERWATER", "SUSPENDED"), List.of("ENCHANTED_HIT", "CRIT_MAGIC"),
            List.of("SMOKE", "SMOKE_NORMAL"), List.of("LARGE_SMOKE", "SMOKE_LARGE"),
            List.of("EFFECT", "SPELL"), List.of("INSTANT_EFFECT", "SPELL_INSTANT"),
            List.of("ENTITY_EFFECT", "SPELL_MOB", "SPELL_MOB_AMBIENT"), List.of("WITCH", "SPELL_WITCH"),
            List.of("DRIPPING_WATER", "DRIP_WATER"), List.of("DRIPPING_LAVA", "DRIP_LAVA"),
            List.of("ANGRY_VILLAGER", "VILLAGER_ANGRY"), List.of("HAPPY_VILLAGER", "VILLAGER_HAPPY"),
            List.of("MYCELIUM", "TOWN_AURA"), List.of("ENCHANT", "ENCHANTMENT_TABLE"),
            List.of("ITEM_SNOWBALL", "SNOWBALL"), List.of("ITEM_SLIME", "SLIME"),
            List.of("ITEM", "ITEM_CRACK"), List.of("BLOCK", "BLOCK_CRACK", "BLOCK_DUST"),
            List.of("RAIN", "WATER_DROP"), List.of("ELDER_GUARDIAN", "MOB_APPEARANCE"),
            List.of("TOTEM_OF_UNDYING", "TOTEM"));
    // Valid simple particles from the supported version range. Unknown names still fail validation.
    private static final Set<String> OPTIONAL = Set.of("TRAIL", "SUSPENDED_DEPTH", "SNOW_SHOVEL",
            "CHERRY_LEAVES", "PALE_OAK_LEAVES", "EGG_CRACK", "DUST_PLUME", "WHITE_SMOKE",
            "GUST", "SMALL_GUST", "GUST_EMITTER_LARGE", "GUST_EMITTER_SMALL",
            "TRIAL_SPAWNER_DETECTION", "TRIAL_SPAWNER_DETECTION_OMINOUS", "VAULT_CONNECTION",
            "INFESTED", "ITEM_COBWEB", "FIREFLY", "OMINOUS_SPAWNING", "RAID_OMEN", "TRIAL_OMEN",
            "COPPER_FIRE_FLAME", "PAUSE_MOB_GROWTH", "RESET_MOB_GROWTH", "NOXIOUS_GAS",
            "NOXIOUS_GAS_CLOUD", "SULFUR_CUBE_GOO", "SULFUR_BUBBLES");
    private static final Set<String> FALLBACKS = ConcurrentHashMap.newKeySet();
    private ParticleTypes() {}

    private static Particle find(String name, String fallback) {
        try { return Particle.valueOf(name); }
        catch (IllegalArgumentException absent) { return fallback == null ? null : Particle.valueOf(fallback); }
    }
    private static Constructor<?> trailConstructor() {
        if (TRAIL == null) return null;
        try { return TRAIL.getDataType().getConstructor(Location.class, Color.class, int.class); }
        catch (NoSuchMethodException absent) { return null; }
    }
    public static Particle dust() { return DUST; }
    public static boolean isTrail(Particle particle) { return TRAIL != null && particle == TRAIL && TRAIL_DATA != null; }
    public static boolean supportsStyle(Particle particle) {
        return particle.getDataType() == Void.class || particle == DUST
                || particle == Particle.DUST_COLOR_TRANSITION || isTrail(particle);
    }

    public static Particle parse(String token) {
        String name = Objects.requireNonNull(token, "particle").toUpperCase(Locale.ROOT);
        Particle resolved = find(name, null);
        if (resolved == null) for (List<String> aliases : ALIASES) if (aliases.contains(name)) {
            for (String alias : aliases) { resolved = find(alias, null); if (resolved != null) break; }
            break;
        }
        if (resolved != null && supportsStyle(resolved)) return resolved;
        // Some formerly data-free particles gained payload types. Keep old profiles usable.
        boolean changedPayload = resolved != null && Set.of("FLASH", "DRAGON_BREATH", "ENTITY_EFFECT",
                "SPELL_MOB", "SPELL_MOB_AMBIENT", "EFFECT", "SPELL", "INSTANT_EFFECT", "SPELL_INSTANT").contains(name);
        if ((resolved == null && OPTIONAL.contains(name)) || "TRAIL".equals(name) || changedPayload) {
            FALLBACKS.add(name);
            return DUST;
        }
        if (resolved == null) throw new IllegalArgumentException("Unknown particle: " + token);
        throw new IllegalArgumentException("Particle needs explicit custom emitter data: " + token);
    }

    /** Names whose configured appearance needed dust on this server; callers can report each once. */
    public static Set<String> fallbacks() { return Set.copyOf(FALLBACKS); }

    public static Object trail(Location target, Color color, int duration) {
        if (TRAIL_DATA == null) throw new IllegalStateException("Native trail particles are unavailable");
        try { return TRAIL_DATA.newInstance(target, color, duration); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot create native trail particle data", failure); }
    }
}
