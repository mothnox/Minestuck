package com.mraof.minestuck.block.multiblock;

import com.mraof.minestuck.block.TotemLatheBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.RegistryObject;

import static com.mraof.minestuck.block.MSBlockShapes.*;

public class TotemLatheMultiblock extends MachineMultiblock
{
	public final RegistryObject<Block> CARD_SLOT = register("totem_lathe_card_slot", () -> new TotemLatheBlock.Slot(this, TOTEM_LATHE_CARD_SLOT, Block.Properties.create(Material.IRON).hardnessAndResistance(3.0F).noDrops()));
	public final RegistryObject<Block> BOTTOM_LEFT = register("totem_lathe_bottom_left", () -> new TotemLatheBlock(this, TOTEM_LATHE_BOTTOM_LEFT, new BlockPos(1, 0, 0), Block.Properties.create(Material.IRON).hardnessAndResistance(3.0F).noDrops()));
	public final RegistryObject<Block> BOTTOM_RIGHT = register("totem_lathe_bottom_right", () -> new TotemLatheBlock(this, TOTEM_LATHE_BOTTOM_RIGHT, new BlockPos(2, 0, 0), Block.Properties.create(Material.IRON).hardnessAndResistance(3.0F).noDrops()));
	public final RegistryObject<Block> BOTTOM_CORNER = register("totem_lathe_bottom_corner", () -> new TotemLatheBlock(this, TOTEM_LATHE_BOTTOM_CORNER, new BlockPos(3, 0, 0), Block.Properties.create(Material.IRON).hardnessAndResistance(3.0F).noDrops()));
	public final RegistryObject<Block> MIDDLE = register("totem_lathe_middle", () -> new TotemLatheBlock(this, TOTEM_LATHE_MIDDLE, new BlockPos(0, -1, 0), Block.Properties.create(Material.IRON).hardnessAndResistance(3.0F).noDrops()));
	public final RegistryObject<Block> WHEEL = register("totem_lathe_wheel", () -> new TotemLatheBlock.Rod(this, TOTEM_LATHE_MIDDLE_RIGHT, new BlockPos(3, -1, 0), Block.Properties.create(Material.IRON).hardnessAndResistance(3.0F).noDrops()));
	public final RegistryObject<Block> ROD = register("totem_lathe_rod", () -> new TotemLatheBlock.Rod(this, TOTEM_LATHE_ROD_LEFT, new BlockPos(1, -1, 0), Block.Properties.create(Material.IRON).hardnessAndResistance(3.0F).noDrops()));
	public final RegistryObject<Block> DOWEL_ROD = register("totem_lathe_dowel_rod", () -> new TotemLatheBlock.DowelRod(this, TOTEM_LATHE_ROD_RIGHT, new BlockPos(2, -1, 0), Block.Properties.create(Material.IRON).hardnessAndResistance(3.0F).noDrops()));
	public final RegistryObject<Block> TOP_CORNER = register("totem_lathe_top_corner", () -> new TotemLatheBlock(this,TOTEM_LATHE_TOP_LEFT, new BlockPos(0, -2, 0), Block.Properties.create(Material.IRON).hardnessAndResistance(3.0F).noDrops()));
	public final RegistryObject<Block> TOP = register("totem_lathe_top", () -> new TotemLatheBlock(this, TOTEM_LATHE_TOP_MIDDLE, new BlockPos(1, -2, 0), Block.Properties.create(Material.IRON).hardnessAndResistance(3.0F).noDrops()));
	public final RegistryObject<Block> CARVER = register("totem_lathe_carver", () -> new TotemLatheBlock(this, TOTEM_LATHE_CARVER, new BlockPos(2, -2, 0), Block.Properties.create(Material.IRON).hardnessAndResistance(3.0F).noDrops()));
	
	public TotemLatheMultiblock(String modId)
	{
		super(modId);
		registerPlacement(new BlockPos(3, 0, 0), applyDirection(CARD_SLOT, Direction.NORTH));
		registerPlacement(new BlockPos(2, 0, 0), applyDirection(BOTTOM_LEFT, Direction.NORTH));
		registerPlacement(new BlockPos(1, 0, 0), applyDirection(BOTTOM_RIGHT, Direction.NORTH));
		registerPlacement(new BlockPos(0, 0, 0), applyDirection(BOTTOM_CORNER, Direction.NORTH));
		registerPlacement(new BlockPos(3, 1, 0), applyDirection(MIDDLE, Direction.NORTH));
		registerPlacement(new BlockPos(2, 1, 0), applyDirection(ROD, Direction.NORTH));
		registerPlacement(new BlockPos(0, 1, 0), applyDirection(WHEEL, Direction.NORTH));
		registerPlacement(new BlockPos(3, 2, 0), applyDirection(TOP_CORNER, Direction.NORTH));
		registerPlacement(new BlockPos(2, 2, 0), applyDirection(TOP, Direction.NORTH));
		registerPlacement(new BlockPos(1, 2, 0), applyDirection(CARVER, Direction.NORTH));
	}
}