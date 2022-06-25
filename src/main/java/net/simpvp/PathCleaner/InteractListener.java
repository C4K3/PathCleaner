package net.simpvp.PathCleaner;

import java.util.HashSet;

import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/**
 * Listens for events that indicate that a given region was modified by
 * players.
 */
public class InteractListener implements Listener {

	private static HashSet<Region> seen_regions = new HashSet<>();

	private void add_region(Block block) {
		String world = block.getWorld().getName();
		int x = block.getX() >> 9;
		int z = block.getZ() >> 9;

		Region r = new Region(world, x, z);

		if (!seen_regions.add(r)) {
			return;
		}

		//PathCleaner.instance.getLogger().info(String.format("Setting safe region %s", r.toString()));

		if (!SQLite.insert_safe_region(r)) {
			PathCleaner.initialized = false;
			seen_regions.remove(r);
			PathCleaner.instance.getLogger().severe(String.format("Error saving safe region %s", r.toString()));
		}
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void onPlayerBlockPlace(BlockPlaceEvent event) {
		add_region(event.getBlockPlaced());
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void onPlayerBreakBlock(BlockBreakEvent event) {
		add_region(event.getBlock());
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void onPlayerDeath(PlayerDeathEvent event) {
		add_region(event.getEntity().getLocation().getBlock());
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
	public void onPlayerInteract(PlayerInteractEvent event) {
		add_region(event.getClickedBlock());
	}

	@EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=false)
	public void onChunkUnload(ChunkUnloadEvent event) {
		Chunk c = event.getChunk();
		//PathCleaner.instance.getLogger().info("Unload time " + c.getInhabitedTime());
		if (c.getInhabitedTime() > PathCleaner.inhabited_threshold) {
			add_region(c.getBlock(0, 0, 0));
		}
	}
}
