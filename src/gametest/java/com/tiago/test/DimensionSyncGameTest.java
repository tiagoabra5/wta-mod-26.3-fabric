package com.tiago.test;

import com.tiago.client.PlayerDimensions;
import com.tiago.network.DimensionSyncPayload;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Runs in a real client with an integrated server; excluded from the release jar. */
public class DimensionSyncGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitFor(client -> client.player != null && PlayerDimensions.isAvailable());
			waitForDimension(context, "minecraft:overworld");
			context.runOnClient(client -> {
                PlayerDimensions.clear();
                if (!PlayerDimensions.canDisplay() || !client.level.dimension().identifier().equals(PlayerDimensions.get(client.player.getUUID()))) {
                    throw new AssertionError("Client-only dimension lookup failed");
                }
                verifyCodec(client.level.registryAccess());
            });

			// An objective makes vanilla show TAB even with only one player in singleplayer.
			world.getServer().runCommand("scoreboard objectives add wta_test dummy");
			world.getServer().runCommand("scoreboard objectives setdisplay list wta_test");
			world.getServer().runCommand("scoreboard players set @a wta_test 7");
			context.getInput().holdKey(options -> options.keyPlayerList);
			context.waitTicks(5);
			context.takeScreenshot("wta-overworld");
			context.getInput().releaseKey(options -> options.keyPlayerList);

			world.getServer().runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
			waitForDimension(context, "minecraft:the_nether");
			context.getInput().holdKey(options -> options.keyPlayerList);
			context.waitTicks(5);
			context.takeScreenshot("wta-nether");
			context.getInput().releaseKey(options -> options.keyPlayerList);

			world.getServer().runCommand("execute in minecraft:the_end run tp @a 0 100 0");
			waitForDimension(context, "minecraft:the_end");
			context.getInput().holdKey(options -> options.keyPlayerList);
			context.waitTicks(5);
			context.takeScreenshot("wta-end");
			context.getInput().releaseKey(options -> options.keyPlayerList);
			context.runOnClient(client -> verifyCache());
		}
		context.waitFor(client -> !PlayerDimensions.isAvailable());
		// Opening another world must receive a fresh snapshot, without the old End entry.
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			waitForDimension(context, "minecraft:overworld");
		}
		context.waitFor(client -> !PlayerDimensions.isAvailable());
	}

	private static void waitForDimension(ClientGameTestContext context, String dimension) {
		Identifier expected = Identifier.parse(dimension);
		context.waitFor(client -> client.player != null && expected.equals(PlayerDimensions.get(client.player.getUUID())));
	}

	private static void verifyCodec(net.minecraft.core.RegistryAccess registries) {
		UUID player = UUID.randomUUID();
		UUID removed = UUID.randomUUID();
		DimensionSyncPayload expected = new DimensionSyncPayload(true,
				Map.of(player, Identifier.parse("custom:moon")), List.of(removed));
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
		try {
			DimensionSyncPayload.CODEC.encode(buf, expected);
			if (!expected.equals(DimensionSyncPayload.CODEC.decode(buf)) || buf.isReadable()) {
				throw new AssertionError("WTA packet did not round-trip");
			}
			buf.clear();
			buf.writeBoolean(false);
			buf.writeVarInt(4097);
			try {
				DimensionSyncPayload.CODEC.decode(buf);
				throw new AssertionError("Oversized player count was accepted");
			} catch (DecoderException expectedException) {
				// The decoder rejects the count before attempting to allocate/read the entries.
			}
		} finally {
			buf.release();
		}
	}

	private static void verifyCache() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		Identifier overworld = Identifier.parse("minecraft:overworld");
		Identifier nether = Identifier.parse("minecraft:the_nether");
		PlayerDimensions.apply(new DimensionSyncPayload(true, Map.of(first, overworld, second, overworld), List.of()));
		PlayerDimensions.apply(new DimensionSyncPayload(false, Map.of(first, nether), List.of(second)));
		if (!nether.equals(PlayerDimensions.get(first)) || PlayerDimensions.get(second) != null) {
			throw new AssertionError("WTA delta did not update/remove the correct players");
		}
		PlayerDimensions.apply(new DimensionSyncPayload(true, Map.of(second, overworld), List.of()));
		if (PlayerDimensions.get(first) != null || !overworld.equals(PlayerDimensions.get(second))) {
			throw new AssertionError("WTA snapshot retained stale players");
		}
	}
}
