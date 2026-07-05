# Cobblemon Dynamic Spawns

*Read this in [Português (Brasil)](README.pt-BR.md).*

An addon for **Cobblemon 1.7.3** (Minecraft 1.21.1, Fabric) that makes Pokémon
spawning far more dynamic: wider biome variety, light-based cave spawns, wild
hordes led by evolved leaders, SV-style Mass Outbreaks, and a dash of pure
randomness.

## Features

### 1. Biome + light-based spawn variety (datapack)
A purely **additive** layer on top of Cobblemon's default spawn pool — nothing
from the original spawns is modified, we only add new entries under
`data/cobblemon/spawn_pool_world/dynamicspawns_*.json`:

- **Dark caves** (`light 0-3`, no sky access): Zubat, Gastly, Sableye (rare)
- **Lit caves** (`light 4+`, no sky access): Geodude, Machop, Aron, Roggenrola
- **Forests**: Hoothoot (night), Seedot, Shroomish, Pineco
- **Plains**: Ponyta, Doduo, Growlithe, Eevee (rare)
- **Deserts**: Sandshrew, Trapinch (day), Cacnea (night), Sandile
- **Snowy biomes**: Swinub, Snorunt (night), Cubchoo, Snover
- **Coasts/beaches**: Krabby, Wingull (day), Corphish, Staryu (night)

### 2. Hordes
When a **base species that can evolve** spawns naturally (e.g. Bidoof), there is
a configurable chance (default 8%) that it turns into a **horde**:
- The spawned Pokémon becomes one of the horde members
- An **evolved leader** (e.g. Bibarel) spawns nearby with a level bonus (+5) and
  3 random perfect IVs
- 3-5 more base members spawn around it

Triggering on the common base-stage spawns (rather than the rare evolved ones)
makes hordes far more frequent — with the trigger applied to most natural
spawns, a chance as low as a few percent already produces plenty; tune `chance`
down accordingly.

### 3. Mass Outbreaks (Scarlet/Violet style)
At random intervals (20-45 min), a global outbreak starts somewhere in the
world, announced in chat to every player:
- Up to **3 simultaneous outbreaks** (`maxSimultaneous`), each with a different
  species, with staggered start times
- **Territorial spacing**: active outbreaks stay at least **16 chunks**
  (256 blocks) apart (`minChunkDistanceBetweenOutbreaks`) — they never overlap;
  if no valid position exists, the start is postponed to the next interval
- The **first outbreak is guaranteed** the moment the first in-game day ends
  (`startAfterInGameDays`); subsequent ones follow the random interval. Admin
  commands bypass this rule
- A single species mass-spawning (budget of 80 Pokémon, max 8 alive per outbreak)
- Legendary, mythical and ultra-beast species never become outbreaks
  (`excludedLabels`)
- Pokémon only materialize while a player is within the outbreak's activation
  radius (`activationRadius`), so they always spawn in loaded, ticking chunks —
  the outbreak is announced globally with coordinates; you travel there to
  trigger the spawns (Scarlet/Violet style)
- Defeat/catch **30** → shiny rolls **x2** | **60** → **x3** (announced in chat)
- **Escalating levels**: every `clearsPerLevelStep` (10) Pokémon defeated/caught,
  the next spawns gain `+levelBonusPerStep` (8) levels — the outbreak gets tougher
  as you thin it out (e.g. 15-40 → ~39-64 after 30 → ~63-80 after 60). Variation
  comes from the level range, capped at `levelCap` (80)
- Clear the entire outbreak → a **guaranteed shiny** spawns as the final reward
- Ends when the timer runs out (20 min) or when fully cleared
- **Persistent**: active outbreaks and the schedule are saved per-world
  (`dynamicspawns_outbreaks.json`), so they survive closing and reopening the
  world instead of restarting. The lifetime uses world time (`gameTime`), so it
  counts down while the server runs — regardless of whether the outbreak's chunk
  is loaded — and pauses while the world is closed. Reopening a world no longer
  spawns a spurious outbreak

