package com.wimbli.WorldBorder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Server;
import org.bukkit.World;

import com.wimbli.WorldBorder.Events.WorldBorderTrimFinishedEvent;
import com.wimbli.WorldBorder.Events.WorldBorderTrimStartEvent;


public class WorldTrimTask implements Runnable
{
	// general task-related reference data
	private transient Server server = null;
	private transient World world = null;
	private transient WorldFileData worldData = null;
	private transient BorderData border = null;
	private transient boolean readyToGo = false;
	private transient boolean paused = false;
	private transient int taskID = -1;
	private transient Player notifyPlayer = null;
	private transient int chunksPerRun = 1;
	private transient WorldFileDataType typeToTrim = null;
	
	// values for what chunk in the current region we're at
	private transient WorldFileDataType currentType = WorldFileDataType.REGION;
	private transient int currentRegion = -1;  // region(file) we're at in regionFiles
	private transient int regionX = 0;  // X location value of the current region
	private transient int regionZ = 0;  // X location value of the current region
	private transient int currentChunk = 0;  // chunk we've reached in the current region (regionChunks)
	private transient List<CoordXZ> regionChunks = new ArrayList<CoordXZ>(1024);
	private transient List<CoordXZ> trimChunks = new ArrayList<CoordXZ>(1024);
	private transient int counter = 0;

	// for reporting progress back to user occasionally
	private transient long lastReport = Config.Now();
	private transient int reportTarget = 0;
	private transient int reportTotal = 0;
	private transient int reportTrimmedRegions = 0;
	private transient int reportTrimmedChunks = 0;


	public WorldTrimTask(Server theServer, Player player, String worldName, int trimDistance, int chunksPerRun)
	{
		this(theServer, player, worldName, trimDistance, chunksPerRun, WorldFileDataType.ALL);
	}

	public WorldTrimTask(Server theServer, Player player, String worldName, int trimDistance, int chunksPerRun, WorldFileDataType type)
	{
		this.server = theServer;
		this.notifyPlayer = player;
		this.chunksPerRun = chunksPerRun;
		this.typeToTrim = type;
		if (type != WorldFileDataType.ALL) this.currentType = type;
		else this.currentType = WorldFileDataType.REGION;

		this.world = server.getWorld(worldName);
		if (this.world == null)
		{
			if (worldName.isEmpty())
				sendMessage("You must specify a world!");
			else
				sendMessage("World \"" + worldName + "\" not found!");
			this.stop();
			return;
		}

		this.border = (Config.Border(worldName) == null) ? null : Config.Border(worldName).copy();
		if (this.border == null)
		{
			sendMessage("No border found for world \"" + worldName + "\"!");
			this.stop();
			return;
		}

		this.border.setRadiusX(border.getRadiusX() + trimDistance);
		this.border.setRadiusZ(border.getRadiusZ() + trimDistance);

		worldData = WorldFileData.create(world, notifyPlayer, currentType, false);
		if (worldData == null)
		{
			this.stop();
			return;
		}

		// each region file covers up to 1024 chunks; with all operations we might need to do, let's figure 3X that
		this.reportTarget = worldData.regionFileCount() * 3072;

		// queue up the first file
		if (!nextFile())
			return;

		this.readyToGo = true;
		Bukkit.getServer().getPluginManager().callEvent(new WorldBorderTrimStartEvent(this));
	}

	public void setTaskID(int ID)
	{
		this.taskID = ID;
	}


