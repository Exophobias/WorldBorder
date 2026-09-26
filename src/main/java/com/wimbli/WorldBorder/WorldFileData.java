package com.wimbli.WorldBorder;

import java.io.*;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.bukkit.World;

// image output stuff, for debugging method at bottom of this file
import java.awt.*;
import java.awt.image.*;
import javax.imageio.*;


// by the way, this region file handler was created based on the divulged region file format: http://mojang.com/2011/02/16/minecraft-save-file-format-in-beta-1-3/

public class WorldFileData
{
	private static final Pattern REGION_FILE_NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.(mca|mcr)", Pattern.CASE_INSENSITIVE);
	private static final int REGION_HEADER_BYTES = 8192;
	private static final int MAX_REGION_COORDINATE = 60_000; // Beyond Minecraft's +/-30M block world.
	private static final LinkOption NO_LINKS = LinkOption.NOFOLLOW_LINKS;

	private transient World world;
	private transient File regionFolder = null;
	private transient File[] regionFiles = null;
	private transient Map<CoordXZ, File> filesByCoordinates = new HashMap<>();
	private transient Map<File, CoordXZ> coordinatesByFile = new HashMap<>();
	private transient Player notifyPlayer = null;
	private transient Map<CoordXZ, List<Boolean>> regionChunkExistence = Collections.synchronizedMap(new HashMap<CoordXZ, List<Boolean>>());

	public static WorldFileData create(World world, Player notifyPlayer)
	{
		return create(world, notifyPlayer, WorldFileDataType.REGION, false);
	}

	// Use this static method to create a new instance of this class. If null is returned, there was a problem so any process relying on this should be cancelled.
	// Defaults to "REGION" if "type" is "WorldFileDataType.ALL"
	public static WorldFileData create(World world, Player notifyPlayer, WorldFileDataType type, boolean silent)
	{
		WorldFileData newData = new WorldFileData(world, notifyPlayer);

		String subFolder = "region";
		if (type == WorldFileDataType.POI) subFolder = "poi";
		else if (type == WorldFileDataType.ENTITIES) subFolder = "entities";

		try
		{
			String legacyDimension = null;
			if (world.getEnvironment() == World.Environment.NETHER) legacyDimension = "DIM-1";
			else if (world.getEnvironment() == World.Environment.THE_END) legacyDimension = "DIM1";
			Path folder = resolveDataFolder(world.getWorldFolder().toPath(), world.getKey().getNamespace(), world.getKey().getKey(), legacyDimension, subFolder);
			if (folder == null)
			{
				if (silent && type != WorldFileDataType.REGION)
				{
					newData.regionFiles = new File[0];
					return newData;
				}
				if (!silent) newData.sendMessage("Could not find the " + subFolder + " folder for world " + world.getName() + ".");
				return null;
			}
			newData.regionFolder = folder.toFile();

			File[] entries = folder.toFile().listFiles();
			if (entries == null) throw new IOException("Could not list " + folder);
			List<File> validFiles = new ArrayList<>();
			for (File entry : entries)
			{
				String lowerName = entry.getName().toLowerCase(Locale.ROOT);
				if (!lowerName.endsWith(".mca") && !lowerName.endsWith(".mcr")) continue;
				CoordXZ coordinates = parseRegionFileName(entry.getName());
				Path file = entry.toPath();
				if (!Files.isRegularFile(file, NO_LINKS) || Files.isSymbolicLink(file))
					throw new IOException("Region data path is not a regular file: " + file);
				if (newData.filesByCoordinates.putIfAbsent(coordinates, entry) != null)
					throw new IOException("Duplicate region coordinates in " + folder + ": " + entry.getName());
				readChunkLocations(file); // Reject unreadable or damaged headers before Fill or Trim starts.
				newData.coordinatesByFile.put(entry, coordinates);
				validFiles.add(entry);
			}
			if (validFiles.isEmpty())
			{
				if (silent && type != WorldFileDataType.REGION)
				{
					newData.regionFiles = new File[0];
					return newData;
				}
				if (!silent) newData.sendMessage("Could not find any region data files in " + folder);
				return null;
			}
			newData.regionFiles = validFiles.toArray(new File[0]);
			Arrays.sort(newData.regionFiles, Comparator.comparing(File::getName));
			return newData;
		}
		catch (IOException | IllegalArgumentException ex)
		{
			newData.sendMessage("Cannot safely use " + subFolder + " data for world " + world.getName() + ": " + ex.getMessage());
			return null;
		}
	}

	// the constructor is private; use create() method above to create an instance of this class.
	private WorldFileData(World world, Player notifyPlayer)
	{
		this.world = world;
		this.notifyPlayer = notifyPlayer;
	}

