# Changelog

## 1.1.1 — Cobblemon 1.7.3 / Minecraft 1.21.1 (Fabric)

- **Outbreak announcements now name the dimension** and include Y, so you can
  actually find them: *"A swarm of Pikachu appeared in the Overworld at X=…,
  Y=…, Z=…"*. The join notice and `/dynamicspawns outbreak info` show it too.
- **Dynamic spawns are now allow-listed by dimension** (`environment.allowedDimensions`,
  default Overworld + Nether) instead of blacklisted. Modded instanced dimensions —
  raid dens, distortion worlds, nightmare/origin, etc. — are excluded automatically
  without having to list each one, so outbreaks no longer fire inside a raid.

## 1.1.0 — Cobblemon 1.7.3 / Minecraft 1.21.1 (Fabric)

Everything below was added on top of the initial 1.0.0 (hordes, Mass Outbreaks,
biome/light datapacks, random spawns).

### Mass Outbreaks
- **Persist across world close/reopen** (per-world save file); no more spurious
  outbreak on every reload.
- Interval **and** lifetime now run on in-game day time, so sleeping / advancing
  days both triggers new outbreaks and ends active ones. The first outbreak is
  guaranteed the moment the first in-game day ends.
- Pokémon only spawn while a player is within the activation radius, so they
  always land in loaded, ticking chunks (no wasted budget, no lag clumps).
- **Escalating levels** and **SV-style IV progression** as you clear the
  outbreak; a **shiny comes near-perfect (5-6 perfect IVs)**.
- Terrain-matched spawns (fish only over water, land Pokémon only on ground),
  configurable level cap (80) and simultaneous-outbreak spacing.
- Legendary / mythical / ultra-beast / fossil species are excluded.
- Awareness: a join notice lists active outbreaks with time left, and
  `/dynamicspawns outbreak info` shows each one's timer and progress.

### Hordes
- Trigger on common **base-stage** spawns (e.g. Bidoof) instead of the rare
  evolved forms, so hordes actually show up. Aquatic species are skipped.

### Flying
- Flying species spawn **airborne, already gliding**, instead of on the ground.
- A recurring **sky spawner** adds fliers high above players under open sky.
- Coherence fix: aquatic species Cobblemon tags with `canFly` (Dragonair,
  Dragonite, jellyfish, eels, squids…) are no longer treated as sky fliers.
- Sky-flier selection is weighted by Cobblemon's rarity buckets.

### Random spawns
- Now **weighted by Cobblemon rarity buckets** (read from the world spawn pool,
  so datapacks/modpacks are respected) instead of a flat pick — see
  [docs/selecao-spawn-aleatorio.pdf](docs/selecao-spawn-aleatorio.pdf).
- Default chance lowered from 10% to 6%. Fossils excluded.

### Environmental rules
- Disabled dimensions (The End), banned types per dimension (no Grass/Water/Ice/
  Bug in the Nether) and terrain suitability checks.

### Mod integration
- **BetterEnd / BetterNether** themed spawn pools (activated only when the mods
  are installed), plus a compatibility tag so Cobblemon's stock End spawns work
  in BetterEnd biomes.
- Declares the `fabric-api` dependency.

## 1.0.0

Initial release: hordes, SV-style Mass Outbreaks, additive biome/light-based
spawn datapacks, and random biome-ignoring spawns.
