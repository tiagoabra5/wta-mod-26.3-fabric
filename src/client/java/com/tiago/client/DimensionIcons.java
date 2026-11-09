package com.tiago.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class DimensionIcons {
	public static final int SLOT_WIDTH = 10;
	private static final ItemStack OVERWORLD = new ItemStack(Items.GRASS_BLOCK);
	private static final ItemStack NETHER = new ItemStack(Items.NETHERRACK);
	private static final ItemStack END = new ItemStack(Items.END_STONE);
	private static final ItemStack OTHER = new ItemStack(Items.ENDER_PEARL);

	private DimensionIcons() {
	}

	public static void extract(GuiGraphicsExtractor graphics, Identifier dimension, int x, int y) {
		if (dimension == null) {
			graphics.text(net.minecraft.client.Minecraft.getInstance().font, "?", x + 2, y, 0xFFAAAAAA);
			return;
		}
		ItemStack icon = switch (dimension.toString()) {
			case "minecraft:overworld" -> OVERWORLD;
			case "minecraft:the_nether" -> NETHER;
			case "minecraft:the_end" -> END;
			default -> OTHER;
		};
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(0.5f, 0.5f);
		graphics.fakeItem(icon, 0, 0);
		graphics.pose().popMatrix();
	}
}
