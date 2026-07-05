# Cobblemon Dynamic Spawns

*Read this in [English](README.md).*

Mod addon para Cobblemon 1.7.3 (Minecraft 1.21.1, Fabric) que deixa os spawns de
pokémon mais dinâmicos.

## Funcionalidades

### 1. Variedade de spawns por bioma + luz (datapack)
Camada **aditiva** sobre a spawn pool padrão do Cobblemon — nada dos spawns
originais é alterado, apenas adicionamos entradas novas em
`data/cobblemon/spawn_pool_world/dynamicspawns_*.json`:

- **Cavernas escuras** (`maxLight 0-3`, sem céu): Zubat, Gastly, Sableye (raro)
- **Cavernas iluminadas** (`minLight 4+`, sem céu): Geodude, Machop, Aron, Roggenrola
- **Florestas**: Hoothoot (noite), Seedot, Shroomish, Pineco
- **Planícies**: Ponyta, Doduo, Growlithe, Eevee (raro)
- **Desertos**: Sandshrew, Trapinch (dia), Cacnea (noite), Sandile
- **Neve**: Swinub, Snorunt (noite), Cubchoo, Snover
- **Costa/praia**: Krabby, Wingull (dia), Corphish, Staryu (noite)

### 2. Hordas
Quando uma espécie **evoluída** spawna naturalmente (ex: Bibarel), há uma chance
(configurável, padrão 8%) de ela virar **líder de horda**:
- Líder ganha bônus de nível (+5) e 3 IVs perfeitos aleatórios
- 3-5 membros da **pré-evolução** (ex: Bidoofs) spawnam ao redor

### 3. Mass Outbreaks (estilo Scarlet/Violet)
Em intervalos aleatórios (20-45 min), um outbreak global começa em um local
aleatório do mapa, anunciado no chat para todos:
- Até **3 outbreaks simultâneos** (`maxSimultaneous`), cada um de uma espécie
  diferente, com inícios escalonados pelo intervalo aleatório
- **Espaçamento territorial**: outbreaks ativos ficam a pelo menos **16 chunks**
  (256 blocos) uns dos outros (`minChunkDistanceBetweenOutbreaks`) — nunca se
  sobrepõem; se não houver posição válida, o início é adiado para o próximo intervalo
- O **primeiro outbreak é garantido** assim que o primeiro dia in-game termina
  (`startAfterInGameDays`); os seguintes seguem o intervalo aleatório. Comandos
  manuais ignoram essa regra
- Espécie única spawnando em massa (orçamento de 80 pokémon, máx. 8 vivos por outbreak)
- Os pokémon só materializam enquanto há um jogador dentro do raio de ativação
  (`activationRadius`), garantindo que nasçam em chunks carregados/ativos — o
  outbreak é anunciado globalmente com coordenadas e você viaja até lá para
  disparar os spawns (estilo Scarlet/Violet)
- Derrotar/capturar **30** → shiny rolls **x2** | **60** → **x3** (anunciado no chat)
- Limpar o outbreak inteiro → spawna um exemplar **shiny garantido** de recompensa
- Termina por tempo (20 min) ou ao ser limpo

## Comandos (permissão nível 2)

| Comando | Efeito |
|---|---|
| `/dynamicspawns reload` | Recarrega a config |
| `/dynamicspawns horde <espécie>` | Força uma horda na sua posição (usar espécie evoluída, ex: `bibarel`) |
| `/dynamicspawns outbreak start [espécie]` | Inicia um outbreak (aleatório ou da espécie dada, perto de você) |
| `/dynamicspawns outbreak stop` | Encerra todos os outbreaks ativos |
| `/dynamicspawns outbreak info` | Lista os outbreaks ativos com status |

## Config

Gerada em `config/dynamicspawns.json` na primeira execução. Todos os números
(chances, tamanhos, intervalos, marcos de shiny) são ajustáveis lá;
`/dynamicspawns reload` aplica sem reiniciar.

### 4. Spawns aleatórios
**10% dos spawns naturais** (configurável) são trocados por uma espécie totalmente
aleatória, ignorando as regras de bioma — qualquer pokémon pode aparecer em
qualquer lugar:
- **Lendários, míticos, ultra beasts e paradox ficam fora do sorteio**
  (`excludedLabels`) — e spawns naturais dessas espécies nunca são trocados
  (protege lendários selvagens de modpacks)
- **Em água/biomas aquáticos** (oceano, rio, água doce), o sorteio considera
  apenas espécies que vivem na água (respiram debaixo d'água)
- O nível do spawn original é mantido, preservando o balanceamento da área
- `excludedNamespaces` permite excluir espécies de addons específicos (ex: `lumymon`)

## Compatibilidade com Cobbleverse

Verificado contra o **COBBLEVERSE 1.7.31** (Modrinth, abril/2026): MC 1.21.1 +
Fabric 0.18.4 + Cobblemon 1.7.3 + fabric-language-kotlin 1.13.10 — todas as
dependências batem com as deste mod. Os addons de spawn do pack (LumyMon,
LegendaryMonuments, Raid Dens, Mega Showdown) usam mecanismos próprios
(estruturas/altares/raids) que não passam pelo BestSpawner, e a proteção de
labels garante que lendários selvagens não sejam trocados pelo spawn aleatório.
O boost de shiny dos outbreaks lê `shinyRate` da config do Cobblemon em runtime,
respeitando automaticamente o 1:2048 do pack.

## Portabilidade

O mod usa **apenas API pública** do Cobblemon (eventos `ENTITY_SPAWN`,
`POKEMON_CAPTURED`, `POKEMON_FAINTED`, `BATTLE_FAINTED` e o formato data-driven
de spawn pools) — **zero mixins** nos internals do Cobblemon. Isso minimiza
quebras em futuras atualizações. A dependência declarada aceita `>=1.7.3 <1.8.0`;
para atualizar, ajuste a versão no `build.gradle.kts` e no `fabric.mod.json`.

## Limitações conhecidas (v1.0.0)

- O estado do outbreak não persiste entre reinicializações do servidor
- Mensagens de chat são traduzidas no cliente (inglês e pt-BR inclusos);
  jogadores que entrarem sem o mod no cliente verão as chaves de tradução cruas
- Outbreaks sempre escolhem posição na superfície (heightmap)

## Build

```
./gradlew build      # jar em build/libs/
./gradlew runClient  # cliente de teste
```
