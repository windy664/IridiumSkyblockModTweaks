package org.windy.iridiumSkyblockModTweaks;

import com.iridium.iridiumskyblock.api.IridiumSkyblockAPI;
import com.iridium.iridiumskyblock.database.Island;
import com.iridium.iridiumskyblock.database.User;
import com.iridium.iridiumteams.PermissionType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import me.clip.placeholderapi.PlaceholderAPI;

import java.util.*;

public final class IridiumSkyblockModTweaks extends JavaPlugin implements Listener {

    private String cannotUseItem;
    private boolean debug;
    List<String> island_worlds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        String version = this.getDescription().getVersion();
        String serverName = this.getServer().getName();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            Bukkit.getPluginManager().registerEvents(this, this);
            this.getServer().getConsoleSender().sendMessage("v" + "§a" + version + "运行环境：§e " + serverName + "\n");
        } else {
            getLogger().warning("你未安装前置PlaceholderAPI！插件没法启动！");
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private boolean isItemDisabled(Material itemType) {
        FileConfiguration config = getConfig();
        return config.getStringList("disabledItems").contains(itemType.name());
    }

    private void loadConfigValues() {
        FileConfiguration config = getConfig();
        cannotUseItem = config.getString("messages", "[✘] 你没有权限在这个空岛使用这个物品");
        debug = config.getBoolean("debug", false);
        island_worlds = config.getStringList("island_worlds");
    }

    private void log(String messages) {
        if (debug) {
            Bukkit.getConsoleSender().sendMessage(messages);
        }
    }

    @EventHandler
    public void playerTeleport(PlayerTeleportEvent event) {
        Location location = event.getPlayer().getLocation();
        String worldName = Objects.requireNonNull(location.getWorld()).getName();
        if (!island_worlds.contains(worldName)) {
            return;
        }
        Player player = event.getPlayer();
        User user = IridiumSkyblockAPI.getInstance().getUser(player);
        Island island = user.getCurrentIsland().get();
        // 判断玩家是否进入城镇区域
        String notprvent = PlaceholderAPI.setPlaceholders(player, "%iridiumskyblock_current_owner%");
        boolean canBreakBlocks = IridiumSkyblockAPI.getInstance().getIslandPermission(island, user, PermissionType.BLOCK_BREAK);

        if (!canBreakBlocks) {  // 玩家进入城镇
            // 获取玩家手持的物品
            ItemStack heldItem = player.getInventory().getItemInMainHand();
            if (heldItem != null && isItemDisabled(heldItem.getType())) {
                // 如果玩家手持禁用物品，掉落该物品
            //    player.getWorld().dropItem(player.getLocation(), heldItem);  // 将禁用物品掉落在玩家当前位置
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));  // 清空玩家的手持物品
                sendActionBar(player,cannotUseItem);
                log(player.getName() + " 手持禁用物品，已丢弃该物品并清空手持物品。");
            }
        }
    }



    // 查找玩家背包中是否有非禁用物品，作为替换的物品
    private ItemStack getNonDisabledItem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !isItemDisabled(item.getType())) {
                return item; // 找到一个非禁用物品并返回
            }
        }
        return null; // 如果没有非禁用物品则返回 null
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Location location = event.getPlayer().getLocation();
        String worldName = Objects.requireNonNull(location.getWorld()).getName();
        if (!island_worlds.contains(worldName) || !worldName.equals("IridiumSkyblock")) {
            return;
        }
        Player player = event.getPlayer();
        User user = IridiumSkyblockAPI.getInstance().getUser(player);
        Island island = user.getCurrentIsland().get();
        ItemStack newItem = player.getInventory().getItemInMainHand(); // 获取玩家主手中的物品
        boolean canBreakBlocks = IridiumSkyblockAPI.getInstance().getIslandPermission(island, user, PermissionType.BLOCK_BREAK);

        if (player.isOp()) {
            return;
        }

        if (newItem != null && isItemDisabled(newItem.getType()) && !canBreakBlocks) {
            event.setCancelled(true);
            if (cannotUseItem != null) {
                sendActionBar(player, cannotUseItem);
            }
        }
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Location location = event.getPlayer().getLocation();
        String worldName = Objects.requireNonNull(location.getWorld()).getName();
        if (!island_worlds.contains(worldName)) {
            return;
        }
        Player player = event.getPlayer();
        User user = IridiumSkyblockAPI.getInstance().getUser(player);
        Island island = user.getCurrentIsland().get();
        // 检查岛屿是否存在以及玩家是否有权限
        if (event.useInteractedBlock() != Event.Result.DENY &&
                event.useItemInHand() != Event.Result.DENY &&
                !event.getAction().name().contains("RIGHT_CLICK")) {
            return;
        }
        if (island != null) {
            boolean canBreakBlocks = IridiumSkyblockAPI.getInstance().getIslandPermission(island, user, PermissionType.BLOCK_BREAK);
            // 如果没有权限，取消事件（例如不能破坏方块）
            if (!canBreakBlocks) {
                event.setUseItemInHand(Event.Result.DENY);
                event.setCancelled(true);
            }
        }
    }
    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();
        String worldName = Objects.requireNonNull(location.getWorld()).getName();

        // 添加日志，查看当前世界和事件动作
        log("玩家 " + player.getName() + " 当前所在世界: " + worldName);

        if (!island_worlds.contains(worldName)) {
            log("世界 " + worldName + " 不在岛屿世界列表中，忽略该事件。");
            return;
        }

        User user = IridiumSkyblockAPI.getInstance().getUser(player);
        Optional<Island> optionalIsland = user.getCurrentIsland();

        if (!optionalIsland.isPresent()) {
            log("玩家 " + player.getName() + " 没有岛屿，忽略该事件。");
            return;
        }

        Island island = optionalIsland.get();

        // 添加日志：玩家点击了方块，检查是否蹲下
        log("玩家 " + player.getName() + " 执行了右键交互。");

        // 先判断玩家是否在蹲下
        if (player.isSneaking()) {
            log("玩家 " + player.getName() + " 正在蹲下，取消事件。");
            event.setCancelled(true);  // 取消事件，防止任何右键交互
            return;
        }


        // 检查岛屿是否存在以及玩家是否有权限
        boolean canBreakBlocks = IridiumSkyblockAPI.getInstance().getIslandPermission(island, user, PermissionType.BLOCK_BREAK);

        if (!canBreakBlocks) {
            event.setCancelled(true);
            log("玩家 " + player.getName() + " 在该岛屿上没有破坏方块的权限，事件已取消。");
        }
    }

    private void sendActionBar(Player player, String message) {
        TextComponent actionBar = new TextComponent(message);
        actionBar.setColor(net.md_5.bungee.api.ChatColor.RED); // 设置颜色
        player.spigot().sendMessage(actionBar); // 发送 ActionBar 消息
    }


    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
    }
