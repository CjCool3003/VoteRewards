package com.example.voterewards;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Sends votes to a Discord webhook, either instantly or as a "recent votes" summary. */
final class DiscordWebhook {

    private static final int MAX_DESCRIPTION = 3900; // Discord limit is 4096
    private static final int DEFAULT_COLOR = 0x5865F2;

    private final VoteRewardsPlugin plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final List<String[]> queued = new ArrayList<>(); // main thread only
    private BukkitTask task;

    DiscordWebhook(VoteRewardsPlugin plugin) {
        this.plugin = plugin;
    }

    /** (Re)starts the batch timer using the current config. */
    void start() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        flush(false); // send anything left from before a reload
        if (!enabled()) {
            return;
        }
        int interval = config().getInt("discord.batch-interval-seconds", 0);
        if (interval > 0) {
            long ticks = interval * 20L;
            task = Bukkit.getScheduler().runTaskTimer(plugin, () -> flush(false), ticks, ticks);
        }
    }

    void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        flush(true);
    }

    void queueVote(String player, String service) {
        if (!enabled()) {
            return;
        }
        if (config().getInt("discord.batch-interval-seconds", 0) > 0) {
            queued.add(new String[]{player, service});
        } else {
            send(buildSingle(player, service), false);
        }
    }

    private void flush(boolean blocking) {
        if (queued.isEmpty()) {
            return;
        }
        List<String[]> votes = new ArrayList<>(queued);
        queued.clear();
        if (enabled()) {
            send(buildBatch(votes), blocking);
        }
    }

    private String buildSingle(String player, String service) {
        FileConfiguration c = config();
        String p = "discord.single-vote.";
        String title = fill(c.getString(p + "title", ""), player, service, true);
        String description = fill(c.getString(p + "description", ""), player, service, true);
        String footer = fill(c.getString(p + "footer", ""), player, service, false);
        String thumbnail = c.getString(p + "thumbnail", "")
                .replace("%player%", URLEncoder.encode(player, StandardCharsets.UTF_8))
                .replace("%service%", URLEncoder.encode(service, StandardCharsets.UTF_8));
        int color = parseColor(c.getString(p + "color"));
        return payload(embed(title, description, color, thumbnail, footer));
    }

    private String buildBatch(List<String[]> votes) {
        FileConfiguration c = config();
        String p = "discord.batch.";
        String lineFormat = c.getString(p + "line", "• **%player%** voted on %service%");

        StringBuilder description = new StringBuilder();
        int shown = 0;
        for (String[] vote : votes) {
            String line = fill(lineFormat, vote[0], vote[1], true);
            if (description.length() + line.length() + 1 > MAX_DESCRIPTION) {
                break;
            }
            if (description.length() > 0) {
                description.append('\n');
            }
            description.append(line);
            shown++;
        }
        if (shown < votes.size()) {
            description.append("\n...and ").append(votes.size() - shown).append(" more");
        }

        String title = c.getString(p + "title", "Recent votes");
        String footer = c.getString(p + "footer", "").replace("%count%", String.valueOf(votes.size()));
        int color = parseColor(c.getString(p + "color"));
        return payload(embed(title, description.toString(), color, "", footer));
    }

    private String embed(String title, String description, int color, String thumbnail, String footer) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"color\":").append(color);
        if (!title.isEmpty()) {
            sb.append(",\"title\":").append(json(truncate(title, 256)));
        }
        if (!description.isEmpty()) {
            sb.append(",\"description\":").append(json(description));
        }
        if (thumbnail.startsWith("http")) {
            sb.append(",\"thumbnail\":{\"url\":").append(json(thumbnail)).append('}');
        }
        if (!footer.isEmpty()) {
            sb.append(",\"footer\":{\"text\":").append(json(truncate(footer, 2048))).append('}');
        }
        if (config().getBoolean("discord.timestamp", true)) {
            sb.append(",\"timestamp\":").append(json(Instant.now().toString()));
        }
        return sb.append('}').toString();
    }

    private String payload(String embed) {
        FileConfiguration c = config();
        StringBuilder sb = new StringBuilder("{");
        String username = c.getString("discord.username", "");
        String avatar = c.getString("discord.avatar-url", "");
        if (username != null && !username.isBlank()) {
            sb.append("\"username\":").append(json(truncate(username, 80))).append(',');
        }
        if (avatar != null && avatar.startsWith("http")) {
            sb.append("\"avatar_url\":").append(json(avatar)).append(',');
        }
        // Never let a vote ping @everyone or roles
        sb.append("\"allowed_mentions\":{\"parse\":[]},");
        sb.append("\"embeds\":[").append(embed).append("]}");
        return sb.toString();
    }

    private void send(String body, boolean blocking) {
        final HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "VoteRewards/" + plugin.getPluginMeta().getVersion())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Discord webhook-url is not a valid URL.");
            return;
        }

        if (blocking) {
            try {
                handle(http.send(request, HttpResponse.BodyHandlers.ofString()), null);
            } catch (Exception e) {
                handle(null, e);
            }
        } else {
            http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete(this::handle);
        }
    }

    private void handle(HttpResponse<String> response, Throwable error) {
        if (error != null) {
            plugin.getLogger().warning("Failed to send Discord webhook: " + error.getMessage());
        } else if (response.statusCode() >= 300) {
            plugin.getLogger().warning("Discord webhook returned HTTP " + response.statusCode() + ": "
                    + truncate(response.body() == null ? "" : response.body(), 200));
        }
    }

    private boolean enabled() {
        return config().getBoolean("discord.enabled", false) && !url().isBlank();
    }

    private String url() {
        String url = config().getString("discord.webhook-url", "");
        return url == null ? "" : url.trim();
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    private static String fill(String text, String player, String service, boolean markdown) {
        if (text == null) {
            return "";
        }
        return text.replace("%player%", markdown ? escapeMarkdown(player) : player)
                .replace("%service%", markdown ? escapeMarkdown(service) : service);
    }

    /** Stops underscores in names (e.g. Cool_Guy_99) turning into italics on Discord. */
    private static String escapeMarkdown(String text) {
        return text.replaceAll("([\\\\*_~`|>])", "\\\\$1");
    }

    private static int parseColor(String hex) {
        if (hex == null) {
            return DEFAULT_COLOR;
        }
        try {
            return Integer.parseInt(hex.replace("#", "").trim(), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return DEFAULT_COLOR;
        }
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    private static String json(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
