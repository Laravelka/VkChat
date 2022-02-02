package ru.laravelka.VkChat;

import api.longpoll.bots.exceptions.VkApiException;
import api.longpoll.bots.methods.VkBotsMethods;
import api.longpoll.bots.methods.impl.messages.Send;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class VkChat extends JavaPlugin {
    final Map<String, FileConfiguration> customConfigs = new HashMap<>();
    final Map<String, File> customConfigFiles = new HashMap<>();
    final Map<UUID, Long> players = new HashMap<>();

    protected FileConfiguration config;
    final Logger log = getLogger();
    protected VkBotsMethods vkApi;
    protected String prefix;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.createCustomConfigs("messages");
        // this.createCustomConfigs("forbidden_words");

        config = this.getConfig();
        prefix = config.getString("prefix", "[VKChat]");
        vkApi  = new VkBotsMethods(config.getString("access_token", "token"));
    }

    @Override
    public void onDisable() {
        log.info("VKChat disabled");
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, String label, @NotNull String[] args) {
        if (label.equalsIgnoreCase("vkchat") || label.equalsIgnoreCase("vkc")) {
            final long timestamp = System.currentTimeMillis();

            if (args.length < 1) {
                sender.sendMessage(
                        this.getCustomConfigs("messages")
                                .getString(getLocale() + ".usage", "Usage /vkc <§3message§r>")
                );
                return false;
            }
            String message = args[0];

            if (sender.hasPermission("vkchat.reload") && message.equalsIgnoreCase("reload")) {
                this.reloadConfig();
                this.createCustomConfigs("messages");

                config = YamlConfiguration.loadConfiguration(
                        new File(this.getDataFolder(), "config.yml")
                );
                log.info("config: " + config.getString("access_token"));

                sender.sendMessage(
                        this.getCustomConfigs("messages")
                                .getString(getLocale() + ".reloaded", "§2Config has been reloaded!")
                );
                return false;
            }

            if (sender.getName().equals("CONSOLE")) {
                final List<String> forbiddenWords = Objects.requireNonNull(
                        config.getStringList("forbidden_words.words_list")
                );
                log.info("words: " + forbiddenWords.toString());

                for (String forbiddenWord : forbiddenWords) {
                    int isForbidden = message.indexOf(forbiddenWord);

                    if (isForbidden != -1) {
                        log.info("(true) word: " + forbiddenWord + " | " + message);

                        sender.sendMessage(
                                this.getCustomConfigs("messages")
                                        .getString(
                                                getLocale() + ".forbidden_words",
                                                "§cYour message contains forbidden characters/words."
                                        )
                        );
                        return false;
                    }
                    log.info("(false) word: " + forbiddenWord + " | " + message);
                }

                if (this.sendToChat(prefix + message)) {
                    sender.sendMessage(
                            prefix +
                            this.getCustomConfigs("messages")
                                    .getString(getLocale() + ".sent", "§2The message has been sent!")
                    );
                } else {
                    sender.sendMessage(
                            prefix +
                            this.getCustomConfigs("messages")
                                    .getString(getLocale() + ".not_sent", "§cThe message was not sent!")
                    );
                }
            } else {
                final Player player = (Player) sender;
                final List<String> forbiddenWords = Objects.requireNonNull(
                        config.getStringList("forbidden_words")
                );

                if (!player.hasPermission("vkchat.bypass.forbiddenWords")) {
                    for (String forbiddenWord : forbiddenWords) {
                        int isForbidden = message.indexOf(forbiddenWord);

                        if (isForbidden != -1) {
                            sender.sendMessage(
                                    prefix +
                                    this.getCustomConfigs("messages")
                                            .getString(
                                                    getLocale() + ".forbidden_words",
                                                    "§cYour message contains forbidden characters/words."
                                            )
                            );
                            return false;
                        }
                    }
                }
                String name = player.getName();
                UUID uuid   = player.getUniqueId();

                if (!player.hasPermission("vkchat.bypass.delay") && this.players.containsKey(uuid)) {
                    Long getLastUsed = this.players.get(uuid);
                    final int messageDelay = config.getInt("message_delay");

                    if ((getLastUsed + messageDelay) > timestamp) {
                        int waitSeconds = (int) Math.ceil(
                                ((getLastUsed + messageDelay) - timestamp) / 1000
                        );

                        player.sendMessage(
                                prefix +
                                this.getCustomConfigs("messages")
                                        .getString(
                                                getLocale() + ".message_delay",
                                                "Not so fast! Wait {seconds} sec."
                                        )
                                        .replace("{seconds}", Integer.toString(waitSeconds))
                        );
                        return false;
                    }
                }

                if (this.sendToChat(name + ": " + message)) {
                    this.players.put(uuid, timestamp);

                    sender.sendMessage(
                            prefix +
                            this.getCustomConfigs("messages")
                                    .getString(getLocale() + ".sent", "The message has been sent!")
                    );

                    sender.sendMessage(
                            prefix +
                            this.getCustomConfigs("messages")
                                    .getString(
                                            getLocale() + ".user_sent_message",
                                            "The {user} sent a message to the VK - {message}"
                                    )
                                    .replace("{user}", player.getDisplayName())
                                    .replace("{message}", message)
                    );

                    log.info(
                            this.getCustomConfigs("messages")
                                    .getString(
                                            getLocale() + ".user_sent_message",
                                            "The {user} sent a message to the VK - {message}"
                                    )
                                    .replace("{user}", player.getDisplayName())
                                    .replace("{message}", message)
                    );
                } else {
                    sender.sendMessage(
                            prefix +
                            this.getCustomConfigs("messages")
                                    .getString(getLocale() + ".not_sent", "§cThe message was not sent!")
                    );
                }
            }
        }
        return super.onCommand(sender, command, label, args);
    }

    @NotNull
    public FileConfiguration getConfig() {
        return super.getConfig();
    }

    @NotNull
    public String getLocale() {
        return config.getString("locale", "ru");
    }

    @NotNull
    public FileConfiguration getCustomConfigs(String name) {
        return this.customConfigs.get(name);
    }

    private boolean sendToChat(String message) {
        try {
            Send.Response response = vkApi.messages.send()
                    .setPeerId(config.getInt("peer_id"))
                    .setMessage(message)
                    .execute();

            if (!response.toString().isEmpty()) {
                log.info("Send message response: " + response);

                return true;
            }
        } catch (VkApiException e) {
            log.info("VkApiException: " + e.getLocalizedMessage());
        }
        return false;
    }

    private void createCustomConfigs(String name) {
        this.customConfigFiles.put(name, new File(this.getDataFolder(), name + ".yml"));

        if (!this.customConfigFiles.get(name).exists()) {
            if (this.customConfigFiles.get(name).getParentFile().mkdirs()) {
                log.info("true mkdirs");
            } else {
                log.info("false mkdirs");
            }
            this.saveResource(name + ".yml", false);
        }
        this.customConfigs.put(name, new YamlConfiguration());

        try {
            this.customConfigs.get(name).load(this.customConfigFiles.get(name));
        } catch (InvalidConfigurationException | IOException e) {
            e.printStackTrace();
        }
    }
}
