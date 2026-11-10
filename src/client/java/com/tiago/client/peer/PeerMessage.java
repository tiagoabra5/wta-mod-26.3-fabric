package com.tiago.client.peer;

import net.minecraft.resources.Identifier;

import java.util.UUID;

/** A small, versioned chat message. Identity comes from Minecraft's sender, never this text. */
public record PeerMessage(Kind kind, UUID session, long sequence, Identifier dimension) {
	private static final String PREFIX = "[WTA:1] ";
	public enum Kind { HELLO, STATE, LEAVE }

	public String encode() {
		String type = switch (kind) { case HELLO -> "H"; case STATE -> "S"; case LEAVE -> "L"; };
		return PREFIX + type + " " + session + " " + sequence + " " + (dimension == null ? "-" : dimension);
	}

	public static PeerMessage decode(String text) {
		if (text == null || text.length() > 256 || !text.startsWith(PREFIX)) return null;
		String[] fields = text.substring(PREFIX.length()).split(" ", -1);
		if (fields.length != 4 || fields[1].length() != 36 || fields[2].length() > 19) return null;
		try {
			Kind kind = switch (fields[0]) { case "H" -> Kind.HELLO; case "S" -> Kind.STATE; case "L" -> Kind.LEAVE; default -> null; };
			if (kind == null || !fields[2].matches("[0-9]+")) return null;
			UUID session = UUID.fromString(fields[1]);
			if (!session.toString().equals(fields[1])) return null;
			long sequence = Long.parseLong(fields[2]);
			Identifier dimension = null;
			if (kind == Kind.STATE) {
				if (fields[3].length() > 128 || !fields[3].contains(":")) return null;
				dimension = Identifier.tryParse(fields[3]);
				if (dimension == null) return null;
			} else if (!fields[3].equals("-")) return null;
			return new PeerMessage(kind, session, sequence, dimension);
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}
}