	// Paper may give the dimension folder directly. Older Bukkit worlds put Nether/End data
	// below DIM-1/DIM1; newer Paper worlds use dimensions/<namespace>/<key>.
	// Never guess among arbitrary DIM* folders or follow a symlink into another world.
	static Path resolveDataFolder(Path worldFolder, String namespace, String key, String legacyDimension, String subFolder) throws IOException
	{
		if (!subFolder.equals("region") && !subFolder.equals("poi") && !subFolder.equals("entities"))
			throw new IOException("Unexpected data folder type: " + subFolder);
		Path root = worldFolder.toRealPath();
		if (!Files.isDirectory(root)) throw new IOException("World folder is not a directory: " + root);
		List<Path> candidates = new ArrayList<>();
		candidates.add(Path.of(subFolder));
		if (namespace != null && key != null && !namespace.isBlank() && !key.isBlank())
			candidates.add(Path.of("dimensions").resolve(namespace).resolve(key).resolve(subFolder));
		if (legacyDimension != null)
			candidates.add(Path.of(legacyDimension).resolve(subFolder));

		Path selected = null;
		for (Path relative : candidates)
		{
			Path parent = relative.getParent();
			if (parent == null || directoryExistsWithoutLinks(root, parent))
			{
				Path sectorFolder = root.resolve(parent == null ? Path.of("") : parent).resolve("sectors");
				if (Files.exists(sectorFolder, NO_LINKS))
					throw new IOException("SectorFile storage is present at " + sectorFolder + "; MCA trimming/filling is unsafe");
			}
			if (!directoryExistsWithoutLinks(root, relative)) continue;
			Path candidate = root.resolve(relative);
			if (selected != null && !selected.equals(candidate))
				throw new IOException("Multiple " + subFolder + " folders found: " + selected + " and " + candidate);
			selected = candidate;
		}
		return selected;
	}

	private static boolean directoryExistsWithoutLinks(Path root, Path relative) throws IOException
	{
		if (relative.isAbsolute() || !relative.normalize().equals(relative))
			throw new IOException("Unsafe dimension path: " + relative);
		Path current = root;
		for (Path component : relative)
		{
			String name = component.toString();
			if (name.isEmpty() || name.equals(".") || name.equals(".."))
				throw new IOException("Unsafe dimension path: " + relative);
			current = current.resolve(component);
			if (!Files.exists(current, NO_LINKS)) return false;
			if (Files.isSymbolicLink(current) || !Files.isDirectory(current, NO_LINKS))
				throw new IOException("Data folder path is not a real directory: " + current);
		}
		return true;
	}

	static CoordXZ parseRegionFileName(String name) throws IOException
	{
		Matcher match = REGION_FILE_NAME.matcher(name);
		if (!match.matches()) throw new IOException("Invalid region file name: " + name);
		try
		{
			int x = Integer.parseInt(match.group(1));
			int z = Integer.parseInt(match.group(2));
			if (Math.abs((long) x) > MAX_REGION_COORDINATE || Math.abs((long) z) > MAX_REGION_COORDINATE)
				throw new IOException("Region coordinates exceed the supported world range: " + name);
			return new CoordXZ(x, z);
		}
		catch (NumberFormatException ex)
		{
			throw new IOException("Region coordinates are out of range: " + name, ex);
		}
	}