	public void run()
	{
		if (server == null || !readyToGo || paused)
			return;

		// this is set so it only does one iteration at a time, no matter how frequently the timer fires
		readyToGo = false;
		// and this is tracked to keep one iteration from dragging on too long and possibly choking the system if the user specified a really high frequency
		long loopStartTime = Config.Now();

		counter = 0;
		while (counter <= chunksPerRun)
		{
			// in case the task has been paused while we're repeating...
			if (server == null || paused)
				return;

			long now = Config.Now();

			// every 5 seconds or so, give basic progress report to let user know how it's going
			if (now > lastReport + 5000)
				reportProgress();

			// if this iteration has been running for 45ms (almost 1 tick) or more, stop to take a breather; shouldn't normally be possible with Trim, but just in case
			if (now > loopStartTime + 45)
			{
				readyToGo = true;
				return;
			}

			if (regionChunks.isEmpty())
				addCornerChunks();
			else if (currentChunk == 4)
			{	// determine if region is completely _inside_ border based on corner chunks
				if (trimChunks.isEmpty())
				{	// it is, so skip it and move on to next file
					counter += 4;
					nextFile();
					continue;
				}
				addEdgeChunks();
				addInnerChunks();
			}
			else if (currentChunk == 1024)
			{	// Only delete an entire file after checking every chunk. A small border can sit
				// wholly inside a region even when all of its edge chunks are outside it.
				counter += 32;
				if (!unloadChunks()) return;
				if (trimChunks.size() == 1024)
				{
					if (!deleteRegionFile()) return;
				}
				else if (!trimChunks.isEmpty() && !wipeChunks()) return;
				nextFile();
				continue;
			}

			// check whether chunk is inside the border or not, add it to the "trim" list if not
			CoordXZ chunk = regionChunks.get(currentChunk);
			if (!isChunkInsideBorder(chunk))
				trimChunks.add(chunk);

			currentChunk++;
			counter++;
		}

		reportTotal += counter;

		// ready for the next iteration to run
		readyToGo = true;
	}

	// Advance to the next region file. Returns true if successful, false if the next file isn't accessible for any reason
	private boolean nextFile()
	{
		reportTotal = currentRegion * 3072;
		currentRegion++;
		regionX = regionZ = currentChunk = 0;
		regionChunks = new ArrayList<CoordXZ>(1024);
		trimChunks = new ArrayList<CoordXZ>(1024);

		// have we already handled all region files?
		if (currentRegion >= worldData.regionFileCount())
		{	// hey, we're done
			paused = true;
			readyToGo = false;
			finish();
			return false;
		}

		counter += 16;

		// get the X and Z coordinates of the current region
		CoordXZ coord = worldData.regionFileCoordinates(currentRegion);
		if (coord == null)
		{
			sendMessage("Region file coordinates are unavailable; trim stopped.");
			stop();
			return false;
		}

		regionX = coord.x;
		regionZ = coord.z;
		return true;
	}

