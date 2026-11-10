<h1 align="center">WTA Mod</h1>
<p align="center">
  <strong>Where They Are</strong><br>
  Your friends' dimensions, one TAB away.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-26.3-5B8C3E?style=flat-square" alt="Minecraft 26.3">
  <img src="https://img.shields.io/badge/Loader-Fabric-C7B995?style=flat-square" alt="Fabric">
  <img src="https://img.shields.io/badge/Java-25-E58A2B?style=flat-square" alt="Java 25">
</p>

<p align="center">
  <strong>English</strong> · <a href="README.pt-BR.md">Português (Brasil)</a>
</p>

---

## A little icon. More context.

**WTA** adds a dimension icon to the left of each player's head in the **TAB** list. See who is in the Overworld, Nether, or End while keeping the familiar Minecraft layout.

```text
[Dimension icon] [Player head] Player name [Connection]
```

The icon updates automatically as dimension information changes. Names, teams, scores, and connection indicators keep their vanilla behavior.

## How it works

**Install it on your client and play.** WTA shows your dimension and those of nearby players currently tracked by your game. When a player's dimension is unavailable, it displays **`?`**.

**Client-to-client sharing, starting with 1.1.0:** players running the new version can exchange dimensions even when they are in different worlds. No OP, online map, or server installation is needed. Discovery uses a small public chat announcement; dimension updates use private messages. WTA hides these protocol messages on participating clients, but the server can log them.

> Both players need WTA 1.1.0 or newer with sharing enabled. The server must allow ordinary player chat and `/msg` (or `/tell`/`/w`). Chat plugins that replace player messages with anonymous system messages are not supported by this transport. A player without WTA can still be identified when locally tracked, or through the optional server component.

Server installation is **optional**. With WTA also on the server, the mod receives dimension information for all players, including those in other dimensions or outside your view distance.

> WTA shares dimension information only. Player coordinates are not transmitted.

### Sharing controls

- `/wta status` — show sharing status and the number of discovered peers.
- `/wta sync` — search for peers again.
- `/wta sharing on` / `/wta sharing off` — enable or disable sharing; saved between launches.

Sharing is enabled by default. Discovery normally takes a few seconds. Data expires if a peer stops responding, and is cleared when you leave the server. Updates are rate-limited to avoid flooding chat commands.

## Dimension icons

| Dimension | Icon |
| :--- | :--- |
| Overworld | Grass block |
| Nether | Netherrack |
| The End | End stone |
| Other dimensions | Ender pearl |
| Unknown | `?` |

## Download

[⬇ Download WTA](https://github.com/tiagoabra5/wta-mod-26.3-fabric/releases/latest)

Requires **Fabric API** for Minecraft 26.3.
