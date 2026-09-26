package pl.pojedynek;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Trzyma ustawienia areny (dwa miejsca pojawienia się) i wspólny zestaw itemów
 * na pojedynek. Wszystko zapisywane na dysku, więc przetrwa restart serwera.
 */
public final class ArenaManager {

    private final JavaPlugin plugin;
    private final File file;

    private Location spawn1;
    private Location spawn2;
    private ItemStack[] loadoutContents;
    private ItemStack[] loadoutArmor;
    private ItemStack loadoutOffhand;

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arena.yml");
    }

    public boolean arenaGotowa() {
        return spawn1 != null && spawn2 != null;
    }

    public boolean itemyGotowe() {
        return loadoutContents != null;
    }

    public Location getSpawn1() {
        return spawn1;
    }

    public Location getSpawn2() {
        return spawn2;
    }

    public void ustawSpawn1(Location loc) {
        this.spawn1 = loc.clone();
        save();
    }

    public void ustawSpawn2(Location loc) {
        this.spawn2 = loc.clone();
        save();
    }

    public void ustawItemy(PlayerInventory inv) {
        this.loadoutContents = kopiuj(inv.getStorageContents());
        this.loadoutArmor = kopiuj(inv.getArmorContents());
        ItemStack off = inv.getItemInOffHand();
        this.loadoutOffhand = (off == null || off.getType().isAir()) ? null : off.clone();
        save();
    }

    /** Zakłada graczowi ustawiony zestaw itemów na pojedynek (podmienia cały ekwipunek). */
    public void ubierzGracza(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.setStorageContents(kopiuj(loadoutContents));
        inv.setArmorContents(kopiuj(loadoutArmor));
        inv.setItemInOffHand(loadoutOffhand != null ? loadoutOffhand.clone() : new ItemStack(Material.AIR));
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

    // ---------- Zapis / odczyt ----------

    public void load() {
        spawn1 = null;
        spawn2 = null;
        loadoutContents = null;
        loadoutArmor = null;
        loadoutOffhand = null;

        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        spawn1 = wczytajLokacje(yaml, "spawn1");
        spawn2 = wczytajLokacje(yaml, "spawn2");

        if (yaml.contains("loadout.contents")) {
            loadoutContents = listaNaTablice(yaml.getList("loadout.contents"));
        }
        if (yaml.contains("loadout.armor")) {
            loadoutArmor = listaNaTablice(yaml.getList("loadout.armor"));
        }
        Object off = yaml.get("loadout.offhand");
        if (off instanceof ItemStack item) {
            loadoutOffhand = item;
        }
    }

    private Location wczytajLokacje(YamlConfiguration yaml, String klucz) {
        if (!yaml.contains(klucz + ".swiat")) {
            return null;
        }
        String worldName = yaml.getString(klucz + ".swiat");
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        if (world == null) {
            plugin.getLogger().warning("Nie znaleziono świata " + worldName + " dla " + klucz);
            return null;
        }
        return new Location(
                world,
                yaml.getDouble(klucz + ".x"),
                yaml.getDouble(klucz + ".y"),
                yaml.getDouble(klucz + ".z"),
                (float) yaml.getDouble(klucz + ".yaw"),
                (float) yaml.getDouble(klucz + ".pitch")
        );
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

        zapiszLokacje(yaml, "spawn1", spawn1);
        zapiszLokacje(yaml, "spawn2", spawn2);

        if (loadoutContents != null) {
            yaml.set("loadout.contents", zamienNaListe(loadoutContents));
        }
        if (loadoutArmor != null) {
            yaml.set("loadout.armor", zamienNaListe(loadoutArmor));
        }
        if (loadoutOffhand != null) {
            yaml.set("loadout.offhand", loadoutOffhand);
        }

        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie udało się zapisać arena.yml: " + e.getMessage());
        }
    }

    private void zapiszLokacje(YamlConfiguration yaml, String klucz, Location loc) {
        if (loc == null) {
            return;
        }
        yaml.set(klucz + ".swiat", loc.getWorld().getName());
        yaml.set(klucz + ".x", loc.getX());
        yaml.set(klucz + ".y", loc.getY());
        yaml.set(klucz + ".z", loc.getZ());
        yaml.set(klucz + ".yaw", loc.getYaw());
        yaml.set(klucz + ".pitch", loc.getPitch());
    }

    private List<ItemStack> zamienNaListe(ItemStack[] tablica) {
        List<ItemStack> lista = new ArrayList<>();
        for (ItemStack item : tablica) {
            // Puste sloty zapisujemy jako AIR, żeby zachować pozycje przedmiotów w ekwipunku.
            lista.add(item == null ? new ItemStack(Material.AIR) : item);
        }
        return lista;
    }
}
