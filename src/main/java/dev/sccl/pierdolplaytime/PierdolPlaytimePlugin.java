package dev.sccl.pierdolplaytime;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

public final class PierdolPlaytimePlugin extends JavaPlugin implements Listener {
    private static final long AFK_NANOS = TimeUnit.SECONDS.toNanos(300);
    private final Map<Player, Activity> players = new IdentityHashMap<>();
    private final AtomicBoolean inFlight = new AtomicBoolean();

    private static final class Activity {
        long lastInput = System.nanoTime();
        boolean afk;
    }

    private record AuthBridge(Object service, Method authenticatedUserIdMethod,
                              Method maxCreditBatchSizeMethod, Method creditActiveMinutesMethod) {
        private static AuthBridge load() {
            try {
                var auth = Bukkit.getPluginManager().getPlugin("PierdolAuth");
                if (auth == null || !auth.isEnabled()) return null;
                Class<?> api = Class.forName("dev.sccl.pierdolauth.api.AuthenticatedPlayerService",
                        false, auth.getClass().getClassLoader());
                Object service = Bukkit.getServicesManager().load(api);
                if (service == null) return null;
                return new AuthBridge(service, api.getMethod("authenticatedUserId", Player.class),
                        api.getMethod("maxCreditBatchSize"),
                        api.getMethod("creditActiveMinutes", Collection.class));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        private OptionalLong authenticatedUserId(Player player) {
            try {
                Object result = authenticatedUserIdMethod.invoke(service, player);
                return result instanceof OptionalLong userId ? userId : OptionalLong.empty();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return OptionalLong.empty();
            }
        }

        private long maxCreditBatchSize() {
            try {
                Object result = maxCreditBatchSizeMethod.invoke(service);
                return result instanceof Number size ? size.longValue() : 0;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return 0;
            }
        }

        private CompletionStage<?> creditActiveMinutes(Collection<Long> userIds) {
            try {
                Object result = creditActiveMinutesMethod.invoke(service, userIds);
                return result instanceof CompletionStage<?> stage ? stage : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }
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
        activity.afk = false;
        player.setSleepingIgnored(false);
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
                player.setSleepingIgnored(true);
            }
        });
    }

    private void creditMinute() {
        if (inFlight.get()) return;
        var service = AuthBridge.load();
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
            var completion = service.creditActiveMinutes(List.copyOf(userIds));
            if (completion == null) {
                inFlight.set(false);
                return;
            }
            completion.whenCompleteAsync((credited, failure) -> inFlight.set(false));
        } catch (RuntimeException ignored) {
            inFlight.set(false);
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.setSleepingIgnored(false);
        players.put(player, new Activity());
    }
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
        for (Player player : Bukkit.getOnlinePlayers()) player.setSleepingIgnored(false);
        Bukkit.getScheduler().cancelTasks(this);
        HandlerList.unregisterAll((Listener) this);
        players.clear();
    }
}
