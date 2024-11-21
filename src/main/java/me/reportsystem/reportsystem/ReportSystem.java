package me.reportsystem.reportsystem;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class ReportSystem extends JavaPlugin {
    private String webhookUrl;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private static final int COOLDOWN_MINUTES = 5;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        Objects.requireNonNull(getCommand("report")).setExecutor(this);
        getLogger().info("§8[§6Report System§8] §7Plugin başlatılıyor...");
        getLogger().info("§8[§6Report System§8] §7Config dosyası yüklendi!");
        getLogger().info("§8[§6Report System§8] §7Komutlar yüklendi!");
        getLogger().info("§8[§6Report System§8] §aPlugin başarıyla aktif edildi!");
    }

    @Override
    public void onDisable() {
        getLogger().info("§cReport System kapatıldı!");
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        config.addDefault("webhook-url", "WEBHOOK_URL_HERE");
        config.options().copyDefaults(true);
        saveConfig();
        webhookUrl = config.getString("webhook-url");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cBu komutu sadece oyuncular kullanabilir!");
            return true;
        }

        if (command.getName().equalsIgnoreCase("report")) {
            if (args.length == 0) {
                player.sendMessage("§cKullanım: /report <mesaj>");
                return true;
            }

            if (isOnCooldown(player)) {
                long timeLeft = getRemainingCooldown(player);
                player.sendMessage("§cBu komutu tekrar kullanabilmek için " + timeLeft + " dakika beklemelisin!");
                return true;
            }

            StringBuilder message = new StringBuilder();
            for (String arg : args) {
                message.append(arg).append(" ");
            }

            sendDiscordWebhook(player, message.toString().trim());
            setCooldown(player);
            player.sendMessage("§aRaporunuz başarıyla gönderildi!");
            return true;
        }
        return false;
    }

    private void sendDiscordWebhook(Player player, String reportMessage) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        String dateTime = now.format(formatter);

        String jsonMessage = String.format("""
            {
                "embeds": [{
                    "title": "Yeni Rapor",
                    "color": 16711680,
                    "fields": [
                        {"name": "Rapor Eden", "value": "%s", "inline": true},
                        {"name": "Rapor Mesajı", "value": "%s", "inline": false},
                        {"name": "Tarih/Saat", "value": "%s", "inline": true}
                    ]
                }]
            }
            """, player.getName(), reportMessage, dateTime);

        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonMessage))
                        .build();

                client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                getLogger().warning("Discord webhook gönderilemedi: " + e.getMessage());
            }
        });
    }

    private boolean isOnCooldown(Player player) {
        if (!cooldowns.containsKey(player.getUniqueId())) {
            return false;
        }
        return System.currentTimeMillis() - cooldowns.get(player.getUniqueId()) < COOLDOWN_MINUTES * 60 * 1000;
    }

    private void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private long getRemainingCooldown(Player player) {
        long timeElapsed = System.currentTimeMillis() - cooldowns.get(player.getUniqueId());
        return ((COOLDOWN_MINUTES * 60 * 1000) - timeElapsed) / (60 * 1000) + 1;
    }
}
