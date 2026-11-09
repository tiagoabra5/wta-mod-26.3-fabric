package com.tiago.server;

import com.tiago.network.DimensionSyncPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DimensionSync {
	private final Map<UUID, Identifier> previous = new HashMap<>();

	private DimensionSync() {
	}

	public static void register() {
		DimensionSync sync = new DimensionSync();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler.player, DimensionSyncPayload.TYPE)) {
				ServerPlayNetworking.send(handler.player,
						new DimensionSyncPayload(true, snapshot(server), List.of()));
			}
		});
		// Comparing at the end of a tick also covers respawns, commands and portal travel.
		ServerTickEvents.END_SERVER_TICK.register(sync::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> sync.previous.clear());
	}

	static Map<UUID, Identifier> snapshot(MinecraftServer server) {
		Map<UUID, Identifier> result = new HashMap<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			result.put(player.getUUID(), player.level().dimension().identifier());
		}
		return result;
	}

	private void tick(MinecraftServer server) {
		Map<UUID, Identifier> current = snapshot(server);
		Map<UUID, Identifier> changed = new HashMap<>();
		current.forEach((id, dimension) -> {
			if (!dimension.equals(previous.get(id))) {
				changed.put(id, dimension);
			}
		});
		List<UUID> removed = previous.keySet().stream().filter(id -> !current.containsKey(id)).toList();
		if (changed.isEmpty() && removed.isEmpty()) {
			return;
		}

		DimensionSyncPayload update = new DimensionSyncPayload(false, changed, removed);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, DimensionSyncPayload.TYPE)) {
				ServerPlayNetworking.send(player, update);
			}
		}
		previous.clear();
		previous.putAll(current);
	}
}
