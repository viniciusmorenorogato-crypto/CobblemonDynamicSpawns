package com.cobblemontest.dynamicspawns.randomspawn

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.entity.SpawnEvent
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemontest.dynamicspawns.DynamicSpawns
import com.cobblemontest.dynamicspawns.environment.SpawnEnvironment
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.FluidTags
import net.minecraft.tags.TagKey

/**
 * Spawns aleatórios: uma fração dos spawns naturais (padrão 10%) é trocada por uma
 * espécie totalmente aleatória, ignorando as regras de bioma — qualquer pokémon pode
 * aparecer em qualquer lugar. Lendários, míticos, ultra beasts e paradox ficam fora
 * do sorteio (configurável).
 *
 * Em contexto aquático (posição na água ou bioma de oceano/rio/água doce), o sorteio
 * considera apenas espécies que vivem na água (respiram debaixo d'água).
 *
 * Roda com Priority.HIGHEST para que a troca aconteça ANTES do HordeSystem — assim
 * uma horda formada no mesmo spawn usa a espécie já sorteada, mantendo consistência.
 */
object RandomSpawnSystem {

    // Faixa típica de base stat total: ~200 (mais fracos) a ~600 (pseudo-lendários),
    // usada para mapear a força da espécie em nível realista.
    private const val BST_MIN = 200.0
    private const val BST_MAX = 600.0

    private val aquaticBiomeTags: List<TagKey<net.minecraft.world.level.biome.Biome>> =
        listOf("is_ocean", "is_deep_ocean", "is_river", "is_freshwater")
            .map { TagKey.create(Registries.BIOME, cobblemonResource(it)) }

    fun register() {
        CobblemonEvents.ENTITY_SPAWN.subscribe(Priority.HIGHEST, ::onSpawn)
    }

    private fun onSpawn(event: SpawnEvent<*>) {
        val cfg = DynamicSpawns.config.randomSpawns
        if (!cfg.enabled) return
        if (event.isCanceled) return
        val entity = event.entity as? PokemonEntity ?: return
        val world = event.spawnablePosition.world
        // Dimensões desligadas (ex: The End) não recebem aleatoriedade
        if (!SpawnEnvironment.dynamicSpawnsAllowed(world)) return
        if (world.random.nextDouble() >= cfg.chance) return

        val excluded = cfg.excludedLabels.toSet()
        val excludedNamespaces = cfg.excludedNamespaces.toSet()

        // Nunca troca spawns especiais: se o pokémon que spawnou naturalmente é
        // lendário/mítico (ou de namespace excluído), o spawn dele é preservado —
        // importante em modpacks (ex: Cobbleverse) com lendários selvagens.
        val original = entity.pokemon.species
        if (original.labels.any { it in excluded }) return
        if (original.resourceIdentifier.namespace in excludedNamespaces) return

        val pos = event.spawnablePosition.position
        val aquatic = isAquaticContext(world, pos)

        // Agrupa as candidatas por bucket de raridade do Cobblemon. Espécies sem spawn
        // natural (sem bucket na spawn pool do mundo) ficam de fora da troca.
        val buckets = speciesBuckets()
        val byBucket = HashMap<String, MutableList<Species>>()
        PokemonSpecies.implemented.forEach { species ->
            val bucket = buckets[species.resourceIdentifier.toString()] ?: return@forEach
            if (species.labels.any { label -> label in excluded }) return@forEach
            if (species.resourceIdentifier.namespace in excludedNamespaces) return@forEach
            if (aquatic && !species.behaviour.moving.swim.canBreatheUnderwater) return@forEach
            // Tipo permitido na dimensão (ex: sem grama no Nether) + terreno adequado
            // à posição (ex: aquático não nasce em cima de árvore)
            if (!SpawnEnvironment.isSpeciesAllowed(species, world, pos)) return@forEach
            byBucket.getOrPut(bucket) { mutableListOf() }.add(species)
        }
        val replacement = pickWeighted(byBucket, world) ?: return

        // Nível realista baseado na força da espécie (BST): fracos nascem baixo, fortes/raros
        // sobem até o teto. Se desativado, mantém o nível do spawn original.
        val level = if (cfg.realisticLevels) {
            realisticLevelFor(replacement, world)
        } else {
            entity.pokemon.level
        }
        // Trocar entity.pokemon é suportado pela API (sincroniza delegate, brain e hitbox).
        entity.pokemon = replacement.create(level)
        DynamicSpawns.LOGGER.debug(
            "Spawn aleatório: {} (nível {}, aquático={})",
            replacement.name, level, aquatic
        )
    }

