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

	/**
	 * Actually run the cleanup (deletion).
	 *
	 * This doesn't directly delete the region files, but rather it moves
	 * them under plugins/PathCleaner, in a directory structure matching
	 * what's found in the real source directories.
	 *
	 * If the deletion looks good, then you can simply delete those
	 * directories. But if any of the regions need to be restored then it
	 * is possible to do so.
	 */
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
		PathCleaner.instance.getLogger().info(String.format("Found %d active regions, %d safe regions, %d marked for deletion ", total_region_count, safe_count, regions.size()));

		// Ensure we unload all worlds before we start editing them
		for (World w : active_worlds) {
			PathCleaner.instance.getServer().unloadWorld(w, true);
		}

		// Clone each world's directory structure in under the target
		// directory, and save each directory in the dir_trees
		// variable. The problem is that we can't be satisfied just
		// copying the region file, there may be other directories
		// containing info for a given region. Luckily these all have
		// the same file name. So for example for r.1.2.mca we want to
		// search for all files named r.1.2.mca in all subdirectories,
		// and then move each file into the corresponding directory in
		// the target directory.
		File plugin_dir = new File("plugins/PathCleaner");
		HashMap<String, ArrayList<Move>> dir_trees = new HashMap<>();
		for (World w : active_worlds) {
			File world_dir = w.getWorldFolder();
			File target_dir = new File(plugin_dir, w.getName());
			ArrayList<Move> dir_tree = new ArrayList<>();
			Move.build(dir_tree, world_dir, target_dir);
			dir_trees.put(w.getName(), dir_tree);
		}

		// Plan all file moves ahead of time in this list. We want to
		// know if there are any conflicts partway through, before we
		// start actually moving files.
		ArrayList<Move> moves = new ArrayList<>();

		for (Region r : regions) {
			String filename = r.filename();
			ArrayList<Move> dir_tree = dir_trees.get(r.world);

			for (Move dir : dir_tree) {
				Move move = new Move(dir, filename);
				if (!move.source.exists()) {
					continue;
				}

				if (move.target.exists()) {
					throw new RuntimeException(String.format("Target file %s already exists", move.target));
				}

				moves.add(move);
			}
		}

		for (Move move : moves) {
			move.move();
		}

		PathCleaner.instance.getLogger().info("Cleanup complete, disabling 'active'");
		PathCleaner.instance.getConfig().set("active", new ArrayList<>());
		PathCleaner.instance.saveConfig();
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

			Region r = new Region(world, x, z);
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
