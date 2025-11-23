package ua.fichamine.fichacommandwhitelist;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.PacketType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import ua.fichamine.fichacommandwhitelist.utils.ColorUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FichaCommandWhitelist extends JavaPlugin {

    private static FichaCommandWhitelist instance;
    private Map<String, String> groupMessages = new HashMap<>();
    private Map<String, List<String>> commandGroups = new HashMap<>();
    private Map<String, List<String>> subcommandGroups = new HashMap<>();
    private Plugin cwlPlugin;
    private ProtocolManager protocolManager;
    private PacketAdapter packetAdapter;

    @Override
    public void onEnable() {
        instance = this;

        cwlPlugin = Bukkit.getPluginManager().getPlugin("CommandWhitelist");
        if (cwlPlugin == null) {
            getLogger().severe("CommandWhitelist не найден");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        protocolManager = ProtocolLibrary.getProtocolManager();

        saveDefaultConfig();
        loadConfig();
        loadCommandWhitelistConfig(cwlPlugin);

        packetAdapter = new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Client.CHAT) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (event.getPlayer() == null) return;

                Player player = event.getPlayer();
                String message = event.getPacket().getStrings().read(0);

                if (!message.startsWith("/")) return;

                String fullCommand = message.substring(1).toLowerCase();
                String[] parts = fullCommand.split(" ");
                String command = parts[0];

                String requiredGroup = findRequiredGroupForCommand(command, player);
                if (requiredGroup != null) {
                    String denyMessage = getGroupRequiredMessage(requiredGroup);
                    if (denyMessage != null) {
                        Bukkit.getScheduler().runTask(FichaCommandWhitelist.this, () -> player.sendMessage(denyMessage));
                        event.setCancelled(true);
                        return;
                    }
                }

                if (parts.length > 1) {
                    String subcommand = parts[1];
                    String requiredSubcommandGroup = findRequiredGroupForSubcommand(subcommand, player);
                    if (requiredSubcommandGroup != null) {
                        String denyMessage = getGroupRequiredMessage(requiredSubcommandGroup);
                        if (denyMessage != null) {
                            Bukkit.getScheduler().runTask(FichaCommandWhitelist.this, () -> player.sendMessage(denyMessage));
                            event.setCancelled(true);
                        }
                    }
                }
            }
        };

        protocolManager.addPacketListener(packetAdapter);
    }

    @Override
    public void onDisable() {
        if (protocolManager != null && packetAdapter != null) {
            protocolManager.removePacketListener(packetAdapter);
        }
    }

    public void loadConfig() {
        reloadConfig();
        groupMessages.clear();

        ConfigurationSection groupMessagesSection = getConfig().getConfigurationSection("group_messages");
        if (groupMessagesSection != null) {
            for (String group : groupMessagesSection.getKeys(false)) {
                groupMessages.put(group, getConfig().getString("group_messages." + group));
            }
        }
    }

    private void loadCommandWhitelistConfig(Plugin cwlPlugin) {
        commandGroups.clear();
        subcommandGroups.clear();

        ConfigurationSection groupsSection = cwlPlugin.getConfig().getConfigurationSection("groups");
        if (groupsSection != null) {
            for (String group : groupsSection.getKeys(false)) {
                List<String> commands = cwlPlugin.getConfig().getStringList("groups." + group + ".commands");
                List<String> subcommands = cwlPlugin.getConfig().getStringList("groups." + group + ".subcommands");
                commandGroups.put(group, commands);
                subcommandGroups.put(group, subcommands);
            }
        }
    }

    private String findRequiredGroupForCommand(String command, Player player) {
        for (Map.Entry<String, List<String>> entry : commandGroups.entrySet()) {
            String group = entry.getKey();
            List<String> commands = entry.getValue();

            if (commands.contains(command)) {
                if (!player.hasPermission("commandwhitelist." + group) && !player.hasPermission("commandwhitelist.*")) {
                    return group;
                }
            }
        }
        return null;
    }

    private String findRequiredGroupForSubcommand(String subcommand, Player player) {
        for (Map.Entry<String, List<String>> entry : subcommandGroups.entrySet()) {
            String group = entry.getKey();
            List<String> subcommands = entry.getValue();

            if (subcommands.contains(subcommand)) {
                if (!player.hasPermission("commandwhitelist." + group) && !player.hasPermission("commandwhitelist.*")) {
                    return group;
                }
            }
        }
        return null;
    }

    private String getGroupRequiredMessage(String group) {
        if (groupMessages.containsKey(group)) {
            return ColorUtils.translateColors(groupMessages.get(group));
        }
        return null;
    }

    public static FichaCommandWhitelist getInstance() {
        return instance;
    }
}