package pl.pojedynek;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PojedynekPlugin extends JavaPlugin {

    private ArenaManager arena;
    private DuelManager duel;

    @Override
    public void onEnable() {
        arena = new ArenaManager(this);
        arena.load();

        duel = new DuelManager(this, arena);
        duel.load();

        getServer().getPluginManager().registerEvents(new PojedynekListener(duel), this);

        PojedynekCommands commands = new PojedynekCommands(arena, duel);
        String[] nazwyKomend = {
                "pojedynek", "pojedynekakceptuj", "pojedynekodrzuc", "ustawpojedynek",
                "pojedynekustawmiejsce1", "pojedynekustawmiejsce2", "pojedynekustawitemy"
        };
        for (String name : nazwyKomend) {
            PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(commands);
            }
        }

        getLogger().info("Pojedynek załadowany. Arena gotowa: " + arena.arenaGotowa() + ", itemy gotowe: " + arena.itemyGotowe());
    }

    @Override
    public void onDisable() {
        if (duel != null) {
            duel.save();
        }
    }
}
