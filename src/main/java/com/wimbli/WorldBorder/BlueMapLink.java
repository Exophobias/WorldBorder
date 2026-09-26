package com.wimbli.WorldBorder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.flowpowered.math.vector.Vector2d;
import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

/** BlueMap-only code; BlueMapPresence reaches this class after checking the API is available. */
final class BlueMapLink implements BlueMapPresence.Registration
{
	private static final String MARKER_SET_ID = "worldborder-borders";
	private static final String LABEL = "World border";
	private static final int ELLIPSE_POINTS = 512;
	private static final Color LINE = new Color(0xFF0000, 1f);
	private static final Color FILL = new Color(0xFF0000, 0f);

	private final Plugin plugin;
	private final AtomicBoolean active = new AtomicBoolean(true);
	private final Consumer<BlueMapAPI> ready = this::onReady;
	private final Set<String> missingMapsWarned = new HashSet<>(); // main thread only
	private volatile boolean linked;

	private BlueMapLink(Plugin plugin)
	{
		this.plugin = plugin;
	}

	static BlueMapPresence.Registration install(Plugin plugin)
	{
		BlueMapLink link = new BlueMapLink(plugin);
		// This can call back synchronously if BlueMap is already ready; onReady always
		// defers Bukkit and Config access to the next main-thread tick.
		BlueMapAPI.onEnable(link.ready);
		return link;
	}

	private void onReady(BlueMapAPI api)
	{
		if (!active.get()) return;
		linked = false;
		try
		{
			plugin.getServer().getScheduler().runTask(plugin, () -> tryUpdate(() ->
			{
				if (!active.get() || !plugin.isEnabled()
					|| BlueMapAPI.getInstance().filter(current -> current == api).isEmpty()) return;
				linked = selfCheck(api);
				if (linked)
					redrawAll(api);
			}));
		}
		catch (IllegalPluginAccessException ex)
		{
			// Plugin disable won the race with the readiness callback.
		}
		catch (RuntimeException | LinkageError ex)
		{
			plugin.getLogger().log(Level.WARNING, "Could not schedule BlueMap border marker publication.", ex);
		}
	}

	// Exercise a flow-math vector crossing into the API. A flat JUnit classpath
	// cannot detect two plugin classloaders holding different Vector3d classes.
	private boolean selfCheck(BlueMapAPI api)
	{
		try
		{
			Shape shape = new Shape(new Vector2d(0, 0), new Vector2d(16, 0),
				new Vector2d(16, 16), new Vector2d(0, 16));
			ShapeMarker marker = new ShapeMarker("worldborder-selfcheck", new Vector3d(0, 64, 0), shape, 64f);
			MarkerSet set = MarkerSet.builder().label("WorldBorder self-check").build();
			set.getMarkers().put("worldborder-selfcheck", marker);
			plugin.getLogger().info("BlueMap link OK; " + api.getMaps().size() + " map(s) loaded. Publishing WorldBorder markers.");
			return true;
		}
		catch (RuntimeException | LinkageError ex)
		{
			plugin.getLogger().log(Level.SEVERE, "BlueMap's API cannot share flow-math classes with WorldBorder; map markers are disabled. Install BlueMap's Paper build and leave flow-math unshaded.", ex);
			return false;
		}
	}

	@Override
	public void showBorder(String world, BorderData border)
	{
		BorderData snapshot = border.copy();
		withApi(api ->
		{
			removeMarker(api, world);
			publish(api, world, snapshot);
		});
	}

	@Override
	public void removeBorder(String world)
	{
		withApi(api -> removeMarker(api, world));
	}

	@Override
	public void showAllBorders()
	{
		withApi(this::redrawAll);
	}

	@Override
	public void removeAllBorders()
	{
		withApi(this::removeAll);
	}

	@Override
	public void shutdown()
	{
		if (!active.compareAndSet(true, false)) return;
		try
		{
			BlueMapAPI.unregisterListener(ready);
			BlueMapAPI.getInstance().ifPresent(this::removeAll);
		}
		catch (RuntimeException | LinkageError ex)
		{
			plugin.getLogger().log(Level.WARNING, "Could not remove BlueMap border markers during shutdown.", ex);
		}
	}

