package com.cobblemontest.dynamicspawns.command

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemontest.dynamicspawns.DynamicSpawns
import com.cobblemontest.dynamicspawns.horde.HordeSystem
import com.cobblemontest.dynamicspawns.outbreak.OutbreakManager
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component

/**
 * Comandos de administração/teste (permissão nível 2):
 *
 * /dynamicspawns reload                     — recarrega a config
 * /dynamicspawns horde <espécie>            — força uma horda na sua posição (espécie evoluída)
 * /dynamicspawns outbreak start [espécie]   — inicia um Mass Outbreak
 * /dynamicspawns outbreak stop              — encerra o outbreak atual
 * /dynamicspawns outbreak info              — status do outbreak atual
 */
object DynamicSpawnsCommands {

    // Nível base usado quando uma horda é forçada por comando (o líder recebe + leaderLevelBonus)
    private const val HORDE_COMMAND_LEVEL = 15

    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal("dynamicspawns")
                    .requires { it.hasPermission(2) }
                    .then(literal("reload").executes(::reload))
                    .then(
                        literal("horde").then(
                            argument("species", StringArgumentType.word())
                                .suggests { _, builder ->
                                    SharedSuggestionProvider.suggest(
                                        PokemonSpecies.implemented
                                            .filter { it.standardForm.evolutions.isNotEmpty() }
                                            .map { it.resourceIdentifier.path },
                                        builder
                                    )
                                }
                                .executes(::forceHorde)
                        )
                    )
                    .then(
                        literal("outbreak")
                            .then(
                                literal("start")
                                    .executes { ctx -> startOutbreak(ctx, null) }
                                    .then(
                                        argument("species", StringArgumentType.word())
                                            .suggests { _, builder ->
                                                SharedSuggestionProvider.suggest(
                                                    PokemonSpecies.implemented
                                                        .filter { OutbreakManager.isEligible(it) }
                                                        .map { it.resourceIdentifier.path },
                                                    builder
                                                )
                                            }
                                            .executes { ctx ->
                                                val name = StringArgumentType.getString(ctx, "species")
                                                val species = findSpecies(name)
                                                if (species == null) {
                                                    ctx.source.sendFailure(
                                                        Component.translatable("dynamicspawns.command.unknown_species", name)
                                                    )
                                                    0
                                                } else {
                                                    startOutbreak(ctx, species)
                                                }
                                            }
                                    )
                            )
                            .then(literal("stop").executes(::stopOutbreak))
                            .then(literal("info").executes(::outbreakInfo))
                    )
            )
        }
    }

    private fun findSpecies(name: String): Species? =
        PokemonSpecies.getByIdentifier(cobblemonResource(name.lowercase()))

    private fun reload(ctx: CommandContext<CommandSourceStack>): Int {
        DynamicSpawns.reloadConfig()
        ctx.source.sendSuccess({ Component.translatable("dynamicspawns.command.reload") }, true)
        return 1
    }

    private fun forceHorde(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "species")
        val base = findSpecies(name)
        if (base == null) {
            ctx.source.sendFailure(Component.translatable("dynamicspawns.command.unknown_species", name))
            return 0
        }
        val leaderSpecies = HordeSystem.evolvedLeaderFor(base)
        if (leaderSpecies == null) {
            ctx.source.sendFailure(Component.translatable("dynamicspawns.command.cannot_evolve", name))
            return 0
        }
        val player = ctx.source.playerOrException
        val world = ctx.source.level
        val result = HordeSystem.spawnHorde(world, leaderSpecies, base, HORDE_COMMAND_LEVEL, player.x, player.y, player.z)
        ctx.source.sendSuccess(
            {
                Component.translatable(
                    "dynamicspawns.command.horde_success",
                    leaderSpecies.name, result.leaderLevel, result.members, base.name
                )
            },
            true
        )
        return 1
    }

    private fun startOutbreak(ctx: CommandContext<CommandSourceStack>, species: Species?): Int {
        val server = ctx.source.server
        val cfg = DynamicSpawns.config.outbreaks
        if (species != null && !OutbreakManager.isEligible(species)) {
            ctx.source.sendFailure(Component.translatable("dynamicspawns.command.outbreak_excluded", species.name))
            return 0
        }
        if (OutbreakManager.activeOutbreaks.size >= cfg.maxSimultaneous) {
            ctx.source.sendFailure(
                Component.translatable("dynamicspawns.command.outbreak_limit", cfg.maxSimultaneous)
            )
            return 0
        }
        // Com espécie forçada e jogador presente, centra perto do executor; senão aleatório
        val player = ctx.source.player
        val started = if (species != null && player != null) {
            val world = player.serverLevel()
            val center = world.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(player.x + 48, player.y, player.z)
            )
            if (!OutbreakManager.isFarEnoughFromActive(world, center)) {
                ctx.source.sendFailure(
                    Component.translatable("dynamicspawns.command.too_close", cfg.minChunkDistanceBetweenOutbreaks)
                )
                return 0
            }
            OutbreakManager.start(server, world, center, species)
        } else {
            OutbreakManager.startRandom(server, species)
        }
        if (!started) {
            ctx.source.sendFailure(Component.translatable("dynamicspawns.command.start_failed"))
            return 0
        }
        return 1
    }

    private fun stopOutbreak(ctx: CommandContext<CommandSourceStack>): Int {
        val stopped = OutbreakManager.stopAll(ctx.source.server)
        return if (stopped > 0) stopped else {
            ctx.source.sendFailure(Component.translatable("dynamicspawns.command.no_outbreak"))
            0
        }
    }

    private fun outbreakInfo(ctx: CommandContext<CommandSourceStack>): Int {
        val outbreaks = OutbreakManager.activeOutbreaks
        if (outbreaks.isEmpty()) {
            ctx.source.sendSuccess({ Component.translatable("dynamicspawns.command.no_outbreak") }, false)
            return 0
        }
        val cfg = DynamicSpawns.config.outbreaks
        ctx.source.sendSuccess(
            { Component.translatable("dynamicspawns.command.info_header", outbreaks.size, cfg.maxSimultaneous) },
            false
        )
        val dayTime = ctx.source.server.overworld().dayTime
        outbreaks.forEachIndexed { index, outbreak ->
            ctx.source.sendSuccess({
                Component.translatable(
                    "dynamicspawns.command.info_line",
                    index + 1,
                    outbreak.species.translatedName,
                    OutbreakManager.dimensionName(outbreak.dimension),
                    outbreak.center.x,
                    outbreak.center.y,
                    outbreak.center.z,
                    outbreak.spawnedTotal,
                    outbreak.cleared,
                    outbreak.shinyRolls(),
                    outbreak.remainingMinutes(dayTime)
                )
            }, false)
        }
        return outbreaks.size
    }
}
