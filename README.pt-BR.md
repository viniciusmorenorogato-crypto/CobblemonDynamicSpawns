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
Quando uma **espécie base que pode evoluir** spawna naturalmente (ex: Bidoof), há
uma chance (configurável, padrão 10%) de ela virar uma **horda**:
- O Pokémon que nasceu vira um dos membros da horda
- Um **líder evoluído** (ex: Bibarel) spawna ao lado com bônus de nível (+5) e 3
  IVs perfeitos aleatórios
- 3-5 membros base a mais spawnam ao redor

Disparar nos spawns de estágio base (comuns), em vez dos evoluídos (raros), deixa
as hordas muito mais frequentes — como o gatilho incide sobre a maioria dos
spawns, uma chance de poucos por cento já produz bastante; ajuste `chance` para
baixo conforme necessário.

### 3. Mass Outbreaks (estilo Scarlet/Violet)
Em intervalos aleatórios (medidos no tempo de dia in-game — então dormir/avançar
os dias os traz, não só o tempo real jogado), um outbreak global começa num local
aleatório do mapa, anunciado no chat para todos:
- Até **6 outbreaks simultâneos** (`maxSimultaneous`), cada um de uma espécie
  diferente, com inícios escalonados pelo intervalo aleatório
- **Espaçamento territorial**: outbreaks ativos ficam a pelo menos **16 chunks**
  (256 blocos) uns dos outros (`minChunkDistanceBetweenOutbreaks`) — nunca se
  sobrepõem; se não houver posição válida, o início é adiado para o próximo intervalo
- O **primeiro outbreak é garantido** assim que o primeiro dia in-game termina
  (`startAfterInGameDays`); os seguintes seguem o intervalo aleatório. Comandos
  manuais ignoram essa regra
- Espécie única spawnando em massa (orçamento de 80 pokémon, máx. 8 vivos por outbreak)
- Espécies lendárias, míticas e ultra beasts nunca viram outbreak
  (`excludedLabels`)
- Os pokémon só materializam enquanto há um jogador dentro do raio de ativação
  (`activationRadius`), garantindo que nasçam em chunks carregados/ativos — o
  outbreak é anunciado globalmente com coordenadas e você viaja até lá para
  disparar os spawns (estilo Scarlet/Violet)
- Terreno combinado: a espécie é escolhida conforme o centro (aquático sobre
  oceano, terrestre sobre chão), e cada spawn é reverificado — nada de peixe em
  terra firme ou pokémon terrestre no meio do oceano
- Derrotar/capturar **30** → shiny rolls **x2** | **60** → **x3** (anunciado no chat)
- **Escalada de nível**: a cada `clearsPerLevelStep` (10) pokémon derrotados/
  capturados, os próximos spawns ganham `+levelBonusPerStep` (8) de nível — o
  outbreak fica mais forte conforme você o reduz (ex: 15-40 → ~39-64 aos 30 →
  ~63-80 aos 60). A variação vem da faixa de nível, com teto em `levelCap` (80)
- Limpar o outbreak inteiro → spawna um exemplar **shiny garantido** de recompensa
- Termina quando o tempo de vida acaba (também em tempo de dia in-game, então
  avançar/dormir também encerra os outbreaks) ou ao ser limpo
- **Ciência**: ao entrar no mundo, os outbreaks ativos são listados (coordenadas
  + minutos restantes), e `/dynamicspawns outbreak info` mostra o progresso e o
  tempo restante de cada um
- **Persistente**: os outbreaks ativos e o agendamento são salvos por mundo
  (`dynamicspawns_outbreaks.json`), então continuam de onde pararam ao fechar e
  reabrir o mundo, em vez de reiniciar. O tempo de vida usa o tempo de mundo
  (`gameTime`), então corre enquanto o servidor está rodando — independente de o
  chunk do outbreak estar carregado — e pausa com o mundo fechado. Reabrir o
  mundo não gera mais um outbreak espúrio

## Comandos (permissão nível 2)

| Comando | Efeito |
|---|---|
| `/dynamicspawns reload` | Recarrega a config |
| `/dynamicspawns horde <espécie>` | Força uma horda na sua posição (usar espécie base que evolui, ex: `bidoof`) |
| `/dynamicspawns outbreak start [espécie]` | Inicia um outbreak (aleatório ou da espécie dada, perto de você) |
| `/dynamicspawns outbreak stop` | Encerra todos os outbreaks ativos |
| `/dynamicspawns outbreak info` | Lista os outbreaks ativos com status |

