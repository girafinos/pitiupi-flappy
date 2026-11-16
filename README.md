# Flappy Bird P2P (`pitiupi.flappy`)

Plugin multiplayer em tempo real no estilo **Ghost Race** (*Packet Runner P2P*) desenvolvido como atividade prática da disciplina de **Redes de Computadores**, utilizando a arquitetura de plugins da plataforma **[Pitiupi](https://github.com/GabrielFSSantos/pitiupi)**.

> **Projeto base:** Este plugin depende do núcleo **[GabrielFSSantos/pitiupi](https://github.com/GabrielFSSantos/pitiupi)** e se integra a ele via `SPI` (`META-INF/services/pitiupi.models.Plugin`), sem modificações no código-fonte base do Pitiupi.

---

## Sumário

- [Visão Geral e Conceitos de Redes](#visão-geral-e-conceitos-de-redes)
- [Estrutura do Plugin](#estrutura-do-plugin)
- [Protocolo de Aplicação](#protocolo-de-aplicação)
- [Como Instalar e Compilar no Pitiupi](#como-instalar-e-compilar-no-pitiupi)
- [Execução em Rede Local (Linux e Windows)](#execução-em-rede-local-linux-e-windows)

---

## Visão Geral e Conceitos de Redes

1. **Comunicação 100% UDP (`PluginTransportCatalog.PluginKind.PING`):**
   - **Aba `Todos ↑` (Sala Geral):** envia datagramas em **UDP Multicast (`224.0.0.3`)** para todos os peers inscritos no grupo.
   - **Aba `+ Nova` (Peer por IPv4):** envia datagramas em **UDP Unicast** direto para o IP do colega, útil em redes Wi-Fi que bloqueiam tráfego multicast.
   - O transporte **TCP** foi intencionalmente desabilitado no plugin para evitar *Head-of-Line Blocking* (travamentos causados por retransmissão de pacotes antigos) em tempo real.

2. **Sincronização Procedural por Semente Compartilhada (`seed`):**
   - Antes da largada, os jogadores compartilham apenas uma semente numérica (`long seed`) na mensagem `SYNC_START`.
   - Cada máquina calcula localmente a abertura exata dos obstáculos usando a função determinística `getPipeGapCenterY(pipeIndex, seed)`, gerando o mesmo circuito com **zero overhead de banda** para o cenário.

3. **Telemetria a 20 Hz vs. Renderização a 60 FPS (*Dead Reckoning*):**
   - Em modo multiplayer, o plugin transmite a cinemática do jogador (`birdY`, `birdVelocityY`, `worldX`, `score`, `alive`) a ~**20 Hz** (a cada `48 ms`) em uma thread separada (`networkExecutor`), sem bloquear a EDT do Swing.
   - Nos intervalos entre pacotes, o receptor extrapola a trajetória dos fantasmas a **60 FPS** utilizando gravidade e velocidade vertical (*Dead Reckoning*). A barra superior permite ligar/desligar a interpolação ao vivo.

4. **Detecção de Perda (`LOSS`) e Pacotes Fora de Ordem (`OOO`):**
   - Cada pacote `FlappyStateMessage` carrega um número de sequência (`seqNum`) em camada de aplicação.
   - Em `GhostState.applyNetworkState`: saltos na sequência (`seqNum > lastSeqNum + 1`) são contabilizados como perda UDP; pacotes atrasados (`seqNum <= lastSeqNum`) são contabilizados como fora de ordem (`OOO`) e descartados.

5. **Sala de Espera Sincronizada (`SYN-READY`) e Voo Local Offline:**
   - Clicar em **Iniciar Corrida Sincronizada (Rede)** abre a tela de pronto exigindo quórum (`total / 2 + 1`, ou seja, todos prontos em dupla ou maioria em salas maiores) antes de o nó líder (menor `instanceId`) sortear a nova `seed` e disparar `SYNC_START` (com reenvio redundante de 80 ms em UDP).
   - O botão **Voo Local (Solo)** mantém `localSoloMode = true` para testes individuais sem envio de pacotes nem renderização de fantasmas.

---

## Estrutura do Plugin

```text
plugins/flappy/
├── build.xml
├── nbproject/
└── src/
    ├── META-INF/services/pitiupi.models.Plugin   # Registro SPI do plugin
    └── pitiupi/flappy/
        ├── plugin/   # Porta de entrada no Pitiupi (FlappyPlugin)
        ├── net/      # Protocolo de aplicação (FlappyAction, FlappyMatchMessage, FlappyStateMessage)
        ├── control/  # Despacho de rede, quórum Ready, LOSS/OOO e Dead Reckoning
        ├── GUI/      # Interface Swing, motor 2D a 60 FPS (VolatileImage), parallax e osciloscópio
        └── assets/   # Sprites em pixel art (.png) feitos no Aseprite
```

---

## Protocolo de Aplicação

| Mensagem | Frequência | Conteúdo / Função |
| --- | --- | --- |
| `FlappyMatchMessage` | Eventos discretos | Controle de sessão via `FlappyAction`: `READY_STATE` (confirmação de pronto), `SYNC_START` (`matchId` + `seed`), `JOIN_ACK` e `GAME_OVER` (`finalScore`). |
| `FlappyStateMessage` | ~20 Hz em voo | Datagrama de telemetria contendo `matchId`, `seed`, `seqNum`, `birdY`, `birdVelocityY`, `worldX`, `score` e `alive`. |

---

## Como Instalar e Compilar no Pitiupi

1. Clone o repositório base do Pitiupi (**Java 17+ / 21** e **Apache Ant** instalados):
   ```bash
   git clone https://github.com/GabrielFSSantos/pitiupi.git
   cd pitiupi
   ```

2. Copie a pasta `plugins/flappy/` deste repositório para `pitiupi/plugins/flappy/` e garanta a entrada correspondente no arquivo `plugins.registry.json` na raiz do Pitiupi:
   ```json
   {
     "id": "flappy",
     "category": "Jogos",
     "dir": "plugins/flappy",
     "jar": "plugins/flappy/dist/pitiupi.flappy.jar",
     "runtimeName": "flappy.jar",
     "mainClass": "pitiupi.flappy.plugin.FlappyPlugin"
   }
   ```

3. Compile o plugin pela raiz do `pitiupi`:
   ```bash
   ./scripts/pitiupi.sh build --plugins flappy
   ```
   O pacote final será gerado em `plugins/flappy/dist/pitiupi.flappy.jar` e linkado em `.pitiupi/plugins/flappy.jar`.

---

## Execução em Rede Local (Linux e Windows)

### No Linux
```bash
./scripts/pitiupi.sh run --plugins flappy
```

### No Windows
Copie o `pitiupi.flappy.jar` compilado para `.pitiupi/plugins/` e inicie o Pitiupi forçando a pilha IPv4 para o socket Multicast funcionar corretamente no Windows:
```powershell
java "-Djava.net.preferIPv4Stack=true" -jar pitiupi.jar
```

> **Dica de rede local:** Certifique-se de que ambos os PCs estão na mesma rede física/Wi-Fi, usando a mesma porta configurada no login do Pitiupi (ex.: `5000`), com a interface correta selecionada em **Configurações > Saída de rede** e o Firewall do Windows permitindo Java em rede privada. Se o roteador bloquear Multicast na aba `Todos ↑`, clique em `+ Nova` em ambos os PCs e informe o IPv4 local do colega para jogar por **UDP Unicast**.