	// Region, POI and entity MCA files share the same location-header format.
	// A bad header must not be interpreted as an empty region: Fill could regenerate
	// existing chunks and Trim could otherwise continue after losing its safety data.
	static List<Boolean> readChunkLocations(Path file) throws IOException
	{
		if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, NO_LINKS))
			throw new IOException("Region data path is not a regular file: " + file);
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, NO_LINKS))
		{
			long length = channel.size();
			if (length < REGION_HEADER_BYTES)
				throw new IOException("Region header is truncated: " + file);
			ByteBuffer header = ByteBuffer.allocate(REGION_HEADER_BYTES);
			while (header.hasRemaining())
			{
				if (channel.read(header) <= 0) throw new IOException("Region header could not be fully read: " + file);
			}
			header.flip();
			List<Boolean> locations = new ArrayList<>(1024);
			NavigableMap<Integer, Integer> occupiedSectors = new TreeMap<>();
			for (int i = 0; i < 1024; i++)
			{
				int pointer = header.getInt();
				int sectorOffset = pointer >>> 8;
				int sectorCount = pointer & 0xff;
				if (pointer != 0 && (sectorOffset < 2 || sectorCount == 0 || ((long) sectorOffset + sectorCount) * 4096 > length))
					throw new IOException("Invalid chunk pointer " + i + " in " + file);
				if (pointer != 0)
				{
					int end = sectorOffset + sectorCount;
					Map.Entry<Integer, Integer> before = occupiedSectors.floorEntry(sectorOffset);
					Map.Entry<Integer, Integer> after = occupiedSectors.ceilingEntry(sectorOffset);
					if ((before != null && before.getValue() > sectorOffset) || (after != null && after.getKey() < end))
						throw new IOException("Overlapping chunk pointers in " + file);
					occupiedSectors.put(sectorOffset, end);
				}
				locations.add(pointer != 0);
			}
			return locations;
		}
	}

	public Path validatedRegionFile(int index) throws IOException
	{
		File file = regionFile(index);
		if (file == null) throw new IOException("Invalid region file index: " + index);
		Path folder = regionFolder.toPath();
		Path path = file.toPath();
		if (Files.isSymbolicLink(folder) || !Files.isDirectory(folder, NO_LINKS)
			|| !folder.equals(path.getParent()) || Files.isSymbolicLink(path) || !Files.isRegularFile(path, NO_LINKS))
			throw new IOException("Region data path changed or is unsafe: " + path);
		readChunkLocations(path);
		return path;
	}


	// number of region files this world has
	public int regionFileCount()
	{
		return regionFiles.length;
	}

	// folder where world's region files are located
	public File regionFolder()
	{
		return regionFolder;
	}

	// return entire list of region files
	public File[] regionFiles()
	{
		return regionFiles.clone();
	}

	// return a region file by index
	public File regionFile(int index)
	{
		if (index < 0 || index >= regionFiles.length)
			return null;
		return regionFiles[index];
	}

	// get the X and Z world coordinates of the region from the filename
	public CoordXZ regionFileCoordinates(int index)
	{
		File regionFile = this.regionFile(index);
		return regionFile == null ? null : coordinatesByFile.get(regionFile);
	}


	// Find out if the chunk at the given coordinates exists.
	public boolean doesChunkExist(int x, int z)
	{
		CoordXZ region = new CoordXZ(CoordXZ.chunkToRegion(x), CoordXZ.chunkToRegion(z));
		List<Boolean> regionChunks = this.getRegionData(region);
//		Bukkit.getLogger().info("x: "+x+"  z: "+z+"  offset: "+coordToRegionOffset(x, z));
		return regionChunks.get(coordToRegionOffset(x, z));
	}

	// Find out if the chunk at the given coordinates has been fully generated.
	// Minecraft only fully generates a chunk when adjacent chunks are also loaded.
	public boolean isChunkFullyGenerated(int x, int z)
	{	// if all adjacent chunks exist, it should be a safe enough bet that this one is fully generated
		// For 1.13+, due to world gen changes, this is now effectively a 3 chunk radius requirement vs a 1 chunk radius
		for (int xx = x-3; xx <= x+3; xx++)
		{
			for (int zz = z-3; zz <= z+3; zz++)
			{
				if (!doesChunkExist(xx, zz))
					return false;
			}
		}
		return true;
	}

	// Method to let us know a chunk has been generated, to update our region map.
	public void chunkExistsNow(int x, int z)
	{
		CoordXZ region = new CoordXZ(CoordXZ.chunkToRegion(x), CoordXZ.chunkToRegion(z));
		List<Boolean> regionChunks = this.getRegionData(region);
		regionChunks.set(coordToRegionOffset(x, z), true);
	}



	// region is 32 * 32 chunks; chunk pointers are stored in region file at position: x + z*32 (32 * 32 chunks = 1024)
	// input x and z values can be world-based chunk coordinates or local-to-region chunk coordinates either one
	private int coordToRegionOffset(int x, int z)
	{
		// "%" modulus is used to convert potential world coordinates to definitely be local region coordinates
		x = x % 32;
		z = z % 32;
		// similarly, for local coordinates, we need to wrap negative values around
		if (x < 0) x += 32;
		if (z < 0) z += 32;
		// return offset position for the now definitely local x and z values
		return (x + (z * 32));
	}

	private List<Boolean> getRegionData(CoordXZ region)
	{
		List<Boolean> data = regionChunkExistence.get(region);
		if (data != null)
			return data;

		File file = filesByCoordinates.get(region);
		if (file == null)
		{
			data = new ArrayList<>(Collections.nCopies(1024, Boolean.FALSE));
		}
		else
		{
			try
			{
				data = readChunkLocations(file.toPath());
			}
			catch (IOException ex)
			{
				sendMessage("Could not read region file " + file.getName() + ": " + ex.getMessage());
				throw new IllegalStateException("Cannot safely read region data for " + world.getName(), ex);
			}
		}
		regionChunkExistence.put(region, data);
//		testImage(region, data);
		return data;
	}

	// send a message to the server console/log and possibly to an in-game player
	private void sendMessage(String text)
	{
		Config.log("[WorldData] " + text);
		if (notifyPlayer != null && notifyPlayer.isOnline())
			notifyPlayer.sendMessage("[WorldData] " + text);
	}

	// crude chunk map PNG image output, for debugging
	private void testImage(CoordXZ region, List<Boolean> data) {
		int width = 32;
		int height = 32;
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = bi.createGraphics();
		int current = 0;
		g2.setColor(Color.BLACK);

		for (int x = 0; x < 32; x++)
		{
			for (int z = 0; z < 32; z++)
			{
				if (data.get(current).booleanValue())
					g2.fillRect(x,z, x+1, z+1);
				current++;
			}
		}

		File f = new File("region_"+region.x+"_"+region.z+"_.png");
		Config.log(f.getAbsolutePath());
		try {
			// png is an image format (like gif or jpg)
			ImageIO.write(bi, "png", f);
		} catch (IOException ex) {
			Config.log("[SEVERE]" + ex.getLocalizedMessage());
		}
	}
}
