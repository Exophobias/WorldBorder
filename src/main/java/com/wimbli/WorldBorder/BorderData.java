package com.wimbli.WorldBorder;

import java.util.EnumSet;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;


public class BorderData
{
	// the main data interacted with
	private double x = 0;
	private double z = 0;
	private int radiusX = 0;
	private int radiusZ = 0;
	private Boolean shapeRound = null;
	private boolean wrapping = false;

	// some extra data kept handy for faster border checks
	private double maxX;
	private double minX;
	private double maxZ;
	private double minZ;
	private double radiusXSquared;
	private double radiusZSquared;
	private double DefiniteRectangleX;
	private double DefiniteRectangleZ;
	private double radiusSquaredQuotient;

	public BorderData(double x, double z, int radiusX, int radiusZ, Boolean shapeRound, boolean wrap)
	{
		setData(x, z, radiusX, radiusZ, shapeRound, wrap);
	}
	public BorderData(double x, double z, int radiusX, int radiusZ)
	{
		setData(x, z, radiusX, radiusZ, null);
	}
	public BorderData(double x, double z, int radiusX, int radiusZ, Boolean shapeRound)
	{
		setData(x, z, radiusX, radiusZ, shapeRound);
	}
	public BorderData(double x, double z, int radius)
	{
		setData(x, z, radius, null);
	}
	public BorderData(double x, double z, int radius, Boolean shapeRound)
	{
		setData(x, z, radius, shapeRound);
	}

	public final void setData(double x, double z, int radiusX, int radiusZ, Boolean shapeRound, boolean wrap)
	{
		this.x = x;
		this.z = z;
		this.shapeRound = shapeRound;
		this.wrapping = wrap;
		this.setRadiusX(radiusX);
		this.setRadiusZ(radiusZ);
	}
	public final void setData(double x, double z, int radiusX, int radiusZ, Boolean shapeRound)
	{
		setData(x, z, radiusX, radiusZ, shapeRound, false);
	}
	public final void setData(double x, double z, int radius, Boolean shapeRound)
	{
		setData(x, z, radius, radius, shapeRound, false);
	}

	public BorderData copy()
	{
		return new BorderData(x, z, radiusX, radiusZ, shapeRound, wrapping);
	}

	public double getX()
	{
		return x;
	}
	public void setX(double x)
	{
		this.x = x;
		this.maxX = x + radiusX;
		this.minX = x - radiusX;
	}
	public double getZ()
	{
		return z;
	}
	public void setZ(double z)
	{
		this.z = z;
		this.maxZ = z + radiusZ;
		this.minZ = z - radiusZ;
	}
	public int getRadiusX()
	{
		return radiusX;
	}
	public int getRadiusZ()
	{
		return radiusZ;
	}
	public void setRadiusX(int radiusX)
	{
		this.radiusX = radiusX;
		this.maxX = x + radiusX;
		this.minX = x - radiusX;
		this.radiusXSquared = (double)radiusX * (double)radiusX;
		this.radiusSquaredQuotient = this.radiusXSquared / this.radiusZSquared;
		this.DefiniteRectangleX = Math.sqrt(.5 * this.radiusXSquared);
	}
	public void setRadiusZ(int radiusZ)
	{
		this.radiusZ = radiusZ;
		this.maxZ = z + radiusZ;
		this.minZ = z - radiusZ;
		this.radiusZSquared = (double)radiusZ * (double)radiusZ;
		this.radiusSquaredQuotient = this.radiusXSquared / this.radiusZSquared;
		this.DefiniteRectangleZ = Math.sqrt(.5 * this.radiusZSquared);
	}


	// backwards-compatible methods from before elliptical/rectangular shapes were supported
	/**
	 * @deprecated  Replaced by {@link #getRadiusX()} and {@link #getRadiusZ()};
	 * this method now returns an average of those two values and is thus imprecise
	 */
	public int getRadius()
	{
		return (radiusX + radiusZ) / 2;  // average radius; not great, but probably best for backwards compatibility
	}
	public void setRadius(int radius)
	{
		setRadiusX(radius);
		setRadiusZ(radius);
	}


	public Boolean getShape()
	{
		return shapeRound;
	}
	public void setShape(Boolean shapeRound)
	{
		this.shapeRound = shapeRound;
	}


	public boolean getWrapping()
	{
		return wrapping;
	}
	public void setWrapping(boolean wrap)
	{
		this.wrapping = wrap;
	}


