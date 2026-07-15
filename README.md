# Cobblemon Dynamic Spawns

*Read this in [Português (Brasil)](README.pt-BR.md).*

An addon for **Cobblemon 1.7.3** (Minecraft 1.21.1, Fabric) that makes Pokémon
spawning far more dynamic: wider biome variety, light-based cave spawns, wild
hordes led by evolved leaders, SV-style Mass Outbreaks, and a dash of pure
randomness.

**Current version: 1.1.0** — see the [changelog](CHANGELOG.md).

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
a configurable chance (default 10%) that it turns into a **horde**:
- The spawned Pokémon becomes one of the horde members
- An **evolved leader** (e.g. Bibarel) spawns nearby with a level bonus (+5) and
  3 random perfect IVs
- 3-5 more base members spawn around it

Triggering on the common base-stage spawns (rather than the rare evolved ones)
makes hordes far more frequent — with the trigger applied to most natural
spawns, a chance as low as a few percent already produces plenty; tune `chance`
down accordingly.

### 3. Mass Outbreaks (Scarlet/Violet style)
At random intervals (measured in in-game day time — so sleeping/advancing days
brings them, not just real playtime), a global outbreak starts somewhere in the
world, announced in chat to every player:
- Up to **6 simultaneous outbreaks** (`maxSimultaneous`), each with a different
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
- Terrain-matched: the species is picked to fit the center (water dweller over
  ocean, land dweller on solid ground), and each individual spawn is re-checked —
  no fish on dry land or land Pokémon out in the ocean
- Defeat/catch **30** → shiny rolls **x2** | **60** → **x3** (announced in chat)
- **Escalating levels**: every `clearsPerLevelStep` (10) Pokémon defeated/caught,
  the next spawns gain `+levelBonusPerStep` (8) levels — the outbreak gets tougher
  as you thin it out (e.g. 15-40 → ~39-64 after 30 → ~63-80 after 60). Variation
  comes from the level range, capped at `levelCap` (80)
- **IV progression** (SV-style): every `clearsPerIvStep` (20) Pokémon cleared,
  the next spawns gain +1 guaranteed perfect IV (31), from `baseGuaranteedIvs`
  up to `maxProgressionIvs` (4); the rest stay random
- **Shiny reward IVs**: a shiny from the outbreak comes near-perfect —
  `shinyMinPerfectIvs`-`shinyMaxPerfectIvs` (5-6) guaranteed 31 IVs
- Clear the entire outbreak → a **guaranteed shiny** spawns as the final reward
- Ends when its lifetime runs out (also in-game day time, so advancing/sleeping
  ends outbreaks too) or when fully cleared
- **Awareness**: joining the world lists any active outbreaks (coords + minutes
  left), and `/dynamicspawns outbreak info` shows each one's progress and time
  remaining
- **Persistent**: active outbreaks and the schedule are saved per-world
  (`dynamicspawns_outbreaks.json`), so they survive closing and reopening the
  world instead of restarting. The lifetime uses world time (`gameTime`), so it
  counts down while the server runs — regardless of whether the outbreak's chunk
  is loaded — and pauses while the world is closed. Reopening a world no longer
  spawns a spurious outbreak

### 4. Flying spawns
Birds and other true fliers spawn **in the air, already flying**, instead of
standing on the ground — an Altaria shows up gliding above the terrain rather
than sitting in a field. A "true flier" is a species that flies **and isn't
aquatic**: Cobblemon marks some water Pokémon (Dragonair, Dragonite, jellyfish,
eels, squids) with `canFly`, and those are deliberately **not** raised into the
sky. Height above the surface is configurable (`minHeight`/`maxHeight`, default
12-40; raise it toward ~120 for cloud-level spawns), and `onlyPureFliers` can
limit it to species that can't walk. Uses Cobblemon's `canFly`/`setFlying` API —
no mixins.

