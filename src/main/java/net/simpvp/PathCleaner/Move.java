package net.simpvp.PathCleaner;

import java.io.File;
import java.util.ArrayList;

public class Move {
	public File source;
	public File target;

	public Move(Move parent, String child) {
		this.source = new File(parent.source, child);
		this.target = new File(parent.target, child);
	}

	private Move(File source, File target) {
		this.source = source;
		this.target = target;
	}

	/**
	 * Build a list of Moves, representing a directory tree.
	 *
	 * The Moves are put onto the first 'ret' argument.
	 */
	public static void build(ArrayList<Move> ret, File source, File target) {
		if (!target.exists()) {
			target.mkdir();
		}

		for (File f : source.listFiles()) {
			if (!f.isDirectory()) {
				continue;
			}

			File f_target = new File(target, f.getName());

			build(ret, f, f_target);
		}

		ret.add(new Move(source, target));
	}

	public void move() {
		PathCleaner.instance.getLogger().info(String.format("Renaming '%s' -> '%s'", this.source, this.target));
		this.source.renameTo(this.target);
	}
}
