package com.tiago.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.tiago.network.DimensionSyncPayload;

public class WtaModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.INIT.register((handler, client) -> PlayerDimensions.clear());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PlayerDimensions.clear());
		ClientPlayNetworking.registerGlobalReceiver(DimensionSyncPayload.TYPE,
				(payload, context) -> PlayerDimensions.apply(payload));
	}
}
