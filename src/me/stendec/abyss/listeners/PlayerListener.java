package me.stendec.abyss.listeners;

import me.stendec.abyss.*;
import me.stendec.abyss.util.ColorBuilder;
import me.stendec.abyss.util.ParseUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.Dye;
import org.bukkit.material.Wool;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginManager;

import java.util.*;

public class PlayerListener implements Listener {

    private final AbyssPlugin plugin;

    public PlayerListener(AbyssPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }


    ///////////////////////////////////////////////////////////////////////////
    // Utilities
    ///////////////////////////////////////////////////////////////////////////

    private static ColorBuilder t() {
        return new ColorBuilder();
    }

    private static ColorBuilder t(final String string) {
        return new ColorBuilder(string);
    }


    private static HashSet<Byte> crap;

    static {
        crap = new HashSet<Byte>();
        crap.add((byte) Material.AIR.getId());
        crap.add((byte) Material.STONE_BUTTON.getId());
        crap.add((byte) Material.WOOD_BUTTON.getId());
    }

    private Block getLiquid(Player player) {
        for(Block b: player.getLineOfSight(crap, 6)) {
            plugin.getLogger().info("Block: " + b.getType().name() + " @ " + b.getLocation().toVector());
            if (AbyssPlugin.validLiquid(b))
                return b;
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////
    // Portal Use
    ///////////////////////////////////////////////////////////////////////////

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(final PlayerMoveEvent event) {
        final Player player = event.getPlayer();

        // Don't handle a sneaking player, or a player with no permissions.
        if (player.isSneaking() || !player.hasPermission("abyss.use"))
            return;

        // Do portal logic.
        final Location to = plugin.usePortal(player, event.getFrom(), event.getTo());
        if ( to == null )
            return;

        // Set the event location.
        event.setFrom(to);
        event.setTo(to);
    }


    ///////////////////////////////////////////////////////////////////////////
    // Buckets
    ///////////////////////////////////////////////////////////////////////////

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketFill(final PlayerBucketFillEvent event) {
        final Block clicked = event.getBlockClicked();
        if ( clicked == null )
            return;

        final Block block = clicked.getRelative(event.getBlockFace());
        final int y = block.getY();

        // See if there's a portal at this location. We don't just use the
        // protectBlock method because we don't need to search for every
        // portal. Portals can't share water blocks.
        final ABPortal portal = plugin.getManager().getAt(block);
        if ( portal == null )
            return;

        // See if we're in the important layers.
        final int py = portal.getLocation().getBlockY();
        if ( y <= py && y > py - 2 ) {
            event.setCancelled(true);
            return;
        }
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketEmpty(final PlayerBucketEmptyEvent event) {
        // Let the player place a water bucket. We don't care if they want to fix the flow.
        if ( event.getBucket() == Material.WATER_BUCKET )
            return;

        final Block clicked = event.getBlockClicked();
        if ( clicked == null )
            return;

        final Block block = clicked.getRelative(event.getBlockFace());
        final int y = block.getY();

        // See if there's a portal at this location.
        final ABPortal portal = plugin.getManager().getAt(block);
        if ( portal == null )
            return;

        // See if we're in the important layers.
        final int py = portal.getLocation().getBlockY();
        if ( y <= py && y > py - 2 ) {
            event.setCancelled(true);
            return;
        }
    }


    ///////////////////////////////////////////////////////////////////////////
    // Portal Configuration
    ///////////////////////////////////////////////////////////////////////////

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(final PlayerInteractEntityEvent event) {
        final Player player = event.getPlayer();
        final Entity entity = event.getRightClicked();

        // Only handle clicks on item frames by players with the use permission.
        if (!(entity instanceof ItemFrame) || !player.hasPermission("abyss.use"))
            return;

        final ItemFrame frame = (ItemFrame) entity;
        final PortalManager manager = plugin.getManager();

        // Try getting the portal for the entity.
        final ABPortal portal = manager.getByFrame(frame);
        if (portal == null)
            return;

        // Get the info object. If we don't have it, abort.
        final FrameInfo info = portal.frameIDs.get(frame.getUniqueId());
        if (info == null)
            return;

        // It's our event, so cancel it.
        event.setCancelled(true);

        if (! portal.canManipulate(player) ) {
            t().red("Access Denied").send(player);
            return;
        }

        final ItemStack item = player.getItemInHand();
        final Material material = item.getType();

        // Get a color, for use with color, ID, and Destination frames.
        DyeColor color = null;

        if (item.getType() == Material.WOOL)
            color = ((Wool) item.getData()).getColor();
        else if (item.getType() == Material.INK_SACK)
            color = ((Dye) item.getData()).getColor();


        // Check for the Modifier wand.
        final String tool = plugin.validatePortalWand(item);
        final boolean mtool = tool != null && (tool.equals("mod") || tool.equals("modifier"));
        if (mtool && info.type != FrameInfo.Frame.MOD) {
            t().red("You must click a modifier frame to use the modifier wand.").send(player);
            return;
        }

        try {
            if ( info.type == FrameInfo.Frame.NETWORK) {
                if ( material != Material.AIR )
                    portal.setNetwork(item);

            } else if ( info.type == FrameInfo.Frame.COLOR) {
                if (color == null)
                    portal.modColor(-1);
                else
                    portal.setColor(color);

            } else if (info.type == FrameInfo.Frame.MOD) {
                if ( material == Material.AIR )
                    return;

                if ( mtool ) {
                    if (frame.getItem().getType() == Material.AIR)
                        throw new IllegalArgumentException("Cannot configure empty modifier.");

                    // Get the ModInfo instance for this modifier.
                    final ModInfo minfo = portal.getModFromFrame(frame);
                    if ( minfo == null )
                        throw new IllegalArgumentException("Error getting modifier information.");

                    if ( ! item.getItemMeta().hasLore() )
                        throw new IllegalArgumentException("This modifier wand has no configuration to apply.");

                    final Map<String, String> config = ParseUtils.tokenizeLore(item.getItemMeta().getLore());
                    if (config != null && config.size() > 0)
                        for(final Map.Entry<String,String> entry: config.entrySet()) {
                            String key = entry.getKey();
                            if ( key.startsWith("-") ) {
                                key = key.substring(1);
                                if (minfo.flags.containsKey(key))
                                    minfo.flags.remove(key);
                            } else {
                                minfo.flags.put(key, entry.getValue());
                            }
                        }

                    t().gold("Portal [").yellow(portal.getName()).gold("]").
                            white("'s modifier was updated successfully.").send(player);
                    return;
                }

                // If the frame already has an item, abort.
                if (frame.getItem().getType() != Material.AIR) {
                    t().red("There's already a modifier in that frame.").send(player);
                    return;
                }

                // Try setting this mod item.
                if (!portal.setMod(player, frame, item))
                    throw new IllegalArgumentException(material.name() + " is not a valid portal modifier.");

                // If the player is in CREATIVE, don't remove anything.
                if (player.getGameMode() == GameMode.CREATIVE)
                    return;

                // Remove just the one.
                item.setAmount(item.getAmount() - 1);
                player.setItemInHand(item);

            } else if (info.type == FrameInfo.Frame.ID1) {
                if ( color == null )
                    portal.modID(-1, true);
                else
                    portal.setPartialID(color.getWoolData() + 1, true);

            } else if (info.type == FrameInfo.Frame.ID2) {
                if ( color == null )
                    portal.modID(-1, false);
                else
                    portal.setPartialID(color.getWoolData() + 1, false);

            } else if (info.type == FrameInfo.Frame.DEST1) {
                if ( color == null )
                    portal.modDestination(-1, true);
                else
                    portal.setPartialDestination(color.getWoolData() + 1, true);

            } else if (info.type == FrameInfo.Frame.DEST2) {
                if ( color == null )
                    portal.modDestination(-1, false);
                else
                    portal.setPartialDestination(color.getWoolData() + 1, false);
            }
        } catch(IllegalArgumentException ex) {
            t().red("Error Configuring Portal").lf().
                gray("    ").append(ex.getMessage()).send(player);
        }

    }

    ///////////////////////////////////////////////////////////////////////////
    // Portal Tool
    ///////////////////////////////////////////////////////////////////////////

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR)
            return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // See if we've got a portal wand.
        String tool = plugin.validatePortalWand(item);
        if (tool == null)
            return;

        // We handle all Portal Wand events.
        event.setCancelled(true);

        // Try to find the command.
        ABCommand cmd = plugin.commands.get(tool);
        if ( cmd == null ) {
            final ArrayList<String> possible = new ArrayList<String>();
            for(final String key: plugin.commands.keySet())
                if ( key.startsWith(tool) )
                    possible.add(key);

            if ( possible.size() > 0 ) {
                // We've got a command, so use it.
                Collections.sort(possible);
                tool = possible.get(0);
                cmd = plugin.commands.get(tool);
            }
        }

        if ( cmd == null ) {
            t().red("Unknown Tool: ").reset(tool).send(player);
            return;
        }

        // Make sure the player has permission to use this tool.
        final PluginManager pm = plugin.getServer().getPluginManager();
        final Permission perm = pm.getPermission("abyss.wand." + tool);
        if ( perm != null && !player.hasPermission(perm) ) {
            t().red("Permission Denied").send(player);
            return;
        }

        // Get the block involved.
        Block block = event.getClickedBlock();
        if ( block == null )
            block = getLiquid(player);
        else
            block = block.getRelative(event.getBlockFace());

        // Get the lore, and check for a usage count and arguments.
        ArrayList<String> args = new ArrayList<String>();
        List<String> lore = item.getItemMeta().getLore();
        int uses = 0;

        if ( lore != null )
            for(final String l: lore) {
                final String plain = ChatColor.stripColor(l).toLowerCase();
                if ( plain.startsWith("remaining uses: ") ) {
                    try {
                        uses = Integer.parseInt(plain.substring(16));
                    } catch(NumberFormatException ex) {
                        t().red("Invalid Remaining Use Count: ").reset(plain).send(player);
                        return;
                    }
                } else {
                    // Do a stupid space split, since that's what Bukkit does.
                    args.addAll(Arrays.asList(l.split("\\s")));
                }
            }

        // Remove empty strings.
        for(Iterator<String> it = args.iterator(); it.hasNext(); )
            if ( it.next().trim().length() == 0 )
                it.remove();

        // If the command needs a portal, try finding one.
        ABPortal portal = plugin.getManager().getAt(block);
        if ( cmd.require_portal && portal == null ) {
            // Try getting a portal from the arguments.
            if ( args.size() > 0 ) {
                final String key = args.remove(0);
                if ( key.length() > 0 && key.charAt(0) == '@' ) {
                    Location loc = null;

                    // First, try getting a player with that name.
                    Player p = plugin.getServer().getPlayer(key.substring(1));
                    if ( p != null )
                        loc = player.getLocation();

                    if ( loc == null && key.contains(",") )
                        loc = ParseUtils.matchLocation(key.substring(1), player.getWorld());

                    // Try using this location as a portal.
                    portal = plugin.getManager().getAt(loc);
                }

                if ( portal == null ) {
                    try {
                        portal = plugin.getManager().getById(UUID.fromString(key));
                    } catch(IllegalArgumentException ex) { }
                }

                if ( portal == null )
                    portal = plugin.getManager().getByName(key);
            }

            if ( portal == null ) {
                t().red("No portal detected.").send(player);
                return;
            }
        }

        // Now, run the command. If not successful, stop now.
        boolean passed = false;
        try {
            passed = cmd.run(player, event, block, portal, args);
        } catch(ABCommand.NeedsHelp ex) {
            return;
        }

        if ( ! passed )
            return;

        // Since it was a success, decrement the uses for our wand.
        if ( uses > 0 && player.getGameMode() != GameMode.CREATIVE ) {
            ItemStack rest = null;

            if ( uses > 1 ) {
                // If it's part of a stack, we'll want to split it up here.
                if ( item.getAmount() > 1 ) {
                    rest = item.clone();
                    rest.setAmount(item.getAmount() - 1);

                    item.setAmount(1);
                }

                for(int i=0; i < lore.size(); i++) {
                    String l = lore.get(i);
                    final String plain = ChatColor.stripColor(l).toLowerCase();
                    if ( ! plain.startsWith("remaining uses: ") )
                        continue;

                    // Replace the uses remaining. Try to preserve color codes.
                    lore.set(i, l.replace(plain.substring(16), Integer.toString(uses - 1)));
                }

                // Apply the new lore.
                final ItemMeta im = item.getItemMeta();
                im.setLore(lore);
                item.setItemMeta(im);

            } else if ( uses == 1 ) {
                // Destroy the wand.
                player.setItemInHand(null);
            }

            // If we've got extra items, add them.
            if ( rest != null ) {
                for(final ItemStack dnf: player.getInventory().addItem(rest).values())
                    player.getWorld().dropItemNaturally(player.getLocation(), dnf);
                player.updateInventory();
            }
        }
    }

}