### 4. Random spawns
**10% of natural spawns** (configurable) are swapped for a completely random
species, ignoring biome rules — any Pokémon can show up anywhere:
- **Legendaries, mythicals, ultra beasts and paradox forms are excluded from
  the roll** (`excludedLabels`) — and natural spawns of those species are never
  swapped away (protects wild legendaries from modpacks)
- **In water/aquatic biomes** (ocean, river, freshwater), the roll only picks
  species that actually live underwater (can breathe underwater)
- **Realistic levels** (`realisticLevels`): the level scales with the species'
  base stat total, ±`levelVariance` (5) for spread — a Magikarp shows up around
  level 1-10 while a pseudo-legendary lands near 50-55 (`rareLevelCap`). Set
  `realisticLevels: false` to instead keep the original spawn's level
- `excludedNamespaces` lets you exclude species from specific addons
  (e.g. `lumymon`)

### Environmental rules
Both the random spawns and the outbreaks respect environmental sanity checks
(config section `environment`), so Pokémon don't appear where they shouldn't:
- **Disabled dimensions** (`disabledDimensions`, default `minecraft:the_end`):
  no dynamic randomness there — the End keeps its vanilla Pokémon
- **Banned types per dimension** (`dimensionBannedTypes`, default: no Grass/
  Water/Ice/Bug in the Nether): species with a banned type are filtered out for
  that dimension
- **Terrain suitability** (`enforceTerrain`): water-dwelling species that avoid
  land only spawn in water (no more Relicanth on top of a tree), and
  water-averse species won't be placed submerged. Hordes skip aquatic species
  entirely since their members are placed on solid ground

## Commands (permission level 2)

| Command | Effect |
|---|---|
| `/dynamicspawns reload` | Reloads the config |
| `/dynamicspawns horde <species>` | Forces a horde at your position (use a base species that evolves, e.g. `bidoof`) |
| `/dynamicspawns outbreak start [species]` | Starts an outbreak (random, or of the given species near you) |
| `/dynamicspawns outbreak stop` | Ends all active outbreaks |
| `/dynamicspawns outbreak info` | Lists active outbreaks with status |

## Configuration

Generated at `config/dynamicspawns.json` on first run. Every number (chances,
sizes, intervals, shiny milestones) is adjustable there;
`/dynamicspawns reload` applies changes without restarting.

## Cobbleverse compatibility

Verified against **COBBLEVERSE 1.7.31** (Modrinth, April 2026): MC 1.21.1 +
Fabric 0.18.4 + Cobblemon 1.7.3 + fabric-language-kotlin 1.13.10 — all
dependencies match this mod. The pack's spawn-related addons (LumyMon,
LegendaryMonuments, Raid Dens, Mega Showdown) use their own mechanisms
(structures/altars/raids) that don't go through the BestSpawner, and the label
protection guarantees wild legendaries are never swapped by the random spawn
system. The outbreak shiny boost reads Cobblemon's `shinyRate` config at
runtime, automatically respecting the pack's improved 1:2048 rate.

This was validated by running a full dedicated server with all 135 Cobbleverse
mods plus this one — clean boot, no conflicts.

## Compatibility & portability

This mod uses **only Cobblemon's public API** (the `ENTITY_SPAWN`,
`POKEMON_CAPTURED`, `POKEMON_FAINTED` and `BATTLE_FAINTED` events plus the
data-driven spawn pool format) — **zero mixins** into Cobblemon internals.
This minimizes breakage across future updates. The declared dependency accepts
`>=1.7.3 <1.8.0`; to update, bump the version in `build.gradle.kts` and
`fabric.mod.json`.

**Dependencies:**
- [Cobblemon](https://modrinth.com/mod/cobblemon) (required)
- [Fabric API](https://modrinth.com/mod/fabric-api) (required)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) (required)

## Known limitations (v1.0.0)

- Chat messages are translated client-side (English and Brazilian Portuguese
  included); players joining without the mod on their client will see raw
  translation keys in chat
- Outbreaks always pick surface positions (heightmap)

## Building

```
./gradlew build      # jar lands in build/libs/
./gradlew runClient  # test client
```

## License

[MIT](LICENSE)
