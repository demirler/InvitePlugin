package com.ayhan.invitetokens;

import com.ayhan.invitetokens.InviteStorage.BlockStatus;
import com.ayhan.invitetokens.InviteStorage.ConsumeResult;
import com.ayhan.invitetokens.InviteStorage.PlayerData;
import com.ayhan.invitetokens.InviteStorage.InviteToken;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public class InviteCommand implements CommandExecutor {

    private final InviteTokensPlugin plugin;
    private final InviteStorage storage;
    private final Set<UUID> confirmSet;

    public InviteCommand(InviteTokensPlugin plugin, InviteStorage storage, Set<UUID> confirmSet) {
        this.plugin = plugin;
        this.storage = storage;
        this.confirmSet = confirmSet;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();
        boolean isOp = player.isOp();

        PlayerData pd = storage.getOrCreatePlayer(uuid, player.getName());

        // OP'lere 100 invite hakkı (yeterince yüksek bir limit)
        if (isOp && pd.remainingInvites < 100) {
            pd.remainingInvites = 100;
            storage.save();
        }

        // /invite  → kod oluşturma
        if (args.length == 0) {
            // OP: her zaman kod oluşturabilir, yeni/eskisi fark etmiyor
            if (isOp) {
                InviteToken token = storage.createTokenFor(pd);
                player.sendMessage(ChatColor.GREEN + "Code has been created: "
                        + ChatColor.YELLOW + token.code);
                plugin.getLogger().info(player.getName() + " created invite code " + token.code);
                return true;
            }

            // Yeni oyuncu davet kodu girmeden kendine kod üretemesin
            if (!pd.joinedBefore) {
                player.sendMessage(ChatColor.RED +
                        "You must first enter a valid invite code to start playing.");
                return true;
            }

            if (pd.remainingInvites <= 0) {
                player.sendMessage(ChatColor.RED + "Can not have more tokens at this moment");
                return true;
            }

            if (!confirmSet.contains(uuid)) {
                confirmSet.add(uuid);
                player.sendMessage(ChatColor.YELLOW +
                        "Are you sure to create the Invite Code? If so please /invite again.");
                return true;
            }


            confirmSet.remove(uuid);
            if (pd.remainingInvites <= 0) {
                player.sendMessage(ChatColor.RED + "Can not have more tokens at this moment");
                return true;
            }

            InviteToken token = storage.createTokenFor(pd);
            player.sendMessage(ChatColor.GREEN + "Code has been created: "
                    + ChatColor.YELLOW + token.code);
            plugin.getLogger().info(player.getName() + " created invite code " + token.code);
            return true;
        }


        if (args.length == 1) {
            String code = args[0];


            if (!isOp) {

                if (pd.joinedBefore) {
                    player.sendMessage(ChatColor.RED + "You have already used an invite code.");
                    return true;
                }

                if (storage.isBlocked(pd)) {
                    long remainingMs = pd.blockedUntil - System.currentTimeMillis();
                    long minutes = Math.max(1, remainingMs / 60000L);
                    player.sendMessage(ChatColor.RED +
                            "You are temporarily blocked from entering invite codes.\n" +
                            "Please try again in about " + minutes + " minutes.");
                    return true;
                }
            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                ConsumeResult result = storage.consumeToken(code, uuid, player.getName());

                if (!result.valid) {
                    if (!isOp) {
                        BlockStatus bs = storage.registerInvalidAttempt(pd);
                        player.sendMessage(ChatColor.DARK_RED + "The token entered is invalid");

                        if (bs.nowBlocked) {
                            long remainingMs = bs.blockedUntil - System.currentTimeMillis();
                            long minutes = Math.max(1, remainingMs / 60000L);

                            String blockMsg;
                            if (bs.level == 1) {
                                blockMsg = "You have entered invalid tokens 5 times.\n" +
                                        "You are blocked for 30 minutes.";
                            } else {
                                blockMsg = "You have entered invalid tokens 10 times.\n" +
                                        "You are blocked for 2 hours.";
                            }

                            final String kickMsg = ChatColor.RED + blockMsg +
                                    "\nYou can try again in about " + minutes + " minutes.";

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                player.kickPlayer(kickMsg);
                            });
                        }
                    } else {

                        player.sendMessage(ChatColor.DARK_RED + "Invalid token (OP test).");
                    }
                    return;
                }


                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.GREEN + "Invite code accepted. Welcome!");

                    if (result.firstTime) {
                        Bukkit.broadcastMessage(ChatColor.GOLD + player.getName()
                                + " joined the server for the first time!");
                    }


                    if (result.owner != null && result.owner.uuid != null) {
                        Player inviterOnline = Bukkit.getPlayer(result.owner.uuid);
                        if (inviterOnline != null && inviterOnline.isOnline()) {
                            inviterOnline.sendMessage(ChatColor.AQUA + player.getName() +
                                    " has joined using your invite code.");
                        }
                    }
                });
            });

            return true;
        }

        player.sendMessage(ChatColor.RED + "Usage: /invite [code]");
        return true;
    }
}
