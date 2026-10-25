package com.example.voterewards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class VoteRewardsPlugin extends JavaPlugin {

    private static final String DEFAULT_USERNAME_REGEX = "^[.]?[A-Za-z0-9_]{1,16}$";

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private FileConfiguration rewardsConfig;
    private Pattern usernamePattern;
    private PendingRewards pendingRewards;
    private DiscordWebhook discordWebhook;
    private VoteHandler voteHandler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "rewards.yml").exists()) {
            saveResource("rewards.yml", false);
        }
        loadSettings();

        pendingRewards = new PendingRewards(this);
        discordWebhook = new DiscordWebhook(this);
        voteHandler = new VoteHandler(this);

        getServer().getPluginManager().registerEvents(voteHandler, this);

        if (VotifierHook.register(this, voteHandler)) {
            getLogger().info("Hooked into Votifier - listening for votes.");
        } else {
            getLogger().warning("VotifierPlus (or NuVotifier) was not found! Votes will NOT be received.");
            getLogger().warning("Install VotifierPlus, then restart the server.");
        }

        PluginCommand vote = getCommand("vote");
        if (vote != null) {
            vote.setExecutor(new VoteCommand(this));
        }
        PluginCommand admin = getCommand("voteadmin");
        if (admin != null) {
            AdminCommand adminCommand = new AdminCommand(this);
            admin.setExecutor(adminCommand);
            admin.setTabCompleter(adminCommand);
        }

        discordWebhook.start();
    }

    @Override
    public void onDisable() {
        if (discordWebhook != null) {
            discordWebhook.shutdown();
        }
        if (pendingRewards != null) {
            pendingRewards.save();
        }
    }

    void reloadAll() {
        reloadConfig();
        loadSettings();
        discordWebhook.start();
    }

    private void loadSettings() {
        rewardsConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "rewards.yml"));
        String regex = getConfig().getString("username-regex", DEFAULT_USERNAME_REGEX);
        try {
            usernamePattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            getLogger().warning("Invalid username-regex in config.yml, using the default.");
            usernamePattern = Pattern.compile(DEFAULT_USERNAME_REGEX);
        }
    }

    Component parse(String input, TagResolver... resolvers) {
        return miniMessage.deserialize(input == null ? "" : input, resolvers);
    }

    /** Sends messages.<path> from config.yml with the prefix. Does nothing if the message is blank. */
    void sendMessage(CommandSender sender, String path, TagResolver... resolvers) {
        String message = getConfig().getString("messages." + path, "");
        if (message == null || message.isEmpty()) {
            return;
        }
        String prefix = getConfig().getString("messages.prefix", "");
        sender.sendMessage(parse(prefix + message, resolvers));
    }

    FileConfiguration rewards() {
        return rewardsConfig;
    }

    Pattern usernamePattern() {
        return usernamePattern;
    }

    PendingRewards pending() {
        return pendingRewards;
    }

    DiscordWebhook discord() {
        return discordWebhook;
    }

    VoteHandler voteHandler() {
        return voteHandler;
    }
}
