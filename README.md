# PracticeBot

A Minecraft (Paper) plugin that adds a **practice bot** which behaves like a normal player and fights against you.

- The bot is a real fake player (via [Citizens](https://citizensnpcs.co/)) – it has a skin, gear, **sprints**, **jumps**, strafes and heals itself.
- **GUI**: choose a kit → choose a difficulty (Normal / Medium / Hard / Professional).
- **One arena per kit** (or more) – you create them with commands, and the plugin tells you the **next step** after every command.
- **Isolated fights**: multiple players can fight in the same arena at once. Everyone sees **only themselves and their own bot**; other players (and their bots) are invisible and cannot hurt each other.
- After the fight the player gets their inventory, location, HP, gamemode… back (the state is also saved to disk, so it survives a server crash).

## Requirements

- Java 21+
- **Paper** 1.21.11 (uses the Paper API; Spigot is not enough)
- **Citizens** (a version matching your Minecraft version)

## Installation

1. Put `Citizens.jar` and `PracticeBot-x.y.z.jar` into the `plugins/` folder.
2. Restart the server.
3. Create arenas (see below).

> Updating from an older version? An outdated `config.yml` is automatically moved to `config-old.yml` and a new one is generated.

## Quick arena setup

Every arena command prints the **next step** you have to do, so you can just follow the chat messages.

```
/pbot arena create sword1 sword   # stand in the arena
/pbot arena setplayer sword1      # stand where the PLAYER should spawn
# go to the other side of the arena
/pbot arena setbot sword1         # stand where the BOT should spawn
```

An arena is usable by players only after **both** spawns are set. `/pbot arena list` and `/pbot arena info <name>` show unfinished arenas and what is still missing.

Do the same for the other kits (`axe`, `nodebuff`, `gapple`). You can have more arenas for one kit – players are spread to the one with the fewest fights. One shared arena is fine too: players cannot see or hit each other in it.

> The arena should have a floor and walls/barriers around it – players cannot break or place blocks during a fight, so falling into the void = a loss.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/pbot` | Opens the GUI (kit → difficulty) | `practicebot.play` |
| `/pbot play <kit> <difficulty>` | Quick start without the GUI | `practicebot.play` |
| `/pbot leave` | Ends the fight | `practicebot.play` |
| `/pbot kits` | List of kits | `practicebot.play` |
| `/pbot arena create <name> <kit>` | Creates an arena | `practicebot.admin` |
| `/pbot arena setplayer <name>` | Sets the player spawn | `practicebot.admin` |
| `/pbot arena setbot <name>` | Sets the bot spawn | `practicebot.admin` |
| `/pbot arena setkit <name> <kit>` | Changes the arena's kit | `practicebot.admin` |
| `/pbot arena info <name>` | Shows the arena status and next step | `practicebot.admin` |
| `/pbot arena delete <name>` | Deletes an arena | `practicebot.admin` |
| `/pbot arena list` / `tp <name>` | List / teleport | `practicebot.admin` |
| `/pbot reload` | Reloads config and arenas | `practicebot.admin` |

Aliases: `/practicebot`, `/pb`

## Kits

| Kit | Equipment |
|---|---|
| `sword` | diamond sword, iron armor, apples, steaks |
| `axe` | diamond axe, iron armor |
| `nodebuff` | diamond sword + diamond armor (Prot II), ender pearls, splash healing potions |
| `gapple` | diamond sword + diamond armor (Prot II), golden apples |

The bot in `nodebuff` and `gapple` heals itself at low HP.

## Bot behavior

- **Sprinting** – the bot sprints while chasing and fighting (sprint hits deal extra knockback). While falling in melee it briefly stops sprinting so its hit can be a critical hit, like in vanilla.
- **Jumping** – the bot bunny-hops while running toward you and jumps during the fight. Higher difficulties jump more often.
- **Strafing** – circles around you, keeping its distance.

All of this can be tuned in `config.yml` (`bot-sprint`, `sprint-speed-multiplier`, `bot-chase-jump` and `jump-chance` per difficulty).

## Difficulties

All values can be tuned in `config.yml` (`difficulties:`): reach, CPS, accuracy, speed, strafing, jumping, heal threshold and heal cooldown.

| | Accuracy | CPS | Reach |
|---|---|---|---|
| Normal | 50 % | 4 | 2.8 |
| Medium | 65 % | 6 | 3.0 |
| Hard | 80 % | 8 | 3.2 |
| Professional | 92 % | 11 | 3.4 |

## Build

```bash
mvn clean package
```

The jar is in `target/PracticeBot-1.0.0.jar`.

Different MC version? Change `paper.version` and `citizens.version` in `pom.xml`.

### GitHub Actions

The workflow `.github/workflows/build.yml` builds the jar on every push (download it in the *Actions → Artifacts* tab). When you push a tag like `v1.0.0`, the jar is automatically attached to a **Release**:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Known limitations

- The bot AI is intentionally simple (Citizens navigation + custom attack/strafe/heal logic). It cannot physically throw potions or shoot a bow – healing is simulated.
- The bot attacks through the server's `attack()`, so vanilla attack cooldown applies at high CPS (fast hits deal less damage, just like for a player).
- Players in a fight cannot see other players on the server (including the tab list) until the fight ends.

## License

MIT
