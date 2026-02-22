package com.ayhan.invitetokens;

import com.ayhan.invitetokens.InviteStorage.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public class InviteOpCommand implements CommandExecutor {

    private final InviteTokensPlugin plugin;
    private final InviteStorage storage;

    public InviteOpCommand(InviteTokensPlugin plugin, InviteStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {


        if (sender instanceof Player p) {
            if (!p.isOp()) {
                p.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /inviteop <playerName>");
            return true;
        }

        String targetName = args[0].trim();
        if (targetName.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Usage: /inviteop <playerName>");
            return true;
        }


        Player onlineExact = Bukkit.getPlayerExact(targetName);
        UUID targetUuid = null;
        String canonicalName = targetName;

        if (onlineExact != null) {
            targetUuid = onlineExact.getUniqueId();
            canonicalName = onlineExact.getName();
        } else {

            OfflinePlayer cached = getOfflinePlayerIfCachedCompat(targetName);
            if (cached != null) {
                targetUuid = cached.getUniqueId();
                if (cached.getName() != null) canonicalName = cached.getName();
            } else {

                OfflinePlayer off = getOfflinePlayerDeprecatedCompat(targetName);


                if (off == null || !off.hasPlayedBefore()) {
                    sender.sendMessage(ChatColor.RED +
                            "Player not found in server history. Ask them to join once first, then run /inviteop again.");
                    return true;
                }

                targetUuid = off.getUniqueId();
                if (off.getName() != null) canonicalName = off.getName();
            }
        }

        // Safety
        if (targetUuid == null) {
            sender.sendMessage(ChatColor.RED + "Could not resolve that player.");
            return true;
        }

        // Update storage
        PlayerData pd = storage.getOrCreatePlayer(targetUuid, canonicalName);
        if (pd.joinedBefore) {
            sender.sendMessage(ChatColor.RED + "The player is already verified / already used a token.");
            return true;
        }

        pd.joinedBefore = true;
        storage.save();

        sender.sendMessage(ChatColor.GREEN + "Player " + canonicalName +
                " has been verified without a token.");


        Player online = Bukkit.getPlayer(targetUuid);
        if (online != null && online.isOnline()) {
            online.sendMessage(ChatColor.YELLOW +
                    "You have been verified by an operator. You can now play freely.");
        }

        plugin.getLogger().info(sender.getName() + " used /inviteop on " + canonicalName +
                " (" + targetUuid + ")");

        return true;
    }


    private OfflinePlayer getOfflinePlayerIfCachedCompat(String name) {
        try {
            Method m = Bukkit.class.getMethod("getOfflinePlayerIfCached", String.class);
            Object result = m.invoke(null, name);
            return (OfflinePlayer) result;
        } catch (NoSuchMethodException ignored) {
            // Not Paper
            return null;
        } catch (Throwable t) {
            // Any unexpected reflection failure: just treat as not available
            return null;
        }
    }


    @SuppressWarnings("deprecation")
    private OfflinePlayer getOfflinePlayerDeprecatedCompat(String name) {
        return Bukkit.getOfflinePlayer(name);
    }
}