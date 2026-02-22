package com.ayhan.invitetokens;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class InviteTokensPlugin extends JavaPlugin {

    private InviteStorage storage;

    private final Set<UUID> inviteConfirm = new HashSet<>();

    @Override
    public void onEnable() {
        this.storage = new InviteStorage(this);
        this.storage.load();

        getCommand("invite").setExecutor(new InviteCommand(this, storage, inviteConfirm));
        getCommand("inviteop").setExecutor(new InviteOpCommand(this, storage));

        getServer().getPluginManager().registerEvents(new InviteListener(this, storage), this);

        getLogger().info("InviteTokens enabled.");
    }


    @Override
    public void onDisable() {
        if (storage != null) {
            storage.save();
        }
    }
}
