package com.cobblemontest.dynamicspawns.horde

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.entity.SpawnEvent
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.IVs
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemontest.dynamicspawns.DynamicSpawns
import com.cobblemontest.dynamicspawns.environment.SpawnEnvironment
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Hordas: quando um Pokémon de **estágio base que pode evoluir** (ex: Bidoof) spawna
 * naturalmente, há uma chance de virar uma horda — o próprio Pokémon que nasceu vira
 * um dos membros, e o sistema spawna um **líder evoluído** (ex: Bibarel, com bônus de
 * nível + IVs perfeitos) cercado de mais membros da espécie base ao redor.
 *
 * Disparar no estágio base (comum nos spawns) em vez do evoluído (raro) deixa as hordas
 * muito mais frequentes. Implementado apenas via evento público ENTITY_SPAWN do Cobblemon
 * (sem mixins), para máxima compatibilidade com futuras versões.
 */
object HordeSystem {

    data class HordeResult(val leaderLevel: Int, val members: Int)

    fun register() {
        CobblemonEvents.ENTITY_SPAWN.subscribe(Priority.NORMAL, ::onSpawn)
    }

    private fun onSpawn(event: SpawnEvent<*>) {
        val cfg = DynamicSpawns.config.hordes
        if (!cfg.enabled) return
        if (event.isCanceled) return
        val entity = event.entity as? PokemonEntity ?: return
        // A espécie que nasceu precisa poder evoluir (para termos um líder evoluído).
        val leaderSpecies = evolvedLeaderFor(entity.pokemon.species) ?: return
        // Hordas usam posicionamento em chão sólido; pular espécies aquáticas (ex: Magikarp)
        // para não colocar cardume em terra.
        if (SpawnEnvironment.isAquaticOnly(entity.pokemon.species)) return
        val world = event.spawnablePosition.world
        if (world.random.nextDouble() >= cfg.chance) return

        val baseSpecies = entity.pokemon.species
        val result = spawnHorde(
            world, leaderSpecies, baseSpecies, entity.pokemon.level,
            entity.x, entity.y, entity.z
        )
        DynamicSpawns.LOGGER.debug(
            "Horda criada: líder {} (nível {}) com {} membros de {}",
            leaderSpecies.name, result.leaderLevel, result.members, baseSpecies.name
        )
    }

    /**
     * Espécie evoluída que [species] vira ao evoluir (primeira evolução válida que
     * resulta numa espécie diferente), ou null se [species] não pode evoluir.
     */
    fun evolvedLeaderFor(species: Species): Species? {
        return species.standardForm.evolutions
            .mapNotNull { it.result.species }
            .mapNotNull { PokemonSpecies.getByName(it) }
            .firstOrNull { it.resourceIdentifier != species.resourceIdentifier }
    }

    /**
     * Spawna um líder evoluído ([leaderSpecies], nível [baseLevel] + bônus, IVs perfeitos)
     * e vários membros [memberSpecies] ao redor de (cx,cy,cz). Retorna nível do líder e
     * quantos membros nasceram.
     */
    fun spawnHorde(
        world: ServerLevel,
        leaderSpecies: Species,
        memberSpecies: Species,
        baseLevel: Int,
        cx: Double,
        cy: Double,
        cz: Double
    ): HordeResult {
        val cfg = DynamicSpawns.config.hordes

        val leaderLevel = min(baseLevel + cfg.leaderLevelBonus, Cobblemon.config.maxPokemonLevel)
        val leaderPokemon = leaderSpecies.create(leaderLevel)
        Stats.PERMANENT.shuffled().take(cfg.perfectIvCount).forEach { stat ->
            leaderPokemon.setIV(stat, IVs.MAX_VALUE)
        }
        placeAround(PokemonEntity(world, leaderPokemon), world, cx, cy, cz)

        val count = world.random.nextIntBetweenInclusive(cfg.minMembers, cfg.maxMembers)
        var spawned = 0
        repeat(count) {
            val level = max(1, baseLevel - world.random.nextInt(3))
            val member = PokemonEntity(world, memberSpecies.create(level))
            if (placeAround(member, world, cx, cy, cz)) spawned++
        }
        return HordeResult(leaderLevel, spawned)
    }

    /** Posiciona [entity] a 2-5 blocos de (cx,cy,cz) sobre o chão e a adiciona ao mundo. */
    private fun placeAround(entity: PokemonEntity, world: ServerLevel, cx: Double, cy: Double, cz: Double): Boolean {
        val angle = world.random.nextDouble() * 2 * PI
        val dist = 2.0 + world.random.nextDouble() * 3.0
        val x = cx + cos(angle) * dist
        val z = cz + sin(angle) * dist
        val y = findGroundY(world, BlockPos.containing(x, cy + 2.0, z)) ?: cy
        entity.moveTo(x, y, z, world.random.nextFloat() * 360f, 0f)
        return world.addFreshEntity(entity)
    }

    /** Procura chão sólido com ar em cima, descendo alguns blocos a partir de [start]. */
    private fun findGroundY(world: ServerLevel, start: BlockPos): Double? {
        var pos = start
        repeat(8) {
            if (!world.getBlockState(pos).isAir && world.getBlockState(pos.above()).isAir) {
                return pos.y + 1.0
            }
            pos = pos.below()
        }
        return null
    }
}
