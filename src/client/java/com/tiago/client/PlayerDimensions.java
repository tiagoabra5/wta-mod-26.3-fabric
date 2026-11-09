package com.tiago.client;

import com.tiago.network.DimensionSyncPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerDimensions {
	private static final Map<UUID, Identifier> DIMENSIONS = new HashMap<>();
	private static boolean available;

	private PlayerDimensions() {
	}

	public static void apply(DimensionSyncPayload payload) {
		if (payload.reset()) {
			DIMENSIONS.clear();
		}
		payload.removed().forEach(DIMENSIONS::remove);
		DIMENSIONS.putAll(payload.dimensions());
		available = true;
	}

	public static Identifier get(UUID playerId) {
		var client = net.minecraft.client.Minecraft.getInstance();
		if (client.level != null && client.player != null) {
			if (client.player.getUUID().equals(playerId) || client.level.getPlayerByUUID(playerId) != null) {
				return client.level.dimension().identifier();
			}
		}
		return DIMENSIONS.get(playerId);
	}

	public static boolean isAvailable() {
		return available;
	}

	public static boolean canDisplay() {
		return net.minecraft.client.Minecraft.getInstance().level != null;
	}

	public static void clear() {
		DIMENSIONS.clear();
		available = false;
	}
}
