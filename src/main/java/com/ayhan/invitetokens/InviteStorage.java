package com.ayhan.invitetokens;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class InviteStorage {

    private static final int DEFAULT_INVITES = 2;

    private final JavaPlugin plugin;
    private final File dbFile;


    final Map<UUID, PlayerData> players = new HashMap<>();
    private final Map<String, UUID> tokenOwnerByCode = new HashMap<>();

    public InviteStorage(JavaPlugin plugin) {
        this.plugin = plugin;

        this.dbFile = new File(plugin.getServer().getWorldContainer(), "invitedb");
    }

    public void load() {
        players.clear();
        tokenOwnerByCode.clear();

        if (!dbFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dbFile);
        ConfigurationSection playersSec = config.getConfigurationSection("players");
        if (playersSec == null) return;

        for (String uuidStr : playersSec.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }

            ConfigurationSection ps = playersSec.getConfigurationSection(uuidStr);
            if (ps == null) continue;

            String name = ps.getString("name", "Unknown");
            int remainingInvites = ps.getInt("remainingInvites", DEFAULT_INVITES);
            boolean joinedBefore = ps.getBoolean("joinedBefore", false);
            int invalidAttempts = ps.getInt("invalidAttempts", 0);
            int blockLevel = ps.getInt("blockLevel", 0);
            long blockedUntil = ps.getLong("blockedUntil", 0L);
            List<String> pendingMessages = ps.getStringList("pendingMessages");

            List<InviteToken> tokenList = new ArrayList<>();
            List<Map<?, ?>> rawTokens = ps.getMapList("tokens");
            for (Map<?, ?> m : rawTokens) {
                String code = (String) m.get("code");
                if (code == null) continue;
                code = code.toUpperCase(Locale.ROOT);

                int maxUses = getInt(m.get("maxUses"), 1);
                int uses = getInt(m.get("uses"), 0);

                List<InvitedPlayer> invitedPlayers = new ArrayList<>();
                Object invitedObj = m.get("invitedPlayers");
                if (invitedObj instanceof List<?>) {
                    for (Object io : (List<?>) invitedObj) {
                        if (io instanceof Map<?, ?>) {
                            Map<?, ?> im = (Map<?, ?>) io;

                            String invitedUuidStr = null;
                            Object uuidObj = im.get("uuid");
                            if (uuidObj instanceof String) {
                                invitedUuidStr = (String) uuidObj;
                            }

                            Object nameObj = im.get("name");
                            String invitedName = (nameObj != null) ? nameObj.toString() : "Unknown";

                            UUID invitedUuid = null;
                            if (invitedUuidStr != null) {
                                try {
                                    invitedUuid = UUID.fromString(invitedUuidStr);
                                } catch (IllegalArgumentException ignored) {}
                            }

                            invitedPlayers.add(new InvitedPlayer(invitedUuid, invitedName));
                        }
                    }
                }

                InviteToken token = new InviteToken(code, maxUses, uses, invitedPlayers);
                tokenList.add(token);
                tokenOwnerByCode.put(code, uuid);
            }

            PlayerData pd = new PlayerData(
                    uuid, name, remainingInvites,
                    joinedBefore, tokenList,
                    invalidAttempts, blockLevel, blockedUntil,
                    new ArrayList<>(pendingMessages)
            );
            players.put(uuid, pd);
        }

        plugin.getLogger().info("InviteStorage loaded " + players.size() + " players.");
    }

    private int getInt(Object obj, int def) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        return def;
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection playersSec = config.createSection("players");

        for (PlayerData pd : players.values()) {
            ConfigurationSection ps = playersSec.createSection(pd.uuid.toString());
            ps.set("name",     pd.name);
            ps.set("remainingInvites", pd.remainingInvites);
            ps.set("joinedBefore",     pd.joinedBefore);
            ps.set("invalidAttempts",  pd.invalidAttempts);
            ps.set("blockLevel",       pd.blockLevel);
            ps.set("blockedUntil",     pd.blockedUntil);
            ps.set("pendingMessages",  pd.pendingMessages);

            List<Map<String, Object>> tokenList = new ArrayList<>();
            for (InviteToken t : pd.tokens) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code",    t.code);
                m.put("maxUses", t.maxUses);
                m.put("uses",    t.uses);

                List<Map<String, Object>> invitedList = new ArrayList<>();
                for (InvitedPlayer ip : t.invitedPlayers) {
                    Map<String, Object> im = new LinkedHashMap<>();
                    if (ip.uuid != null) {
                        im.put("uuid", ip.uuid.toString());
                    }
                    im.put("name", ip.name);
                    invitedList.add(im);
                }
                m.put("invitedPlayers", invitedList);
                tokenList.add(m);
            }
            ps.set("tokens", tokenList);
        }

        try {
            config.save(dbFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save invitedb: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public PlayerData getOrCreatePlayer(UUID uuid, String name) {
        PlayerData pd = players.get(uuid);
        if (pd == null) {
            pd = new PlayerData(uuid, name, DEFAULT_INVITES, false,
                    new ArrayList<>(), 0, 0, 0L, new ArrayList<>());
            players.put(uuid, pd);
        } else {
            pd.name = name;
        }
        return pd;
    }


    private String generateCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        while (true) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            String code = sb.toString();
            if (!tokenOwnerByCode.containsKey(code)) {
                return code;
            }
        }
    }


    public InviteToken createTokenFor(PlayerData inviter) {
        String code = generateCode();
        InviteToken token = new InviteToken(code, 1, 0, new ArrayList<>());
        inviter.tokens.add(token);
        inviter.remainingInvites = Math.max(0, inviter.remainingInvites - 1);
        tokenOwnerByCode.put(code, inviter.uuid);
        save();
        return token;
    }


    public ConsumeResult consumeToken(String rawCode, UUID playerUuid, String playerName) {
        if (rawCode == null) {
            return ConsumeResult.invalid("Invalid code.");
        }
        String code = rawCode.toUpperCase(Locale.ROOT);

        UUID ownerUuid = tokenOwnerByCode.get(code);
        if (ownerUuid == null) {
            return ConsumeResult.invalid("This code does not exist.");
        }

        PlayerData owner = players.get(ownerUuid);
        if (owner == null) {
            return ConsumeResult.invalid("Internal error (owner not found).");
        }

        InviteToken targetToken = null;
        for (InviteToken t : owner.tokens) {
            if (t.code.equals(code)) {
                targetToken = t;
                break;
            }
        }
        if (targetToken == null) {
            return ConsumeResult.invalid("Internal error (token not found).");
        }

        if (targetToken.uses >= targetToken.maxUses) {
            return ConsumeResult.invalid("This code has already been used.");
        }


        PlayerData invited = getOrCreatePlayer(playerUuid, playerName);
        boolean firstTime = !invited.joinedBefore;

        invited.joinedBefore = true;
        targetToken.uses += 1;
        targetToken.invitedPlayers.add(new InvitedPlayer(playerUuid, playerName));


        invited.invalidAttempts = 0;
        invited.blockLevel = 0;
        invited.blockedUntil = 0L;


        String msg = playerName + " has joined using your invite code.";
        owner.pendingMessages.add(msg);

        save();

        return ConsumeResult.valid(owner, invited, firstTime);
    }


    public boolean isNewAndUnverified(UUID uuid) {
        PlayerData pd = players.get(uuid);
        return pd != null && !pd.joinedBefore;
    }

    public boolean isBlocked(PlayerData pd) {
        return pd.blockedUntil > System.currentTimeMillis();
    }


    public BlockStatus registerInvalidAttempt(PlayerData pd) {
        long now = System.currentTimeMillis();
        pd.invalidAttempts++;

        BlockStatus status = new BlockStatus(false, 0L, 0);

        if (pd.blockLevel == 0 && pd.invalidAttempts >= 5) {
            pd.blockLevel = 1;
            pd.blockedUntil = now + 30L * 60L * 1000L; // 30 dk
            status = new BlockStatus(true, pd.blockedUntil, 1);
        } else if (pd.blockLevel == 1 && pd.invalidAttempts >= 10) {
            pd.blockLevel = 2;
            pd.blockedUntil = now + 2L * 60L * 60L * 1000L; // 2 saat
            status = new BlockStatus(true, pd.blockedUntil, 2);
        }

        save();
        return status;
    }



    public static class PlayerData {
        public final UUID uuid;
        public String name;
        public int remainingInvites;
        public boolean joinedBefore;
        public final List<InviteToken> tokens;

        public int invalidAttempts;
        public int blockLevel;
        public long blockedUntil;

        public final List<String> pendingMessages;

        public PlayerData(UUID uuid, String name, int remainingInvites,
                          boolean joinedBefore, List<InviteToken> tokens,
                          int invalidAttempts, int blockLevel, long blockedUntil,
                          List<String> pendingMessages) {
            this.uuid = uuid;
            this.name = name;
            this.remainingInvites = remainingInvites;
            this.joinedBefore = joinedBefore;
            this.tokens = tokens;
            this.invalidAttempts = invalidAttempts;
            this.blockLevel = blockLevel;
            this.blockedUntil = blockedUntil;
            this.pendingMessages = pendingMessages;
        }
    }

    public static class InviteToken {
        public final String code;
        public final int maxUses;
        public int uses;
        public final List<InvitedPlayer> invitedPlayers;

        public InviteToken(String code, int maxUses, int uses, List<InvitedPlayer> invitedPlayers) {
            this.code = code;
            this.maxUses = maxUses;
            this.uses = uses;
            this.invitedPlayers = invitedPlayers;
        }
    }

    public static class InvitedPlayer {
        public final UUID uuid;
        public final String name;

        public InvitedPlayer(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    public static class ConsumeResult {
        public final boolean valid;
        public final String errorMessage;
        public final PlayerData owner;
        public final PlayerData invited;
        public final boolean firstTime;

        private ConsumeResult(boolean valid, String errorMessage,
                              PlayerData owner, PlayerData invited, boolean firstTime) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.owner = owner;
            this.invited = invited;
            this.firstTime = firstTime;
        }

        public static ConsumeResult invalid(String msg) {
            return new ConsumeResult(false, msg, null, null, false);
        }

        public static ConsumeResult valid(PlayerData owner, PlayerData invited, boolean firstTime) {
            return new ConsumeResult(true, null, owner, invited, firstTime);
        }
    }

    public static class BlockStatus {
        public final boolean nowBlocked;
        public final long blockedUntil;
        public final int level;

        public BlockStatus(boolean nowBlocked, long blockedUntil, int level) {
            this.nowBlocked = nowBlocked;
            this.blockedUntil = blockedUntil;
            this.level = level;
        }
    }
}
