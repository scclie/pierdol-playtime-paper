package dev.sccl.pierdolplaytime;

import dev.sccl.pierdolauth.api.AuthenticatedPlayerService;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

public final class PierdolPlaytimePlugin extends JavaPlugin implements Listener {
    private static final long AFK_NANOS = TimeUnit.SECONDS.toNanos(300);
    private final Map<Player, Activity> players = new IdentityHashMap<>();
    private final AtomicBoolean inFlight = new AtomicBoolean();

    private static final class Activity {
        long lastInput = System.nanoTime();
        boolean afk;
        Component original;
        Component decorated;
    }

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player player : Bukkit.getOnlinePlayers()) players.put(player, new Activity());
        Bukkit.getScheduler().runTaskTimer(this, this::checkAfk, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::creditMinute, 1200L, 1200L);
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
                activity.original = player.playerListName();
                activity.decorated = Component.text("⌚").append(activity.original);
                player.playerListName(activity.decorated);
            }
        });
    }

    private void creditMinute() {
        if (inFlight.get()) return;
        var service = Bukkit.getServicesManager().load(AuthenticatedPlayerService.class);
        if (service == null) return;
        var userIds = new HashSet<Long>();
        long now = System.nanoTime();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Activity activity = players.get(player);
            if (activity == null || activity.afk || now - activity.lastInput >= AFK_NANOS) continue;
            var userId = service.authenticatedUserId(player);
            if (userId.isPresent()) userIds.add(userId.getAsLong());
            if (userIds.size() > 100 || userIds.size() > service.maxCreditBatchSize()) return;
        }
        if (userIds.isEmpty() || !inFlight.compareAndSet(false, true)) return;
        try {
            service.creditActiveMinutes(List.copyOf(userIds))
                    .whenCompleteAsync((credited, failure) -> inFlight.set(false));
        } catch (RuntimeException ignored) {
            inFlight.set(false);
        }
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
