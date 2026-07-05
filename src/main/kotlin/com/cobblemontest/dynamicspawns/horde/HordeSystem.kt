package com.cobblemontest.dynamicspawns.horde

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.entity.SpawnEvent
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.IVs
import com.cobblemontest.dynamicspawns.DynamicSpawns
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Hordas: quando uma espécie evoluída spawna naturalmente, há uma chance de ela virar
 * a líder de uma horda — ela recebe bônus de nível + IVs perfeitos e é cercada por
 * membros da sua pré-evolução (ex: um Bibarel líder cercado de Bidoofs).
 *
 * Implementado apenas via evento público ENTITY_SPAWN do Cobblemon (sem mixins),
 * para máxima compatibilidade com futuras versões.
 */
object HordeSystem {

    fun register() {
        CobblemonEvents.ENTITY_SPAWN.subscribe(Priority.NORMAL, ::onSpawn)
    }

    private fun onSpawn(event: SpawnEvent<*>) {
        val cfg = DynamicSpawns.config.hordes
        if (!cfg.enabled) return
        if (event.isCanceled) return
        val entity = event.entity as? PokemonEntity ?: return
        // Só espécies evoluídas podem liderar hordas
        if (entity.pokemon.species.preEvolution == null) return
        val world = event.spawnablePosition.world
        if (world.random.nextDouble() >= cfg.chance) return
        val members = spawnHordeAround(entity, world)
        if (members > 0) {
            DynamicSpawns.LOGGER.debug(
                "Horda criada: líder {} (nível {}) com {} membros",
                entity.pokemon.species.name, entity.pokemon.level, members
            )
        }
    }

    /**
     * Transforma [leader] em líder de horda (nível + IVs) e spawna membros da
     * pré-evolução ao redor. Retorna quantos membros spawnaram.
     */
    fun spawnHordeAround(leader: PokemonEntity, world: ServerLevel): Int {
        val cfg = DynamicSpawns.config.hordes
        val preEvo = leader.pokemon.species.preEvolution ?: return 0
        val baseLevel = leader.pokemon.level

        leader.pokemon.level = min(baseLevel + cfg.leaderLevelBonus, Cobblemon.config.maxPokemonLevel)
        Stats.PERMANENT.shuffled().take(cfg.perfectIvCount).forEach { stat ->
            leader.pokemon.setIV(stat, IVs.MAX_VALUE)
        }

        val count = world.random.nextIntBetweenInclusive(cfg.minMembers, cfg.maxMembers)
        var spawned = 0
        repeat(count) {
            val level = max(1, baseLevel - world.random.nextInt(3))
            val member = PokemonEntity(world, preEvo.species.create(level))
            val angle = world.random.nextDouble() * 2 * PI
            val dist = 2.0 + world.random.nextDouble() * 3.0
            val x = leader.x + cos(angle) * dist
            val z = leader.z + sin(angle) * dist
            val y = findGroundY(world, BlockPos.containing(x, leader.y + 2.0, z)) ?: leader.y
            member.moveTo(x, y, z, world.random.nextFloat() * 360f, 0f)
            if (world.addFreshEntity(member)) spawned++
        }
        return spawned
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
