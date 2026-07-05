package com.cobblemontest.dynamicspawns.outbreak

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemontest.dynamicspawns.DynamicSpawns
import java.util.UUID
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Um Mass Outbreak estilo Scarlet/Violet: uma espécie única spawna em massa num
 * ponto do mapa. Derrotar/capturar [shinyMilestone1] e [shinyMilestone2] pokémon
 * aumenta os rolls de shiny (x2 / x3). Limpar o outbreak inteiro pode spawnar um
 * exemplar shiny garantido como recompensa final.
 */
class Outbreak(
    val species: Species,
    val dimension: ResourceKey<Level>,
    val center: BlockPos,
    // Prazo final em tempo de mundo (world.gameTime), que persiste entre save/load — ao
    // contrário de server.tickCount, que zera a cada abertura do servidor.
    private val deadlineGameTime: Long
) {
    private val cfg get() = DynamicSpawns.config.outbreaks

    // Pokemon.uuid (objeto de dados) para detectar captura/derrota; Entity.uuid para contar vivos
    private val pokemonUuids = mutableSetOf<UUID>()
    private val entityUuids = mutableSetOf<UUID>()

    var spawnedTotal = 0
        private set
    var cleared = 0
        private set
    var finished = false
        private set

    private var milestone1Announced = false
    private var milestone2Announced = false
    private var finaleSpawned = false

    companion object {
        /** Cria um outbreak novo com prazo = agora + duração configurada. */
        fun create(species: Species, dimension: ResourceKey<Level>, center: BlockPos, gameTime: Long): Outbreak {
            val durationTicks = DynamicSpawns.config.outbreaks.durationMinutes * 60L * 20L
            return Outbreak(species, dimension, center, gameTime + durationTicks)
        }

        /** Reconstrói um outbreak a partir do snapshot salvo (espécie/dimensão já resolvidas). */
        fun fromDto(dto: OutbreakDto, species: Species, dimension: ResourceKey<Level>): Outbreak {
            val outbreak = Outbreak(species, dimension, BlockPos(dto.cx, dto.cy, dto.cz), dto.deadlineGameTime)
            outbreak.spawnedTotal = dto.spawnedTotal
            outbreak.cleared = dto.cleared
            outbreak.milestone1Announced = dto.milestone1
            outbreak.milestone2Announced = dto.milestone2
            outbreak.finaleSpawned = dto.finale
            dto.pokemonUuids.mapTo(outbreak.pokemonUuids) { UUID.fromString(it) }
            dto.entityUuids.mapTo(outbreak.entityUuids) { UUID.fromString(it) }
            return outbreak
        }
    }

    /** Rolls de shiny atuais (1 base, x2 e x3 nos marcos, como em SV). */
    fun shinyRolls(): Int {
        var rolls = 1
        if (cleared >= cfg.shinyMilestone1) rolls++
        if (cleared >= cfg.shinyMilestone2) rolls++
        return rolls
    }

    fun tick(server: MinecraftServer) {
        if (finished) return
        val world = server.getLevel(dimension)
        if (world == null) {
            finished = true
            return
        }
        if (world.gameTime >= deadlineGameTime) {
            OutbreakManager.broadcast(
                server,
                Component.translatable("dynamicspawns.outbreak.ended_time", species.translatedName)
                    .withStyle(ChatFormatting.YELLOW)
            )
            finished = true
            return
        }
        // Uma tentativa de spawn a cada 5 segundos, respeitando orçamento e limite simultâneo.
        // Só materializa pokémon com um jogador dentro do raio de ativação: assim os spawns
        // caem em chunks que ticam (visíveis/ativos) e não desperdiçamos o orçamento nem
        // estouramos o maxConcurrent contando entidades descarregadas como mortas.
        if (server.tickCount % 100 == 0 &&
            spawnedTotal < cfg.totalPokemon &&
            hasPlayerNearby(world) &&
            countAlive(world) < cfg.maxConcurrent
        ) {
            spawnOne(world)
        }
    }

    /** Há algum jogador dentro do raio de ativação (horizontal) do centro? */
    private fun hasPlayerNearby(world: ServerLevel): Boolean {
        val rSq = cfg.activationRadius.toDouble() * cfg.activationRadius.toDouble()
        return world.players().any { player ->
            val dx = player.x - center.x
            val dz = player.z - center.z
            dx * dx + dz * dz < rSq
        }
    }

    private fun countAlive(world: ServerLevel): Int =
        entityUuids.count { world.getEntity(it)?.isAlive == true }

    fun spawnOne(world: ServerLevel, guaranteedShiny: Boolean = false): Boolean {
        val random = world.random
        val angle = random.nextDouble() * 2 * PI
        val dist = random.nextDouble() * cfg.spawnRadius
        val x = center.x + cos(angle) * dist
        val z = center.z + sin(angle) * dist
        val surface = world.getHeightmapPos(
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
            BlockPos.containing(x, 0.0, z)
        )

        val level = random.nextIntBetweenInclusive(cfg.levelMin, cfg.levelMax)
        val pokemon = species.create(level)
        pokemon.shiny = guaranteedShiny ||
            random.nextFloat() < shinyRolls() / Cobblemon.config.shinyRate

        val entity = PokemonEntity(world, pokemon)
        entity.moveTo(x, surface.y.toDouble(), z, random.nextFloat() * 360f, 0f)
        if (!world.addFreshEntity(entity)) return false

        pokemonUuids += pokemon.uuid
        entityUuids += entity.uuid
        spawnedTotal++
        return true
    }

    /** Chamado quando um pokémon do outbreak é capturado ou derrotado. */
    fun onPokemonCleared(server: MinecraftServer, pokemonUuid: UUID, byCapture: Boolean) {
        if (finished) return
        // remove() deduplica eventos (captura/faint podem disparar mais de um evento)
        if (!pokemonUuids.remove(pokemonUuid)) return
        cleared++

        if (!milestone1Announced && cleared >= cfg.shinyMilestone1) {
            milestone1Announced = true
            OutbreakManager.broadcast(
                server,
                Component.translatable("dynamicspawns.outbreak.milestone1", species.translatedName)
                    .withStyle(ChatFormatting.AQUA)
            )
        }
        if (!milestone2Announced && cleared >= cfg.shinyMilestone2) {
            milestone2Announced = true
            OutbreakManager.broadcast(
                server,
                Component.translatable("dynamicspawns.outbreak.milestone2", species.translatedName)
                    .withStyle(ChatFormatting.LIGHT_PURPLE)
            )
        }

        if (cleared >= cfg.totalPokemon) {
            if (cfg.guaranteedShinyOnClear && !finaleSpawned) {
                finaleSpawned = true
                server.getLevel(dimension)?.let { world ->
                    if (spawnOne(world, guaranteedShiny = true)) {
                        OutbreakManager.broadcast(
                            server,
                            Component.translatable("dynamicspawns.outbreak.finale", species.translatedName)
                                .withStyle(ChatFormatting.GOLD)
                        )
                    }
                }
            } else {
                OutbreakManager.broadcast(
                    server,
                    Component.translatable("dynamicspawns.outbreak.cleared", species.translatedName)
                        .withStyle(ChatFormatting.GREEN)
                )
            }
            finished = true
        }
    }

    /** Snapshot serializável do estado atual (para persistir no arquivo do mundo). */
    fun toDto(): OutbreakDto = OutbreakDto(
        species = species.resourceIdentifier.toString(),
        dimension = dimension.location().toString(),
        cx = center.x, cy = center.y, cz = center.z,
        spawnedTotal = spawnedTotal,
        cleared = cleared,
        milestone1 = milestone1Announced,
        milestone2 = milestone2Announced,
        finale = finaleSpawned,
        deadlineGameTime = deadlineGameTime,
        pokemonUuids = pokemonUuids.map { it.toString() },
        entityUuids = entityUuids.map { it.toString() }
    )
}
