package com.cobblemontest.dynamicspawns.outbreak

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemontest.dynamicspawns.DynamicSpawns
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Agenda e gerencia Mass Outbreaks globais. Vários outbreaks podem estar ativos ao
 * mesmo tempo (limite configurável em [maxSimultaneous]); os inícios são escalonados
 * pelo intervalo aleatório configurado. Notificações via chat para todos os jogadores.
 */
object OutbreakManager {

    private const val POSITION_ATTEMPTS = 10

    private val active = mutableListOf<Outbreak>()

    /** Outbreaks ativos no momento (somente leitura). */
    val activeOutbreaks: List<Outbreak> get() = active

    private var nextStartAtTick = -1
    // Assim que o primeiro dia in-game termina, o primeiro outbreak é obrigatório
    // (dispara na hora). Só depois desse os intervalos aleatórios entram em ação.
    private var firstOutbreakDone = false
    private var currentServer: MinecraftServer? = null

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::onTick)
        ServerLifecycleEvents.SERVER_STOPPED.register {
            active.clear()
            nextStartAtTick = -1
            firstOutbreakDone = false
            currentServer = null
        }

        CobblemonEvents.POKEMON_CAPTURED.subscribe(Priority.NORMAL) { event ->
            notifyCleared(event.player.server, event.pokemon.uuid, byCapture = true)
        }
        CobblemonEvents.POKEMON_FAINTED.subscribe(Priority.NORMAL) { event ->
            val server = currentServer ?: return@subscribe
            notifyCleared(server, event.pokemon.uuid, byCapture = false)
        }
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.NORMAL) { event ->
            val server = currentServer ?: return@subscribe
            notifyCleared(server, event.killed.effectedPokemon.uuid, byCapture = false)
        }
    }

    private fun notifyCleared(server: MinecraftServer, pokemonUuid: java.util.UUID, byCapture: Boolean) {
        // Cada pokémon pertence a no máximo um outbreak; remove() interno deduplica
        active.forEach { it.onPokemonCleared(server, pokemonUuid, byCapture) }
    }

    private fun onTick(server: MinecraftServer) {
        currentServer = server
        val cfg = DynamicSpawns.config.outbreaks
        if (!cfg.enabled) return

        active.forEach { it.tick(server) }
        active.removeAll { it.finished }

        // Outbreaks naturais só começam depois do primeiro dia in-game (configurável).
        // Outbreaks manuais (/dynamicspawns outbreak start) não passam por aqui.
        if (server.overworld().dayTime < cfg.startAfterInGameDays * 24000L) return

        if (active.size >= cfg.maxSimultaneous) {
            // No limite: quando abrir vaga, um novo intervalo é sorteado do zero
            nextStartAtTick = -1
            return
        }
        if (!firstOutbreakDone) {
            // Virada do primeiro dia: outbreak obrigatório, sem esperar intervalo.
            // Se falhar (sem jogadores), tenta de novo no próximo tick até nascer.
            if (startRandom(server)) firstOutbreakDone = true
        } else if (nextStartAtTick < 0) {
            scheduleNext(server)
        } else if (server.tickCount >= nextStartAtTick) {
            startRandom(server)
        }
    }

    /**
     * Garante o espaçamento radial entre outbreaks: [center] precisa estar a pelo menos
     * [DynamicSpawnsConfig.Outbreaks.minChunkDistanceBetweenOutbreaks] chunks de qualquer
     * outbreak ativo na mesma dimensão.
     */
    fun isFarEnoughFromActive(world: ServerLevel, center: BlockPos): Boolean {
        val minDistBlocks = DynamicSpawns.config.outbreaks.minChunkDistanceBetweenOutbreaks * 16.0
        val minDistSq = minDistBlocks * minDistBlocks
        return active.none { outbreak ->
            outbreak.dimension == world.dimension() &&
                outbreak.center.distSqr(center) < minDistSq
        }
    }

    private fun scheduleNext(server: MinecraftServer) {
        val cfg = DynamicSpawns.config.outbreaks
        val minTicks = cfg.minIntervalMinutes * 60 * 20
        val maxTicks = max(minTicks, cfg.maxIntervalMinutes * 60 * 20)
        val delay = minTicks + server.overworld().random.nextInt(maxTicks - minTicks + 1)
        nextStartAtTick = server.tickCount + delay
    }

    /**
     * Inicia um outbreak em local aleatório perto de um jogador aleatório, respeitando
     * a distância mínima entre outbreaks ativos. Tenta alguns sorteios de posição;
     * se nenhum respeitar o espaçamento, adia para o próximo intervalo.
     */
    fun startRandom(server: MinecraftServer, forcedSpecies: Species? = null): Boolean {
        val players = server.playerList.players
        if (players.isEmpty()) {
            // Sem jogadores online: tenta de novo no próximo intervalo
            scheduleNext(server)
            return false
        }
        val cfg = DynamicSpawns.config.outbreaks

        // Evita duas espécies iguais em outbreaks simultâneos
        val species = forcedSpecies
            ?: PokemonSpecies.implemented
                .filter { candidate -> active.none { it.species == candidate } }
                .randomOrNull()
        if (species == null) {
            scheduleNext(server)
            return false
        }

        repeat(POSITION_ATTEMPTS) {
            val player = players.random()
            val world = player.serverLevel()
            val random = world.random

            val angle = random.nextDouble() * 2 * PI
            val distRange = max(0, cfg.maxDistanceFromPlayer - cfg.minDistanceFromPlayer)
            val dist = cfg.minDistanceFromPlayer + random.nextDouble() * distRange
            val x = player.x + cos(angle) * dist
            val z = player.z + sin(angle) * dist
            val center = world.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(x, 0.0, z)
            )
            if (isFarEnoughFromActive(world, center)) {
                return start(server, world, center, species)
            }
        }
        // Não achou posição válida (mapa saturado de outbreaks): tenta no próximo intervalo
        scheduleNext(server)
        return false
    }

    /** Inicia um outbreak da [species] centrado em [center]. */
    fun start(server: MinecraftServer, world: ServerLevel, center: BlockPos, species: Species): Boolean {
        val cfg = DynamicSpawns.config.outbreaks
        if (active.size >= cfg.maxSimultaneous) return false
        if (!isFarEnoughFromActive(world, center)) return false
        active += Outbreak(species, world.dimension(), center, server.tickCount)
        broadcast(
            server,
            Component.translatable(
                "dynamicspawns.outbreak.started",
                species.translatedName, center.x, center.z
            ).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
        )
        // Escalona o próximo início a partir de agora
        scheduleNext(server)
        return true
    }

    /** Encerra todos os outbreaks ativos. Retorna quantos foram encerrados. */
    fun stopAll(server: MinecraftServer): Int {
        if (active.isEmpty()) return 0
        active.forEach { outbreak ->
            broadcast(
                server,
                Component.translatable("dynamicspawns.outbreak.stopped", outbreak.species.translatedName)
                    .withStyle(ChatFormatting.YELLOW)
            )
        }
        val count = active.size
        active.clear()
        scheduleNext(server)
        return count
    }

    fun broadcast(server: MinecraftServer, message: Component) {
        server.playerList.broadcastSystemMessage(message, false)
    }
}
