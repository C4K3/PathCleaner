package net.simpvp.PathCleaner;

import java.util.HashSet;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class SQLite {
	private static Connection conn = null;

	public static void connect() {
		String database = "jdbc:sqlite:plugins/PathCleaner/pathcleaner.sqlite";

		try {
			Class.forName("org.sqlite.JDBC");
			conn = DriverManager.getConnection(database);

			Statement st = conn.createStatement();
			ResultSet rs = st.executeQuery("PRAGMA user_version;");

			int user_version = 0;
			while (rs.next()) {
				user_version = rs.getInt("user_version");
			}

			rs.close();
			st.close();

			switch (user_version) {
				// Database is brand new
				case 0: {
						PathCleaner.instance.getLogger().info("Database is brand new. Creating ...");
						String query = "CREATE TABLE safe_regions"
							+ "(id INTEGER PRIMARY KEY AUTOINCREMENT,"
							+ "world TEXT,"
							+ "x INT,"
							+ "z INT);"
							+ "CREATE UNIQUE INDEX safe_regions_world_xz ON safe_regions(world, x, z);"
							+ "PRAGMA user_version = 1;";
						st = conn.createStatement();
						st.executeUpdate(query);
						st.close();
				}

			}

		} catch (Exception e) {
			PathCleaner.instance.getLogger().severe("PathCleaner could not initialize SQLite");
			e.printStackTrace();
			conn = null;
			return;
		}

		PathCleaner.instance.getLogger().info("SQLite initialized");
	}

	public static void synchronous_mode(boolean synchronous) {
		try {
			String query;
			if (synchronous) {
				PathCleaner.instance.getLogger().info("Enabling SQLite synchronous mode");
				query = "PRAGMA synchronous=NORMAL;";
			} else {
				PathCleaner.instance.getLogger().info("Disabling SQLite synchronous mode");
				query = "PRAGMA synchronous=OFF;";
			}

			Statement st = conn.createStatement();
			st.executeUpdate(query);
			st.close();
		} catch (Exception e) {
			PathCleaner.instance.getLogger().severe("PathCleaner could not modify synchronous mode");
			e.printStackTrace();
		}
	}

	public static void close() {
		if (conn == null) {
			return;
		}

		try {
			conn.close();
		} catch (Exception e) {
			PathCleaner.instance.getLogger().severe("PathCleaner could not close SQLite");
			e.printStackTrace();
		}
	}

	public static boolean is_initialized() {
		return conn != null;
	}

	/**
	 * Mark the given region as "safe" (do not ever delete)
	 */
	public static boolean insert_safe_region(Region region) {
		try {
			String query = "INSERT OR IGNORE INTO safe_regions (world, x, z) VALUES (?, ?, ?)";
			PreparedStatement st = conn.prepareStatement(query);
			st.setString(1, region.world);
			st.setInt(2, region.x);
			st.setInt(3, region.z);

			st.executeUpdate();
			st.close();

			//PathCleaner.instance.getLogger().info(String.format("PathCleaner marked %s as safe", region.toString()));

			return true;
		} catch (Exception e) {
			PathCleaner.instance.getLogger().severe(String.format("PathCleaner could not mark %s as safe", region.toString()));
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Get the list of regions from the database that have been declared safe.
	 */
	public static HashSet<Region> get_safe_regions() {
		HashSet<Region> ret = new HashSet<>();

		try {
			String query = "SELECT world, x, z FROM safe_regions";
			PreparedStatement st = conn.prepareStatement(query);
			ResultSet rs = st.executeQuery();

			while (rs.next()) {
				Region r = new Region(rs.getString("world"), rs.getInt("x"), rs.getInt("z"));
				ret.add(r);
			}

			rs.close();
			st.close();
		} catch (Exception e) {
			PathCleaner.instance.getLogger().severe("PathCleaner could not fetch all safe regions");
			e.printStackTrace();
			return null;
		}

		return ret;
	}

}
