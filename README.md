# PracticeBot

Minecraft plugin (Paper), který přidá **tréninkového bota**, jenž se chová jako normální hráč a bojuje proti tobě.

- Bot je skutečný fake hráč (přes [Citizens](https://citizensnpcs.co/)) – má skin, vybavu, pohybuje se, strafuje, skáče, lécí se.
- **GUI**: výběr kitu → výběr obtížnosti (Normal / Medium / Hard / Professional).
- **Aréna pro každý kit** – vytvoříš si je příkazy.
- **Izolované souboje**: víc hráčů může hrát stejný kit ve stejné aréně naráz. Každý vidí **jen sebe a svého bota**, ostatní hráče (i jejich boty) nevidí a nemůžou si ublížit.
- Po souboji se hráči vrátí jeho inventář, poloha, HP, gamemode… (stav se ukládá i na disk, takže přežije crash serveru).

## Požadavky

- Java 17+
- **Paper** 1.20.4+ (používá Paper API; Spigot nestačí)
- **Citizens** (verze odpovídající tvé verzi MC)

## Instalace

1. Dej `Citizens.jar` a `PracticeBot-x.y.z.jar` do složky `plugins/`.
2. Restartuj server.
3. Vytvoř arény (viz níže).

## Rychlé nastavení arény

Postav se do arény na místo, kde má stát hráč:

```
/pbot arena create sword1 sword
/pbot arena setplayer sword1      # spawn hráče (tam kde stojíš)
# přejdi na druhý konec arény
/pbot arena setbot sword1         # spawn bota
```

Stejné udělej pro další kity (`axe`, `nodebuff`, `gapple`). Pro jeden kit můžeš mít víc arén – hráči se rozdělují na tu s nejméně souboji. Aréna může být i jediná sdílená: hráči se v ní neuvidí a nekolidují.

> Aréna by měla mít kolem sebe zdi/bariéru a podlahu – hráči nesmí v souboji ničit ani pokládat bloky (je to zakázané), takže pád do voidu = prohra.

## Příkazy

| Příkaz | Popis | Oprávnění |
|---|---|---|
| `/pbot` | Otevře GUI (kit → obtížnost) | `practicebot.play` |
| `/pbot play <kit> <obtížnost>` | Rychlý start bez GUI | `practicebot.play` |
| `/pbot leave` | Ukončí souboj | `practicebot.play` |
| `/pbot kits` | Seznam kitů | `practicebot.play` |
| `/pbot arena create <název> <kit>` | Vytvoří arénu | `practicebot.admin` |
| `/pbot arena setplayer <název>` | Nastaví spawn hráče | `practicebot.admin` |
| `/pbot arena setbot <název>` | Nastaví spawn bota | `practicebot.admin` |
| `/pbot arena setkit <název> <kit>` | Změní kit arény | `practicebot.admin` |
| `/pbot arena delete <název>` | Smaže arénu | `practicebot.admin` |
| `/pbot arena list` / `tp <název>` | Seznam / teleport | `practicebot.admin` |
| `/pbot reload` | Znovu načte config a arény | `practicebot.admin` |

Aliasy: `/practicebot`, `/pb`

## Kity

| Kit | Výbava |
|---|---|
| `sword` | diamantový meč, železná zbroj, jablka, steaky |
| `axe` | diamantová sekera, železná zbroj |
| `nodebuff` | diamantový meč + diamantová zbroj (Prot II), enderpearly, splash healing potiony |
| `gapple` | diamantový meč + diamantová zbroj (Prot II), zlatá jablka |

Bot ve `nodebuff` a `gapple` se při nízkém HP léčí.

## Obtížnosti

Všechny hodnoty jdou ladit v `config.yml` (`difficulties:`): dosah, CPS, přesnost, rychlost, strafování, skákání (crity), práh a pauza léčení.

| | Přesnost | CPS | Dosah |
|---|---|---|---|
| Normal | 50 % | 4 | 2.8 |
| Medium | 65 % | 6 | 3.0 |
| Hard | 80 % | 8 | 3.2 |
| Professional | 92 % | 11 | 3.4 |

## Build

```bash
mvn clean package
```

Jar najdeš v `target/PracticeBot-1.0.0.jar`.

Jiná verze MC? V `pom.xml` změň `paper.version` a `citizens.version`.

### GitHub Actions

Workflow `.github/workflows/build.yml` jar sestaví při každém pushi (stáhneš ho v záložce *Actions → Artifacts*). Když pushneš tag `v1.0.0`, jar se automaticky přiloží k **Release**:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Známá omezení

- AI bota je záměrně jednoduchá (navigace Citizens + vlastní logika útoku/strafu/léčení). Neumí házet potiony fyzicky ani střílet z luku – léčení je simulované.
- Bot útočí přes `attack()` serveru, takže na vysoké CPS platí vanilla attack cooldown (rychlé údery dávají menší damage, stejně jako u hráče).
- Hráči v souboji nevidí ostatní hráče na serveru (včetně chatu v tabu), dokud souboj neskončí.

## Licence

MIT
