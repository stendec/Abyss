package me.stendec.abyss.commands;

import me.stendec.abyss.ABCommand;
import me.stendec.abyss.ABPortal;
import me.stendec.abyss.AbyssPlugin;
import me.stendec.abyss.util.ColorBuilder;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.ArrayList;

public class DeleteCommand extends ABCommand {

    public DeleteCommand(final AbyssPlugin plugin) {
        super(plugin, "delete");

        color = ChatColor.RED;
        require_portal = true;
        maximumArguments = 0;

        description = "Delete the targeted portal.";
    }

    public boolean run(final CommandSender sender, final Event event, final Block target, ABPortal portal, final ArrayList<String> args) throws NeedsHelp {
        if ( (sender instanceof Player) && !portal.canManipulate((Player) sender) ) {
            t().red("Permission Denied").send(sender);
            return false;
        }

        ColorBuilder out = portal.getDisplayName();
        if (!plugin.getManager().destroy(portal)) {
            out.red(" could not be deleted.").send(sender);
            return false;
        }

        out.darkgreen(" was deleted successfully.").send(sender);
        return true;
    }
}
