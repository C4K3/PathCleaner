package net.simpvp.PathCleaner;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public class Cleanup {

	public static HashSet<Region> check = null;

	public static void run() {
		HashSet<Region> safe = SQLite.get_safe_regions();
		if (safe == null) {
			throw new RuntimeException("Could not get safe regions");
		}
		long safe_count = safe.size();

		ArrayList<World> active_worlds = new ArrayList<>();
		for (String w : PathCleaner.instance.getConfig().getStringList("active")) {
			PathCleaner.instance.getLogger().info(String.format("Running cleanup on world %s", w));
			active_worlds.add(PathCleaner.instance.getServer().getWorld(w));
		}

		for (World w : active_worlds) {
			w.save();
		}

		// Start with all regions saved to disk
		HashSet<Region> regions = new HashSet<>();
		for (World w : active_worlds) {
			get_regions(regions, w);
		}
		long total_region_count = regions.size();

		// Remove all regions marked as safe in the database
		regions.removeAll(safe);

		long non_safe_region_count = regions.size();

		// Now left over only with regions not marked as safe, and that
		// do not have a high inhabited time
		PathCleaner.instance.getLogger().info(String.format("Found %d total regions, %d safe regions, %d marked for deletion ", total_region_count, safe_count, regions.size()));

		// Ensure we unload all worlds before we start editing them
		//for (World w : active_worlds) {
		//	PathCleaner.instance.getServer().unloadWorld(w, true);
		//}

		check = regions;
		
		check_regions();

		//PathCleaner.instance.getLogger().info("Cleanup complete, disabling 'active'");
		//PathCleaner.instance.getConfig().set("active", new ArrayList<>());
		//PathCleaner.instance.saveConfig();
	}

	private static void check_regions() {
		PathCleaner.instance.getLogger().info(String.format("Need to check %d regions", check.size()));

		for (int i = 0; i < 10; i++) {
			if (check.size() == 0) {
				PathCleaner.instance.getLogger().info("All done checking regions");
				return;
			}

			Region r = check.iterator().next();

			PathCleaner.instance.getLogger().info(String.format("Checking region %s", r));
			int min_x = r.x << 5;
			int min_z = r.z << 5;

			int max_x = min_x + 32;
			int max_z = min_z + 32;

			for (int x = min_x; x < max_x; x++) {
				for (int z = min_z; z < max_z; z++) {
					r.world_obj.loadChunk(x, z, false);
				}
			}

			for (int x = min_x; x < max_x; x++) {
				for (int z = min_z; z < max_z; z++) {
					r.world_obj.unloadChunkRequest(x, z);
				}
			}

			check.remove(r);
		}


		new BukkitRunnable() {
			@Override
			public void run() {
				check_regions();

			}
		}.runTaskLater(PathCleaner.instance, 1L);
	}

	private static Pattern REGION_FILE_REGEX = Pattern.compile("^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca$");

	/**
	 * Scans the directory for the given World and returns the list of
	 * region files.
	 */
	private static void get_regions(HashSet<Region> regions, World world) {

		File dir = world.getWorldFolder();

		PathCleaner.instance.getLogger().info(String.format("World %s has dir %s", world.getName(), dir.toString()));

		File region_dir = null;

		File tmp = new File(dir, "region");
		if (tmp.exists()) {
			region_dir = tmp;
		}

		if (region_dir == null) {
			tmp = new File(dir, "DIM1");
			tmp = new File(tmp, "region");
			if (tmp.exists()) {
				region_dir = tmp;
			}
		}

		if (region_dir == null) {
			tmp = new File(dir, "DIM-1");
			tmp = new File(tmp, "region");
			if (tmp.exists()) {
				region_dir = tmp;
			}
		}

		if (region_dir == null) {
			throw new RuntimeException(String.format("Unable to find region directory for world %s with dir", world.getName(), dir.toString()));
		}

		PathCleaner.instance.getLogger().info(String.format("Found region dir for %s at %s", world.getName(), region_dir.toString()));

		for (File f : region_dir.listFiles()) {
			Matcher m = REGION_FILE_REGEX.matcher(f.getName());
			if (!m.find()) {
				PathCleaner.instance.getLogger().info(String.format("Region file %s does not match regex", f.toString()));
				continue;
			}

			int x, z;
			try {
				x = Integer.parseInt(m.group(1));
				z = Integer.parseInt(m.group(2));
			} catch (Exception e) {
				// unreachable
				e.printStackTrace();
				throw new RuntimeException(String.format("Invalid filename %s had invalid integers", f.toString()));
			}

			Region r = new Region(world, x, z, f);
			regions.add(r);
			//PathCleaner.instance.getLogger().info(String.format("Found region file %s %s %s %s", f.toString(), f.getName(), x, z));
		}
	}

	/**
	 * Mark all existing regions safe.
	 */
	public static void init_worlds() {
		PathCleaner.instance.getLogger().info("Initializing all worlds");
		HashSet<Region> regions = new HashSet<>();
		for (World w : PathCleaner.instance.getServer().getWorlds()) {
			get_regions(regions, w);
		}
		PathCleaner.instance.getLogger().info(String.format("World initializer found %d regions", regions.size()));

		SQLite.synchronous_mode(false);

		for (Region r : regions) {
			SQLite.insert_safe_region(r);
		}

		SQLite.synchronous_mode(true);

		PathCleaner.instance.reloadConfig();
		PathCleaner.instance.getConfig().set("initWorlds", false);
		PathCleaner.instance.saveConfig();
		PathCleaner.instance.getLogger().info("Initialization complete");
	}
}
