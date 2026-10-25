package com.example.voterewards;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

final class VoteCommand implements CommandExecutor {

    private static final String DEFAULT_FORMAT =
            "<gray>»</gray> <click:open_url:'%url%'><aqua><underlined>%name%</underlined></aqua></click>";

    private final VoteRewardsPlugin plugin;

    VoteCommand(VoteRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("voterewards.vote")) {
            plugin.sendMessage(sender, "no-permission");
            return true;
        }

        FileConfiguration config = plugin.getConfig();
        for (String line : config.getStringList("vote-command.header")) {
            sender.sendMessage(plugin.parse(line));
        }

        String format = config.getString("vote-command.site-format", DEFAULT_FORMAT);
        for (Map<?, ?> site : config.getMapList("vote-command.sites")) {
            Object url = site.get("url");
            if (url == null) {
                continue;
            }
            Object nameObj = site.get("name");
            String name = nameObj == null ? "Vote" : nameObj.toString();
            String safeUrl = url.toString().trim().replace("'", "%27");
            sender.sendMessage(plugin.parse(format.replace("%url%", safeUrl).replace("%name%", name)));
        }

        for (String line : config.getStringList("vote-command.footer")) {
            sender.sendMessage(plugin.parse(line));
        }
        return true;
    }
}
