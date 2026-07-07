package com.cobblemontest.dynamicspawns.flying

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemontest.dynamicspawns.DynamicSpawns
import com.cobblemontest.dynamicspawns.environment.SpawnEnvironment
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Spawner recorrente de "pure fliers" (espécies que voam e não andam, ex: Altaria,
 * Swablu) no céu, perto dos jogadores. Em ondas espaçadas por um intervalo aleatório,
 * spawna alguns pássaros no ar sob céu aberto, já voando — dando vida ao céu.
 *
 * Exclui lendários/míticos/ultra beasts (labels) e os muito raros (catch rate baixo).
 * Só API pública do Cobblemon (canFly/setFlying + catchRate/labels), sem mixins. Sem
 * persistência: é ambiente/efêmero, então o cronômetro reinicia a cada abertura do mundo.
 */
object SkyFlierSystem {

    private const val MAX_SPAWN_Y = 315

    private var cooldownTicks = 0

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::onTick)
    }

    private fun onTick(server: MinecraftServer) {
        val cfg = DynamicSpawns.config.skyFliers
        if (!cfg.enabled) return
        if (cooldownTicks > 0) {
            cooldownTicks--
            return
        }
        spawnWave(server, cfg)
        val minTicks = cfg.minIntervalSeconds * 20
        val maxTicks = max(minTicks, cfg.maxIntervalSeconds * 20)
        cooldownTicks = minTicks + server.overworld().random.nextInt(maxTicks - minTicks + 1)
    }

    private fun spawnWave(server: MinecraftServer, cfg: com.cobblemontest.dynamicspawns.config.DynamicSpawnsConfig.SkyFliers) {
        val pool = fliersPool(cfg)
        if (pool.isEmpty()) return
        server.playerList.players.forEach { player ->
            val world = player.serverLevel()
            if (!SpawnEnvironment.dynamicSpawnsAllowed(world)) return@forEach
            val count = world.random.nextIntBetweenInclusive(cfg.perPlayerMin, cfg.perPlayerMax)
            repeat(count) { spawnFlierNear(player, world, cfg, pool.random()) }
        }
    }

    /** Espécies voadoras não muito raras e sem labels excluídos. */
    private fun fliersPool(cfg: com.cobblemontest.dynamicspawns.config.DynamicSpawnsConfig.SkyFliers): List<Species> {
        val excluded = cfg.excludedLabels.toSet()
        return PokemonSpecies.implemented.filter { s ->
            val move = s.behaviour.moving
            move.fly.canFly &&
                (!cfg.onlyPureFliers || move.walk.avoidsLand || !move.walk.canWalk) &&
                s.catchRate >= cfg.minCatchRate &&
                s.labels.none { it in excluded }
        }
    }

    private fun spawnFlierNear(
        player: ServerPlayer,
        world: ServerLevel,
        cfg: com.cobblemontest.dynamicspawns.config.DynamicSpawnsConfig.SkyFliers,
        species: Species
    ) {
        val random = world.random
        val angle = random.nextDouble() * 2 * PI
        val dist = cfg.minDistance + random.nextDouble() * (cfg.maxDistance - cfg.minDistance)
        val x = player.x + cos(angle) * dist
        val z = player.z + sin(angle) * dist
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()
        val surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz)
        val y = (surfaceY + random.nextIntBetweenInclusive(cfg.minHeight, cfg.maxHeight)).coerceAtMost(MAX_SPAWN_Y)
        // Só sob céu aberto: exclui Nether (teto), cavernas e áreas cobertas.
        if (!world.canSeeSky(BlockPos(bx, y, bz))) return

        val level = random.nextIntBetweenInclusive(cfg.levelMin, cfg.levelMax)
        val entity = PokemonEntity(world, species.create(level))
        entity.moveTo(x, y.toDouble(), z, random.nextFloat() * 360f, 0f)
        entity.setFlying(true)
        entity.deltaMovement = Vec3.ZERO
        entity.fallDistance = 0f
        world.addFreshEntity(entity)
    }
}
