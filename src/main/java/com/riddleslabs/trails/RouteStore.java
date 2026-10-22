package com.riddleslabs.trails;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import java.io.IOException;
import java.util.*;

/** Immutable read snapshots; writes are serialized and committed to disk before becoming visible. */
public final class RouteStore {
    private final com.riddleslabs.api.files.FileCoordinator persistence;
    private static final String FILE = "destinations.yml";
    private volatile Map<String, Route> routes = Map.of();
    public RouteStore(com.riddleslabs.api.files.FileCoordinator persistence) { this.persistence = persistence; }
    public Map<String, Route> all() { return routes; }
    public Route get(String id) { return routes.get(id); }

    public synchronized void load() throws IOException, InvalidConfigurationException {
        try {
            String content = persistence.read(FILE);
            if (content == null) { persistence.acceptRead(FILE); routes = Map.of(); return; }
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(content);
            Map<String, Route> loaded = new TreeMap<>();
            ConfigurationSection root = yaml.getConfigurationSection("destinations");
            if ((yaml.contains("destinations") && root == null) || (root == null && !yaml.getKeys(false).isEmpty()))
                throw new IllegalArgumentException("Invalid destinations document; existing routes retained");
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
            persistence.acceptRead(FILE);
        } catch (InvalidConfigurationException | RuntimeException failure) {
            persistence.health(FILE, false); throw failure;
        }
    }

    private static double number(Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Point coordinates must be numbers");
        return number.doubleValue();
    }

    public synchronized void save(Route route) throws IOException {
        Map<String, Route> updated = new TreeMap<>(routes);
        if (updated.containsKey(route.id())) throw new IllegalArgumentException("Destination already exists");
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
        persistence.write(FILE, yaml.saveToString());
        routes = Map.copyOf(updated);
    }
}
