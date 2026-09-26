package pl.pojedynek;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PojedynekListener implements Listener {

    private final JavaPlugin plugin;
    private final DuelManager duel;

    public PojedynekListener(JavaPlugin plugin, DuelManager duel) {
        this.plugin = plugin;
        this.duel = duel;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player ofiara = event.getEntity();
        if (!duel.jestWPojedynku(ofiara.getUniqueId())) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);

        String nazwaOfiary = ofiara.getName();
        Player przeciwnik = duel.znajdzPrzeciwnika(ofiara.getUniqueId());

        duel.zakonczPojedynekGracza(ofiara.getUniqueId());

        ofiara.sendMessage(Component.text("Przegrałeś pojedynek. Wracasz na swoje miejsce.", NamedTextColor.RED));
        if (przeciwnik != null) {
            przeciwnik.sendMessage(Component.text(
                    "Wygrałeś pojedynek z " + nazwaOfiary + "! Wracasz na swoje miejsce.", NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        duel.usunWyzwaniaGracza(event.getPlayer().getUniqueId());
        duel.przerwijJesliWPojedynku(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!duel.maZawieszonyStan(player.getUniqueId())) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            duel.przywrocStan(player.getUniqueId());
            player.sendMessage(Component.text("Odzyskujesz swój ekwipunek sprzed pojedynku.", NamedTextColor.YELLOW));
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (duel.maZawieszonyStan(player.getUniqueId())) {
            duel.przywrocStan(player.getUniqueId());
            player.sendMessage(Component.text(
                    "Odzyskano Twój ekwipunek sprzed przerwanego pojedynku.", NamedTextColor.YELLOW));
        }
    }
}