	private void withApi(Consumer<BlueMapAPI> update)
	{
		onMainThread(() ->
		{
			if (!active.get() || !plugin.isEnabled() || !linked) return;
			BlueMapAPI.getInstance().ifPresent(api -> tryUpdate(() -> update.accept(api)));
		});
	}

	private void onMainThread(Runnable task)
	{
		if (Bukkit.isPrimaryThread())
			task.run();
		else
		{
			try
			{
				plugin.getServer().getScheduler().runTask(plugin, task);
			}
			catch (IllegalPluginAccessException ex)
			{
				// No marker update remains to do after disable.
			}
			catch (RuntimeException | LinkageError ex)
			{
				plugin.getLogger().log(Level.WARNING, "Could not schedule BlueMap border marker update.", ex);
			}
		}
	}

	private void tryUpdate(Runnable update)
	{
		try
		{
			update.run();
		}
		catch (RuntimeException | LinkageError ex)
		{
			plugin.getLogger().log(Level.WARNING, "Could not update BlueMap border markers; the configured border is still enforced in-game.", ex);
		}
	}

	private void redrawAll(BlueMapAPI api)
	{
		removeAll(api);
		for (Map.Entry<String, BorderData> entry : Config.getBorders().entrySet())
			publish(api, entry.getKey(), entry.getValue());
	}

	private void publish(BlueMapAPI api, String worldName, BorderData border)
	{
		World world = Bukkit.getWorld(worldName);
		if (world == null)
		{
			warnMissingMap(worldName, "the Bukkit world is not loaded");
			return;
		}
		List<BlueMapMap> maps = api.getWorld(world).map(BlueMapWorld::getMaps)
			.map(List::copyOf).orElseGet(List::of);
		if (maps.isEmpty())
		{
			warnMissingMap(worldName, "BlueMap has no map matching this Bukkit world; check its maps/ world: setting");
			return;
		}
		missingMapsWarned.remove(worldName);

		boolean round = border.getShape() == null ? Config.ShapeRound() : border.getShape();
		Shape shape = borderShape(border, round);
		if (shape == null)
		{
			plugin.getLogger().warning("BlueMap border for " + worldName + " has an invalid center or radius; marker skipped.");
			return;
		}
		ShapeMarker marker = ShapeMarker.builder()
			.label(LABEL)
			.shape(shape, 64f)
			.lineColor(LINE)
			.fillColor(FILL)
			.lineWidth(3)
			.depthTestEnabled(false)
			.build();
		for (BlueMapMap map : maps)
			map.getMarkerSets().computeIfAbsent(MARKER_SET_ID,
				id -> MarkerSet.builder().label("WorldBorder").toggleable(true).build())
				.getMarkers().put(markerId(worldName), marker);
	}

	static Shape borderShape(BorderData border, boolean round)
	{
		if (border.getRadiusX() <= 0 || border.getRadiusZ() <= 0
			|| !Double.isFinite(border.getX()) || !Double.isFinite(border.getZ())) return null;
		return round
			? Shape.createEllipse(border.getX(), border.getZ(), border.getRadiusX(), border.getRadiusZ(), ELLIPSE_POINTS)
			: Shape.createRect(border.getX() - border.getRadiusX(), border.getZ() - border.getRadiusZ(),
				border.getX() + border.getRadiusX(), border.getZ() + border.getRadiusZ());
	}

	private static String markerId(String worldName)
	{
		return "border_" + worldName;
	}

	private void removeMarker(BlueMapAPI api, String worldName)
	{
		for (BlueMapMap map : api.getMaps())
		{
			MarkerSet set = map.getMarkerSets().get(MARKER_SET_ID);
			if (set == null) continue;
			set.getMarkers().remove(markerId(worldName));
			if (set.getMarkers().isEmpty()) map.getMarkerSets().remove(MARKER_SET_ID);
		}
	}

	private void removeAll(BlueMapAPI api)
	{
		for (BlueMapMap map : api.getMaps())
			map.getMarkerSets().remove(MARKER_SET_ID);
	}

	private void warnMissingMap(String worldName, String cause)
	{
		if (missingMapsWarned.add(worldName))
			plugin.getLogger().warning("BlueMap border marker for world " + worldName + " was not drawn: " + cause + ".");
	}
}
