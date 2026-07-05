# Ambi IoT — Monitor de Som e Ambiente com ESP32

> **Projeto Final — Curso "IoT e Android Embarcado" · Unicamp**

Firmware para o nó sensor baseado em ESP32 que forma a espinha dorsal IoT do sistema de monitoramento de conforto **Ambi**. O dispositivo mede ruído acústico (NPS / Leq), temperatura e umidade em um ambiente de restaurante e, simultaneamente, simula cinco localizações virtuais de sensores — cada uma com um perfil realista e diferenciado — publicando todos os dados na **plataforma Konker IoT** via MQTT.

---

## Sumário

1. [Visão Geral](#visão-geral)
2. [Hardware e Mapa de Pinos](#hardware-e-mapa-de-pinos)
3. [Dependências de Bibliotecas](#dependências-de-bibliotecas)
4. [Estrutura do Projeto](#estrutura-do-projeto)
5. [Funcionalidades Principais](#funcionalidades-principais)
6. [Algoritmo de Medição de NPS (IEC 61672 Leq)](#algoritmo-de-medição-de-nps-iec-61672-leq)
7. [Motor de Simulação Gaussiana](#motor-de-simulação-gaussiana)
8. [Perfis dos Dispositivos Simulados](#perfis-dos-dispositivos-simulados)
9. [Máquina de Estados do Display OLED](#máquina-de-estados-do-display-oled)
10. [Publicação MQTT](#publicação-mqtt)
11. [Registro em Cartão SD](#registro-em-cartão-sd)
12. [Wi-Fi e Portal Cativo de Configuração](#wi-fi-e-portal-cativo-de-configuração)
13. [RTC e Sincronização NTP](#rtc-e-sincronização-ntp)
14. [Formato do Payload JSON](#formato-do-payload-json)
15. [Primeiros Passos](#primeiros-passos)
16. [Referência de Configuração](#referência-de-configuração)

---

## Visão Geral

| Item | Detalhe |
|---|---|
| MCU | ESP32 (dual-core, 240 MHz) |
| Plataforma | Arduino / ESP-IDF via Arduino Core |
| Backend IoT | Plataforma Konker (broker MQTT: `ucmp.soneca.dev:1883`) |
| Sensores | Microfone eletret + pré-amp MCP602 (ADC), DHT22 (Temp/Umid) |
| Display | SSD1306 OLED 128×64 (I²C) |
| RTC | DS3232 (I²C) com sincronização NTP única |
| Armazenamento | MicroSD (SPI) — log de dados CSV |
| Conectividade | Wi-Fi 802.11 b/g/n; portal cativo WiFiManager para provisionamento |
| Intervalo de publicação | 15 minutos (configurável via `PUBLISH_INTERVAL`) |
| Localizações virtuais | 5 (Área de Almoço, Entrada, Próximo à Cozinha, Próximo aos Banheiros, Área Externa) |

---

## Hardware e Mapa de Pinos

**Componentes sem pino específico:**
- **MCU:** ESP32 DevKit (38 pinos, dual-core 240 MHz)
- **Display OLED:** SSD1306, 128×64, endereço I²C `0x3C`
- **RTC:** DS3232 (compatível com DS3231), I²C — compartilha barramento com o OLED

| GPIO | Função | Componente | Notas Elétricas |
|---|---|---|---|
| 34 | ADC do microfone | Cápsula eletret + pré-amplificador op-amp MCP602 | Pino somente entrada; pull-up não necessário |
| 4 | Dados do DHT22 | Sensor de temperatura/umidade DHT22 | Pull-up de 10 kΩ para 3,3 V |
| 21 | I²C SDA | Compartilhado: OLED SSD1306 + RTC DS3232 | — |
| 22 | I²C SCL | Compartilhado: OLED SSD1306 + RTC DS3232 | — |
| 5 | CS do SD (SPI) | Módulo MicroSD | Chip-select |
| 2 | LED de status | LED integrado | Ativo em nível ALTO; pisca durante publicação |
| 25 | Botão Wi-Fi | Botão tátil | Ativo em nível BAIXO, debounce 50 ms — aciona o portal cativo |
| 26 | Botão Display | Botão tátil | Ativo em nível BAIXO, debounce 50 ms — cicla o estado do OLED |

---

## Dependências de Bibliotecas

Instale todas as bibliotecas pelo Gerenciador de Bibliotecas do Arduino ou PlatformIO:

| Biblioteca | Versão | Finalidade |
|---|---|---|
| Arduino ESP32 Core | ≥ 2.0 | Abstração de hardware ESP32, `esp_random()` |
| DHT sensor library | ≥ 1.4 | Driver DHT22 temperatura/umidade |
| Adafruit SSD1306 | ≥ 2.5 | Driver do display OLED |
| Adafruit GFX | ≥ 1.11 | Primitivas gráficas para OLED |
| PubSubClient | ≥ 2.8 | Cliente MQTT |
| WiFiManager | ≥ 2.0 | Portal cativo para provisionamento Wi-Fi |
| RTClib | ≥ 2.1 | Interface RTC DS3232/DS3231 |
| SD (integrada) | — | E/S de arquivo no cartão MicroSD |
| NTPClient | ≥ 3.2 | Busca de tempo NTP para sincronização única do RTC |
| Wire (integrada) | — | Barramento I²C para OLED + RTC |

---

## Estrutura do Projeto

```
main/
├── main.ino                    # Ponto de entrada: setup(), loop(), variáveis globais do anel
├── Secrets_Example.h           # ⚠ Renomeie para Secrets.h — credenciais + perfis de simulação
├── processAudioSPL.ino         # Medição de NPS (IEC 61672 Leq) + simulador gaussiano
├── sendDataViaMQTT.ino         # Construtor de payload JSON + publicador MQTT por dispositivo
├── displayOledData.ino         # Máquina de 5 estados OLED (3 numéricos + 2 gráficos)
├── connectToWiFi.ino           # Configuração do portal cativo WiFiManager
├── getSetReadRTCValues.ino     # Driver RTC DS3232 + sincronização NTP única
├── logSDCard.ino               # Logger CSV com buffer (tamanho = 15 registros)
├── mqttReconnect.ino           # Conexão MQTT por dispositivo com lógica de 3 tentativas
├── refreshKeyboardReadings.ino # Handlers de botões com debounce (50 ms)
├── startOLEDDisplay.ino        # Inicialização I²C + SSD1306
└── startSDCard.ino             # Montagem do SD, verificação de tipo, criação do cabeçalho
```

Todas as constantes ajustáveis (`PUBLISH_INTERVAL`, `CHART_POINTS`, atribuições de pinos, etc.) e seus valores padrão estão listados na seção [Referência de Configuração](#referência-de-configuração). Quatro buffers circulares (`splHistory`, `tempHistory`, `humHistory`, `rssiHistory`, profundidade = `CHART_POINTS`) são atualizados a cada ciclo de publicação e lidos pelas telas de sparkline do OLED — veja [Funcionalidades Principais](#funcionalidades-principais) para o fluxo de atualização.

---

## Funcionalidades Principais

### Sequência do Loop Principal

Cada iteração de `loop()` executa na seguinte ordem:

1. `refreshKeyboardReadings()` — verifica botões; atualiza estado do OLED ou enfileira o portal cativo
2. Verificação de sincronização NTP — chama `getNTPClientDateTimeAndSetDS3231RTC()` uma vez após a primeira conexão Wi-Fi
3. `processAudioSPL()` — janela ADC de 50 ms; acumula soma de energia para cálculo do Leq
4. `readTemperatureAndHumidityFromDHT22()` — lê DHT22; atualiza variáveis globais
5. `displayOledData()` — renderiza a tela OLED ativa
6. Verificação de publicação — se `millis() - lastPublishTime >= PUBLISH_INTERVAL`, executa o ciclo completo de publicação:
   - Chama `generateSimulatedReadings()` para cada um dos 5 dispositivos virtuais
   - Chama `sendDataViaMQTT()` por dispositivo (rotação de credenciais, reconexão, publicação, desconexão)
   - Acrescenta uma linha ao log SD via `logSDCard()`
   - Atualiza os buffers circulares e reseta os acumuladores de Leq

---

## Algoritmo de Medição de NPS (IEC 61672 Leq)

O firmware implementa um algoritmo de média energética consistente com a norma IEC 61672-1 para nível de pressão sonora contínuo equivalente (Leq).

### Janela de Amostragem

Cada chamada a `processAudioSPL()` abre uma **janela ADC de 50 ms**. Durante essa janela, o firmware lê continuamente o ADC do microfone (GPIO 34) e rastreia a amplitude pico a pico da forma de onda.

### Mapeamento de NPS

A amplitude pico a pico (0–4095 contagens ADC) é mapeada linearmente para uma faixa em decibéis:

```
NPS = NPS_MIN_DB + (picoPico / 4095) × (NPS_MAX_DB − NPS_MIN_DB)
```

onde `NPS_MIN_DB = 30 dB` e `NPS_MAX_DB = 120 dB`, cobrindo a faixa dinâmica audível completa da cápsula eletret.

### Média Energética Leq

Em vez de calcular a média dos valores de NPS diretamente (o que subestimaria eventos sonoros intensos), o firmware acumula **energia acústica linear**:

```
linearEnergySum += 10^(NPS / 10)
sampleCount++
```

No momento da publicação, o nível contínuo equivalente é calculado como:

```
Leq = 10 × log₁₀(linearEnergySum / sampleCount)
```

Isso é matematicamente equivalente à média de energia ponderada no tempo da IEC 61672 ao longo do período de medição.

### Lmax e Lmin

Os valores máximo e mínimo de NPS em execução são rastreados ao longo do mesmo período de acumulação e redefinidos juntamente com os acumuladores de Leq após cada ciclo de publicação.

---

## Motor de Simulação Gaussiana

Como um único ESP32 físico representa todas as cinco zonas do restaurante, o firmware inclui um gerador de ruído gaussiano Box-Muller semeado com o gerador de números aleatórios por hardware do ESP32 (`esp_random()`). Isso produz leituras de sensor estatisticamente realistas e com variação natural para cada dispositivo virtual.

### Transformação Box-Muller

```cpp
static float gaussRand() {
    float u1 = (float)(esp_random() & 0x7FFFFFFF) / 2147483647.0f + 1e-9f;
    float u2 = (float)(esp_random() & 0x7FFFFFFF) / 2147483647.0f;
    return sqrtf(-2.0f * logf(u1)) * cosf(2.0f * M_PI * u2);
}
```

`u1` e `u2` são valores uniformes independentes [0,1] derivados de entropia de hardware de 32 bits. A transformação produz uma variável normal padrão (µ=0, σ=1), que é então escalada por um sigma por parâmetro e deslocada pelo bias do dispositivo.

### Baselines e Parâmetros de Ruído da Simulação

| Parâmetro | Baseline | Gaussian σ |
|---|---|---|
| Leq NPS | 55 dB (LEQ_FLOOR) | 2,5 dB |
| Lmax NPS | Leq + lmaxBias | 4,0 dB |
| Lmin NPS | Leq − 3 dB | 1,5 dB |
| Temperatura | 22 °C | 0,4 °C |
| Umidade Relativa | 45 % | 1,5 % |
| Wi-Fi RSSI | −45 dBm (RSSI_REF) | 3,0 dBm |

O `SimProfile` de cada dispositivo virtual adiciona um bias determinístico sobre o baseline antes de o ruído gaussiano ser aplicado, fazendo com que as cinco localizações pareçam distintas enquanto ainda mostram variação natural ciclo a ciclo.

---

## Perfis dos Dispositivos Simulados

Definidos em `Secrets.h` (renomeado de `Secrets_Example.h`):

| Localização | Bias Leq | Bias Lmax | Bias Temp | Bias Umid | Bias RSSI | Justificativa |
|---|---|---|---|---|---|---|
| Área de Almoço | +12 dB | +8 dB | +1,5 °C | +3 % | 0 dBm | Mesas movimentadas; melhor Wi-Fi (mais próximo do roteador) |
| Entrada | +8 dB | +14 dB | −1 °C | −5 % | −15 dBm | Batidas de porta elevam Lmax; mais frio, sinal cai quando porta de vidro abre |
| Próximo à Cozinha | +22 dB | +10 dB | +8 °C | +18 % | −28 dBm | Mais barulhenta, mais quente, mais úmida; eletrodomésticos de aço atenuam Wi-Fi |
| Próximo aos Banheiros | +3 dB | +5 dB | −0,5 °C | +6 % | −12 dBm | Zona mais silenciosa; levemente úmida |
| Área Externa | +15 dB | +3 dB | −0,5 °C | +6 % | −15 dBm | Ruído de rua externo; clima ameno; cobertura parcial de Wi-Fi |

---

## Máquina de Estados do Display OLED

O display SSD1306 128×64 cicla por **5 telas**, avançadas ao pressionar o botão Display (GPIO 26):

| Estado | Tela | Conteúdo |
|---|---|---|
| 0 | **Numérico: NPS** | Leq (grande), Lmax, Lmin, ícone RSSI Wi-Fi |
| 1 | **Numérico: Temp/Umid** | Temperatura (°C), Umidade (%), ícone RSSI Wi-Fi |
| 2 | **Numérico: Rede** | Endereço IP, barra RSSI, status MQTT |
| 3 | **Gráfico: NPS** | Sparkline das últimas 20 leituras Leq (buffer circular), eixo Y com auto-escala |
| 4 | **Gráfico: Temp+Umid** | Sparkline duplo — temperatura (sólido) e umidade (tracejado), com auto-escala |

### Infraestrutura de Gráficos

- **`autoRange(values, n, snapStep, minSpan, &outMin, &outMax)`** — arredonda min/max para o incremento `snapStep` mais próximo e impõe uma amplitude mínima em Y (`minSpan`) para evitar gráficos degenerados de linha plana.
- **`drawSparkline(values, n, yMin, yMax, x0, y0, w, h, dashed)`** — desenha segmentos de linha conectados pelo buffer circular; modo tracejado opcional para a sobreposição de umidade.
- **`drawWifiRSSI(rssi, x, y)`** — renderiza um ícone Wi-Fi de 4 barras com limiares em −55, −67 e −80 dBm. Um valor sentinela de RSSI `1` indica "não conectado" e renderiza todas as barras vazias.

---

## Publicação MQTT

### Rotação de Credenciais por Dispositivo

Cada um dos 5 dispositivos virtuais possui seu próprio usuário, senha e tópico de publicação MQTT no Konker (definidos em `Secrets.h`). O ciclo de publicação itera sobre todos os dispositivos sequencialmente:

1. Chama `mqttReconnect(device)` — estabelece uma nova conexão MQTT usando as credenciais daquele dispositivo.  
   - Formato do Client ID: `"ESP32_" + device.mqttUser`  
   - Até **3 tentativas** com intervalos de 2 segundos entre elas.
2. Chama `client.publish(device.mqttTopic, payload)` para enviar o payload JSON.
3. Chama `client.disconnect()` para liberar a conexão antes de passar ao próximo dispositivo.

Essa abordagem de rotação de credenciais permite que um único ESP32 se apresente como cinco dispositivos Konker independentes sem manter conexões simultâneas. O host e a porta do broker estão listados na tabela de [Visão Geral](#visão-geral) e configurados em `Secrets.h`.

---

## Registro em Cartão SD

`logSDCard.ino` registra as **leituras físicas dos sensores** (do microfone real e do DHT22) em `/data.txt` no cartão MicroSD. As gravações são armazenadas em um `std::vector<String>` (tamanho do buffer = `BUFFER_SIZE = 15` registros) e descarregadas no disco em uma única chamada `appendFile()` quando o buffer atinge a capacidade.

### Formato CSV

```
ESP DATA IoT
CUSTOMER: PUT_HERE_THE_CUSTOMER_NAME
SECTOR:   PUT_HERE_THE_SECTOR_NAME
LOCAL:    PUT_HERE_THE_LOCAL_NAME

timestamp;customer_ID;iot_device_serial_number;temperature;humidity;avg_spl;max_spl;wifi_status
```

O número de série do dispositivo é definido por `#define ESP32_DEVICE_ID "001Corp20250122"` em `logSDCard.ino`.

Se o cartão SD estiver ausente ou falhar ao montar, o display exibe uma mensagem de erro por 2 segundos e o firmware continua sem registrar.

---

## Wi-Fi e Portal Cativo de Configuração

As credenciais Wi-Fi são provisionadas usando o **WiFiManager**. Na primeira inicialização (ou sempre que as credenciais armazenadas falharem), pressionar o botão Wi-Fi (GPIO 25) inicia um ponto de acesso:

| Item | Valor |
|---|---|
| Nome da rede (AP) | `Ambi IoT Device` |
| IP do AP | `192.168.4.1` |

Conecte qualquer celular ou notebook a essa rede e um portal cativo abrirá automaticamente. Digite o SSID e a senha do Wi-Fi; o ESP32 salva as credenciais e reinicia.

O fuso horário NTP é configurado para **GMT−4 + 1 h DST** usando `pool.ntp.org`, correspondendo ao fuso horário `America/Sao_Paulo` usado pelo aplicativo Android Ambi.

---

## RTC e Sincronização NTP

O RTC DS3232 fornece timestamps precisos entre ciclos de energia. Fluxo de sincronização de tempo:

1. Após a primeira conexão Wi-Fi bem-sucedida, `getNTPClientDateTimeAndSetDS3231RTC()` busca a hora atual de `pool.ntp.org` e a grava no DS3232 via I²C.
2. Uma flag (`ntpSynced`) impede sincronizações repetidas — inicializações subsequentes reutilizam a hora do RTC diretamente.
3. Todos os timestamps usam o formato `yyyy.MM.ddTHH:mm:ss`, que a plataforma Konker IoT e o aplicativo Android Ambi analisam sem conversão.

---

## Formato do Payload JSON

Cada publicação MQTT envia um único objeto JSON:

```json
{
  "reading_time":    "2025.01.22T14:30:00",
  "customer_ID":     "Customer_ID",
  "device_location": "Área de Almoço",
  "temperature":     23.50,
  "humidity":        46.20,
  "leq_spl":         67.34,
  "lmax_spl":        75.12,
  "lmin_spl":        61.05,
  "wifi_rssi":       -45
}
```

| Campo | Tipo | Unidade | Origem |
|---|---|---|---|
| `reading_time` | String | `yyyy.MM.ddTHH:mm:ss` | RTC DS3232 |
| `customer_ID` | String | — | `Secrets.h` |
| `device_location` | String | — | `IoTDevice.location` |
| `temperature` | Float (2 dp) | °C | DHT22 / SimProfile |
| `humidity` | Float (2 dp) | % | DHT22 / SimProfile |
| `leq_spl` | Float (2 dp) | dB | IEC 61672 Leq / SimProfile |
| `lmax_spl` | Float (2 dp) | dB | Máximo em execução / SimProfile |
| `lmin_spl` | Float (2 dp) | dB | Mínimo em execução / SimProfile |
| `wifi_rssi` | Inteiro | dBm | `WiFi.RSSI()` / SimProfile |

---

## Primeiros Passos

### Pré-requisitos

- Arduino IDE 2.x (ou PlatformIO)
- Pacote de placa ESP32 instalado (`espressif/arduino-esp32 ≥ 2.0`)
- Todas as bibliotecas listadas em [Dependências de Bibliotecas](#dependências-de-bibliotecas) instaladas
- Cartão MicroSD formatado como FAT32
- Conta ativa no Konker com 5 dispositivos registrados (um por localização simulada)

### Passos de Configuração

1. **Clone / copie** a pasta `main/` para o seu sketchbook do Arduino.

2. **Renomeie** `Secrets_Example.h` para `Secrets.h`.

3. **Edite `Secrets.h`:**
   - Defina `customer_ID` com o nome da sua organização.
   - Substitua os valores de espaço reservado `mqttUser`, `mqttPass` e `mqttTopic` para cada um dos 5 dispositivos pelas suas credenciais Konker reais.
   - Ajuste os biases do `SimProfile` se o layout do seu restaurante for diferente.

4. **Conecte o hardware** conforme o [Mapa de Pinos](#mapa-de-pinos).

5. **Insira** um cartão MicroSD formatado em FAT32.

6. **Faça o upload** do sketch para o seu ESP32.

7. **Provisione o Wi-Fi:** na primeira inicialização, pressione o botão Wi-Fi (GPIO 25), conecte-se à rede `Ambi IoT Device` e insira suas credenciais no portal cativo.

8. **Verifique:** observe o display OLED ciclar pelas telas. Após 15 minutos, verifique os dashboards do Konker para dados chegando de todos os 5 dispositivos.

---

## Referência de Configuração

Todas as constantes ajustáveis estão em `main.ino` e `Secrets.h`:

| Constante | Arquivo | Padrão | Descrição |
|---|---|---|---|
| `PUBLISH_INTERVAL` | `main.ino` | `900000` ms | Tempo entre ciclos de publicação (15 min) |
| `CHART_POINTS` | `main.ino` | `20` | Profundidade do buffer circular para sparklines OLED |
| `BUFFER_SIZE` | `logSDCard.ino` | `15` | Profundidade do buffer de escrita SD (registros) |
| `MQTT_SERVER` | `Secrets.h` | `ucmp.soneca.dev` | Hostname do broker MQTT Konker |
| `MQTT_PORT` | `Secrets.h` | `1883` | Porta do broker MQTT |
| `customer_ID` | `Secrets.h` | `"Customer_ID"` | Rótulo escrito em cada payload |
| `ESP32_DEVICE_ID` | `logSDCard.ino` | `"001Corp20250122"` | Número de série do dispositivo no log SD |
| `LEQ_FLOOR` | `processAudioSPL.ino` | `55,0 dB` | Baseline de silêncio da simulação |
| `RSSI_REF` | `processAudioSPL.ino` | `−45 dBm` | Nível de referência Wi-Fi da simulação |
| `SPL_MIN_DB` | `processAudioSPL.ino` | `30 dB` | Limite inferior do mapeamento ADC |
| `SPL_MAX_DB` | `processAudioSPL.ino` | `120 dB` | Limite superior do mapeamento ADC |

---

*Desenvolvido como projeto final do curso "IoT e Android Embarcado" da Unicamp.*
