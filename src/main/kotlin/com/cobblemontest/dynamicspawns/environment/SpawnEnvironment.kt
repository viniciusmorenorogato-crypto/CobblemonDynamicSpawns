package com.cobblemontest.dynamicspawns.environment

import com.cobblemon.mod.common.pokemon.Species
import com.cobblemontest.dynamicspawns.DynamicSpawns
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.FluidTags

/**
 * Regras de adequação ambiental para os spawns dinâmicos (aleatórios e outbreaks):
 * exclusão de dimensões, tipos elementais proibidos por dimensão, e adequação de
 * terreno (espécie aquática só na água, etc.). Tudo dirigido por `config.environment`,
 * usando apenas API pública do Cobblemon.
 */
object SpawnEnvironment {

    private fun dimensionId(world: ServerLevel): String =
        world.dimension().location().toString()

    /**
     * Os spawns dinâmicos estão habilitados nesta dimensão? Usa lista branca: só as
     * dimensões de "mundo real" (Overworld/Nether por padrão). Assim dimensões
     * instanciadas de mods (raid dens, distortion world, etc.) ficam de fora
     * automaticamente, sem precisar listar cada uma.
     */
    fun dynamicSpawnsAllowed(world: ServerLevel): Boolean =
        dimensionId(world) in DynamicSpawns.config.environment.allowedDimensions

    /** Nenhum dos tipos da espécie está na lista de proibidos da dimensão atual? */
    fun isTypeAllowed(species: Species, world: ServerLevel): Boolean {
        val banned = DynamicSpawns.config.environment.dimensionBannedTypes[dimensionId(world)]
            ?: return true
        if (banned.isEmpty()) return true
        val bannedLower = banned.map { it.lowercase() }.toSet()
        return species.types.none { it.showdownId.lowercase() in bannedLower }
    }

    /** True se uma espécie que só vive na água (evita terra / não anda) precisa de água. */
    fun isAquaticOnly(species: Species): Boolean {
        val walk = species.behaviour.moving.walk
        return walk.avoidsLand || !walk.canWalk
    }

    /**
     * Voador "de verdade" do céu: voa (canFly) e NÃO respira debaixo d'água. O segundo
     * critério exclui espécies aquáticas que têm canFly no Cobblemon (ex: Dragonair,
     * Dragonite, Gyarados-like, enguias, águas-vivas), que não devem voar no céu.
     */
    fun isSkyFlier(species: Species): Boolean {
        val move = species.behaviour.moving
        return move.fly.canFly && !move.swim.canBreatheUnderwater
    }

    /** True se a espécie é aquática (vive na água): respira debaixo d'água ou evita terra. */
    fun isWaterDweller(species: Species): Boolean =
        species.behaviour.moving.swim.canBreatheUnderwater || isAquaticOnly(species)

    /** A espécie combina com o terreno da posição? (aquático só na água; anti-água fora d'água) */
    fun isTerrainSuitable(species: Species, world: ServerLevel, pos: BlockPos): Boolean {
        if (!DynamicSpawns.config.environment.enforceTerrain) return true
        val inWater = world.getBlockState(pos).fluidState.`is`(FluidTags.WATER)
        // Espécie aquática (evita terra) fora da água → inadequado (ex: Relicanth na árvore)
        if (isAquaticOnly(species) && !inWater) return false
        // Espécie que evita água, submersa → inadequado
        if (species.behaviour.moving.swim.avoidsWater && inWater) return false
        return true
    }

    /** Combinação: tipo permitido na dimensão E terreno adequado à posição. */
    fun isSpeciesAllowed(species: Species, world: ServerLevel, pos: BlockPos): Boolean =
        isTypeAllowed(species, world) && isTerrainSuitable(species, world, pos)
}
