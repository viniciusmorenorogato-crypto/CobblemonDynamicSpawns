package com.cobblemontest.dynamicspawns.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import org.slf4j.LoggerFactory

class DynamicSpawnsConfig {
    var hordes = Hordes()
    var outbreaks = Outbreaks()
    var randomSpawns = RandomSpawns()

    class RandomSpawns {
        var enabled = true
        // Chance (0..1) de um spawn natural ser trocado por uma espécie totalmente aleatória
        var chance = 0.10
        // Espécies com qualquer um desses labels ficam fora do sorteio E têm seus
        // spawns naturais protegidos contra troca (ex: lendário selvagem de um modpack)
        var excludedLabels = mutableListOf("legendary", "mythical", "ultra_beast", "paradox")
        // Namespaces de espécies fora do sorteio/troca (ex: addons de modpack como "lumymon")
        var excludedNamespaces = mutableListOf<String>()
    }

    class Hordes {
        var enabled = true
        // Chance (0..1) de um spawn natural de uma espécie evoluída virar uma horda
        var chance = 0.08
        var minMembers = 3
        var maxMembers = 5
        var leaderLevelBonus = 5
        var perfectIvCount = 3
    }

    class Outbreaks {
        var enabled = true
        // Quantos outbreaks podem estar ativos ao mesmo tempo no servidor
        var maxSimultaneous = 3
        // Distância radial mínima entre centros de outbreaks ativos, em chunks (1 chunk = 16 blocos)
        var minChunkDistanceBetweenOutbreaks = 16
        // Outbreaks naturais só começam depois de N dias in-game completos
        var startAfterInGameDays = 1
        var minIntervalMinutes = 20
        var maxIntervalMinutes = 45
        var durationMinutes = 20
        // Total de pokémon do outbreak; derrotar/capturar todos "limpa" o outbreak
        var totalPokemon = 80
        var maxConcurrent = 8
        var spawnRadius = 32
        var minDistanceFromPlayer = 96
        var maxDistanceFromPlayer = 288
        var levelMin = 15
        var levelMax = 40
        // Marcos estilo SV: rolls de shiny x2 e x3
        var shinyMilestone1 = 30
        var shinyMilestone2 = 60
        var guaranteedShinyOnClear = true
    }

    fun save(path: Path) {
        Files.createDirectories(path.parent)
        Files.newBufferedWriter(path).use { GSON.toJson(this, it) }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("dynamicspawns")
        private val GSON = GsonBuilder().setPrettyPrinting().create()

        fun configPath(): Path = FabricLoader.getInstance().configDir.resolve("dynamicspawns.json")

        fun load(): DynamicSpawnsConfig {
            val path = configPath()
            val config = try {
                if (Files.exists(path)) {
                    Files.newBufferedReader(path).use { GSON.fromJson(it, DynamicSpawnsConfig::class.java) }
                        ?: DynamicSpawnsConfig()
                } else {
                    DynamicSpawnsConfig()
                }
            } catch (e: Exception) {
                LOGGER.error("Falha ao ler config, usando valores padrão", e)
                DynamicSpawnsConfig()
            }
            // Regrava para materializar campos novos após updates do mod
            try {
                config.save(path)
            } catch (e: Exception) {
                LOGGER.error("Falha ao salvar config", e)
            }
            return config
        }
    }
}
