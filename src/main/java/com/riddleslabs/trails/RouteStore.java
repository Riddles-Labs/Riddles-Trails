package com.riddleslabs.trails;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Immutable read snapshots; writes are serialized and committed to disk before becoming visible. */
public final class RouteStore {
    private final Path file;
    private volatile Map<String, Route> routes = Map.of();
    public RouteStore(Path file) { this.file = file; }
    public Map<String, Route> all() { return routes; }
    public Route get(String id) { return routes.get(id); }

    public synchronized void load() throws IOException, InvalidConfigurationException {
        if (!Files.exists(file)) { routes = Map.of(); return; }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        Map<String, Route> loaded = new TreeMap<>();
        ConfigurationSection root = yaml.getConfigurationSection("destinations");
        if (root != null) for (String id : root.getKeys(false)) {
            List<Route.Point> points = new ArrayList<>();
            for (Map<?, ?> row : root.getMapList(id + ".points")) {
                points.add(new Route.Point(number(row.get("x")), number(row.get("y")), number(row.get("z"))));
            }
            List<Integer> teleports = new ArrayList<>();
            for (Object value : root.getList(id + ".teleports", List.of())) {
                double index = number(value);
                if (index != (int) index) throw new IllegalArgumentException("Teleport indices must be integers");
                teleports.add((int) index);
            }
            loaded.put(id, new Route(id, root.getString(id + ".world"), points, teleports));
        }
        routes = Map.copyOf(loaded);
    }

    private static double number(Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Point coordinates must be numbers");
        return number.doubleValue();
    }

    public synchronized void save(Route route) throws IOException {
        Map<String, Route> updated = new TreeMap<>(routes);
        updated.put(route.id(), route);
        persist(updated);
    }
    public synchronized boolean delete(String id) throws IOException {
        if (!routes.containsKey(id)) return false;
        Map<String, Route> updated = new TreeMap<>(routes);
        updated.remove(id);
        persist(updated);
        return true;
    }
    private void persist(Map<String, Route> updated) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Route route : updated.values()) {
            String base = "destinations." + route.id();
            yaml.set(base + ".world", route.world());
            yaml.set(base + ".points", route.points().stream().map(p -> Map.of("x", p.x(), "y", p.y(), "z", p.z())).toList());
            if (!route.teleports().isEmpty()) yaml.set(base + ".teleports", route.teleports());
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, yaml.saveToString());
        try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        routes = Map.copyOf(updated);
    }
}
