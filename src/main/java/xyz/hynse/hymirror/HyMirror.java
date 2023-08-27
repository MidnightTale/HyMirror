package xyz.hynse.hymirror;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.hynse.hymirror.Util.Scheduler;

import java.util.*;

public final class HyMirror extends JavaPlugin implements Listener {

    private static final String MIRROR_TAG = "MirrorOfReturn";
    private final Map<Player, Long> cooldowns = new HashMap<>();
    private final Map<Player, Long> combatCooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        registerCustomRecipe();
    }

    private void registerCustomRecipe() {
        NamespacedKey key = new NamespacedKey(this, "mirror_of_return");
        ShapedRecipe recipe = new ShapedRecipe(key, createCustomItem());
        recipe.shape(" E ", " D ", " G ");
        recipe.setIngredient('E', Material.ENDER_PEARL);
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('G', Material.GHAST_TEAR);
        getServer().addRecipe(recipe);
    }

    private ItemStack createCustomItem() {
        ItemStack item = new ItemStack(Material.GLOWSTONE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Mirror of Return");
        meta.getPersistentDataContainer().set(new NamespacedKey(this, MIRROR_TAG), PersistentDataType.BYTE, (byte) 1);
        meta.setCustomModelData(202340001);
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setLore(createLore(0));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == Material.GLOWSTONE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCustomItemClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (handItem != null && isCustomItem(handItem)) {
            if (isInCombat(player)) {
                player.sendMessage(ChatColor.RED + "You are in combat! Wait until you are out of combat.");
                return;
            }

            if (isOnCooldown(player)) {
                player.sendMessage(ChatColor.RED + "The Mirror of Return is on cooldown!");
                return;
            }

            Location spawnLocation = player.getBedSpawnLocation();
            if (spawnLocation == null) {
                spawnLocation = player.getWorld().getSpawnLocation();
            }
            player.teleportAsync(spawnLocation);

            playWarpEffects(player);

            applyCooldown(player);

            deductEchoShard(player);

            int usageCount = getUsageCount(handItem.getItemMeta());
            setUsageCount(handItem.getItemMeta(), usageCount + 1);
            ItemMeta meta = handItem.getItemMeta();
            meta.setLore(createLore(usageCount + 1));
            handItem.setItemMeta(meta);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isCustomItem(player.getInventory().getItemInMainHand())) {
                combatCooldowns.remove(player);
            }
        }
    }

    private boolean isInCombat(Player player) {
        return combatCooldowns.getOrDefault(player, 0L) > System.currentTimeMillis();
    }

    private boolean isOnCooldown(Player player) {
        return cooldowns.getOrDefault(player, 0L) > System.currentTimeMillis();
    }

    private void playWarpEffects(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        // Add particle effects if desired
    }

    private void applyCooldown(Player player) {
        long cooldownDuration = 30000;
        cooldowns.put(player, System.currentTimeMillis() + cooldownDuration);
        Scheduler.runAsyncSchedulerDelay(this, (onlinePlayer) -> {
            cooldowns.remove(player);
        }, (int) cooldownDuration / 1000);
    }


    private void deductEchoShard(Player player) {
        ItemStack echoShard = new ItemStack(Material.ECHO_SHARD); // Replace with the actual item type
        int count = 1; // Deduct one echo shard
        Map<Integer, ItemStack> remainingItems = player.getInventory().removeItem(echoShard);

        if (!remainingItems.isEmpty()) {
            ItemStack remainingEchoShard = remainingItems.values().iterator().next();
            int remainingCount = remainingEchoShard.getAmount();
            count = Math.max(0, count - remainingCount);
        }

        if (count > 0) {
            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem != null && isCustomItem(handItem)) {
                ItemMeta meta = handItem.getItemMeta();
                int usageCount = getUsageCount(meta);
                setUsageCount(meta, usageCount + count);
                handItem.setItemMeta(meta);
            }
        }
    }

    private boolean isCustomItem(ItemStack item) {
        if (item == null || item.getType() != Material.GLOWSTONE) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().has(new NamespacedKey(this, MIRROR_TAG), PersistentDataType.BYTE);
    }

    private int getUsageCount(ItemMeta meta) {
        return meta != null && meta.getPersistentDataContainer().has(new NamespacedKey(this, "usage_count"), PersistentDataType.INTEGER)
                ? meta.getPersistentDataContainer().get(new NamespacedKey(this, "usage_count"), PersistentDataType.INTEGER)
                : 0;
    }

    private void setUsageCount(ItemMeta meta, int count) {
        meta.getPersistentDataContainer().set(new NamespacedKey(this, "usage_count"), PersistentDataType.INTEGER, count);
    }

    private List<String> createLore(int usageCount) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Usage Count: " + usageCount);
        lore.add(ChatColor.GRAY + "Right-click to teleport.");
        return lore;
    }
}
