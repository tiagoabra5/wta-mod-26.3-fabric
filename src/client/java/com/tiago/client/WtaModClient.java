package com.tiago.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.tiago.network.DimensionSyncPayload;
import com.tiago.client.peer.PeerSharing;

public class WtaModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.INIT.register((handler, client) -> { PlayerDimensions.clear(); PeerSharing.reset(); });
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { PlayerDimensions.clear(); PeerSharing.reset(); });
		ClientPlayNetworking.registerGlobalReceiver(DimensionSyncPayload.TYPE,
				(payload, context) -> PlayerDimensions.apply(payload));
		PeerSharing.register();
	}
}