    // Cache espécie (identifier) → bucket de raridade, montado a partir da spawn pool
    // do mundo (inclui datapacks/modpacks). Invalidado quando a pool recarrega.
    private var bucketCache: Map<String, String>? = null
    private var reloadHookRegistered = false

    /**
     * Mapa identifier→bucket. Se a espécie aparece em mais de um bucket na pool,
     * vale o mais comum (é a raridade "efetiva" dela no mundo).
     */
    private fun speciesBuckets(): Map<String, String> {
        bucketCache?.let { return it }
        if (!reloadHookRegistered) {
            // Datapack reload (/reload, mods) refaz a pool → invalida o cache
            CobblemonSpawnPools.WORLD_SPAWN_POOL.observable.subscribe(Priority.NORMAL) { bucketCache = null }
            reloadHookRegistered = true
        }
        val rank = mapOf("common" to 0, "uncommon" to 1, "rare" to 2, "ultra-rare" to 3)
        val map = mutableMapOf<String, String>()
        CobblemonSpawnPools.WORLD_SPAWN_POOL.details.forEach { detail ->
            val pokemonDetail = detail as? PokemonSpawnDetail ?: return@forEach
            val speciesStr = pokemonDetail.pokemon.species ?: return@forEach
            if (speciesStr.equals("random", ignoreCase = true)) return@forEach
            val species = PokemonSpecies.getByIdentifier(speciesStr.asIdentifierDefaultingNamespace()) ?: return@forEach
            val key = species.resourceIdentifier.toString()
            val bucket = detail.bucket.name
            val current = map[key]
            if (current == null || (rank[bucket] ?: Int.MAX_VALUE) < (rank[current] ?: Int.MAX_VALUE)) {
                map[key] = bucket
            }
        }
        bucketCache = map
        DynamicSpawns.LOGGER.debug("Cache de raridade montado: {} espécies com bucket", map.size)
        return map
    }

    /** Sorteia o bucket pelos pesos da config e uma espécie uniforme dentro dele. */
    private fun pickWeighted(byBucket: Map<String, List<Species>>, world: ServerLevel): Species? {
        val weights = DynamicSpawns.config.randomSpawns.bucketWeights
        val entries = byBucket.entries.filter { it.value.isNotEmpty() && (weights[it.key] ?: 0.0) > 0.0 }
        if (entries.isEmpty()) return null
        val total = entries.sumOf { weights[it.key]!! }
        var roll = world.random.nextDouble() * total
        for (entry in entries) {
            roll -= weights[entry.key]!!
            if (roll <= 0) return entry.value[world.random.nextInt(entry.value.size)]
        }
        val last = entries.last().value
        return last[world.random.nextInt(last.size)]
    }

    /**
     * Nível "realista" pela força da espécie: mapeia o base stat total (BST) numa faixa
     * de [levelMin] (fraco) até [rareLevelCap] (forte/raro), com uma leve variação. Assim
     * um Magikarp nasce fraquinho e um pseudo-lendário nasce mais forte (até o teto).
     */
    private fun realisticLevelFor(species: com.cobblemon.mod.common.pokemon.Species, world: ServerLevel): Int {
        val cfg = DynamicSpawns.config.randomSpawns
        val bst = species.baseStats.values.sum()
        val fraction = ((bst - BST_MIN) / (BST_MAX - BST_MIN)).coerceIn(0.0, 1.0)
        val base = cfg.levelMin + fraction * (cfg.rareLevelCap - cfg.levelMin)
        val variance = cfg.levelVariance
        val jitter = if (variance > 0) world.random.nextIntBetweenInclusive(-variance, variance) else 0
        return (Math.round(base).toInt() + jitter).coerceIn(1, cfg.rareLevelCap)
    }

    /** Água na posição do spawn ou bioma aquático (oceano, rio, água doce). */
    fun isAquaticContext(world: ServerLevel, pos: BlockPos): Boolean {
        if (world.getBlockState(pos).fluidState.`is`(FluidTags.WATER)) return true
        val biome = world.getBiome(pos)
        return aquaticBiomeTags.any { biome.`is`(it) }
    }
}
