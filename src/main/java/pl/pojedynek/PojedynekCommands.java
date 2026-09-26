package pl.pojedynek;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class PojedynekCommands implements CommandExecutor {

    private final ArenaManager arena;
    private final DuelManager duel;

    public PojedynekCommands(ArenaManager arena, DuelManager duel) {
        this.arena = arena;
        this.duel = duel;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        return switch (command.getName().toLowerCase()) {
            case "pojedynek" -> wyzwij(sender, args);
            case "pojedynekakceptuj" -> akceptuj(sender);
            case "pojedynekodrzuc" -> odrzuc(sender);
            case "ustawpojedynek" -> ustawPojedynek(sender, args);
            case "pojedynekustawmiejsce1" -> ustawMiejsce1(sender);
            case "pojedynekustawmiejsce2" -> ustawMiejsce2(sender);
            case "pojedynekustawitemy" -> ustawItemy(sender);
            default -> false;
        };
    }

    private boolean wyzwij(CommandSender sender, String[] args) {
        if (!(sender instanceof Player wyzywajacy)) {
            sender.sendMessage(Component.text("Ta komenda jest tylko dla graczy.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            wyzywajacy.sendMessage(Component.text("Użycie: /pojedynek <gracz>", NamedTextColor.YELLOW));
            return true;
        }

        if (!arena.arenaGotowa() || !arena.itemyGotowe()) {
            wyzywajacy.sendMessage(Component.text(
                    "Arena pojedynków nie jest jeszcze skonfigurowana przez admina.", NamedTextColor.RED));
            return true;
        }

        Player cel = Bukkit.getPlayerExact(args[0]);
        if (cel == null) {
            wyzywajacy.sendMessage(Component.text("Nie znaleziono gracza \"" + args[0] + "\" (musi być online).", NamedTextColor.RED));
            return true;
        }
        if (cel.getUniqueId().equals(wyzywajacy.getUniqueId())) {
            wyzywajacy.sendMessage(Component.text("Nie możesz wyzwać samego siebie.", NamedTextColor.RED));
            return true;
        }
        if (duel.jestWPojedynku(wyzywajacy.getUniqueId())) {
            wyzywajacy.sendMessage(Component.text("Jesteś już w trakcie pojedynku.", NamedTextColor.RED));
            return true;
        }
        if (duel.jestWPojedynku(cel.getUniqueId())) {
            wyzywajacy.sendMessage(Component.text(cel.getName() + " jest już w trakcie innego pojedynku.", NamedTextColor.RED));
            return true;
        }

        duel.wyzwij(wyzywajacy, cel);

        wyzywajacy.sendMessage(Component.text("Wysłano wyzwanie do " + cel.getName() + ".", NamedTextColor.GREEN));
        cel.sendMessage(Component.text(
                wyzywajacy.getName() + " wyzywa Cię na pojedynek! Wpisz /pojedynekakceptuj albo /pojedynekodrzuc (masz 60 s).",
                NamedTextColor.GOLD));
        return true;
    }

    private boolean akceptuj(CommandSender sender) {
        if (!(sender instanceof Player cel)) {
            sender.sendMessage(Component.text("Ta komenda jest tylko dla graczy.", NamedTextColor.RED));
            return true;
        }

        UUID wyzywajacyUuid = duel.pobierzWyzywajacego(cel);
        if (wyzywajacyUuid == null) {
            cel.sendMessage(Component.text("Nie masz żadnego oczekującego wyzwania.", NamedTextColor.RED));
            return true;
        }

        Player wyzywajacy = Bukkit.getPlayer(wyzywajacyUuid);
        if (wyzywajacy == null) {
            cel.sendMessage(Component.text("Gracz, który Cię wyzwał, jest już offline.", NamedTextColor.RED));
            duel.usunWyzwanie(cel);
            return true;
        }

        if (duel.jestWPojedynku(wyzywajacy.getUniqueId()) || duel.jestWPojedynku(cel.getUniqueId())) {
            cel.sendMessage(Component.text("Jeden z was jest już w trakcie innego pojedynku.", NamedTextColor.RED));
            duel.usunWyzwanie(cel);
            return true;
        }

        boolean ok = duel.rozpocznijPojedynek(wyzywajacy, cel);
        if (!ok) {
            cel.sendMessage(Component.text(
                    "Nie udało się rozpocząć pojedynku - arena lub itemy nie są skonfigurowane.", NamedTextColor.RED));
            return true;
        }

        wyzywajacy.sendMessage(Component.text(cel.getName() + " zaakceptował Twoje wyzwanie! Walka się zaczyna.", NamedTextColor.GREEN));
        cel.sendMessage(Component.text("Zaakceptowano pojedynek z " + wyzywajacy.getName() + "! Walka się zaczyna.", NamedTextColor.GREEN));
        return true;
    }

    private boolean odrzuc(CommandSender sender) {
        if (!(sender instanceof Player cel)) {
            sender.sendMessage(Component.text("Ta komenda jest tylko dla graczy.", NamedTextColor.RED));
            return true;
        }

        UUID wyzywajacyUuid = duel.pobierzWyzywajacego(cel);
        if (wyzywajacyUuid == null) {
            cel.sendMessage(Component.text("Nie masz żadnego oczekującego wyzwania.", NamedTextColor.RED));
            return true;
        }

        duel.usunWyzwanie(cel);
        cel.sendMessage(Component.text("Odrzucono wyzwanie.", NamedTextColor.YELLOW));

        Player wyzywajacy = Bukkit.getPlayer(wyzywajacyUuid);
        if (wyzywajacy != null) {
            wyzywajacy.sendMessage(Component.text(cel.getName() + " odrzucił Twoje wyzwanie.", NamedTextColor.RED));
        }
        return true;
    }

    private boolean ustawPojedynek(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Użycie: /ustawpojedynek <gracz1> <gracz2>", NamedTextColor.YELLOW));
            return true;
        }

        Player p1 = Bukkit.getPlayerExact(args[0]);
        Player p2 = Bukkit.getPlayerExact(args[1]);

        if (p1 == null || p2 == null) {
            sender.sendMessage(Component.text("Obaj gracze muszą być online.", NamedTextColor.RED));
            return true;
        }
        if (p1.getUniqueId().equals(p2.getUniqueId())) {
            sender.sendMessage(Component.text("To musi być dwóch różnych graczy.", NamedTextColor.RED));
            return true;
        }
        if (duel.jestWPojedynku(p1.getUniqueId()) || duel.jestWPojedynku(p2.getUniqueId())) {
            sender.sendMessage(Component.text("Jeden z tych graczy jest już w trakcie pojedynku.", NamedTextColor.RED));
            return true;
        }

        boolean ok = duel.rozpocznijPojedynek(p1, p2);
        if (!ok) {
            sender.sendMessage(Component.text(
                    "Nie udało się rozpocząć pojedynku - ustaw najpierw arenę i itemy.", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("Rozpoczęto pojedynek: " + p1.getName() + " vs " + p2.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean ustawMiejsce1(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Ta komenda jest tylko dla graczy.", NamedTextColor.RED));
            return true;
        }
        arena.ustawSpawn1(player.getLocation());
        player.sendMessage(Component.text("Ustawiono miejsce pojawienia się gracza 1 na Twojej pozycji.", NamedTextColor.GREEN));
        return true;
    }

    private boolean ustawMiejsce2(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Ta komenda jest tylko dla graczy.", NamedTextColor.RED));
            return true;
        }
        arena.ustawSpawn2(player.getLocation());
        player.sendMessage(Component.text("Ustawiono miejsce pojawienia się gracza 2 na Twojej pozycji.", NamedTextColor.GREEN));
        return true;
    }

    private boolean ustawItemy(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Ta komenda jest tylko dla graczy.", NamedTextColor.RED));
            return true;
        }
        arena.ustawItemy(player.getInventory());
        player.sendMessage(Component.text(
                "Ustawiono zestaw itemów na pojedynek na podstawie Twojego aktualnego ekwipunku.", NamedTextColor.GREEN));
        return true;
    }
}