	@Override
	public String toString()
	{
		return "radius " + ((radiusX == radiusZ) ? radiusX : radiusX + "x" + radiusZ) + " at X: " + Config.coord.format(x) + " Z: " + Config.coord.format(z) + (shapeRound != null ? (" (shape override: " + Config.ShapeName(shapeRound.booleanValue()) + ")") : "") + (wrapping ? (" (wrapping)") : "");
	}

	// This algorithm of course needs to be fast, since it will be run very frequently
	public boolean insideBorder(double xLoc, double zLoc, boolean round)
	{
		// if this border has a shape override set, use it
		if (shapeRound != null)
			round = shapeRound.booleanValue();

		// square border
		if (!round)
			return !(xLoc < minX || xLoc > maxX || zLoc < minZ || zLoc > maxZ);

		// round border
		else
		{
			// elegant round border checking algorithm is from rBorder by Reil with almost no changes, all credit to him for it
			double X = Math.abs(x - xLoc);
			double Z = Math.abs(z - zLoc);

			if (X < DefiniteRectangleX && Z < DefiniteRectangleZ)
				return true;	// Definitely inside
			else if (X >= radiusX || Z >= radiusZ)
				return false;	// Definitely outside
			else if (X * X + Z * Z * radiusSquaredQuotient < radiusXSquared)
				return true;	// After further calculation, inside
			else
				return false;	// Apparently outside, then
		}
	}
	public boolean insideBorder(double xLoc, double zLoc)
	{
		return insideBorder(xLoc, zLoc, Config.ShapeRound());
	}
	public boolean insideBorder(Location loc)
	{
		return insideBorder(loc.getX(), loc.getZ(), Config.ShapeRound());
	}
	public boolean insideBorder(CoordXZ coord, boolean round)
	{
		return insideBorder(coord.x, coord.z, round);
	}
	public boolean insideBorder(CoordXZ coord)
	{
		return insideBorder(coord.x, coord.z, Config.ShapeRound());
	}

	public Location correctedPosition(Location loc, boolean round, boolean flying)
	{
		// if this border has a shape override set, use it
		if (shapeRound != null)
			round = shapeRound.booleanValue();

		double xLoc = loc.getX();
		double zLoc = loc.getZ();
		double yLoc = loc.getY();

		// square border
		if (!round)
		{
			if (wrapping)
			{
				if (xLoc <= minX)
					xLoc = maxX - Config.KnockBack();
				else if (xLoc >= maxX)
					xLoc = minX + Config.KnockBack();
				if (zLoc <= minZ)
					zLoc = maxZ - Config.KnockBack();
				else if (zLoc >= maxZ)
					zLoc = minZ + Config.KnockBack();
			}
			else
			{
				if (xLoc <= minX)
					xLoc = minX + Config.KnockBack();
				else if (xLoc >= maxX)
					xLoc = maxX - Config.KnockBack();
				if (zLoc <= minZ)
					zLoc = minZ + Config.KnockBack();
				else if (zLoc >= maxZ)
					zLoc = maxZ - Config.KnockBack();
			}
		}

		// round border
		else
		{
			// algorithm originally from: http://stackoverflow.com/questions/300871/best-way-to-find-a-point-on-a-circle-closest-to-a-given-point
			// modified by Lang Lukas to support elliptical border shape

			//Transform the ellipse to a circle with radius 1 (we need to transform the point the same way)
			double dX = xLoc - x;
			double dZ = zLoc - z;
			double dU = Math.sqrt(dX *dX + dZ * dZ); //distance of the untransformed point from the center
			double dT = Math.sqrt(dX *dX / radiusXSquared + dZ * dZ / radiusZSquared); //distance of the transformed point from the center
			double f = (1 / dT - Config.KnockBack() / dU); //"correction" factor for the distances
			if (wrapping)
			{
				xLoc = x - dX * f;
				zLoc = z - dZ * f;
			} else {
				xLoc = x + dX * f;
				zLoc = z + dZ * f;
			}
		}

		return safeLandingAt(new Location(loc.getWorld(), xLoc, yLoc, zLoc, loc.getYaw(), loc.getPitch()), flying);
	}

