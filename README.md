# Plugin Flappy Bird P2P (`pitiupi.flappy`)

Jogo multiplayer em tempo real no modelo **Ghost Race (Corrida de Fantasmas)** construído sobre o núcleo P2P do Pitiupi para práticas de **Redes de Computadores**.

---

## 1. Conceitos de Redes Demonstrados

1. **Sincronização Procedural por Semente Compartilhada (*Deterministic Seed*):**
   - Em vez de trafegar coordenadas de todos os canos pela rede a cada quadro, o nó que inicia a partida transmite apenas uma semente inteira (`seed`, 64 bits) na mensagem `FlappyMatchMessage(SYNC_START)`.
   - A altura da abertura de cada cano `i` é calculada por uma função pura determinística `getPipeGapCenterY(i, seed)`. Todos os nós na sala geram exatamente o mesmo circuito de obstáculos com **zero overhead de banda** para o cenário.

2. **Telemetria em Tempo Real (~20 Hz) vs Renderização a 60 FPS (*Dead Reckoning*):**
   - Durante o voo, cada jogador transmite pacotes `FlappyStateMessage` a ~20 Hz (a cada ~48 ms) contendo `(birdY, birdVelocityY, worldX, seqNum)`.
   - Entre dois pacotes UDP consecutivos, o receptor extrapola localmente o movimento do fantasma a 60 FPS aplicando a velocidade vertical e gravidade (*Dead Reckoning*).
   - O painel inclui a caixa **"Interpolação UDP (Dead Reckoning)"**, permitindo ligar/desligar a extrapolação ao vivo durante a apresentação para demonstrar visualmente a diferença entre pacotes brutos a 20 Hz e movimento interpolado a 60 FPS.

3. **Controle de Sequência, Perda de Pacotes e Desordenação (`seqNum`):**
   - Cada datagrama `FlappyStateMessage` carrega um número de sequência monotônico (`seqNum`).
   - Se chegar um pacote atrasado (`seqNum <= lastSeqNum`), ele é contabilizado como **Fora de Ordem (OOO)** e descartado para o pássaro fantasma não "voltar no tempo".
   - Se houver salto (`seqNum > lastSeqNum + 1`), a diferença é contabilizada como **Perda UDP** no painel de telemetria.

---

## 2. Especificação do Protocolo de Aplicação

| Classe (`Message`) | Frequência | Campos Principais | Papel no Protocolo |
| --- | --- | --- | --- |
| `FlappyMatchMessage` | Eventos discretos | `action` (`SYNC_START`, `JOIN_ACK`, `GAME_OVER`), `matchId`, `seed`, `senderInstanceId`, `senderName`, `senderIp`, `finalScore` | Anuncia nova corrida sincronizada com semente compartilhada (contagem regressiva de 3s), confirma presença (`JOIN_ACK`) e informa pontuação final ao colidir (`GAME_OVER`). |
| `FlappyStateMessage` | ~20 Hz em voo | `matchId`, `seed`, `senderInstanceId`, `seqNum`, `birdY`, `birdVelocityY`, `worldX`, `score`, `alive` | Atualiza posição cinemática do pássaro fantasma e alimenta as métricas de pacotes (TX, RX, Perdas e OOO). |

---

## 3. Como Compilar e Executar

Na raiz do repositório `pitiupi`:

```bash
./scripts/pitiupi.sh run --all
# Ou apenas com o plugin flappy:
./scripts/pitiupi.sh run --plugins flappy
```