On top of that, a recurring **sky spawner** (`skyFliers`) periodically spawns a
few fliers high in the air near each player, under open sky. The pool excludes
aquatic fliers, legendaries/mythicals/ultra-beasts and very rare species (low
catch rate), and the pick is **weighted by Cobblemon's rarity buckets**
(`bucketWeights`, common>uncommon>rare>ultra-rare) so common fliers show up more
than rare ones. By default it's strict **pure fliers** (species that can't walk,
e.g. Zubat, Drifloon); set `onlyPureFliers: false` to also include walking birds
like Altaria and Pidgeot. Interval, count, height, distance and levels are all
configurable.

### 5. Random spawns
**6% of natural spawns** (configurable) are swapped for another species,
ignoring biome rules — any Pokémon can show up anywhere, but **following
Cobblemon's own rarity**:
- The replacement is drawn by **rarity bucket** (read at runtime from the world
  spawn pool, so datapacks/modpacks are respected): first a bucket is rolled
  with the mod's weights (`bucketWeights`, default **common 60 / uncommon 25 /
  rare 10 / ultra-rare 5** — softer than Cobblemon's 94.3/5/0.5/0.2 so rares
  show up more), then a uniform species within that bucket. A species' bucket is
  the most common one it appears in (e.g. Charizard = ultra-rare)
- Species **without any natural spawn** (no bucket) can't be rolled
- See [docs/selecao-spawn-aleatorio.pdf](docs/selecao-spawn-aleatorio.pdf) for
  the full species listing with per-species probabilities
- **Legendaries, mythicals, ultra beasts, paradox forms and fossils are excluded
  from the roll** (`excludedLabels`) — and natural spawns of those species are
  never swapped away (protects wild legendaries from modpacks)
- **In water/aquatic biomes** (ocean, river, freshwater), the roll only picks
  species that actually live underwater (can breathe underwater)
- **Realistic levels** (`realisticLevels`): the level scales with the species'
  base stat total, ±`levelVariance` (5) for spread — a Magikarp shows up around
  level 1-10 while a pseudo-legendary lands near 50-55 (`rareLevelCap`). Set
  `realisticLevels: false` to instead keep the original spawn's level
- `excludedNamespaces` lets you exclude species from specific addons
  (e.g. `lumymon`)

### BetterEnd / BetterNether integration
Optional datapack pools (activated only when the mods are installed, via
`neededInstalledMods`) that give the modded dimensions a themed Pokémon roster:

- **BetterNether** (22 biomes, 51 spawns): crimson/wart woods → Houndour,
  Salandit, Salazzle; warped woods → Gastly, Elgyem; fungi & upside-down forests
  → Koffing, Grimer, Weezing; swamplands → Croagunk, Stunky, Muk; magma & deltas
  → Slugma, Torkoal, Magcargo, plus **Heatran** as an ultra-rare; gravel desert →
  Geodude, Rhyhorn, Onix; bone reefs & soul plain → Cubone, Litwick, Duskull,
  Marowak, Spiritomb. No Grass/Water/Ice/Bug types, matching the Nether rules.
- **BetterEnd** (25 biomes, 71 spawns): chorus forest → Elgyem, Drifblim;
  shadow forest & umbra valley → Murkrow, Zorua, Absol, Spiritomb; crystal/amber/
  jade caves → Roggenrola, Carbink, Probopass, Beldum; glowing biomes → Clefairy,
  Ralts, Chimecho, plus **Cresselia**; dragon graveyards → Deino, Druddigon,
  Golurk, plus **Regidrago**; wastelands → Baltoy, Claydol, Rhydon; megalake →
  Chinchou, Frillish, Starmie, Lapras; ice starfield → Snorunt, Cryogonal,
  Avalugg, plus **Deoxys**; mushroomland & sulphur springs; end caves → Woobat,
  Noibat, Noivern.
- Legendaries spawn only in the `ultra-rare` bucket at weight `0.05` (≈180× rarer
  than a common in the same biome).
- **Compat fix:** BetterEnd's 25 biomes are added to the `cobblemon:is_end` tag
  (`required: false`), so Cobblemon's ~42 stock End spawns work there too — they
  otherwise appear not to, since nothing adds those biomes to `minecraft:is_end`.

Note these are native Cobblemon datapack spawns, independent of the mod's
`environment` rules (which only govern the random-swap and outbreak systems).

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

## Known limitations (v1.1.0)

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
