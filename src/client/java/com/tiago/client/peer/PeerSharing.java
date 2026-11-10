package com.tiago.client.peer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.tiago.WtaMod;
import com.tiago.client.PlayerDimensions;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PeerSharing {
	private static final PeerSession SESSION = new PeerSession();
	private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("wta-mod.json");

	private PeerSharing() {}
	static long now() { return System.nanoTime() / 1_000_000; }

	public static void register() {
		loadConfig();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			var connection = client.getConnection();
			if (connection == null || client.player == null || client.level == null) return;
			if (client.options.chatVisibility().get() != net.minecraft.world.entity.player.ChatVisiblity.FULL) return;
			Map<UUID, String> online = new HashMap<>();
			connection.getOnlinePlayers().forEach(info -> {
				UUID id = info.getProfile().id();
				if (!client.isBlocked(id) && !client.isFriendOnlyRestricted(id)) online.put(id, info.getProfile().name());
			});
			String whisper = null;
			for (String candidate : new String[]{"minecraft:msg", "msg", "tell", "w"}) {
				if (connection.getCommands().getRoot().getChild(candidate) != null) { whisper = candidate; break; }
			}
			// Do not advertise on a server that offers no whisper command.
			if (whisper == null) return;
			String command = whisper;
			SESSION.tick(client.player.getUUID(), client.level.dimension().identifier(), online, now(),
					PlayerDimensions.isAvailable(), new PeerSession.Transport() {
						public void announce(String message) {
							connection.sendChat(message);
						}
						public void whisper(String name, String message) { connection.sendCommand(command + " " + name + " " + message); }
					});
		});
		ClientReceiveMessageEvents.ALLOW_CHAT.register((decorated, message, sender, type, received) -> {
			if (message == null || sender == null || !sender.id().equals(message.sender())) return true;
			PeerMessage decoded = PeerMessage.decode(message.signedContent());
			if (decoded == null) return true;
			var client = Minecraft.getInstance();
			if (client.player == null || client.getConnection() == null) return true;
			if (sender.id().equals(client.player.getUUID())) return false;
			if (client.isBlocked(sender.id()) || client.isFriendOnlyRestricted(sender.id())) return true;
			if (!type.chatType().is(ChatType.CHAT) && !type.chatType().is(ChatType.MSG_COMMAND_INCOMING)) return true;
			if (client.getConnection().getPlayerInfo(sender.id()) == null) return true;
			if (Duration.between(message.timeStamp(), Instant.now()).abs().toSeconds() <= 90) {
				SESSION.receive(sender.id(), decoded, now());
			}
			return false;
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
				ClientCommands.literal("wta")
						.then(ClientCommands.literal("status").executes(command -> {
							command.getSource().sendFeedback(Component.translatable("wta.status", SESSION.isEnabled(), SESSION.peerCount(), PlayerDimensions.isAvailable()));
							return 1;
						}))
						.then(ClientCommands.literal("sync").executes(command -> {
							SESSION.discover();
							command.getSource().sendFeedback(Component.translatable("wta.searching"));
							return 1;
						}))
						.then(ClientCommands.literal("sharing")
								.then(ClientCommands.literal("on").executes(command -> { setEnabled(true); return 1; }))
								.then(ClientCommands.literal("off").executes(command -> { setEnabled(false); return 1; })))));
	}

	public static Identifier get(UUID player) { return SESSION.get(player, now()); }
	public static void reset() { SESSION.reset(); }

	private static void setEnabled(boolean enabled) {
		SESSION.setEnabled(enabled);
		JsonObject config = new JsonObject();
		config.addProperty("peerSharing", enabled);
		try {
			Files.createDirectories(CONFIG.getParent());
			Files.writeString(CONFIG, new Gson().toJson(config));
		} catch (IOException exception) {
			WtaMod.LOGGER.warn("Could not save WTA sharing preference", exception);
		}
		Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.translatable(enabled ? "wta.enabled" : "wta.disabled"));
	}

	private static void loadConfig() {
		if (!Files.isRegularFile(CONFIG)) return;
		try {
			JsonObject config = new Gson().fromJson(Files.readString(CONFIG), JsonObject.class);
			if (config != null && config.has("peerSharing")) SESSION.setEnabled(config.get("peerSharing").getAsBoolean());
		} catch (IOException | RuntimeException exception) {
			WtaMod.LOGGER.warn("Could not read WTA sharing preference; using default", exception);
		}
	}
}
