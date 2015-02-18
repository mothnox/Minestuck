package com.mraof.minestuck.world;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.mraof.minestuck.Minestuck;
import com.mraof.minestuck.network.LandRegisterPacket;
import com.mraof.minestuck.util.Debug;
import com.mraof.minestuck.world.gen.ChunkProviderLands;
import com.mraof.minestuck.world.gen.lands.LandAspectRegistry;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLLog;

public class MinestuckDimensionHandler
{
	
	private static Hashtable<Byte, LandAspectRegistry.AspectCombination> lands = new Hashtable<Byte, LandAspectRegistry.AspectCombination>();
	private static Hashtable<Byte, BlockPos> spawnpoints = new Hashtable<Byte, BlockPos>();
	
	public static void unregisterDimensions()
	{
		for(Iterator<Byte> iterator = lands.keySet().iterator(); iterator.hasNext();)
		{
			byte b = iterator.next();
			if(DimensionManager.isDimensionRegistered(b))
			{
				DimensionManager.unregisterDimension(b);
			}
		}
		lands.clear();
		spawnpoints.clear();
	}
	
	public static void saveData(NBTTagCompound nbt)
	{
		NBTTagList list = new NBTTagList();
		for(Map.Entry<Byte, LandAspectRegistry.AspectCombination> entry : lands.entrySet())
		{
			NBTTagCompound tagCompound = new NBTTagCompound();
			tagCompound.setByte("dimID", entry.getKey());
			tagCompound.setString("type", "land");
			tagCompound.setString("aspect1", entry.getValue().aspect1.getPrimaryName());
			tagCompound.setString("aspect2", entry.getValue().aspect2.getPrimaryName());
			BlockPos spawn = spawnpoints.get(entry.getKey());
			tagCompound.setInteger("spawnX", spawn.getX());
			tagCompound.setInteger("spawnY", spawn.getY());
			tagCompound.setInteger("spawnZ", spawn.getZ());
			list.appendTag(tagCompound);
		}
		nbt.setTag("dimensionData", list);
	}
	
	public static void loadData(NBTTagCompound nbt)
	{
		NBTTagList list = nbt.getTagList("dimensionData", new NBTTagCompound().getId());
		for(int i = 0; i < list.tagCount(); i++)
		{
			NBTTagCompound tagCompound = list.getCompoundTagAt(i);
			byte dim = tagCompound.getByte("dimID");
			String type = tagCompound.getString("type");
			if(type.equals("land"))
			{
				String name1 = tagCompound.getString("aspect1");
				String name2 = tagCompound.getString("aspect2");
				LandAspectRegistry.AspectCombination aspects = new LandAspectRegistry.AspectCombination(LandAspectRegistry.fromName(name1), LandAspectRegistry.fromName2(name2));
				BlockPos spawn = new BlockPos(tagCompound.getInteger("spawnX"), tagCompound.getInteger("spawnY"), tagCompound.getInteger("spawnZ"));
				
				lands.put(dim, aspects);
				spawnpoints.put(dim, spawn);
				DimensionManager.registerDimension(dim, Minestuck.landProviderTypeId);
			}
		}
	}
	
	public static void registerLandDimension(byte dimensionId, LandAspectRegistry.AspectCombination landAspects)
	{
		if(landAspects == null)
			throw new IllegalArgumentException("May not register a land aspect combination that is null");
		if(!lands.containsKey(dimensionId) && !DimensionManager.isDimensionRegistered(dimensionId))
		{
			lands.put(dimensionId, landAspects);
			DimensionManager.registerDimension(dimensionId, Minestuck.landProviderTypeId);
		}
		else FMLLog.warning("[Minestuck] Did not register land dimension with id %d.", dimensionId);
	}
	
	public static LandAspectRegistry.AspectCombination getAspects(byte dimensionId)
	{
		LandAspectRegistry.AspectCombination aspects = lands.get(dimensionId);
		
		if(aspects == null)
		{
			FMLLog.warning("[Minestuck] Tried to access land aspect for dimension %d, but didn't find any!", dimensionId);
		}
		
		return aspects;
	}
	
	public static boolean isLandDimension(byte dimensionId)
	{
		return lands.containsKey(dimensionId);
	}
	
	public static Set<Map.Entry<Byte, LandAspectRegistry.AspectCombination>> getLandSet()
	{
		return lands.entrySet();
	}
	
	public static void onLandPacket(LandRegisterPacket packet)
	{
		if(Minestuck.isServerRunning)
			return;
		lands.clear();
		spawnpoints.clear();
		
		lands.putAll(packet.aspectMap);
		spawnpoints.putAll(packet.spawnMap);
		
		for(byte dim : lands.keySet())
		{
			if(!DimensionManager.isDimensionRegistered(dim))
				DimensionManager.registerDimension(dim, Minestuck.landProviderTypeId);
		}
	}
	
	public static BlockPos getSpawn(byte dim)
	{
		return spawnpoints.get(dim);
	}
	
	public static void setSpawn(byte dim, BlockPos spawnpoint)
	{
		spawnpoints.put(dim, spawnpoint);
	}
}
