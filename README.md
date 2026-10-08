# PracticeBot

A Minecraft (Paper) plugin that adds a **practice bot** which behaves like a normal player and fights against you.

- The bot is a real fake player (via [Citizens](https://citizensnpcs.co/)) – it has a skin, gear, **sprints**, **jumps**, strafes and heals itself.
- **GUI**: choose a kit → choose a difficulty (Normal / Medium / Hard / Professional).
- **One arena per kit** (or more) – you create them with commands, and the plugin tells you the **next step** after every command.
- **20 languages** – set `language: cs` (or `de`, `es`, `fr`, `ja`, …) in `config.yml`.
- **Random spawns** – an arena can have several spawn pairs; each fight starts at a random one. Chunks are preloaded before the teleport, so a slow server never makes you wait.
- **Statistics, leaderboard, PlaceholderAPI, win rewards, fight scoreboard.**
- **Isolated fights**: multiple players can fight in the same arena at once. Everyone sees **only themselves and their own bot**; other players (and their bots) are invisible and cannot hurt each other.
- After the fight the player gets their inventory, location, HP, gamemode… back (the state is also saved to disk, so it survives a server crash).

## Requirements

- Java 21+
- (optional) PlaceholderAPI
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

### Random spawns (optional)

The pair from `setplayer` / `setbot` is spawn pair #1. To add more, run `/pbot arena addspawn <name>` **twice**: first standing where the player should spawn, then standing where the bot should spawn. Every fight then starts at a random pair (never the same one twice in a row). `/pbot arena clearspawns <name>` removes the extra pairs. Players never have to set anything.

### Chunk preloading

When a fight starts, the plugin loads the chunks around both spawns asynchronously **before** teleporting the player (and keeps them loaded during the fight). It also warms up the chunks of the kit's arenas while the player is still choosing a difficulty in the GUI, so the fight starts instantly even on a slow server.

> The arena should have a floor and walls/barriers around it – players cannot break or place blocks during a fight, so falling into the void = a loss.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/pbot` | Opens the GUI (kit → difficulty) | `practicebot.play` |
| `/pbot play <kit> <difficulty>` | Quick start without the GUI | `practicebot.play` |
| `/pbot leave` | Ends the fight | `practicebot.play` |
| `/pbot kits` | List of kits | `practicebot.play` |
| `/pbot stats [player]` | Wins, losses, win rate, streaks | `practicebot.play` |
| `/pbot top` | Top 10 by wins | `practicebot.play` |
| `/pbot arena create <name> <kit>` | Creates an arena | `practicebot.admin` |
| `/pbot arena setplayer <name>` | Sets the player spawn | `practicebot.admin` |
| `/pbot arena setbot <name>` | Sets the bot spawn | `practicebot.admin` |
| `/pbot arena addspawn <name>` | Adds an extra random spawn pair (run twice) | `practicebot.admin` |
| `/pbot arena clearspawns <name>` | Removes the extra spawn pairs | `practicebot.admin` |
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

- **Real healing** – in `nodebuff` the bot really throws a splash healing potion at its feet (it only affects the thrower). In `gapple` it holds the golden apple, eats for 1.6 s with sounds and crumbs, is slowed down and cannot attack while eating, then gets Regeneration + Absorption. (The arm "eating" animation itself needs NMS, so the apple is shown in its hand instead.)
- **Ender pearls** – in `nodebuff` the bot throws a real pearl toward you when you are far away (max 4 per fight) and teleports to where it lands, taking pearl damage like in vanilla.

## Languages

Set `language:` in `config.yml` to one of: `en, cs, sk, de, es, fr, it, pt, pl, ru, uk, nl, sv, tr, ja, ko, zh, hu, ro, da` (aliases like `cz`, `jp`, `ua`, `en_US` also work). The files are copied to `plugins/PracticeBot/lang/`, where you can edit any text; missing keys fall back to the built-in text. Run `/pbot reload` after changes.

## Statistics, scoreboard and rewards

- **Stats** are saved to `stats.yml` (wins, losses, streak, best streak). Leaving a fight early counts as neither a win nor a loss.
- **Scoreboard** during a fight: time, bot HP, your HP, your CPS and your win streak (`scoreboard: false` disables it). Your previous scoreboard is restored afterwards.
- **Rewards**: console commands in `config.yml` under `rewards:` (`on-win` / `on-loss`, for `ALL`, a difficulty or a kit). Placeholders `{player} {kit} {difficulty} {streak}`. Example: `PROFESSIONAL: ["give {player} diamond 1"]`.
- **PlaceholderAPI** (optional): `%practicebot_wins%`, `%practicebot_losses%`, `%practicebot_fights%`, `%practicebot_streak%`, `%practicebot_beststreak%`, `%practicebot_winrate%`, and the leaderboard `%practicebot_top_name_1%`, `%practicebot_top_wins_1%`, `%practicebot_top_best_1%` (positions 1-10).

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

- The bot AI is intentionally simple (Citizens navigation + custom attack/strafe/heal logic). It cannot shoot a bow.
- The bot's eating has no real arm animation (that needs NMS) – it holds the apple and plays the sounds/particles instead.
- The bot attacks through the server's `attack()`, so vanilla attack cooldown applies at high CPS (fast hits deal less damage, just like for a player).
- Players in a fight cannot see other players on the server (including the tab list) until the fight ends.

## License

MIT
