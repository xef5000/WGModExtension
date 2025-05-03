package ca.xef5000.plugin;

import ca.xef5000.plugin.flags.ListStringFlag;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 *
 * @author Creepinson101
 *
**/
public class WGModPlugin extends JavaPlugin implements Listener {

	// Feel free to change this to your own plugin's name and color of your choice.
	public static final String CHAT_PREFIX = ChatColor.AQUA + "WGMod Plugin";

	private static WGModPlugin plugin; // This is a static plugin instance that is private. Use getPlugin() as seen
									// further below.

	private ListStringFlag MOD_BREAK_FLAG;

	PluginDescriptionFile pdfFile; // plugin.yml

	// Called when the plugin is disabled, such as when you reload the server.
	public void onDisable() {

	}

	public static WGModPlugin getPlugin() { // getter for the static plugin instance
		return plugin;
	}

	// Called when the plugin is enabled. It is used to set up variables and to register things such as commands.
	@Override
	public void onEnable() {
		plugin = this; // Use 'this', not getPlugin(WGModPlugin.class)

		// Ensure WorldGuard is present
		if (getServer().getPluginManager().getPlugin("WorldGuard") == null) {
			getLogger().severe("WorldGuard not found! Disabling WGModPlugin.");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		// Register the flag (moved from onLoad for simplicity, ensure dependency in plugin.yml)
		try {
			FlagRegistry registry = WorldGuardPlugin.inst().getFlagRegistry();
			// Define the flag
			ListStringFlag customFlag = new ListStringFlag("mod-break");
			registry.register(customFlag);
			MOD_BREAK_FLAG = customFlag; // Assign to member variable
			getLogger().info("Registered custom flag: mod-break");
		} catch (FlagConflictException e) {
			getLogger().warning("Flag 'mod-break' already registered. Attempting to retrieve it.");
			FlagRegistry registry = WorldGuardPlugin.inst().getFlagRegistry();
			// Flag already exists, retrieve it. Make sure the retrieved flag is of the expected type.
			com.sk89q.worldguard.protection.flags.Flag<?> existingFlag = registry.get("mod-break");
			if (existingFlag instanceof ListStringFlag) {
				MOD_BREAK_FLAG = (ListStringFlag) existingFlag;
				getLogger().info("Successfully retrieved existing flag: mod-break");
			} else {
				getLogger().severe("Flag 'mod-break' already exists but is not a ListStringFlag! Plugin conflict?");
				MOD_BREAK_FLAG = null; // Ensure it's null if unusable
				// Maybe disable the listener or plugin?
			}
		} catch (Exception e) {
			getLogger().severe("An unexpected error occurred registering the 'mod-break' flag:");
			e.printStackTrace();
			// Maybe disable plugin
			getServer().getPluginManager().disablePlugin(this);
			return;
		}

		// Register listener if flag registration was successful
		if (MOD_BREAK_FLAG != null) {
			Bukkit.getPluginManager().registerEvents(this, this);
			getLogger().info("Event listener registered.");
		} else {
			getLogger().severe("Could not register or retrieve the 'mod-break' flag. Event listener NOT registered.");
		}

		getLogger().info("WGModPlugin has been enabled!");
	}

	/**
	 * Event handler for block breaking
	 * This will check if a block is in the mod-break flag list and allow breaking it
	 * even if WorldGuard would normally deny it
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void onBlockBreak(BlockBreakEvent event) {
		// If the flag wasn't loaded correctly, don't process
		if (MOD_BREAK_FLAG == null) {
			return;
		}

		Block block = event.getBlock();
		Player player = event.getPlayer();

		// Get the block's material name - this will be something like "STONE" for vanilla blocks
		String blockName = block.getType().toString();

		// For modded blocks, we need to get the full name with namespace
		// In 1.12.2, we can use the block data to check if it's a modded block
		// If it's a modded block, the name will be something like "minecraft:stone" or "minepiece:chromium_block"
		String fullBlockName = getFullBlockName(block);

		// Get WorldGuard's region container - using the legacy API for 1.12.2
		com.sk89q.worldguard.bukkit.RegionContainer container = WorldGuardPlugin.inst().getRegionContainer();
		com.sk89q.worldguard.bukkit.RegionQuery query = container.createQuery();

		// Get the applicable regions at this location
		ApplicableRegionSet regions = query.getApplicableRegions(block.getLocation());

		// Check if WorldGuard would allow breaking this block
		com.sk89q.worldguard.LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
		if (regions.testState(localPlayer, DefaultFlag.BUILD)) {
			// WorldGuard allows breaking, so we don't need to do anything
			return;
		}

		// WorldGuard would deny breaking, so check our custom flag
		List<String> allowedBlocks = regions.queryValue(localPlayer, MOD_BREAK_FLAG);
		if (allowedBlocks == null || allowedBlocks.isEmpty()) {
			// No blocks are allowed by our flag
			return;
		}

		// Check if the block is in our allowed list
		// First check the full name (with namespace)
		if (allowedBlocks.contains(fullBlockName)) {
			// Block is allowed by our flag, so allow breaking
			event.setCancelled(false);
			return;
		}

		// Then check just the material name (for backward compatibility)
		if (allowedBlocks.contains(blockName)) {
			// Block is allowed by our flag, so allow breaking
			event.setCancelled(false);
		}
	}

	/**
	 * Get the full name of a block, including namespace
	 * For vanilla blocks, this will be "minecraft:block_name"
	 * For modded blocks, this will be "modid:block_name"
	 *
	 * @param block The block to get the name of
	 * @return The full name of the block
	 */
	private String getFullBlockName(Block block) {
		try {
			// For 1.12.2, we need to use reflection to access Forge/modded block data
			// First try to get the CraftBlock class
			Class<?> craftBlockClass = block.getClass();

			// Try to get the NMS block
			Object nmsBlock = null;
			try {
				// Try CraftBlock.getNMSBlock() method
				nmsBlock = craftBlockClass.getMethod("getNMSBlock").invoke(block);
			} catch (Exception e) {
				try {
					// Try CraftBlock.getHandle() method
					nmsBlock = craftBlockClass.getMethod("getHandle").invoke(block);
				} catch (Exception e2) {
					// If both fail, try to get the block data
					Object blockData = craftBlockClass.getMethod("getData").invoke(block);
					nmsBlock = blockData.getClass().getMethod("getBlock").invoke(blockData);
				}
			}

			if (nmsBlock != null) {
				// Try to get the registry name from the NMS block
				try {
					// For Forge blocks, try to get the registry name
					Object registryName = nmsBlock.getClass().getMethod("getRegistryName").invoke(nmsBlock);
					if (registryName != null) {
						return registryName.toString();
					}
				} catch (Exception e) {
					// Try alternative methods for getting the mod ID and name
					try {
						String modId = (String) nmsBlock.getClass().getMethod("getModId").invoke(nmsBlock);
						String blockName = (String) nmsBlock.getClass().getMethod("getName").invoke(nmsBlock);
						if (modId != null && blockName != null) {
							return modId + ":" + blockName;
						}
					} catch (Exception e2) {
						// If all else fails, try to get the unlocalizedName
						try {
							String unlocalizedName = (String) nmsBlock.getClass().getMethod("getUnlocalizedName").invoke(nmsBlock);
							if (unlocalizedName != null && unlocalizedName.contains(".")) {
								// Unlocalized names are often in the format "tile.modid:blockname"
								String[] parts = unlocalizedName.split("\\.");
								if (parts.length > 1 && parts[1].contains(":")) {
									return parts[1];
								}
							}
						} catch (Exception e3) {
							// Ignore and fall back
						}
					}
				}
			}
		} catch (Exception e) {
			// Log the exception at debug level
			if (isDebugEnabled()) {
				getLogger().info("Debug: Exception getting full block name: " + e.getMessage());
			}
		}

		// If all reflection attempts fail, fall back to the material name
		// For vanilla blocks, return with minecraft namespace
		String materialName = block.getType().toString().toLowerCase();
		return "minecraft:" + materialName;
	}

	/**
	 * Check if debug mode is enabled
	 * @return true if debug mode is enabled
	 */
	private boolean isDebugEnabled() {
		return false; // Change to true to enable debug logging
	}

	/**
	 * Handle commands for the plugin
	 */
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (command.getName().equalsIgnoreCase("wgmod")) {
			// Check permission
			if (!sender.hasPermission("wgmod.admin")) {
				sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
				return true;
			}

			// Handle subcommands
			if (args.length > 0) {
				if (args[0].equalsIgnoreCase("info")) {
					// Show plugin info
					sender.sendMessage(ChatColor.AQUA + "=== WGMod Plugin Info ===");
					sender.sendMessage(ChatColor.GREEN + "Version: " + getDescription().getVersion());
					sender.sendMessage(ChatColor.GREEN + "Author: " + getDescription().getAuthors().get(0));
					sender.sendMessage(ChatColor.GREEN + "Flag registered: " + (MOD_BREAK_FLAG != null ? "Yes" : "No"));
					return true;
				} else if (args[0].equalsIgnoreCase("block") && sender instanceof Player) {
					// Show info about the block the player is looking at
					Player player = (Player) sender;
					Block targetBlock = player.getTargetBlock(null, 5);
					if (targetBlock != null && targetBlock.getType() != Material.AIR) {
						String fullName = getFullBlockName(targetBlock);
						String materialName = targetBlock.getType().toString();

						sender.sendMessage(ChatColor.AQUA + "=== Block Info ===");
						sender.sendMessage(ChatColor.GREEN + "Material: " + materialName);
						sender.sendMessage(ChatColor.GREEN + "Full name: " + fullName);
						sender.sendMessage(ChatColor.GREEN + "Location: " + targetBlock.getLocation().getBlockX() + ", " +
							targetBlock.getLocation().getBlockY() + ", " + targetBlock.getLocation().getBlockZ());

						// Show how to add this block to a region's mod-break flag
						sender.sendMessage(ChatColor.YELLOW + "To allow breaking this block in a WorldGuard region:");
						sender.sendMessage(ChatColor.YELLOW + "/rg flag [region] mod-break " + fullName);
					} else {
						sender.sendMessage(ChatColor.RED + "You must be looking at a block.");
					}
					return true;
				} else if (args[0].equalsIgnoreCase("help")) {
					// Show help
					sender.sendMessage(ChatColor.AQUA + "=== WGMod Commands ===");
					sender.sendMessage(ChatColor.GREEN + "/wgmod info" + ChatColor.WHITE + " - Show plugin info");
					sender.sendMessage(ChatColor.GREEN + "/wgmod block" + ChatColor.WHITE + " - Show info about the block you're looking at");
					sender.sendMessage(ChatColor.GREEN + "/wgmod help" + ChatColor.WHITE + " - Show this help message");
					return true;
				}
			}

			// Default to showing help
			sender.sendMessage(ChatColor.AQUA + "=== WGMod Commands ===");
			sender.sendMessage(ChatColor.GREEN + "/wgmod info" + ChatColor.WHITE + " - Show plugin info");
			sender.sendMessage(ChatColor.GREEN + "/wgmod block" + ChatColor.WHITE + " - Show info about the block you're looking at");
			sender.sendMessage(ChatColor.GREEN + "/wgmod help" + ChatColor.WHITE + " - Show this help message");
			return true;
		}

		return false;
	}
}
