package com.tiago.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.tiago.client.DimensionIcons;
import com.tiago.client.PlayerDimensions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
	// Vanilla reserves 13 pixels for padding and ping. Add a separate icon slot per column.
	@ModifyConstant(method = "extractRenderState", constant = @Constant(intValue = 13))
	private int wta$expandColumn(int original) {
		return original + (PlayerDimensions.canDisplay() ? DimensionIcons.SLOT_WIDTH : 0);
	}

	// These local slots belong to the player-row loop in Minecraft 26.3, after its background.
	@Inject(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/multiplayer/PlayerInfo;getProfile()Lcom/mojang/authlib/GameProfile;",
			ordinal = 1))
	private void wta$extractDimension(GuiGraphicsExtractor graphics, int width, Scoreboard scoreboard,
			Objective objective, CallbackInfo ci, @Local(index = 25) LocalIntRef x,
			@Local(index = 26) int y, @Local(index = 27) PlayerInfo player) {
		if (PlayerDimensions.canDisplay()) {
			DimensionIcons.extract(graphics, PlayerDimensions.get(player.getProfile().id()), x.get(), y);
			x.set(x.get() + DimensionIcons.SLOT_WIDTH);
		}
	}

	// The ping renderer uses the row origin, rather than the shifted head/name origin.
	@ModifyArg(method = "extractRenderState", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;extractPingIcon(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIILnet/minecraft/client/multiplayer/PlayerInfo;)V"),
			index = 2)
	private int wta$keepPingInRow(int x) {
		return x - (PlayerDimensions.canDisplay() ? DimensionIcons.SLOT_WIDTH : 0);
	}
}
