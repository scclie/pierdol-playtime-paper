package dev.sccl.pierdolplaytime;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
// import org.bukkit.event.block.BlockBreakEvent;
// import org.bukkit.event.block.BlockPlaceEvent;
// import org.bukkit.event.inventory.InventoryClickEvent;
// import org.bukkit.event.inventory.InventoryDragEvent;
// import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Local development only. Deliberately contains no Auth bridge or crediting path. */
public final class OfflineDevPlaytimePlugin extends JavaPlugin implements Listener {
    private static final long AFK_NANOS = TimeUnit.SECONDS.toNanos(5);
    private final Map<Player, Activity> players = new IdentityHashMap<>();

    private static final class Activity {
        long lastInput = System.nanoTime();
        boolean afk;
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
        activity.afk = false;
    }

    public boolean isAfk(Player player) {
        Activity activity = players.get(player);
        return activity != null && activity.afk;
    }

    private void checkAfk() {
        long now = System.nanoTime();
        players.forEach((player, activity) -> {
            if (!activity.afk && now - activity.lastInput >= AFK_NANOS) {
                activity.afk = true;
            }
        });
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { players.put(event.getPlayer(), new Activity()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        players.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) return;
        if (event.getFrom().getYaw() != event.getTo().getYaw()
                || event.getFrom().getPitch() != event.getTo().getPitch()) input(event.getPlayer());
    }

    // @EventHandler(priority = EventPriority.MONITOR)
    // public void onCommand(PlayerCommandPreprocessEvent event) { input(event.getPlayer()); }
    // @EventHandler(priority = EventPriority.MONITOR)
    // public void onInteract(PlayerInteractEvent event) {
    //     if (event.getAction() != org.bukkit.event.block.Action.PHYSICAL) input(event.getPlayer());
    // }
    // @EventHandler(priority = EventPriority.MONITOR)
    // public void onEntityInteract(PlayerInteractEntityEvent event) { input(event.getPlayer()); }
    // @EventHandler(priority = EventPriority.MONITOR)
    // public void onEntityInteractAt(PlayerInteractAtEntityEvent event) { input(event.getPlayer()); }
    // @EventHandler(priority = EventPriority.MONITOR)
    // public void onBreak(BlockBreakEvent event) { input(event.getPlayer()); }
    // @EventHandler(priority = EventPriority.MONITOR)
    // public void onPlace(BlockPlaceEvent event) { input(event.getPlayer()); }
    // @EventHandler(priority = EventPriority.MONITOR)
    // public void onClick(InventoryClickEvent event) {
    //     if (event.getWhoClicked() instanceof Player player) input(player);
    // }
    // @EventHandler(priority = EventPriority.MONITOR)
    // public void onDrag(InventoryDragEvent event) {
    //     if (event.getWhoClicked() instanceof Player player) input(player);
    // }
    // @EventHandler(priority = EventPriority.MONITOR)
    // public void onHeld(PlayerItemHeldEvent event) { input(event.getPlayer()); }
    // @EventHandler(priority = EventPriority.MONITOR)
    // public void onDrop(PlayerDropItemEvent event) { input(event.getPlayer()); }
    // @EventHandler(priority = EventPriority.MONITOR)
    // public void onSwap(PlayerSwapHandItemsEvent event) { input(event.getPlayer()); }

    @Override public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        HandlerList.unregisterAll((Listener) this);
        players.clear();
    }
}
