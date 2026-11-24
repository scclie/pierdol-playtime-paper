package dev.sccl.pierdolplaytime;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Local development only. Deliberately contains no Auth bridge or crediting path. */
public final class OfflineDevPlaytimePlugin extends JavaPlugin implements Listener {
    private static final long AFK_NANOS = TimeUnit.SECONDS.toNanos(5);
    private final Map<Player, Activity> players = new IdentityHashMap<>();

    private static final class Activity {
        long lastInput = System.nanoTime();
        boolean afk;
        Component original;
        Component decorated;
    }

    @Override public void onEnable() {
        getLogger().warning("LOCAL OFFLINE DEV ONLY: 5-second AFK indicator; Auth and playtime crediting absent.");
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player player : Bukkit.getOnlinePlayers()) players.put(player, new Activity());
        Bukkit.getScheduler().runTaskTimer(this, this::checkAfk, 20L, 20L);
    }

    private void input(Player player) {
        Activity activity = players.get(player);
        if (activity == null) return;
        activity.lastInput = System.nanoTime();
        if (activity.afk) restore(player, activity);
    }

    private void restore(Player player, Activity activity) {
        if (activity.decorated != null && activity.decorated.equals(player.playerListName())) {
            player.playerListName(activity.original);
        }
        activity.afk = false;
        activity.original = null;
        activity.decorated = null;
    }

    private void checkAfk() {
        long now = System.nanoTime();
        players.forEach((player, activity) -> {
            if (!activity.afk && now - activity.lastInput >= AFK_NANOS) {
                activity.afk = true;
            }
            if (activity.afk) {
                Component current = player.playerListName();
                if (!current.equals(activity.decorated)) {
                    // Recompose from another plugin's latest base, including world colours.
                    activity.original = current;
                    activity.decorated = Component.text("⌚ ").append(current);
                    player.playerListName(activity.decorated);
                }
            }
        });
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { players.put(event.getPlayer(), new Activity()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Activity activity = players.remove(event.getPlayer());
        if (activity != null && activity.afk) restore(event.getPlayer(), activity);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) return;
        if (event.getFrom().getYaw() != event.getTo().getYaw()
                || event.getFrom().getPitch() != event.getTo().getPitch()) input(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) { input(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.PHYSICAL) input(event.getPlayer());
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityInteract(PlayerInteractEntityEvent event) { input(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityInteractAt(PlayerInteractAtEntityEvent event) { input(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) { input(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) { input(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) input(player);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) input(player);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent event) { input(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrop(PlayerDropItemEvent event) { input(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwap(PlayerSwapHandItemsEvent event) { input(event.getPlayer()); }

    @Override public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        HandlerList.unregisterAll((Listener) this);
        players.forEach((player, activity) -> {
            if (activity.afk) restore(player, activity);
        });
        players.clear();
    }
}
