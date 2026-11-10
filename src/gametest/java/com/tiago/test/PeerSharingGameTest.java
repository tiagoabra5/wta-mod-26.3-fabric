package com.tiago.test;

import com.tiago.client.PlayerDimensions;
import com.tiago.client.peer.PeerSharing;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.resources.Identifier;

import java.util.UUID;
import java.util.function.BooleanSupplier;

public class PeerSharingGameTest implements FabricClientGameTest {
	public static volatile boolean blockServerSync;

	@Override
	public void runTest(ClientGameTestContext context) {
		blockServerSync = true;
		try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(settings -> settings.setAllowCommands(false)).create()) {
			var registries = world.getServer().computeOnServer(server -> server.registryAccess());
			var address = world.getServer().computeOnServer(server -> server.getConnection().startMemoryChannel());
			try (VanillaPeerClient peer = context.computeOnClient(client -> new VanillaPeerClient(address, registries))) {
				UUID self = context.computeOnClient(client -> client.player.getUUID());
				await(context, peer, () -> peer.id() != null && peer.get(self) != null && PeerSharing.get(peer.id()) != null);
				world.getServer().runOnServer(server -> {
					for (var player : server.getPlayerList().getPlayers()) {
						if (player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
							throw new AssertionError("Peer sharing test player has OP permissions");
						}
					}
				});
				world.getServer().runCommand("gamemode creative " + VanillaPeerClient.NAME);
				world.getServer().runCommand("execute in minecraft:the_nether run tp " + VanillaPeerClient.NAME + " 0 100 0");
				Identifier nether = Identifier.parse("minecraft:the_nether");
				await(context, peer, () -> nether.equals(PeerSharing.get(peer.id())));
				context.runOnClient(client -> {
					if (PlayerDimensions.isAvailable()) throw new AssertionError("Peer test used server-side WTA sync");
					if (client.level.getPlayerByUUID(peer.id()) != null) throw new AssertionError("Peer is still locally tracked");
					if (!nether.equals(PlayerDimensions.get(peer.id()))) throw new AssertionError("TAB cache did not use the remote peer's dimension");
				});
				context.getInput().holdKey(options -> options.keyPlayerList);
				context.waitTicks(5);
				context.takeScreenshot("wta-peer-nether-from-overworld");
				context.getInput().releaseKey(options -> options.keyPlayerList);

				world.getServer().runCommand("execute in minecraft:the_end run tp " + VanillaPeerClient.NAME + " 0 100 0");
				await(context, peer, () -> Identifier.parse("minecraft:the_end").equals(PeerSharing.get(peer.id())));
				context.getInput().holdKey(options -> options.keyPlayerList);
				context.waitTicks(5);
				context.takeScreenshot("wta-peer-end-from-overworld");
				context.getInput().releaseKey(options -> options.keyPlayerList);

				context.runOnClient(client -> peer.setSharing(false));
				await(context, peer, () -> PeerSharing.get(peer.id()) == null);
				context.runOnClient(client -> peer.setSharing(true));
				await(context, peer, () -> Identifier.parse("minecraft:the_end").equals(PeerSharing.get(peer.id())));
				if (peer.messagesSent() > 20) throw new AssertionError("Peer test produced excessive chat traffic");
			}
		} finally {
			blockServerSync = false;
		}
		context.waitFor(client -> !PlayerDimensions.isAvailable());
	}

	private static void await(ClientGameTestContext context, VanillaPeerClient peer, BooleanSupplier done) {
		for (int i = 0; i < 600; i++) {
			boolean finished = context.computeOnClient(client -> {
				peer.tick();
				if (PlayerDimensions.isAvailable()) throw new AssertionError("Unexpected WTA server synchronization");
				return done.getAsBoolean();
			});
			if (finished) return;
			context.waitTick();
		}
		throw new AssertionError("Timed out waiting for client-only peer sharing");
	}
}
