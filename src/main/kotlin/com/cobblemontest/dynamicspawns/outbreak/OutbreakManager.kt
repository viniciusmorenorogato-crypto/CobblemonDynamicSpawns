package com.cobblemontest.dynamicspawns.outbreak

import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemontest.dynamicspawns.DynamicSpawns
import com.cobblemontest.dynamicspawns.environment.SpawnEnvironment
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.Level
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
    private const val SAVE_INTERVAL_TICKS = 600 // save periódico (30s) para segurança contra crash

    private val active = mutableListOf<Outbreak>()

    /** Outbreaks ativos no momento (somente leitura). */
    val activeOutbreaks: List<Outbreak> get() = active

    // Prazo do próximo outbreak em world.dayTime (avança ao dormir; persiste entre save/load); -1 = não agendado
    private var nextStartAtDayTime = -1L
    // Assim que o primeiro dia in-game termina, o primeiro outbreak é obrigatório
    // (dispara na hora). Só depois desse os intervalos aleatórios entram em ação.
    private var firstOutbreakDone = false
    private var dirty = false
    private var currentServer: MinecraftServer? = null

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::onTick)
        // Carrega o estado salvo do mundo ao abrir; salva ao fechar. Assim os outbreaks
        // continuam de onde pararam e NÃO nasce um outbreak espúrio a cada reabertura.
        ServerLifecycleEvents.SERVER_STARTED.register { server -> loadState(server) }
        ServerLifecycleEvents.SERVER_STOPPING.register { server -> saveState(server) }
        ServerLifecycleEvents.SERVER_STOPPED.register { resetInMemory() }
        // Ao entrar, avisa se há outbreaks ativos (com coordenadas e tempo restante).
        ServerPlayConnectionEvents.JOIN.register { handler, _, server -> onPlayerJoin(handler.player, server) }

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
        dirty = true
    }

    private fun resetInMemory() {
        active.clear()
        nextStartAtDayTime = -1L
        firstOutbreakDone = false
        dirty = false
        currentServer = null
    }

    private fun buildState() = OutbreakStateDto(firstOutbreakDone, nextStartAtDayTime, active.map { it.toDto() })

    private fun saveState(server: MinecraftServer) {
        OutbreakPersistence.save(server, buildState())
        dirty = false
    }

    private fun loadState(server: MinecraftServer) {
        resetInMemory()
        val state = OutbreakPersistence.load(server)
        firstOutbreakDone = state.firstOutbreakDone
        nextStartAtDayTime = state.nextStartAtDayTime
        state.outbreaks.forEach { dto -> OutbreakPersistence.restore(server, dto)?.let { active += it } }
        if (active.isNotEmpty()) {
            DynamicSpawns.LOGGER.info("Restaurados {} outbreak(s) do mundo", active.size)
        }
    }

    private fun onTick(server: MinecraftServer) {
        currentServer = server
        val cfg = DynamicSpawns.config.outbreaks
        if (!cfg.enabled) return

        val activeBefore = active.size
        active.forEach { it.tick(server) }
        active.removeAll { it.finished }
        if (active.size != activeBefore) dirty = true

        // Usamos dayTime (ciclo dia/noite) e NÃO gameTime para o intervalo: dayTime avança
        // ao dormir/pular dias, então os outbreaks continuam nascendo conforme o tempo passa,
        // mesmo que o jogador não gaste minutos reais acordado.
        val dayTime = server.overworld().dayTime
        // Outbreaks naturais só começam depois do primeiro dia in-game (configurável).
        // Outbreaks manuais (/dynamicspawns outbreak start) não passam por aqui.
        if (dayTime >= cfg.startAfterInGameDays * 24000L) {
            val maxDelayTicks = cfg.maxIntervalMinutes * 60L * 20L
            if (active.size >= cfg.maxSimultaneous) {
                // No limite: quando abrir vaga, um novo intervalo é sorteado do zero
                nextStartAtDayTime = -1L
            } else if (!firstOutbreakDone) {
                // Virada do primeiro dia: outbreak obrigatório, sem esperar intervalo.
                // Se falhar (sem jogadores), tenta de novo no próximo tick até nascer.
                if (startRandom(server)) {
                    firstOutbreakDone = true
                    dirty = true
                }
            } else if (nextStartAtDayTime < 0 || nextStartAtDayTime - dayTime > maxDelayTicks) {
                // Não agendado, ou dayTime foi movido para trás (/time set): reagenda de agora.
                scheduleNext(server)
            } else if (dayTime >= nextStartAtDayTime) {
                startRandom(server)
            }
        }

        // Save periódico de segurança; o fechamento normal do mundo salva em SERVER_STOPPING.
        if (dirty && server.tickCount % SAVE_INTERVAL_TICKS == 0) {
            saveState(server)
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
        // Agendado em dayTime (avança ao dormir/pular dias), não em gameTime.
        nextStartAtDayTime = server.overworld().dayTime + delay
        dirty = true
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

        repeat(POSITION_ATTEMPTS) {
            val player = players.random()
            val world = player.serverLevel()
            // Pula dimensões desligadas (ex: The End não tem outbreak aleatório)
            if (!SpawnEnvironment.dynamicSpawnsAllowed(world)) return@repeat
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
            if (!isFarEnoughFromActive(world, center)) return@repeat

            // Escolhe uma espécie adequada ao local (tipo permitido na dimensão + terreno):
            // evita ex. outbreak de tipo grama no Nether ou de peixe em superfície seca.
            val species = forcedSpecies ?: pickSpeciesFor(world, center)
            if (species != null) {
                return start(server, world, center, species)
            }
        }
        // Nenhuma posição/espécie válida: tenta no próximo intervalo
        scheduleNext(server)
        return false
    }

    /** Espécie adequada ao local, evitando repetir espécies de outbreaks ativos. */
    private fun pickSpeciesFor(world: ServerLevel, center: BlockPos): Species? {
        // Centro sobre água ou terra? Escolhe espécie do tipo certo para o local, para o
        // outbreak inteiro combinar (peixe no oceano, terrestre em terra firme).
        val centerOverWater = world.getBlockState(center.below()).fluidState.`is`(FluidTags.WATER)
        return PokemonSpecies.implemented
            .filter { candidate ->
                isEligible(candidate) &&
                    active.none { it.species == candidate } &&
                    SpawnEnvironment.isSpeciesAllowed(candidate, world, center) &&
                    SpawnEnvironment.isWaterDweller(candidate) == centerOverWater
            }
            .randomOrNull()
    }

    /** A espécie pode virar outbreak? (fora lendários/míticos/ultra beasts, configurável) */
    fun isEligible(species: Species): Boolean {
        val excluded = DynamicSpawns.config.outbreaks.excludedLabels.toSet()
        return species.labels.none { it in excluded }
    }

    /** Inicia um outbreak da [species] centrado em [center]. */
    fun start(server: MinecraftServer, world: ServerLevel, center: BlockPos, species: Species): Boolean {
        val cfg = DynamicSpawns.config.outbreaks
        if (active.size >= cfg.maxSimultaneous) return false
        if (!SpawnEnvironment.dynamicSpawnsAllowed(world)) return false
        if (!isFarEnoughFromActive(world, center)) return false
        active += Outbreak.create(species, world.dimension(), center, world.dayTime)
        broadcast(
            server,
            Component.translatable(
                "dynamicspawns.outbreak.started",
                species.translatedName, center.x, center.z
            ).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
        )
        // Waypoint no Xaero's Minimap: mensagem no formato que o Xaero reconhece como [Add].
        if (cfg.xaeroWaypoint) {
            broadcast(server, Component.literal(xaeroWaypointString(species, world.dimension(), center)))
        }
        // Escalona o próximo início a partir de agora
        scheduleNext(server)
        dirty = true
        return true
    }

    /**
     * Constrói a mensagem de waypoint do Xaero's Minimap (10 campos separados por ':'):
     * nome:símbolo:x:y:z:cor:disabled:yaw:internal-<dim>-waypoints. Clientes com Xaero
     * mostram um botão [Add]; sem Xaero, aparece como texto.
     */
    private fun xaeroWaypointString(species: Species, dimension: ResourceKey<Level>, center: BlockPos): String {
        val name = "Outbreak-${species.resourceIdentifier.path}"
        val dim = xaeroDimSegment(dimension)
        // cor 14 = vermelho na paleta do Xaero; símbolo "!" como marcador de outbreak
        return "xaero-waypoint:$name:!:${center.x}:${center.y}:${center.z}:14:false:0:internal-$dim-waypoints"
    }

    private fun xaeroDimSegment(dimension: ResourceKey<Level>): String = when (dimension) {
        Level.OVERWORLD -> "overworld"
        Level.NETHER -> "the-nether"
        Level.END -> "the-end"
        else -> dimension.location().path.replace("_", "-")
    }

    /** Ao entrar no mundo, lista os outbreaks ativos (coordenadas + tempo restante). */
    private fun onPlayerJoin(player: ServerPlayer, server: MinecraftServer) {
        if (active.isEmpty()) return
        val dayTime = server.overworld().dayTime
        player.sendSystemMessage(
            Component.translatable("dynamicspawns.join.header", active.size)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
        )
        active.forEach { outbreak ->
            player.sendSystemMessage(
                Component.translatable(
                    "dynamicspawns.join.line",
                    outbreak.species.translatedName,
                    outbreak.center.x,
                    outbreak.center.z,
                    outbreak.remainingMinutes(dayTime)
                ).withStyle(ChatFormatting.YELLOW)
            )
        }
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
