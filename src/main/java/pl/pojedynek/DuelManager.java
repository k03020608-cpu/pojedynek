package pl.pojedynek;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DuelManager {

    /** Klucz metadanych sprawdzany przez plugin Lifesteal - gracz z tą metadaną nie traci/zyskuje serca za śmierć. */
    public static final String METADATA_W_POJEDYNKU = "wPojedynku";

    private static final long WAZNOSC_WYZWANIA_MS = 60_000L;

    private final JavaPlugin plugin;
    private final ArenaManager arena;
    private final File file;

    private final Map<UUID, Wyzwanie> oczekujace = new HashMap<>();
    private final Map<UUID, Sesja> aktywne = new HashMap<>();
    /** Zawieszone (jeszcze nieoddane) ekwipunki - klucz to UUID gracza, dla bezpieczeństwa na wypadek restartu/rozłączenia. */
    private final Map<UUID, ZawieszonyStan> zawieszone = new HashMap<>();

    public DuelManager(JavaPlugin plugin, ArenaManager arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.file = new File(plugin.getDataFolder(), "zawieszone.yml");
    }

    // ---------- Wyzwania ----------

    private record Wyzwanie(UUID wyzywajacy, long czasUtworzenia) {
    }

    public void wyzwij(Player wyzywajacy, Player cel) {
        oczekujace.put(cel.getUniqueId(), new Wyzwanie(wyzywajacy.getUniqueId(), System.currentTimeMillis()));
    }

    public UUID pobierzWyzywajacego(Player cel) {
        Wyzwanie w = oczekujace.get(cel.getUniqueId());
        if (w == null) {
            return null;
        }
        if (System.currentTimeMillis() - w.czasUtworzenia() > WAZNOSC_WYZWANIA_MS) {
            oczekujace.remove(cel.getUniqueId());
            return null;
        }
        return w.wyzywajacy();
    }

    public void usunWyzwanie(Player cel) {
        oczekujace.remove(cel.getUniqueId());
    }

    public void usunWyzwaniaGracza(UUID uuid) {
        oczekujace.remove(uuid);
        oczekujace.values().removeIf(w -> w.wyzywajacy().equals(uuid));
    }

    // ---------- Sesje pojedynku ----------

    private static final class Sesja {
        final UUID gracz1;
        final UUID gracz2;
        boolean zakonczona = false;

        Sesja(UUID gracz1, UUID gracz2) {
            this.gracz1 = gracz1;
            this.gracz2 = gracz2;
        }

        UUID przeciwnik(UUID kogo) {
            return kogo.equals(gracz1) ? gracz2 : gracz1;
        }
    }

    public boolean jestWPojedynku(UUID uuid) {
        return aktywne.containsKey(uuid);
    }

    /** Zwraca online przeciwnika gracza z jego bieżącego pojedynku, albo null. */
    public Player znajdzPrzeciwnika(UUID uuid) {
        Sesja sesja = aktywne.get(uuid);
        if (sesja == null) {
            return null;
        }
        return Bukkit.getPlayer(sesja.przeciwnik(uuid));
    }

    public boolean rozpocznijPojedynek(Player p1, Player p2) {
        if (!arena.arenaGotowa() || !arena.itemyGotowe()) {
            return false;
        }
        if (jestWPojedynku(p1.getUniqueId()) || jestWPojedynku(p2.getUniqueId())) {
            return false;
        }

        // Zapamiętaj skąd ich zabrano i co mieli w ekwipunku.
        zapiszStan(p1);
        zapiszStan(p2);

        p1.setMetadata(METADATA_W_POJEDYNKU, new FixedMetadataValue(plugin, true));
        p2.setMetadata(METADATA_W_POJEDYNKU, new FixedMetadataValue(plugin, true));

        arena.ubierzGracza(p1);
        arena.ubierzGracza(p2);

        p1.teleport(arena.getSpawn1());
        p2.teleport(arena.getSpawn2());

        // Nadaj obu graczom rangę "pojedynek" na czas walki (np. żeby dać im inne uprawnienia/chat).
        nadajRange(p1.getName(), "pojedynek");
        nadajRange(p2.getName(), "pojedynek");

        Sesja sesja = new Sesja(p1.getUniqueId(), p2.getUniqueId());
        aktywne.put(p1.getUniqueId(), sesja);
        aktywne.put(p2.getUniqueId(), sesja);

        usunWyzwaniaGracza(p1.getUniqueId());
        usunWyzwaniaGracza(p2.getUniqueId());

        return true;
    }

    /** Kończy pojedynek gracza (np. po jego śmierci) - odsyła oboje uczestników do domu z ich rzeczami. */
    public void zakonczPojedynekGracza(UUID zmarly) {
        Sesja sesja = aktywne.get(zmarly);
        if (sesja == null || sesja.zakonczona) {
            return;
        }
        sesja.zakonczona = true;

        UUID przeciwnik = sesja.przeciwnik(zmarly);
        aktywne.remove(sesja.gracz1);
        aktywne.remove(sesja.gracz2);

        przywrocGracza(zmarly);
        przywrocGracza(przeciwnik);
    }

    /** Wołane przy rozłączeniu gracza w trakcie pojedynku - kończy pojedynek dla obu stron. */
    public void przerwijJesliWPojedynku(UUID uuid) {
        if (jestWPojedynku(uuid)) {
            zakonczPojedynekGracza(uuid);
        }
    }

    private void przywrocGracza(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.removeMetadata(METADATA_W_POJEDYNKU, plugin);
            nadajRange(player.getName(), "default");
        } else {
            // Gracz offline (np. rozłączył się) - i tak spróbuj cofnąć rangę po jego znanej nazwie.
            String nazwa = Bukkit.getOfflinePlayer(uuid).getName();
            if (nazwa != null) {
                nadajRange(nazwa, "default");
            }
        }
        przywrocStan(uuid);
    }

    /** Wykonuje "/lp user <gracz> parent set <ranga>" z konsoli. */
    private void nadajRange(String nazwaGracza, String ranga) {
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + nazwaGracza + " parent set " + ranga));
    }

    // ---------- Zawieszony stan (ekwipunek + pozycja sprzed pojedynku) ----------

    private static final class ZawieszonyStan {
        final ItemStack[] contents;
        final ItemStack[] armor;
        final ItemStack offhand;
        final Location lokacja;

        ZawieszonyStan(ItemStack[] contents, ItemStack[] armor, ItemStack offhand, Location lokacja) {
            this.contents = contents;
            this.armor = armor;
            this.offhand = offhand;
            this.lokacja = lokacja;
        }
    }

    private void zapiszStan(Player player) {
        PlayerInventory inv = player.getInventory();
        ZawieszonyStan stan = new ZawieszonyStan(
                kopiuj(inv.getStorageContents()),
                kopiuj(inv.getArmorContents()),
                inv.getItemInOffHand().clone(),
                player.getLocation().clone()
        );
        zawieszone.put(player.getUniqueId(), stan);
        save();
    }

    /** Przywraca ekwipunek i pozycję gracza sprzed pojedynku, jeśli jakiś zawieszony stan czeka. */
    public void przywrocStan(UUID uuid) {
        ZawieszonyStan stan = zawieszone.remove(uuid);
        if (stan == null) {
            return;
        }
        save();

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            // Gracz offline - stan i tak został usunięty z mapy zawieszonych, więc trzeba by go inaczej oddać.
            // W praktyce nie powinno się zdarzyć, bo przywracamy stan zaraz po śmierci/rozłączeniu.
            return;
        }

        PlayerInventory inv = player.getInventory();
        inv.setStorageContents(kopiuj(stan.contents));
        inv.setArmorContents(kopiuj(stan.armor));
        inv.setItemInOffHand(stan.offhand != null ? stan.offhand.clone() : new ItemStack(Material.AIR));

        if (stan.lokacja != null) {
            player.teleport(stan.lokacja);
        }
    }

    /** Czy dla tego gracza czeka jeszcze nieoddany ekwipunek (np. po restarcie serwera w trakcie pojedynku). */
    public boolean maZawieszonyStan(UUID uuid) {
        return zawieszone.containsKey(uuid);
    }

    private ItemStack[] kopiuj(ItemStack[] zrodlo) {
        if (zrodlo == null) {
            return new ItemStack[0];
        }
        ItemStack[] kopia = new ItemStack[zrodlo.length];
        for (int i = 0; i < zrodlo.length; i++) {
            kopia[i] = (zrodlo[i] == null) ? null : zrodlo[i].clone();
        }
        return kopia;
    }

    // ---------- Zapis / odczyt (bezpieczeństwo na wypadek restartu w trakcie pojedynku) ----------

    public void load() {
        zawieszone.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.contains("gracze")) {
            return;
        }

        for (String key : yaml.getConfigurationSection("gracze").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String base = "gracze." + key + ".";

                ItemStack[] contents = listaNaTablice(yaml.getList(base + "contents"));
                ItemStack[] armor = listaNaTablice(yaml.getList(base + "armor"));
                Object offObj = yaml.get(base + "offhand");
                ItemStack offhand = (offObj instanceof ItemStack item) ? item : new ItemStack(Material.AIR);

                Location lokacja = null;
                if (yaml.contains(base + "lokacja.swiat")) {
                    String worldName = yaml.getString(base + "lokacja.swiat");
                    World world = worldName != null ? Bukkit.getWorld(worldName) : null;
                    if (world != null) {
                        lokacja = new Location(
                                world,
                                yaml.getDouble(base + "lokacja.x"),
                                yaml.getDouble(base + "lokacja.y"),
                                yaml.getDouble(base + "lokacja.z"),
                                (float) yaml.getDouble(base + "lokacja.yaw"),
                                (float) yaml.getDouble(base + "lokacja.pitch")
                        );
                    }
                }

                zawieszone.put(uuid, new ZawieszonyStan(contents, armor, offhand, lokacja));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Pominięto nieprawidłowy wpis w zawieszone.yml: " + key);
            }
        }
    }

    private ItemStack[] listaNaTablice(List<?> lista) {
        if (lista == null) {
            return new ItemStack[0];
        }
        ItemStack[] tablica = new ItemStack[lista.size()];
        for (int i = 0; i < lista.size(); i++) {
            Object o = lista.get(i);
            tablica[i] = (o instanceof ItemStack item) ? item : null;
        }
        return tablica;
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<UUID, ZawieszonyStan> entry : zawieszone.entrySet()) {
            String base = "gracze." + entry.getKey() + ".";
            ZawieszonyStan stan = entry.getValue();

            yaml.set(base + "contents", zamienNaListe(stan.contents));
            yaml.set(base + "armor", zamienNaListe(stan.armor));
            yaml.set(base + "offhand", stan.offhand != null ? stan.offhand : new ItemStack(Material.AIR));

            if (stan.lokacja != null) {
                yaml.set(base + "lokacja.swiat", stan.lokacja.getWorld().getName());
                yaml.set(base + "lokacja.x", stan.lokacja.getX());
                yaml.set(base + "lokacja.y", stan.lokacja.getY());
                yaml.set(base + "lokacja.z", stan.lokacja.getZ());
                yaml.set(base + "lokacja.yaw", stan.lokacja.getYaw());
                yaml.set(base + "lokacja.pitch", stan.lokacja.getPitch());
            }
        }

        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udało się zapisać zawieszone.yml: " + e.getMessage());
        }
    }

    private List<ItemStack> zamienNaListe(ItemStack[] tablica) {
        List<ItemStack> lista = new ArrayList<>();
        for (ItemStack item : tablica) {
            lista.add(item == null ? new ItemStack(Material.AIR) : item);
        }
        return lista;
    }
}
