package com.tiago.test;

import com.tiago.client.peer.PeerMessage;
import com.tiago.client.peer.PeerSession;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Deterministic checks for the two-client protocol, including packet loss and replay. */
public final class PeerSessionChecks {
	private static final UUID ALICE = UUID.randomUUID();
	private static final UUID BOB = UUID.randomUUID();
	private static final Map<UUID, String> ONLINE = Map.of(ALICE, "Alice", BOB, "Bob");
	private static final Identifier OVERWORLD = Identifier.parse("minecraft:overworld");
	private static final Identifier NETHER = Identifier.parse("minecraft:the_nether");
	private static final Identifier END = Identifier.parse("minecraft:the_end");
	private final PeerSession alice = new PeerSession();
	private final PeerSession bob = new PeerSession();
	private final List<Delivery> messages = new ArrayList<>();
	private final List<Delivery> history = new ArrayList<>();
	private Identifier bobDimension = OVERWORLD;
	private long now;

	private record Delivery(UUID sender, UUID recipient, String text, boolean broadcast) {}

	public static void run() {
		new PeerSessionChecks().verify();
	}

	private PeerSession.Transport transport(UUID sender, UUID recipient) {
		return new PeerSession.Transport() {
			public void announce(String text) { messages.add(new Delivery(sender, recipient, text, true)); }
			public void whisper(String name, String text) {
				check(ONLINE.get(recipient).equals(name), "Wrong whisper recipient");
				messages.add(new Delivery(sender, recipient, text, false));
			}
		};
	}

	private void advance(long duration, boolean deliver) {
		long end = now + duration;
		for (; now < end; now += 50) {
			alice.tick(ALICE, OVERWORLD, ONLINE, now, false, transport(ALICE, BOB));
			bob.tick(BOB, bobDimension, ONLINE, now, false, transport(BOB, ALICE));
			for (Delivery message : List.copyOf(messages)) {
				PeerMessage decoded = PeerMessage.decode(message.text);
				check(decoded != null && decoded.encode().equals(message.text), "Protocol round-trip failed");
				check(!message.broadcast || decoded.kind() == PeerMessage.Kind.HELLO, "Dimension leaked to public discovery");
				if (deliver) (message.recipient.equals(ALICE) ? alice : bob).receive(message.sender, decoded, now);
				history.add(message);
			}
			messages.clear();
		}
	}

	private void verify() {
		advance(8_000, true);
		check(OVERWORLD.equals(alice.get(BOB, now)) && OVERWORLD.equals(bob.get(ALICE, now)), "Discovery did not exchange dimensions");
		Delivery oldState = history.stream().filter(m -> m.sender.equals(BOB) && PeerMessage.decode(m.text).kind() == PeerMessage.Kind.STATE).findFirst().orElseThrow();
		bobDimension = NETHER;
		advance(3_000, true);
		check(NETHER.equals(alice.get(BOB, now)), "Cross-dimension update failed");
		alice.receive(BOB, PeerMessage.decode(oldState.text), now);
		check(NETHER.equals(alice.get(BOB, now)), "Replayed state replaced newer data");
		alice.receive(UUID.randomUUID(), PeerMessage.decode(oldState.text), now);
		check(alice.peerCount() == 1, "Unknown sender was accepted");
		bobDimension = END;
		advance(3_000, true);
		check(END.equals(alice.get(BOB, now)), "End update failed");
		bobDimension = Identifier.parse("custom:moon");
		advance(3_000, true);
		check(bobDimension.equals(alice.get(BOB, now)), "Custom dimension update failed");
		bob.setEnabled(false);
		advance(3_000, true);
		check(alice.get(BOB, now) == null, "Disabling sharing left stale data");
		bob.setEnabled(true);
		advance(8_000, true);
		check(bobDimension.equals(alice.get(BOB, now)), "Re-enabling/restarting did not reconnect");
		advance(PeerSession.EXPIRY_MS + 1_000, false);
		check(alice.get(BOB, now) == null, "Unreachable peer never expired");
		alice.reset();
		check(alice.peerCount() == 0 && alice.get(BOB, now) == null, "Reconnect retained a previous server's state");
		check(PeerMessage.decode("ordinary chat") == null, "Ordinary chat was treated as protocol");
		check(PeerMessage.decode(oldState.text.replace("[WTA:1]", "[WTA:2]")) == null, "Unsupported version accepted");
		check(PeerMessage.decode(oldState.text + " extra") == null, "Malformed fields accepted");
		check(PeerMessage.decode(oldState.text.replace("minecraft:overworld", "BAD:dimension")) == null, "Invalid identifier accepted");
		check(PeerMessage.decode("[WTA:1] S " + UUID.randomUUID() + " -1 minecraft:overworld") == null, "Negative sequence accepted");
		// Discovery messages must not refresh a stale dimension without an actual STATE update.
		PeerSession isolated = new PeerSession();
		isolated.tick(ALICE, OVERWORLD, ONLINE, 0, false, transport(ALICE, BOB));
		UUID session = UUID.randomUUID();
		isolated.receive(BOB, new PeerMessage(PeerMessage.Kind.STATE, session, 0, NETHER), 0);
		isolated.receive(BOB, new PeerMessage(PeerMessage.Kind.HELLO, session, 1, null), PeerSession.EXPIRY_MS - 100);
		check(isolated.get(BOB, PeerSession.EXPIRY_MS) == null, "Discovery kept an old dimension alive");
	}

	private static void check(boolean condition, String failure) {
		if (!condition) throw new AssertionError(failure);
	}
}
