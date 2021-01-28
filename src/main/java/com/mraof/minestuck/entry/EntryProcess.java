package com.mraof.minestuck.entry;

import com.mraof.minestuck.MinestuckConfig;
import com.mraof.minestuck.block.GateBlock;
import com.mraof.minestuck.block.MSBlocks;
import com.mraof.minestuck.computer.editmode.ServerEditHandler;
import com.mraof.minestuck.player.IdentifierHandler;
import com.mraof.minestuck.player.PlayerIdentifier;
import com.mraof.minestuck.skaianet.SburbConnection;
import com.mraof.minestuck.skaianet.SkaianetHandler;
import com.mraof.minestuck.skaianet.TitleSelectionHook;
import com.mraof.minestuck.tileentity.ComputerTileEntity;
import com.mraof.minestuck.tileentity.GateTileEntity;
import com.mraof.minestuck.tileentity.TransportalizerTileEntity;
import com.mraof.minestuck.util.Teleport;
import com.mraof.minestuck.world.GateHandler;
import com.mraof.minestuck.world.MSDimensions;
import com.mraof.minestuck.world.storage.MSExtraData;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class EntryProcess
{
	private static final Logger LOGGER = LogManager.getLogger();
	
	private static final Set<EntryBlockProcessing> blockProcessors = new HashSet<>();
	
	/**
	 * Not thread-safe. Make sure to only call this on the main thread
	 */
	public static void addBlockProcessing(EntryBlockProcessing processing)
	{
		blockProcessors.add(processing);
	}
	
	private EntryPositioning positioning;
	private boolean creative;
	
	public void onArtifactActivated(ServerPlayerEntity player)
	{
		long totalTime = System.currentTimeMillis();
		try
		{
			if(player.world.getDimension().getType() != DimensionType.THE_NETHER)
			{
				if(!TitleSelectionHook.performEntryCheck(player))
					return;
				
				PlayerIdentifier identifier = IdentifierHandler.encode(player);
				Optional<SburbConnection> c = SkaianetHandler.get(player.world).getPrimaryConnection(identifier, true);
				
				//Only performs Entry if you have no connection, haven't Entered, or you're not in a Land and additional Entries are permitted.
				if(!c.isPresent() || !c.get().hasEntered() || !MinestuckConfig.SERVER.stopSecondEntry.get() && !MSDimensions.isLandDimension(player.world.getDimension().getType()))
				{
					ServerWorld oldWorld = (ServerWorld) player.world;
					
					positioning = new DefaultPositioning(player.getPosition(), oldWorld);
					
					if(!canModifyEntryBlocks(player.world, player))
					{
						player.sendMessage(new StringTextComponent("You are not allowed to enter here."));
						return;
					}
					
					if(c.isPresent() && c.get().hasEntered())
					{
						ServerWorld landWorld = Objects.requireNonNull(player.getServer()).getWorld(c.get().getClientDimension());
						if(landWorld == null)
							return;
						
						//Teleports the player to their home in the Medium, without any bells or whistles.
						BlockPos pos = landWorld.getDimension().getSpawnPoint();
						Teleport.teleportEntity(player, landWorld, pos.getX() + 0.5F, pos.getY(), pos.getZ() + 0.5F, player.rotationYaw, player.rotationPitch);
						
						return;
					}
					
					DimensionType landDimension = SkaianetHandler.get(player.world).prepareEntry(identifier);
					if(landDimension == null)
						player.sendMessage(new StringTextComponent("Something went wrong while creating your Land. More details in the server console."));
					else
					{
						ServerWorld newWorld = Objects.requireNonNull(player.getServer()).getWorld(landDimension);
						if(newWorld == null)
							return;
						
						long time = System.currentTimeMillis();
						if(this.prepareDestination(player, oldWorld))
						{
							moveBlocks(oldWorld, newWorld);
							if(Teleport.teleportEntity(player, newWorld) != null)
							{
								finalizeDestination(player, oldWorld, newWorld);
								SkaianetHandler.get(player.world).onEntry(identifier);
							} else player.sendMessage(new StringTextComponent("Entry failed. Unable to teleport you!"));
						}
						time = System.currentTimeMillis() - time;
						LOGGER.debug("Actual entry time: {}", time);
					}
				}
			}
		} catch(Exception e)
		{
			LOGGER.error("Exception when {} tried to enter their land.", player.getName().getFormattedText(), e);
			player.sendMessage(new StringTextComponent("[Minestuck] Something went wrong during entry. "+ (player.getServer().isDedicatedServer()?"Check the console for the error message.":"Notify the server owner about this.")).setStyle(new Style().setColor(TextFormatting.RED)));
		}
		totalTime = System.currentTimeMillis() - totalTime;
		LOGGER.debug("Total entry time: {}", totalTime);
	}
	
	private boolean foundComputer = false;
	
	private boolean prepareDestination(ServerPlayerEntity player, ServerWorld world)
	{
		long time = System.currentTimeMillis();
		
		LOGGER.info("Starting entry for player {}", player.getName().getFormattedText());
		
		creative = player.interactionManager.isCreative();
		
		LOGGER.debug("Loading block movements...");
		
		if(!positioning.forEachBlockTry((pos, edge) -> makeBlockMove(pos, world, player)))
			return false;
		
		if(!foundComputer && MinestuckConfig.SERVER.needComputer.get())
		{
			player.sendStatusMessage(new StringTextComponent("There is no computer in range."), false);
			return false;
		}
		
		time = System.currentTimeMillis() - time;
		LOGGER.debug("Block move and computer-checking preparation time: {}", time);
		
		return true;
	}
	
	private boolean makeBlockMove(BlockPos pos, ServerWorld world, ServerPlayerEntity player)
	{
		pos = pos.toImmutable();
		BlockState block = world.getBlockState(pos);
		TileEntity te = world.getTileEntity(pos);
		
		Block gotBlock = block.getBlock();
		
		if(!creative && (gotBlock == Blocks.COMMAND_BLOCK || gotBlock == Blocks.CHAIN_COMMAND_BLOCK || gotBlock == Blocks.REPEATING_COMMAND_BLOCK))
		{
			player.sendStatusMessage(new StringTextComponent("You are not allowed to move command blocks."), false);
			return false;
		} else if(te instanceof ComputerTileEntity)        //If the block is a computer
		{
			if(!((ComputerTileEntity) te).owner.equals(IdentifierHandler.encode(player)))    //You can't Enter with someone else's computer
			{
				player.sendStatusMessage(new StringTextComponent("You are not allowed to move other players' computers."), false);
				return false;
			}
			
			foundComputer = true;    //You have a computer in range. That means you're taking your computer with you when you Enter. Smart move.
		}
		return true;
	}
	
	private void moveBlocks(ServerWorld originWorld, ServerWorld destinationWorld)
	{
		long time = System.currentTimeMillis();
		
		LOGGER.debug("Moving blocks...");
		
		BlockPos offset = positioning.getTeleportOffset();
		positioning.forEachBlock((pos, edge) -> {
			BlockState state = originWorld.getBlockState(pos);
			if(state.getBlock() != Blocks.BEDROCK && state.getBlock() != Blocks.NETHER_PORTAL)
				copyBlock(originWorld, pos, destinationWorld, pos.add(offset), state, edge);
		});
		
		time = System.currentTimeMillis() - time;
		LOGGER.debug("Block moving time: {}", time);
	}
	
	private void finalizeDestination(ServerPlayerEntity player, ServerWorld originWorld, ServerWorld landWorld)
	{
		long time = System.currentTimeMillis();
		LOGGER.debug("Teleporting entities...");
		List<Entity> entitiesToKeep = teleportEntities(player, originWorld, landWorld);
		time = System.currentTimeMillis() - time;
		LOGGER.debug("Entity teleporting time: {}", time);
		time = System.currentTimeMillis();
		
		LOGGER.debug("Removing original blocks");
		removeOriginalBlocks(originWorld);
		time = System.currentTimeMillis() - time;
		LOGGER.debug("Block removing time: {}", time);
		time = System.currentTimeMillis();
		
		BlockPos offset = positioning.getTeleportOffset();
		player.setPositionAndUpdate(player.getPosX() + offset.getX(), player.getPosY() + offset.getY(), player.getPosZ() + offset.getZ());
		
		//Remove entities that were generated in the process of teleporting entities and removing blocks.
		// This is usually caused by "anchored" blocks being updated between the removal of their anchor and their own removal.
		if(!creative || MinestuckConfig.SERVER.entryCrater.get())
		{
			LOGGER.debug("Removing entities left in the crater...");
			removeCreatedEntities(originWorld, player, entitiesToKeep);
		}
		
		LOGGER.debug("Placing gates...");
		placeGates(landWorld);
		
		
		MSExtraData.get(landWorld).addPostEntryTask(new PostEntryTask(landWorld.getDimension().getType(), positioning));
		MSDimensions.getLandInfo(landWorld).setSpawn(MathHelper.floor(player.getPosY()));
		
		LOGGER.info("Entry finished");
		time = System.currentTimeMillis() - time;
		LOGGER.debug("Remaining tasks time: {}", time);
	}
	
	private List<Entity> teleportEntities(ServerPlayerEntity player, ServerWorld originWorld, ServerWorld landWorld)
	{
		BlockPos offset = positioning.getTeleportOffset();
		List<Entity> list = positioning.getOtherEntitiesToTeleport(player, originWorld);
		Iterator<Entity> iterator = list.iterator();
		while (iterator.hasNext())
		{
			Entity e = iterator.next();
			if(MinestuckConfig.SERVER.entryCrater.get() || e instanceof PlayerEntity || !creative && e instanceof ItemEntity)
			{
				if(e instanceof PlayerEntity && ServerEditHandler.getData((PlayerEntity) e) != null)
					ServerEditHandler.reset(ServerEditHandler.getData((PlayerEntity) e));
				else
				{
					Teleport.teleportEntity(e, landWorld, e.getPosX() + offset.getX(), e.getPosY() + offset.getY(), e.getPosZ() + offset.getZ());
				}
				//These entities should no longer be in the world, and this list is later used for entities that *should* remain.
				iterator.remove();
			} else    //Copy instead of teleport
			{
				Entity newEntity = e.getType().create(landWorld);
				if(newEntity != null)
				{
					CompoundNBT nbttagcompound = new CompoundNBT();
					e.writeWithoutTypeId(nbttagcompound);
					nbttagcompound.remove("Dimension");
					newEntity.read(nbttagcompound);
					newEntity.dimension = landWorld.getDimension().getType();
					newEntity.setPosition(newEntity.getPosX() + offset.getX(), newEntity.getPosY() + offset.getY(), newEntity.getPosZ() + offset.getZ());
					landWorld.addEntity(newEntity);
				}
			}
		}
		return list;
	}
	
	private void removeOriginalBlocks(ServerWorld originWorld)
	{
		positioning.forEachBlock((pos, edge) -> {
			removeTileEntity(originWorld, pos, creative);	//Tile entities need special treatment
			
			if(MinestuckConfig.SERVER.entryCrater.get() && originWorld.getBlockState(pos).getBlock() != Blocks.BEDROCK)
				originWorld.setBlockState(pos, Blocks.AIR.getDefaultState(), edge ? Constants.BlockFlags.DEFAULT : Constants.BlockFlags.BLOCK_UPDATE);
		});
	}
	
	/**
	 * Determines if it is appropriate to remove the tile entity in the specified location,
	 * and removes both the tile entity and its corresponding block if so.
	 * This method is expressly designed to prevent drops from appearing when the block is removed.
	 * It will also deliberately trigger block updates based on the removal of the tile entity's block.
	 * @param worldserver0 The world where the tile entity is located
	 * @param pos The position at which the tile entity is located
	 * @param creative Whether or not creative-mode rules should be employed
	 */
	private static void removeTileEntity(ServerWorld worldserver0, BlockPos pos, boolean creative)
	{
		TileEntity tileEntity = worldserver0.getTileEntity(pos);
		if(tileEntity != null)
		{
			if(MinestuckConfig.SERVER.entryCrater.get() || !creative)
			{
				String name = worldserver0.getBlockState(pos).getBlock().getRegistryName().toString();
				try {
					worldserver0.removeTileEntity(pos);
					worldserver0.removeBlock(pos, true);
				} catch (Exception e) {
					LOGGER.warn("Exception encountered when removing tile entity " + name + " during entry:", e);
				}
			} else
			{
				if(tileEntity instanceof ComputerTileEntity)	//Avoid duplicating computer data when a computer is kept in the overworld
					((ComputerTileEntity) tileEntity).programData = new CompoundNBT();
				else if(tileEntity instanceof TransportalizerTileEntity)
					worldserver0.removeTileEntity(pos);
			}
		}
	}
	
	private void removeCreatedEntities(ServerWorld originWorld, ServerPlayerEntity player, List<Entity> entitiesToKeep)
	{
		List<Entity> removalList = positioning.getOtherEntitiesToTeleport(player, originWorld);
		
		//We check if the old list contains the entity, because that means it was there before the entities were teleported and blocks removed.
		// This can be caused by them being outside the Entry radius but still within the AABB,
		// Or by the player being in creative mode, or having entryCrater disabled, etc.
		// Ultimately, this means that the entity has already been taken care of as much as it needs to be, and it is inappropriate to remove the entity.
		removalList.removeAll(entitiesToKeep);
		
		Iterator<Entity> iterator = removalList.iterator();
		if(MinestuckConfig.SERVER.entryCrater.get())
		{
			while (iterator.hasNext())
			{
				iterator.next().remove();
			}
		} else
		{
			while (iterator.hasNext())
			{
				Entity e = iterator.next();
				if(e instanceof ItemEntity)
					e.remove();
			}
		}
	}
	
	private boolean canModifyEntryBlocks(World world, PlayerEntity player)
	{
		return positioning.forEachXZTry((pos, edge) -> world.isBlockModifiable(player, pos));
	}
	
	private static void copyBlockDirect(IWorld world, IChunk cSrc, IChunk cDst, int xSrc, int ySrc, int zSrc, int xDst, int yDst, int zDst)
	{
		BlockPos dest = new BlockPos(xDst, yDst, zDst);
		ChunkSection blockStorageSrc = getBlockStorage(cSrc, ySrc >> 4);
		ChunkSection blockStorageDst = getBlockStorage(cDst, yDst >> 4);
		int y = yDst;
		xSrc &= 15; ySrc &= 15; zSrc &= 15; xDst &= 15; yDst &= 15; zDst &= 15;
		
		boolean isEmpty = blockStorageDst.isEmpty();
		BlockState state = blockStorageSrc.getBlockState(xSrc, ySrc, zSrc);
		blockStorageDst.setBlockState(xDst, yDst, zDst, state);
		if(isEmpty != blockStorageDst.isEmpty())
			world.getChunkProvider().getLightManager().func_215567_a(dest, blockStorageDst.isEmpty());	//I assume this adds or removes a light storage section here depending on if it is needed (because a section with just air doesn't have to be regarded)
		
		cDst.getHeightmap(Heightmap.Type.MOTION_BLOCKING).update(xDst, y, zDst, state);
		cDst.getHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES).update(xDst, y, zDst, state);
		cDst.getHeightmap(Heightmap.Type.OCEAN_FLOOR).update(xDst, y, zDst, state);
		cDst.getHeightmap(Heightmap.Type.WORLD_SURFACE).update(xDst, y, zDst, state);
	}
	
	private static ChunkSection getBlockStorage(IChunk c, int y)
	{
		ChunkSection section = c.getSections()[y];
		if(section == Chunk.EMPTY_SECTION)
			section = c.getSections()[y] = new ChunkSection(y << 4);
		return section;
	}
	
	public static void placeGates(ServerWorld world)
	{
		placeGate(GateHandler.Type.GATE_1, new BlockPos(0, GateHandler.gateHeight1, 0), world);
		placeGate(GateHandler.Type.GATE_2, new BlockPos(0, GateHandler.gateHeight2, 0), world);
	}
	
	private static void placeGate(GateHandler.Type gateType, BlockPos pos, ServerWorld world)
	{
		for(int i = 0; i < 9; i++)
			if(i == 4)
			{
				world.setBlockState(pos, MSBlocks.GATE.getDefaultState().cycle(GateBlock.MAIN), 0);
				TileEntity tileEntity = world.getTileEntity(pos);
				if(tileEntity instanceof GateTileEntity)
					((GateTileEntity) tileEntity).gateType = gateType;
				else LOGGER.error("Expected a gate tile entity when placing a gate, but found {}", tileEntity);
			}
			else world.setBlockState(pos.add((i % 3) - 1, 0, i/3 - 1), MSBlocks.GATE.getDefaultState(), 0);
	}
	
	static void copyBlock(ServerWorld worldFrom, BlockPos posFrom, ServerWorld worldTo, BlockPos posTo, BlockState state, boolean shouldUpdate)
	{
		if(worldTo.getBlockState(posTo).getBlock() == Blocks.BEDROCK)
			return;
		
		IChunk chunkTo = worldTo.getChunk(posTo), chunkFrom = worldFrom.getChunk(posFrom);
		if(shouldUpdate)
		{
			chunkTo.setBlockState(posTo, state, true);
		} else if(state == Blocks.AIR.getDefaultState())
		{
			worldTo.setBlockState(posTo, state, 0);
		} else
		{
			copyBlockDirect(worldTo, chunkFrom, chunkTo, posFrom.getX(), posFrom.getY(), posFrom.getZ(), posTo.getX(), posTo.getY(), posTo.getZ());
		}
		
		TileEntity tileEntity = chunkFrom.getTileEntity(posFrom);
		TileEntity newTE = null;
		if(tileEntity != null)
		{
			CompoundNBT nbt = new CompoundNBT();
			tileEntity.write(nbt);
			nbt.putInt("x", posTo.getX());
			nbt.putInt("y", posTo.getY());
			nbt.putInt("z", posTo.getZ());
			newTE = TileEntity.create(nbt);
			if(newTE != null)
				worldTo.setTileEntity(posTo, newTE);
			else LOGGER.warn("Unable to create a new tile entity {} when teleporting blocks to the medium!", tileEntity.getType().getRegistryName());
			
		}
		
		for(EntryBlockProcessing processing : blockProcessors)
		{
			processing.copyOver((ServerWorld) chunkFrom.getWorldForge(), posFrom, worldTo, posTo, state, tileEntity, newTE);
		}
	}
}