	// add just the 4 corner chunks of the region; can determine if entire region is _inside_ the border 
	private void addCornerChunks()
	{
		regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX), CoordXZ.regionToChunk(regionZ)));
		regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX) + 31, CoordXZ.regionToChunk(regionZ)));
		regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX), CoordXZ.regionToChunk(regionZ) + 31));
		regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX) + 31, CoordXZ.regionToChunk(regionZ) + 31));
	}

	// add all chunks along the 4 edges of the region (minus the corners); can determine if entire region is _outside_ the border 
	private void addEdgeChunks()
	{
		int chunkX = 0, chunkZ;

		for (chunkZ = 1; chunkZ < 31; chunkZ++)
		{
			regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX)+chunkX, CoordXZ.regionToChunk(regionZ)+chunkZ));
		}
		chunkX = 31;
		for (chunkZ = 1; chunkZ < 31; chunkZ++)
		{
			regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX)+chunkX, CoordXZ.regionToChunk(regionZ)+chunkZ));
		}
		chunkZ = 0;
		for (chunkX = 1; chunkX < 31; chunkX++)
		{
			regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX)+chunkX, CoordXZ.regionToChunk(regionZ)+chunkZ));
		}
		chunkZ = 31;
		for (chunkX = 1; chunkX < 31; chunkX++)
		{
			regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX)+chunkX, CoordXZ.regionToChunk(regionZ)+chunkZ));
		}
		counter += 4;
	}

	// add the remaining interior chunks (after corners and edges)
	private void addInnerChunks()
	{
		for (int chunkX = 1; chunkX < 31; chunkX++)
		{
			for (int chunkZ = 1; chunkZ < 31; chunkZ++)
			{
				regionChunks.add(new CoordXZ(CoordXZ.regionToChunk(regionX)+chunkX, CoordXZ.regionToChunk(regionZ)+chunkZ));
			}
		}
		counter += 32;
	}

	// make sure chunks set to be trimmed are not currently loaded by the server
	private boolean unloadChunks()
	{
		for (CoordXZ unload : trimChunks)
		{
			if (world.isChunkLoaded(unload.x, unload.z))
			{
				if (!world.unloadChunk(unload.x, unload.z, false) || world.isChunkLoaded(unload.x, unload.z))
				{
					sendMessage("Cannot unload chunk " + unload.x + "," + unload.z + "; trim stopped before editing its region file.");
					stop();
					return false;
				}
			}
		}
		counter += trimChunks.size();
		return true;
	}

	private boolean deleteRegionFile()
	{
		try
		{
			Files.delete(worldData.validatedRegionFile(currentRegion));
			reportTrimmedRegions++;
			return true;
		}
		catch (IOException | IllegalStateException ex)
		{
			sendMessage("Could not safely delete region file: " + ex.getMessage());
			stop();
			return false;
		}
	}

	// Edit only the location and timestamp entries of outside chunks in a validated MCA file.
	private boolean wipeChunks()
	{
		// since our stored chunk positions are based on world, we need to offset those to positions in the region file
		int offsetX = CoordXZ.regionToChunk(regionX);
		int offsetZ = CoordXZ.regionToChunk(regionZ);
		int chunkCount = 0;

		try (FileChannel file = FileChannel.open(worldData.validatedRegionFile(currentRegion),
			StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DSYNC, LinkOption.NOFOLLOW_LINKS))
		{
			for (CoordXZ wipe : trimChunks)
			{
				int localX = wipe.x - offsetX;
				int localZ = wipe.z - offsetZ;
				if (localX < 0 || localX >= 32 || localZ < 0 || localZ >= 32)
					throw new IOException("Chunk does not belong to region " + regionX + "," + regionZ);
				long wipePos = 4L * (localX + localZ * 32);
				ByteBuffer pointer = ByteBuffer.allocate(4);
				if (file.read(pointer, wipePos) != 4) throw new IOException("Could not read chunk pointer at " + wipePos);
				pointer.flip();
				// if the chunk pointer is empty (chunk doesn't technically exist), no need to wipe the already empty pointer
				if (pointer.getInt() == 0) continue;

				writeZero(file, wipePos);
				writeZero(file, 4096 + wipePos);
				chunkCount++;
			}
			file.force(true);

			// if DynMap is installed, re-render the trimmed chunks ... disabled since it's not currently working, oh well
//			DynMapFeatures.renderChunks(world.getName(), trimChunks);

			reportTrimmedChunks += chunkCount;
			counter += trimChunks.size();
			return true;
		}
		catch (IOException | IllegalStateException ex)
		{
			sendMessage("Could not safely trim region file: " + ex.getMessage());
			stop();
			return false;
		}
	}

	private static void writeZero(FileChannel file, long position) throws IOException
	{
		ByteBuffer zero = ByteBuffer.allocate(4);
		while (zero.hasRemaining())
		{
			int written = file.write(zero, position);
			if (written <= 0) throw new IOException("Could not write region header at " + position);
			position += written;
		}
	}

	private boolean isChunkInsideBorder(CoordXZ chunk)
	{
		return border.insideBorder(CoordXZ.chunkToBlock(chunk.x) + 8, CoordXZ.chunkToBlock(chunk.z) + 8);
	}

	// for successful completion
	public void finish()
	{
		reportTotal = reportTarget;
		reportProgress();

		boolean resetAndRestart = false;

		// If trim all types : region -> poi
		if (!resetAndRestart && typeToTrim == WorldFileDataType.ALL && currentType == WorldFileDataType.REGION )
		{
			currentType = WorldFileDataType.POI;
			worldData = WorldFileData.create(world, notifyPlayer, currentType, true);
			if (worldData == null)
			{
				sendMessage("POI files could not be validated; trim stopped.");
				stop();
				return;
			}
			resetAndRestart = true;
		}

		// If trim all types : poi -> entities
		if (!resetAndRestart && typeToTrim == WorldFileDataType.ALL && currentType == WorldFileDataType.POI )
		{
			currentType = WorldFileDataType.ENTITIES;
			worldData = WorldFileData.create(world, notifyPlayer, currentType, true);
			if (worldData == null)
			{
				sendMessage("Entity files could not be validated; trim stopped.");
				stop();
				return;
			}
			resetAndRestart = true;
		}

		if (resetAndRestart) 
		{
			currentRegion = -1;
			reportTarget = worldData.regionFileCount() * 3072;
			reportTotal = 0;
			reportTrimmedRegions = 0;
			reportTrimmedChunks = 0;
			counter = 0;
			if (nextFile())
			{
				paused = false;
				readyToGo = true;
			}
			return;
		}

		Bukkit.getServer().getPluginManager().callEvent(new WorldBorderTrimFinishedEvent(world, reportTotal));
		sendMessage("Task successfully completed!");
		sendMessage("NOTICE: it is recommended that you restart your server after a Trim, to be on the safe side.");
		if (DynMapFeatures.renderEnabled())
			sendMessage("This especially true with DynMap. You should also run a fullrender in DynMap for the trimmed world after restarting, so trimmed chunks are updated on the map.");
		this.stop();
	}

	// for cancelling prematurely
	public void cancel()
	{
		this.stop();
	}

	// we're done, whether finished or cancelled
	private void stop()
	{
		if (server == null)
			return;

		readyToGo = false;
		if (taskID != -1)
			server.getScheduler().cancelTask(taskID);
		server = null;
	}

	// is this task still valid/workable?
	public boolean valid()
	{
		return this.server != null;
	}

	// handle pausing/unpausing the task
	public void pause()
	{
		pause(!this.paused);
	}
	public void pause(boolean pause)
	{
		this.paused = pause;
		if (pause)
			reportProgress();
	}
	public boolean isPaused()
	{
		return this.paused;
	}

	// let the user know how things are coming along
	private void reportProgress()
	{
		lastReport = Config.Now();
		double perc = getPercentageCompleted();
		String type = "Regions";
		if (currentType == WorldFileDataType.POI) type = "POIs";
		if (currentType == WorldFileDataType.ENTITIES) type = "Entities";
		sendMessage("[" + type + "] " + reportTrimmedRegions + " entire region(s) and " + reportTrimmedChunks + " individual chunk(s) trimmed so far (" + Config.coord.format(perc) + "% done" + ")");
	}

	// send a message to the server console/log and possibly to an in-game player
	private void sendMessage(String text)
	{
		Config.log("[Trim] " + text);
		if (notifyPlayer != null)
			notifyPlayer.sendMessage("[Trim] " + text);
	}
	
	/**
	 * Get the percentage completed for the trim task.
	 * 
	 * @return Percentage
	 */
	public double getPercentageCompleted() {
		return reportTarget == 0 ? 100 : ((double) (reportTotal) / (double) reportTarget) * 100;
	}

	/**
	 * Amount of chunks completed for the trim task.
	 * 
	 * @return Number of chunks processed.
	 */
	public int getChunksCompleted() {
		return reportTotal;
	}

	/**
	 * Total amount of chunks that need to be trimmed for the trim task.
	 * 
	 * @return Number of chunks that need to be processed.
	 */
	public int getChunksTotal() {
		return reportTarget;
	}
}
