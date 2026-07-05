package com.cobblemontest.dynamicspawns.outbreak

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemontest.dynamicspawns.DynamicSpawns
import com.google.gson.GsonBuilder
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files

/** Snapshot serializável de um outbreak (persistido no arquivo do mundo). */
data class OutbreakDto(
    val species: String,
    val dimension: String,
    val cx: Int,
    val cy: Int,
    val cz: Int,
    val spawnedTotal: Int,
    val cleared: Int,
    val milestone1: Boolean,
    val milestone2: Boolean,
    val finale: Boolean,
    val deadlineGameTime: Long,
    val pokemonUuids: List<String>,
    val entityUuids: List<String>
)

/** Estado completo do gerenciador de outbreaks salvo por mundo. */
data class OutbreakStateDto(
    val firstOutbreakDone: Boolean = false,
    val nextStartAtGameTime: Long = -1L,
    val outbreaks: List<OutbreakDto> = emptyList()
)

/**
 * Persistência dos Mass Outbreaks no arquivo `dynamicspawns_outbreaks.json` dentro da
 * pasta do mundo. Assim os outbreaks continuam de onde pararam ao fechar e reabrir o
 * mundo (e a base de tempo usa world.gameTime, que também persiste).
 */
object OutbreakPersistence {

    private const val FILE_NAME = "dynamicspawns_outbreaks.json"
    private val GSON = GsonBuilder().setPrettyPrinting().create()

    private fun file(server: MinecraftServer) =
        server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME)

    fun save(server: MinecraftServer, state: OutbreakStateDto) {
        try {
            Files.newBufferedWriter(file(server)).use { GSON.toJson(state, it) }
        } catch (e: Exception) {
            DynamicSpawns.LOGGER.error("Falha ao salvar estado dos outbreaks", e)
        }
    }

    /** Lê o estado salvo; devolve estado vazio (padrão) se não houver arquivo. */
    fun load(server: MinecraftServer): OutbreakStateDto {
        val path = file(server)
        if (!Files.exists(path)) return OutbreakStateDto()
        return try {
            Files.newBufferedReader(path).use { GSON.fromJson(it, OutbreakStateDto::class.java) }
                ?: OutbreakStateDto()
        } catch (e: Exception) {
            DynamicSpawns.LOGGER.error("Falha ao ler estado dos outbreaks, ignorando", e)
            OutbreakStateDto()
        }
    }

    /** Resolve espécie e dimensão de um DTO e reconstrói o outbreak, ou null se inválido. */
    fun restore(server: MinecraftServer, dto: OutbreakDto): Outbreak? {
        val speciesId = ResourceLocation.tryParse(dto.species) ?: return null
        val species = PokemonSpecies.getByIdentifier(speciesId) ?: return null
        val dimensionId = ResourceLocation.tryParse(dto.dimension) ?: return null
        val dimension: ResourceKey<Level> = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId)
        // Dimensão precisa existir neste mundo
        if (server.getLevel(dimension) == null) return null
        return Outbreak.fromDto(dto, species, dimension)
    }
}
