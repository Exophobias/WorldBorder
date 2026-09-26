package com.wimbli.WorldBorder;

import java.util.logging.Level;

import org.bukkit.plugin.Plugin;

/** Keeps optional BlueMap classes out of the plugin's normal loading path. */
final class BlueMapPresence
{
	private static final String API_CLASS = "de.bluecolored.bluemap.api.BlueMapAPI";

	interface Registration
	{
		void showBorder(String world, BorderData border);
		void removeBorder(String world);
		void showAllBorders();
		void removeAllBorders();
		void shutdown();
	}

	private static final Registration NONE = new Registration()
	{
		@Override public void showBorder(String world, BorderData border) {}
		@Override public void removeBorder(String world) {}
		@Override public void showAllBorders() {}
		@Override public void removeAllBorders() {}
		@Override public void shutdown() {}
	};

	private static Registration registration = NONE;

	private BlueMapPresence() {}

	static void install(Plugin plugin)
	{
		Plugin blueMap = plugin.getServer().getPluginManager().getPlugin("BlueMap");
		if (blueMap == null || !blueMap.isEnabled()) return;
		try
		{
			Class.forName(API_CLASS);
			registration = BlueMapLink.install(plugin);
		}
		catch (ClassNotFoundException | LinkageError ex)
		{
			plugin.getLogger().log(Level.WARNING, "BlueMap is installed but its API could not be linked; border map markers are disabled.", ex);
		}
		catch (RuntimeException ex)
		{
			plugin.getLogger().log(Level.WARNING, "BlueMap did not accept the border marker listener; border map markers are disabled.", ex);
		}
	}

	static void showBorder(String world, BorderData border)
	{
		registration.showBorder(world, border);
	}

	static void removeBorder(String world)
	{
		registration.removeBorder(world);
	}

	static void showAllBorders()
	{
		registration.showAllBorders();
	}

	static void removeAllBorders()
	{
		registration.removeAllBorders();
	}

	static void shutdown()
	{
		Registration previous = registration;
		registration = NONE;
		previous.shutdown();
	}
}