	// Check a position already inside the border without projecting it to the edge.
	// The spawn fallback uses this because a world's spawn can itself be unsafe.
	Location safeLandingAt(Location loc, boolean flying)
	{
		int ixLoc = Location.locToBlock(loc.getX());
		int izLoc = Location.locToBlock(loc.getZ());

		// Make sure the chunk we're checking in is actually loaded
		Chunk tChunk = loc.getWorld().getChunkAt(CoordXZ.blockToChunk(ixLoc), CoordXZ.blockToChunk(izLoc));
		if (!tChunk.isLoaded())
			tChunk.load();

		double yLoc = getSafeY(loc.getWorld(), ixLoc, Location.locToBlock(loc.getY()), izLoc, flying);
		if (Double.isNaN(yLoc))
			return null;

		return new Location(loc.getWorld(), Math.floor(loc.getX()) + 0.5, yLoc, Math.floor(loc.getZ()) + 0.5, loc.getYaw(), loc.getPitch());
	}
	public Location correctedPosition(Location loc, boolean round)
	{
		return correctedPosition(loc, round, false);
	}
	public Location correctedPosition(Location loc)
	{
		return correctedPosition(loc, Config.ShapeRound(), false);
	}

	// Kept for plugins using the old API. A material alone cannot describe the collision
	// of stateful blocks such as trapdoors, so safe destination checks use Block.isPassable().
	@Deprecated
	public static final EnumSet<Material> safeOpenBlocks = EnumSet.noneOf(Material.class);
	// Include hazards in the feet, head, or supporting block. Resolve names at startup
	// so a removed enum constant cannot prevent the plugin from loading.
	public static final EnumSet<Material> painfulBlocks = EnumSet.noneOf(Material.class);
	static
	{
		for (String name : new String[] {"LAVA", "FIRE", "SOUL_FIRE", "CACTUS", "MAGMA_BLOCK",
			"CAMPFIRE", "SOUL_CAMPFIRE", "END_PORTAL", "END_GATEWAY", "NETHER_PORTAL",
			"POWDER_SNOW", "SWEET_BERRY_BUSH", "WITHER_ROSE", "POINTED_DRIPSTONE",
			"BUBBLE_COLUMN"})
		{
			Material material = Material.getMaterial(name);
			if (material != null)
				painfulBlocks.add(material);
		}

		// Preserve the old material-only view for API consumers, resolving names so
		// removed constants cannot stop the plugin from loading. Current checks use
		// block state passability instead of this approximate list.
		String legacyOpenNames = """
			AIR CAVE_AIR VOID_AIR WATER OAK_SAPLING SPRUCE_SAPLING BIRCH_SAPLING
			JUNGLE_SAPLING ACACIA_SAPLING DARK_OAK_SAPLING MANGROVE_PROPAGULE
			RAIL POWERED_RAIL DETECTOR_RAIL ACTIVATOR_RAIL COBWEB SHORT_GRASS FERN
			DEAD_BUSH DANDELION POPPY BLUE_ORCHID ALLIUM AZURE_BLUET RED_TULIP
			ORANGE_TULIP WHITE_TULIP PINK_TULIP OXEYE_DAISY BROWN_MUSHROOM
			RED_MUSHROOM TORCH WALL_TORCH REDSTONE_WIRE WHEAT LADDER LEVER
			LIGHT_WEIGHTED_PRESSURE_PLATE HEAVY_WEIGHTED_PRESSURE_PLATE
			STONE_PRESSURE_PLATE OAK_PRESSURE_PLATE SPRUCE_PRESSURE_PLATE
			BIRCH_PRESSURE_PLATE JUNGLE_PRESSURE_PLATE ACACIA_PRESSURE_PLATE
			DARK_OAK_PRESSURE_PLATE REDSTONE_TORCH REDSTONE_WALL_TORCH
			STONE_BUTTON SNOW SUGAR_CANE REPEATER COMPARATOR OAK_TRAPDOOR
			SPRUCE_TRAPDOOR BIRCH_TRAPDOOR JUNGLE_TRAPDOOR ACACIA_TRAPDOOR
			DARK_OAK_TRAPDOOR MELON_STEM ATTACHED_MELON_STEM PUMPKIN_STEM
			ATTACHED_PUMPKIN_STEM VINE NETHER_WART TRIPWIRE TRIPWIRE_HOOK
			CARROTS POTATOES OAK_BUTTON SPRUCE_BUTTON BIRCH_BUTTON JUNGLE_BUTTON
			ACACIA_BUTTON DARK_OAK_BUTTON SUNFLOWER LILAC ROSE_BUSH PEONY
			TALL_GRASS LARGE_FERN BEETROOTS ACACIA_SIGN ACACIA_WALL_SIGN
			BIRCH_SIGN BIRCH_WALL_SIGN DARK_OAK_SIGN DARK_OAK_WALL_SIGN
			JUNGLE_SIGN JUNGLE_WALL_SIGN OAK_SIGN OAK_WALL_SIGN SPRUCE_SIGN
			SPRUCE_WALL_SIGN
			""";
		for (String name : legacyOpenNames.trim().split("\\s+"))
		{
			Material material = Material.getMaterial(name);
			if (material != null)
				safeOpenBlocks.add(material);
		}
	}

