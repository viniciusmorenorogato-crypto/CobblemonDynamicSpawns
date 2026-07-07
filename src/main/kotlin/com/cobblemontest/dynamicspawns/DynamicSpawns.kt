package com.cobblemontest.dynamicspawns

import com.cobblemontest.dynamicspawns.command.DynamicSpawnsCommands
import com.cobblemontest.dynamicspawns.config.DynamicSpawnsConfig
import com.cobblemontest.dynamicspawns.flying.FlyingSpawnSystem
import com.cobblemontest.dynamicspawns.horde.HordeSystem
import com.cobblemontest.dynamicspawns.outbreak.OutbreakManager
import com.cobblemontest.dynamicspawns.randomspawn.RandomSpawnSystem
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object DynamicSpawns : ModInitializer {
    const val MOD_ID = "dynamicspawns"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    var config = DynamicSpawnsConfig.load()
        private set

    fun reloadConfig() {
        config = DynamicSpawnsConfig.load()
    }

    override fun onInitialize() {
        RandomSpawnSystem.register()
        HordeSystem.register()
        OutbreakManager.register()
        FlyingSpawnSystem.register()
        DynamicSpawnsCommands.register()
        LOGGER.info(
            "Cobblemon Dynamic Spawns inicializado (hordas={}, outbreaks={}, spawns aleatórios={}, voo={})",
            config.hordes.enabled,
            config.outbreaks.enabled,
            config.randomSpawns.enabled,
            config.flyingSpawns.enabled
        )
    }
}
