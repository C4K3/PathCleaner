package net.simpvp.PathCleaner;

import java.io.File;
import java.util.Objects;

import org.bukkit.World;

public class Region {
	public String world;
	public int x;
	public int z;
	public File path = null;
	public World world_obj = null;

	public Region(String world, int x, int z) {
		this.world = world;
		this.x = x;
		this.z = z;
	}

	public Region(World world_obj, int x, int z, File path) {
		this.world = world_obj.getName();
		this.x = x;
		this.z = z;
		this.path = path;
		this.world_obj = world_obj;
	}

	@Override
	public String toString() {
		return String.format("Region(%s %d %d)", this.world, this.x, this.z);
	}

	// Override equals and hashCode such that hashmaps don't care about the
	// path.
	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}

		Region r;
		if (o instanceof Region) {
			r = (Region) o;
		} else {
			return false;
		}

		return this.x == r.x && this.z == r.z && this.world.equals(r.world);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.world, this.x, this.z);
	}

}
