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
    var environment = Environment()
    var flyingSpawns = FlyingSpawns()
    var skyFliers = SkyFliers()

    /** Faz pássaros (espécies que voam) nascerem no ar, já voando. */
    class FlyingSpawns {
        var enabled = true
        // Altura (blocos acima da superfície) em que os pássaros nascem
        var minHeight = 12
        var maxHeight = 40
        // Se true, só levanta pássaros "puros" (que não andam, ex: Altaria); se false,
        // qualquer espécie que voa (canFly) nasce voando.
        var onlyPureFliers = false
    }

    /** Spawner recorrente de pure fliers no céu, perto dos jogadores. */
    class SkyFliers {
        var enabled = true
        // Intervalo (segundos, tempo real de jogo) entre ondas de spawn
        var minIntervalSeconds = 180
        var maxIntervalSeconds = 480
        // Quantos pássaros por onda, por jogador
        var perPlayerMin = 1
        var perPlayerMax = 2
        // Altura acima da superfície e distância horizontal do jogador
        var minHeight = 40
        var maxHeight = 90
        var minDistance = 20
        var maxDistance = 64
        var levelMin = 10
        var levelMax = 35
        // Exclui espécies muito raras: catch rate mínimo (maior = mais comum; lendários ~3)
        var minCatchRate = 45
        var excludedLabels = mutableListOf("legendary", "mythical", "ultra_beast", "paradox")
        // true = só pure fliers estritos (que não andam, ex: Zubat, Drifloon). false =
        // qualquer espécie que voa, incluindo pássaros que também andam (ex: Altaria, Pidgeot).
        var onlyPureFliers = true
    }

    /** Regras de adequação ambiental aplicadas aos spawns dinâmicos (aleatórios e outbreaks). */
    class Environment {
        // Dimensões onde os spawns dinâmicos são totalmente desligados (ex: The End sem aleatoriedade)
        var disabledDimensions = mutableListOf("minecraft:the_end")
        // Tipos elementais proibidos por dimensão (ex: nada de grama/água/gelo/inseto no Nether)
        var dimensionBannedTypes: MutableMap<String, MutableList<String>> = mutableMapOf(
            "minecraft:the_nether" to mutableListOf("grass", "water", "ice", "bug")
        )
        // Respeitar adequação de terreno: espécie aquática (que evita terra) só em água, e
        // espécie que evita água não fica submersa. Evita ex: Relicanth em cima de uma árvore.
        var enforceTerrain = true
    }

    class RandomSpawns {
        var enabled = true
        // Chance (0..1) de um spawn natural ser trocado por uma espécie totalmente aleatória
        var chance = 0.10
        // Espécies com qualquer um desses labels ficam fora do sorteio E têm seus
        // spawns naturais protegidos contra troca (ex: lendário selvagem de um modpack)
        var excludedLabels = mutableListOf("legendary", "mythical", "ultra_beast", "paradox")
        // Namespaces de espécies fora do sorteio/troca (ex: addons de modpack como "lumymon")
        var excludedNamespaces = mutableListOf<String>()
        // Nível realista baseado no base stat total (BST): fracos nascem em nível baixo,
        // fortes/raros escalam até rareLevelCap. Se false, mantém o nível do spawn original.
        var realisticLevels = true
        var levelMin = 5
        // Teto de nível para as espécies mais fortes/raras (BST alto)
        var rareLevelCap = 55
        // Variação (+/-) aplicada ao nível realista, para não ficar monótono
        var levelVariance = 5
    }

    class Hordes {
        var enabled = true
        // Chance (0..1) de um spawn natural de uma espécie evoluída virar uma horda
        var chance = 0.1
        var minMembers = 3
        var maxMembers = 5
        var leaderLevelBonus = 15
        var perfectIvCount = 3
    }

    class Outbreaks {
        var enabled = true
        // Quantos outbreaks podem estar ativos ao mesmo tempo no servidor
        var maxSimultaneous = 6
        // Distância radial mínima entre centros de outbreaks ativos, em chunks (1 chunk = 16 blocos)
        var minChunkDistanceBetweenOutbreaks = 16
        // Outbreaks naturais só começam depois de N dias in-game completos
        var startAfterInGameDays = 1
        var minIntervalMinutes = 5
        var maxIntervalMinutes = 20
        var durationMinutes = 20
        // Total de pokémon do outbreak; derrotar/capturar todos "limpa" o outbreak
        var totalPokemon = 80
        var maxConcurrent = 8
        var spawnRadius = 32
        var minDistanceFromPlayer = 96
        var maxDistanceFromPlayer = 288
        // Raio (blocos) em volta do centro dentro do qual precisa haver um jogador para
        // que o outbreak materialize pokémon. Mantenha <= simulation distance * 16 para
        // garantir que os spawns fiquem em chunks que ticam (visíveis e ativos).
        var activationRadius = 80
        var levelMin = 15
        var levelMax = 40
        // Marcos estilo SV: rolls de shiny x2 e x3
        var shinyMilestone1 = 30
        var shinyMilestone2 = 60
        var guaranteedShinyOnClear = true
        // Labels de espécie que nunca podem virar um Mass Outbreak
        var excludedLabels = mutableListOf("legendary", "mythical", "ultra_beast")
        // Escalada de nível: a cada N pokémon limpos, os próximos spawns ganham
        // +levelBonusPerStep de nível (ex: começa 15-40, sobe conforme o outbreak progride)
        var clearsPerLevelStep = 10
        var levelBonusPerStep = 8
        // Teto de nível dos spawns do outbreak (mesmo com a escalada). A variação continua
        // vindo da faixa levelMin-levelMax, que se desloca com a escalada.
        var levelCap = 80
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
