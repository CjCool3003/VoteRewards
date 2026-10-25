package com.example.voterewards;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

final class VoteHandler implements Listener {

    private final VoteRewardsPlugin plugin;

    VoteHandler(VoteRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Entry point for every vote (real or /voteadmin test). Safe to call from any thread. */
    void handleVote(String rawUsername, String rawService, String address) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> handleVote(rawUsername, rawService, address));
            return;
        }

        String username = rawUsername == null ? "" : rawUsername.trim();
        String service = cleanService(rawService);

        if (!plugin.usernamePattern().matcher(username).matches()) {
            plugin.getLogger().warning("Ignored vote with invalid username '" + forLog(username) + "' from " + service);
            return;
        }

        Player player = Bukkit.getPlayerExact(username);
        String name = player != null ? player.getName() : username;

        plugin.getLogger().info("Received vote from " + name + " via " + service
                + (address == null || address.isEmpty() ? "" : " (" + forLog(address) + ")"));

        broadcast(name, service);
        plugin.discord().queueVote(name, service);

        if (player != null) {
            giveRewards(player, name, service);
            return;
        }

        String mode = plugin.getConfig().getString("offline-rewards", "queue").toLowerCase(Locale.ROOT);
        switch (mode) {
            case "run" -> giveRewards(null, name, service);
            case "skip" -> plugin.getLogger().info(name + " is offline - rewards skipped (offline-rewards: skip).");
            default -> {
                plugin.pending().add(name, service);
                plugin.getLogger().info(name + " is offline - rewards saved for their next join.");
            }
        }
    }

    private void broadcast(String name, String service) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("broadcast.enabled", true)) {
            return;
        }
        TagResolver resolver = placeholders(name, service);
        for (String line : config.getStringList("broadcast.messages")) {
            Bukkit.broadcast(plugin.parse(line, resolver));
        }
    }

    private void giveRewards(Player player, String name, String service) {
        FileConfiguration rewards = plugin.rewards();

        for (String command : rewards.getStringList("commands")) {
            runCommand(command, name, service);
        }

        for (Map<?, ?> entry : rewards.getMapList("service-rewards")) {
            Object serviceName = entry.get("service");
            if (serviceName == null || !serviceName.toString().equalsIgnoreCase(service)) {
                continue;
            }
            if (entry.get("commands") instanceof List<?> commands) {
                for (Object command : commands) {
                    if (command != null) {
                        runCommand(command.toString(), name, service);
                    }
                }
            }
        }

        if (player != null && player.isOnline()) {
            TagResolver resolver = placeholders(name, service);
            for (String line : rewards.getStringList("player-messages")) {
                player.sendMessage(plugin.parse(line, resolver));
            }
        }
    }

    private void runCommand(String template, String name, String service) {
        String command = template.replace("%player%", name).replace("%service%", service).trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        if (command.isEmpty()) {
            return;
        }
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Reward command failed: " + command, e);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.pending().has(player.getName())) {
            return;
        }
        // Short delay so the player has fully loaded in before receiving items
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            List<String> services = plugin.pending().take(player.getName());
            if (services.isEmpty()) {
                return;
            }
            for (String service : services) {
                giveRewards(player, player.getName(), service);
            }
            plugin.sendMessage(player, "offline-rewards-delivered",
                    Placeholder.unparsed("count", String.valueOf(services.size())));
        }, 40L);
    }

    private static TagResolver placeholders(String name, String service) {
        // "unparsed" means names can never inject formatting or click actions
        return TagResolver.resolver(
                Placeholder.unparsed("player", name),
                Placeholder.unparsed("service", service));
    }

    private static String cleanService(String raw) {
        if (raw == null) {
            return "Unknown";
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9 ._\\-]", "").trim();
        if (cleaned.length() > 64) {
            cleaned = cleaned.substring(0, 64);
        }
        return cleaned.isEmpty() ? "Unknown" : cleaned;
    }

    private static String forLog(String text) {
        String cleaned = text.replaceAll("\\p{Cntrl}", "");
        return cleaned.length() > 40 ? cleaned.substring(0, 40) + "..." : cleaned;
    }
}
