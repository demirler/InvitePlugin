package com.ayhan.invitetokens;

import com.ayhan.invitetokens.InviteStorage.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.UUID;

public class InviteListener implements Listener {

    private final InviteTokensPlugin plugin;
    private final InviteStorage storage;

    public InviteListener(InviteTokensPlugin plugin, InviteStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();

        PlayerData pd = storage.getOrCreatePlayer(uuid, p.getName());


        if (p.isOp()) {
            pd.joinedBefore = true;
            storage.save();


            if (!pd.pendingMessages.isEmpty()) {
                for (String msg : pd.pendingMessages) {
                    p.sendMessage(ChatColor.AQUA + msg);
                }
                pd.pendingMessages.clear();
                storage.save();
            }
            return;
        }

        long now = System.currentTimeMillis();
        if (pd.blockedUntil > now) {
            long remainingMs = pd.blockedUntil - now;
            long minutes = Math.max(1, remainingMs / 60000L);
            p.kickPlayer(ChatColor.RED +
                    "You are temporarily blocked from entering invite codes.\n" +
                    "Please try again in about " + minutes + " minutes.");
            return;
        }


        if (!pd.pendingMessages.isEmpty()) {
            for (String msg : pd.pendingMessages) {
                p.sendMessage(ChatColor.AQUA + msg);
            }
            pd.pendingMessages.clear();
            storage.save();
        }


        if (!pd.joinedBefore) {
            p.sendMessage(ChatColor.YELLOW +
                    "Please enter your invite code with /invite <code> to start playing.");


            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player again = Bukkit.getPlayer(uuid);
                if (again != null && again.isOnline() && storage.isNewAndUnverified(uuid)) {
                    again.kickPlayer(ChatColor.RED +
                            "You did not enter a valid invite code in time.\n" +
                            "Please rejoin with a valid invite code.");
                }
            }, 5L * 60L * 20L);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (p.isOp()) return;

        UUID uuid = p.getUniqueId();
        if (!storage.isNewAndUnverified(uuid)) return;


        if (event.getFrom().getX() == event.getTo().getX() &&
                event.getFrom().getY() == event.getTo().getY() &&
                event.getFrom().getZ() == event.getTo().getZ()) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;

        Player p = (Player) event.getDamager();
        if (p.isOp()) return;

        if (storage.isNewAndUnverified(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