## Config

Gerada em `config/dynamicspawns.json` na primeira execução. Todos os números
(chances, tamanhos, intervalos, marcos de shiny) são ajustáveis lá;
`/dynamicspawns reload` aplica sem reiniciar.

### 4. Spawns voadores
Pássaros e outras espécies que voam (`canFly`) nascem **no ar, já voando**, em
vez de paradas no chão — uma Altaria aparece planando sobre o terreno em vez de
sentada num campo. A altura acima da superfície é configurável
(`minHeight`/`maxHeight`, padrão 12-40; aumente para ~120 para nascerem no nível
das nuvens), e `onlyPureFliers` limita às espécies que não andam. Usa a API
`canFly`/`setFlying` do Cobblemon — sem mixins.

Além disso, um **spawner de céu** recorrente (`skyFliers`) spawna periodicamente
alguns pássaros no alto, perto de cada jogador, sob céu aberto, de um pool
filtrado — lendários/míticos/ultra beasts e espécies muito raras (catch rate
baixo) ficam de fora. Por padrão são **pure fliers** estritos (espécies que não
andam, ex: Zubat, Drifloon); use `onlyPureFliers: false` para incluir também os
pássaros que andam, como Altaria e Pidgeot. Intervalo, quantidade, altura,
distância e níveis são configuráveis.

### 5. Spawns aleatórios
**6% dos spawns naturais** (configurável) são trocados por outra espécie,
ignorando as regras de bioma — qualquer pokémon pode aparecer em qualquer
lugar, mas **seguindo a raridade do próprio Cobblemon**:
- A troca sorteia por **bucket de raridade** (lido em runtime da spawn pool do
  mundo, respeitando datapacks/modpacks): primeiro o bucket, pelos pesos do mod
  (`bucketWeights`, padrão **common 60 / uncommon 25 / rare 10 / ultra-rare 5**
  — curva mais suave que a do Cobblemon, 94.3/5/0.5/0.2, para os raros
  aparecerem mais), depois uma espécie uniforme dentro do bucket. O bucket da
  espécie é o mais comum em que ela aparece (ex: Charizard = ultra-rare)
- Espécies **sem spawn natural** (sem bucket) não podem ser sorteadas
- Veja [docs/selecao-spawn-aleatorio.pdf](docs/selecao-spawn-aleatorio.pdf) com
  a listagem completa das espécies e a probabilidade de cada uma
- **Lendários, míticos, ultra beasts, paradox e fósseis ficam fora do sorteio**
  (`excludedLabels`) — e spawns naturais dessas espécies nunca são trocados
  (protege lendários selvagens de modpacks)
- **Em água/biomas aquáticos** (oceano, rio, água doce), o sorteio considera
  apenas espécies que vivem na água (respiram debaixo d'água)
- **Níveis realistas** (`realisticLevels`): o nível escala com o base stat total
  da espécie, com ±`levelVariance` (5) de variação — um Magikarp aparece por volta
  do nível 1-10 e um pseudo-lendário fica em ~50-55 (`rareLevelCap`). Use
  `realisticLevels: false` para manter o nível do spawn original
- `excludedNamespaces` permite excluir espécies de addons específicos (ex: `lumymon`)

### Regras ambientais
Tanto os spawns aleatórios quanto os outbreaks respeitam checagens de adequação
ambiental (seção `environment` da config), para os pokémon não aparecerem onde
não deveriam:
- **Dimensões desligadas** (`disabledDimensions`, padrão `minecraft:the_end`):
  sem aleatoriedade dinâmica lá — o The End mantém seus pokémon originais
- **Tipos proibidos por dimensão** (`dimensionBannedTypes`, padrão: sem grama/
  água/gelo/inseto no Nether): espécies com um tipo proibido são filtradas
  naquela dimensão
- **Adequação de terreno** (`enforceTerrain`): espécies aquáticas que evitam
  terra só nascem na água (chega de Relicanth em cima de árvore), e espécies que
  evitam água não são colocadas submersas. Hordas pulam espécies aquáticas, já
  que seus membros são posicionados em chão sólido

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

- Mensagens de chat são traduzidas no cliente (inglês e pt-BR inclusos);
  jogadores que entrarem sem o mod no cliente verão as chaves de tradução cruas
- Outbreaks sempre escolhem posição na superfície (heightmap)

## Build

```
./gradlew build      # jar em build/libs/
./gradlew runClient  # cliente de teste
```
