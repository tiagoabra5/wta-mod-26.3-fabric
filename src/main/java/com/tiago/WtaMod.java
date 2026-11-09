package com.tiago;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import com.tiago.network.DimensionSyncPayload;
import com.tiago.server.DimensionSync;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WtaMod implements ModInitializer {
	public static final String MOD_ID = "wta-mod";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(DimensionSyncPayload.TYPE, DimensionSyncPayload.CODEC);
		DimensionSync.register();
		LOGGER.info("WTA — Where They Are initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
