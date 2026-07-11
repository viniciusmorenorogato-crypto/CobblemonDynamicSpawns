package com.cobblemontest.dynamicspawns.flying

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.entity.SpawnEvent
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemontest.dynamicspawns.DynamicSpawns
import com.cobblemontest.dynamicspawns.environment.SpawnEnvironment
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3

/**
 * Faz os pássaros voarem ao nascer: quando uma espécie que voa (canFly) spawna
 * naturalmente, ela é levantada para uma altura de voo acima da superfície e
 * colocada em modo de voo (ex: Altaria nascendo no céu em vez de no chão).
 *
 * Usa apenas API pública do Cobblemon (evento ENTITY_SPAWN + PokemonEntity.canFly/
 * setFlying), sem mixins. Roda com Priority.LOWEST para agir depois do swap aleatório
 * (HIGHEST) e das hordas (NORMAL), usando a espécie final do spawn.
 */
object FlyingSpawnSystem {

    private const val MAX_SPAWN_Y = 315

    fun register() {
        CobblemonEvents.ENTITY_SPAWN.subscribe(Priority.LOWEST, ::onSpawn)
    }

    private fun onSpawn(event: SpawnEvent<*>) {
        val cfg = DynamicSpawns.config.flyingSpawns
        if (!cfg.enabled) return
        if (event.isCanceled) return
        val entity = event.entity as? PokemonEntity ?: return
        // Voador de verdade: voa e não é aquático (exclui Dragonair/Dragonite/enguias/etc.)
        if (!SpawnEnvironment.isSkyFlier(entity.pokemon.species)) return
        if (cfg.onlyPureFliers && !isPureFlier(entity)) return
        makeAirborne(entity, event.spawnablePosition.world)
    }

    /** Pássaro "puro" = não anda (evita terra ou não pode andar), como Altaria. */
    private fun isPureFlier(entity: PokemonEntity): Boolean {
        val walk = entity.pokemon.species.behaviour.moving.walk
        return walk.avoidsLand || !walk.canWalk
    }

    /** Levanta [entity] para uma altura de voo acima da superfície e liga o modo de voo. */
    fun makeAirborne(entity: PokemonEntity, world: ServerLevel) {
        val cfg = DynamicSpawns.config.flyingSpawns
        val surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, entity.blockX, entity.blockZ)
        val offset = world.random.nextIntBetweenInclusive(cfg.minHeight, cfg.maxHeight)
        val targetY = (surfaceY + offset).coerceAtMost(MAX_SPAWN_Y).toDouble()
        entity.moveTo(entity.x, targetY, entity.z, entity.yRot, entity.xRot)
        entity.setFlying(true)
        // Evita despencar e tomar dano de queda antes de a IA de voo assumir.
        entity.deltaMovement = Vec3(entity.deltaMovement.x, 0.0, entity.deltaMovement.z)
        entity.fallDistance = 0f
    }
}
