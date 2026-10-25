package com.example.voterewards;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/** Stores rewards for players who voted while offline (pending.yml). */
final class PendingRewards {

    private final VoteRewardsPlugin plugin;
    private final File file;
    private final YamlConfiguration data = new YamlConfiguration();

    PendingRewards(VoteRewardsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending.yml");
        // Bedrock names can start with "." so don't use "." as the YAML path separator
        data.options().pathSeparator('/');
        if (file.exists()) {
            try {
                data.load(file);
            } catch (IOException | InvalidConfigurationException e) {
                plugin.getLogger().log(Level.WARNING, "Could not load pending.yml", e);
            }
        }
    }

    void add(String name, String service) {
        String key = key(name);
        List<String> services = new ArrayList<>(data.getStringList(key));
        services.add(service);
        data.set(key, services);
        save();
    }

    boolean has(String name) {
        return !data.getStringList(key(name)).isEmpty();
    }

    List<String> take(String name) {
        String key = key(name);
        List<String> services = new ArrayList<>(data.getStringList(key));
        if (services.isEmpty()) {
            return List.of();
        }
        data.set(key, null);
        save();
        return services;
    }

    void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save pending.yml", e);
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
