package com.example.voterewards;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AdminCommand implements TabExecutor {

    private final VoteRewardsPlugin plugin;

    AdminCommand(VoteRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("voterewards.admin")) {
            plugin.sendMessage(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.sendMessage(sender, "admin-usage");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadAll();
                plugin.sendMessage(sender, "reloaded");
            }
            case "test" -> {
                if (args.length < 2) {
                    plugin.sendMessage(sender, "admin-usage");
                    return true;
                }
                String player = args[1];
                String service = args.length >= 3 ? args[2] : "TestVote";
                plugin.voteHandler().handleVote(player, service, "127.0.0.1");
                plugin.sendMessage(sender, "test-sent",
                        Placeholder.unparsed("player", player),
                        Placeholder.unparsed("service", service));
            }
            default -> plugin.sendMessage(sender, "admin-usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("voterewards.admin")) {
            return out;
        }
        if (args.length == 1) {
            for (String option : List.of("reload", "test")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(option);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
