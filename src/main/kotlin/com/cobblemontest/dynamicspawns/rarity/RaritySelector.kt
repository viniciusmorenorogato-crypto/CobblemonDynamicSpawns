package com.cobblemontest.dynamicspawns.rarity

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import net.minecraft.util.RandomSource

/**
 * Seleção de espécies ponderada pela raridade real do Cobblemon (buckets common/
 * uncommon/rare/ultra-rare). O bucket de cada espécie é lido em runtime da spawn pool
 * do mundo (respeita datapacks/modpacks) e cacheado; o cache se refaz quando a pool
 * recarrega (/reload, mods). Espécies sem spawn natural (sem bucket) não são sorteadas.
 */
object RaritySelector {

    private val RANK = mapOf("common" to 0, "uncommon" to 1, "rare" to 2, "ultra-rare" to 3)

    private var bucketCache: Map<String, String>? = null
    private var reloadHookRegistered = false

    /**
     * Mapa identifier→bucket. Se a espécie aparece em mais de um bucket na pool,
     * vale o mais comum (a raridade "efetiva" dela no mundo).
     */
    fun speciesBuckets(): Map<String, String> {
        bucketCache?.let { return it }
        if (!reloadHookRegistered) {
            CobblemonSpawnPools.WORLD_SPAWN_POOL.observable.subscribe(Priority.NORMAL) { bucketCache = null }
            reloadHookRegistered = true
        }
        val map = mutableMapOf<String, String>()
        CobblemonSpawnPools.WORLD_SPAWN_POOL.details.forEach { detail ->
            val pokemonDetail = detail as? PokemonSpawnDetail ?: return@forEach
            val speciesStr = pokemonDetail.pokemon.species ?: return@forEach
            if (speciesStr.equals("random", ignoreCase = true)) return@forEach
            val species = PokemonSpecies.getByIdentifier(speciesStr.asIdentifierDefaultingNamespace()) ?: return@forEach
            val key = species.resourceIdentifier.toString()
            val bucket = detail.bucket.name
            val current = map[key]
            if (current == null || (RANK[bucket] ?: Int.MAX_VALUE) < (RANK[current] ?: Int.MAX_VALUE)) {
                map[key] = bucket
            }
        }
        bucketCache = map
        return map
    }

    /** Bucket de raridade da espécie, ou null se ela não tem spawn natural. */
    fun bucketOf(species: Species): String? = speciesBuckets()[species.resourceIdentifier.toString()]

    /**
     * Sorteia uma espécie de [candidates] ponderando pelo bucket ([weights]): primeiro
     * sorteia o bucket pelos pesos, depois uma espécie uniforme dentro dele. Candidatas
     * sem bucket são descartadas. Retorna null se nada elegível.
     */
    fun pickWeighted(candidates: List<Species>, weights: Map<String, Double>, random: RandomSource): Species? {
        val buckets = speciesBuckets()
        val byBucket = HashMap<String, MutableList<Species>>()
        for (species in candidates) {
            val bucket = buckets[species.resourceIdentifier.toString()] ?: continue
            byBucket.getOrPut(bucket) { mutableListOf() }.add(species)
        }
        val entries = byBucket.entries.filter { it.value.isNotEmpty() && (weights[it.key] ?: 0.0) > 0.0 }
        if (entries.isEmpty()) return null
        val total = entries.sumOf { weights[it.key]!! }
        var roll = random.nextDouble() * total
        for (entry in entries) {
            roll -= weights[entry.key]!!
            if (roll <= 0) return entry.value[random.nextInt(entry.value.size)]
        }
        val last = entries.last().value
        return last[random.nextInt(last.size)]
    }
}