	private boolean isSafeOpenBlock(Block block)
	{
		return block.isPassable() && !painfulBlocks.contains(block.getType());
	}

	// Check for two passable, non-hazardous blocks over a safe landing surface.
	private boolean isSafeSpot(World world, int X, int Y, int Z, boolean flying)
	{
		int maxHeight = world.getMaxHeight();
		if (Y < world.getMinHeight() || Y > maxHeight)
			return false;

		boolean safe =
			(Y == maxHeight
			 || (isSafeOpenBlock(world.getBlockAt(X, Y, Z))
			     && (Y + 1 >= maxHeight || isSafeOpenBlock(world.getBlockAt(X, Y + 1, Z)))));
		if (!safe || flying)
			return safe;

		if (Y <= world.getMinHeight())
			return false;

		Block below = world.getBlockAt(X, Y - 1, Z);
		return (below.getType() == Material.WATER || !below.isPassable())
			&& !painfulBlocks.contains(below.getType());
	}

	// find closest safe Y position from the starting position
	private double getSafeY(World world, int X, int Y, int Z, boolean flying)
	{
		// Keep Nether correction below the bedrock roof even though the world's build
		// height extends above it.
		final boolean isNether = world.getEnvironment() == World.Environment.NETHER;
		int limTop = isNether ? Math.min(125, world.getMaxHeight()) : world.getMaxHeight();
		int limBot = world.getMinHeight();
		// add 1 because getHighestBlockYAt() will give us the Y coordinate of a solid block, and we want the air block above it
		final int highestBlockBoundary = Math.min(world.getHighestBlockYAt(X, Z) + 1, limTop);

		// if Y is larger than the world can be and user can fly, return Y - Unless we are in the Nether, we might not want players on the roof
		if (flying && Y > limTop && !isNether)
			return (double) Y;

		// make sure Y values are within the boundaries of the world.
		if (Y > limTop)
		{
			if (isNether) 
				Y = limTop; // because of the roof, the nether can not rely on highestBlockBoundary, so limTop has to be used
			else
			{
				if (flying)
					Y = limTop;
				else
					Y = highestBlockBoundary; // no safe block to stand on above this boundary
			}
		}
		if (Y < limBot)
			Y = limBot;

		// for non Nether worlds we don't need to check upwards to the world-limit, it is enough to check up to and including the highestBlockBoundary, unless player is flying
		if (!isNether && !flying)
			limTop = highestBlockBoundary;
		// Expanding Y search method adapted from Acru's code in the Nether plugin

		// Note that we want to include limTop in the search - in the extreme case, world.getMaxHeight() should be included since the player can stand on top of the highest block
		for(int y1 = Y, y2 = Y; (y1 >= limBot) || (y2 <= limTop); y1--, y2++){
			// Look below.
			if(y1 >= limBot)
			{
				if (isSafeSpot(world, X, y1, Z, flying))
					return (double)y1;
			}

			// Look above.
			if(y2 <= limTop && y2 != y1)
			{
				if (isSafeSpot(world, X, y2, Z, flying))
					return (double)y2;
			}
		}

		return Double.NaN;	// no safe Y location
	}


	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		else if (obj == null || obj.getClass() != this.getClass())
			return false;

		BorderData test = (BorderData)obj;
		return test.x == this.x && test.z == this.z && test.radiusX == this.radiusX && test.radiusZ == this.radiusZ;
	}

	@Override
	public int hashCode()
	{
		return (((int)(this.x * 10) << 4) + (int)this.z + (this.radiusX << 2) + (this.radiusZ << 3));
	}
}
