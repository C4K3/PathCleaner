package net.simpvp.PathCleaner;

import java.io.File;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class PathCleaner extends JavaPlugin {
	public static PathCleaner instance;

	public static long inhabited_threshold = -1;

	public static boolean initialized = false;

	public PathCleaner() {
		instance = this;
	}

	public void onEnable() {
		saveDefaultConfig();
		reloadConfig();

		inhabited_threshold = getConfig().getLong("inhabitedThreshold") * 20L;
		if (inhabited_threshold <= 0) {
			throw new RuntimeException(String.format("Invalid inhabited threshold %d", inhabited_threshold));
		}

		File dir = new File("plugins/PathCleaner");
		if (!dir.exists()) {
			dir.mkdir();
		}

		SQLite.connect();

		getServer().getPluginManager().registerEvents(new InteractListener(), this);

		if (!SQLite.is_initialized()) {
			throw new RuntimeException("SQLite is not initialized");
		}

		if (getConfig().getBoolean("initWorlds")) {
			// Run the init task one tick later, so that plugins
			// have a chance to load all worlds.
			new BukkitRunnable() {
				@Override
				public void run() {
					Cleanup.init_worlds();

				}
			}.runTaskLater(this, 1L);

			return;
		} else {
			new BukkitRunnable() {
				@Override
				public void run() {
					Cleanup.run();

				}
			}.runTaskLater(this, 5L);
		}

		initialized = true;
	}

	public void onDisable() {
		InteractListener.check_loaded_chunks();

		if (!initialized) {
			getLogger().severe("Initialized set to false, not running");
			return;
		}

		reloadConfig();
		//Cleanup.run();
		SQLite.close();
	}
}
