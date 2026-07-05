# Ambi — Monitor IoT ComfortPlaces

> **Projeto Final — Curso "IoT e Android Embarcado" · Unicamp**

O Ambi é um aplicativo Android que monitora parâmetros de conforto acústico e ambiental em tempo real para um ambiente de restaurante. O app se conecta à **plataforma IoT Konker**, busca leituras de sensores via API REST e as apresenta em dashboards animados e multilíngues, além de uma planta baixa interativa.

---

## Sumário

1. [Visão Geral](#visão-geral)
2. [Funcionalidades](#funcionalidades)
3. [Arquitetura](#arquitetura)
4. [Stack Tecnológica](#stack-tecnológica)
5. [Estrutura do Projeto](#estrutura-do-projeto)
6. [Integração IoT](#integração-iot)
7. [Telas](#telas)
8. [Como Executar](#como-executar)
9. [Configuração](#configuração)
10. [Payload do Sensor](#payload-do-sensor)

---

## Visão Geral

| Item | Detalhe |
|---|---|
| Plataforma | Android (API 26+) |
| Linguagem | Kotlin |
| Toolkit de UI | Jetpack Compose + Material 3 |
| Backend IoT | Plataforma Konker (`api-ucmp.soneca.dev`) |
| Autenticação | OAuth 2.0 — Client Credentials (Bearer token) |
| Atualização automática | A cada 30 segundos |
| Janela de dados | Últimos 7 dias |
| Permissões | Somente `INTERNET` |

---

## Funcionalidades

### Suporte a Múltiplos Idiomas
O app suporta três idiomas, alternáveis a qualquer momento tanto na tela de login quanto no cabeçalho principal:

- 🇺🇸 English
- 🇧🇷 Português BR
- 🇪🇸 Español

A troca de idioma é instantânea e totalmente reativa — cada rótulo, título de medidor, nome de zona e cabeçalho do mapa de calor se atualiza sem reiniciar o app. O sistema é construído sobre uma `AppStrings` data class injetada via `CompositionLocal` na raiz da hierarquia de composição.

### Dashboard
O dashboard agrega os últimos 7 dias de dados dos sensores em quatro cards:

**Mapa de Ruído (Heatmap)** — Matriz 7 dias × 3 períodos (Manhã / Tarde / Noite) exibindo o Leq médio por localização. A grade é filtrável por zona (Todos / Almoço / Entrada / Cozinha / Banheiros / Externo) e codificada por cores do verde (silencioso) ao âmbar (moderado) ao vermelho (ruidoso). Leituras entre 00h00 e 05h59 são excluídas da agregação.

**Gráfico de Nível Sonoro** — Gráfico de linha com as últimas 10 leituras de Leq, gerado pela biblioteca Vico.

**Gráfico de Temperatura e Umidade** — Gráfico de linha dupla com as últimas 10 leituras de temperatura (°C) e umidade (%).

**Card de RSSI Wi-Fi** — Medidor de intensidade de sinal com quatro faixas de qualidade: Excelente / Bom / Regular / Fraco.

### Planta Baixa (Mapa Interativo)
Uma planta baixa de restaurante no estilo Sims, vista de cima, desenhada inteiramente com `Canvas` do Jetpack Compose. Visualiza cinco zonas monitoradas:

| Chave | Nome exibido | Posição na grade |
|---|---|---|
| Close to Kitchen | Cozinha | Superior esquerdo |
| Close to Restrooms | Banheiros | Superior direito |
| Lunch Area | Área de Almoço | Centro |
| Entrance | Entrada | Centro inferior |
| External Area | Pátio Externo | Base |

Cada zona possui:
- **Overlay de conforto** — tinta semitransparente (verde / âmbar / vermelho) baseada no Leq ao vivo
- **Destaque de seleção** — borda animada pulsante; modo strobe (ciclo de 220 ms) quando Leq > 80 dB
- **Ponto de sensor** — ponto colorido com badge de leitura exibindo o valor atual em dB
- **Rótulo da sala** — localizado, desenhado com `NativePaint` para texto nítido em sub-pixel

#### Personagem "Inspetor de Conforto"
Um sprite de pixel-art inspirado no estilo Pokémon Yellow, intitulado **"Inspetor de Conforto"** (ou "Comfort Inspector" / "Inspector de Confort", dependendo do idioma), habita o mapa. Quando o usuário toca no botão de uma zona, o personagem caminha suavemente da sua posição atual até a zona de destino usando corrotinas `Animatable` paralelas para os eixos X e Y (900 ms, interpolação linear), com ciclo de caminhada de 4 frames (120 ms por frame). Ao chegar, um balão de pensamento aparece com fade-in exibindo a leitura de dB da zona, depois desaparece após 2,8 segundos.

### Autenticação
A tela de login conecta ao endpoint OAuth 2.0 de token do Konker. As credenciais são sempre verificadas no servidor durante o login (sem bypass). Um token válido é armazenado em cache no `SharedPreferences` com margem de segurança de 5 minutos antes do vencimento; chamadas de API subsequentes reutilizam o token em cache e só buscam um novo quando necessário.

---

## Arquitetura

O app segue o padrão **MVVM** com **Fluxo de Dados Unidirecional** e injeção de dependência via **Hilt**.

```
┌──────────────────────────────────────────────────────┐
│                    Camada de UI                      │
│  LoginScreen · DashboardScreen · FloorPlanScreen     │
│  (Jetpack Compose — sem estado, guiada pelo VM)      │
└──────────────────┬───────────────────────────────────┘
                   │ observa estado
┌──────────────────▼───────────────────────────────────┐
│              Camada ViewModel                        │
│  LoginViewModel · DashboardViewModel                 │
│  (Hilt @HiltViewModel, corrotinas viewModelScope)    │
└──────────────────┬───────────────────────────────────┘
                   │ chama funções suspend
┌──────────────────▼───────────────────────────────────┐
│             Camada Repository                        │
│  KonkerRepository                                    │
│  (orquestra token de auth + chamadas de API)         │
└──────────┬────────────────────────┬──────────────────┘
           │                        │
┌──────────▼──────────┐  ┌─────────▼──────────────────┐
│    KonkerApi        │  │      TokenManager           │
│  (Retrofit/OkHttp)  │  │  (OAuth2 + cache de token   │
│                     │  │   em SharedPreferences)     │
└─────────────────────┘  └────────────────────────────┘
```

**Decisões de design principais:**

- O `DashboardViewModel` recebe as credenciais via `SavedStateHandle` (passado pela pilha de navegação após login bem-sucedido) — sem estado global nem singleton de credenciais.
- O `KonkerRepository` resolve o GUID do dispositivo de forma lazy e o armazena em memória durante a sessão, evitando chamadas repetidas à API de listagem de dispositivos.
- Todo I/O de rede é executado em `Dispatchers.IO` dentro de blocos `withContext`.
- O composable de planta baixa (`CompactPixelMap`) é totalmente autossuficiente: mantém seu próprio estado de animação (`Animatable`, `rememberInfiniteTransition`) e recebe apenas dados somente-leitura do ViewModel.

---

## Stack Tecnológica

| Biblioteca | Versão | Finalidade |
|---|---|---|
| Android Gradle Plugin | 8.13.2 | Toolchain de build |
| Kotlin | 2.1.0 | Linguagem |
| KSP | 2.1.0-1.0.29 | Processamento de anotações |
| Jetpack Compose BOM | 2024.05.00 | Componentes de UI |
| Material 3 | — | Sistema de design |
| Activity Compose | 1.9.0 | Integração com entry point |
| Navigation Compose | 2.7.7 | Navegação interna |
| Lifecycle ViewModel Compose | 2.8.0 | Integração com ViewModel |
| Hilt | 2.51.1 | Injeção de dependência |
| Hilt Navigation Compose | 1.2.0 | `hiltViewModel()` no Compose |
| Retrofit | 2.11.0 | Cliente HTTP (REST) |
| OkHttp | 4.12.0 | Engine HTTP + logging |
| Gson Converter | 2.11.0 | Desserialização JSON |
| Vico | 1.15.0 | Gráficos nativos no Compose |
| Security Crypto | 1.1.0-alpha06 | SharedPreferences criptografado |
| Kotlinx Coroutines Android | 1.8.1 | Assincronismo / concorrência |

---

## Estrutura do Projeto

```
app/src/main/java/com/example/comfortplaces/
│
├── ComfortPlacesApp.kt          # Classe Application do Hilt
├── MainActivity.kt              # Activity única; grafo de navegação + estado de idioma
│
├── data/
│   ├── model/
│   │   ├── SensorReading.kt     # Modelo de dados principal (leqSpl, lmaxSpl, lminSpl,
│   │   │                        #   temperature, humidity, wifiRssi, deviceLocation)
│   │   ├── DeviceEvent.kt       # Evento Konker bruto; fun extensão toSensorReading()
│   │   ├── DevicesResponse.kt   # Wrapper de resposta da lista de dispositivos
│   │   └── TokenResponse.kt     # Resposta do token OAuth
│   ├── remote/
│   │   ├── KonkerApi.kt         # Interface Retrofit (getDevices, getOutgoingEvents)
│   │   ├── NetworkModule.kt     # @Module Hilt — provê Retrofit + OkHttp
│   │   └── TokenManager.kt      # Lógica de busca, cache e renovação do token OAuth2
│   └── repository/
│       └── KonkerRepository.kt  # Agrega auth + busca de dados; mapeia para SensorReading
│
└── ui/
    ├── theme/
    │   └── Theme.kt             # Tema escuro Material 3
    ├── language/
    │   ├── AppLanguage.kt       # Enum AppLanguage, data class AppStrings,
    │   │                        #   objetos de string EN / PT / ES
    │   └── LanguageSelector.kt  # Composable de botões com bandeiras
    ├── login/
    │   ├── LoginScreen.kt       # UI de login com seletor de idioma
    │   └── LoginViewModel.kt    # Hilt ViewModel para o fluxo de login
    ├── dashboard/
    │   ├── DashboardScreen.kt   # Composable raiz; conecta os quatro cards
    │   ├── DashboardViewModel.kt # Consulta a API a cada 30 s; agrupa por localização
    │   ├── HeatmapCard.kt       # Mapa de calor 7 dias × 3 períodos
    │   ├── HeatmapData.kt       # Enum DayPeriod + buildLocationHeatmaps()
    │   ├── SoundCard.kt         # Gráfico de linha do Leq (Vico)
    │   ├── TempHumCard.kt       # Gráfico de temperatura + umidade (Vico)
    │   └── RssiCard.kt          # Medidor de RSSI Wi-Fi
    └── floorplan/
        ├── CompactPixelMap.kt   # Planta Canvas + sprite Inspetor de Conforto
        ├── FloorPlanScreen.kt   # Layout: mapa (42%) + medidores (58%) + botões de zona
        └── SensorAnimatedCard.kt # Cards de medidor individuais com animação
```

---

## Integração IoT

### Plataforma Konker

O app se conecta a uma instalação privada do Konker:

- **URL base:** `https://api-ucmp.soneca.dev`
- **Endpoint de autenticação:** `POST /v1/oauth/token` (Basic auth → Bearer token)
- **Endpoint de eventos:** `GET /v1/{application}/outgoingEvents`
- **Nome do dispositivo:** `comfort_places_app`

### Fluxo de Busca de Dados

1. Na inicialização do `DashboardViewModel`, as credenciais do `SavedStateHandle` são usadas para chamar `KonkerRepository.getSensorReadings()`.
2. O repositório chama `TokenManager.getValidToken()` — retorna o token em cache se não vencer em menos de 5 minutos, caso contrário busca um novo.
3. O GUID do dispositivo `comfort_places_app` é resolvido via `GET /v1/default/devices/` e armazenado em memória.
4. Os eventos são buscados com a query `device:{guid} timestamp:>{since}` para os últimos 7 dias, ordenados do mais novo para o mais antigo, com limite de 10.000 registros.
5. Cada evento bruto é mapeado para `SensorReading` via `DeviceEvent.toSensorReading()`, que extrai `deviceLocation` da chave do payload e faz parse de todos os campos numéricos.
6. O ViewModel agrupa as leituras por `deviceLocation` e expõe o mapa como estado observável do Compose.
7. Os passos 1 a 6 se repetem automaticamente a cada 30 segundos.

---

## Telas

### Login
- Campos de usuário e senha (credenciais Konker)
- Seletor de idioma (chips com bandeiras) — EN / PT-BR / ES
- Validação: campos vazios são rejeitados antes da chamada de rede

### Dashboard (Aba 1)
- **Barra superior:** título "Ambi" + seletor de idioma
- **Mapa de Ruído:** filtrável por zona; escala de cores verde→âmbar→vermelho; faixa 45–90 dB
- **Gráfico Sonoro:** últimas 10 leituras de Leq em gráfico de linha
- **Gráfico Temp. e Umidade:** últimas 10 leituras, dois eixos
- **Card de RSSI:** qualidade do sinal com indicador textual

### Planta Baixa (Aba 2)
- Coluna esquerda (42% da largura): mapa Canvas interativo + botões de seleção de zona
- Coluna direita (58% da largura): quatro cards de medidor animados para a zona selecionada
  - Som Leq (dB)
  - Temperatura (°C)
  - Umidade (%)
  - RSSI Wi-Fi (dBm)
- Card de picos na base: valores Lmax / Lmin
- Faixa de legenda: chave de cores por faixa de dB

---

## Como Executar

### Pré-requisitos

- Android Studio Hedgehog (2023.1) ou versão mais recente
- JDK 17
- Dispositivo Android ou emulador com API 26+
- Conta ativa no Konker com dispositivo registrado publicando o formato de payload correto

### Build e Execução

```bash
# Clone o repositório
git clone <url-do-repositório>
cd ComfortPlacesApp_1

# Abra no Android Studio e sincronize o Gradle, ou compile pelo CLI:
./gradlew assembleDebug

# Instale no dispositivo conectado
./gradlew installDebug
```

### Login

Abra o app e insira seu **usuário e senha do Konker**. O app autenticará no servidor e navegará para a tela principal em caso de sucesso.

---

## Configuração

Toda a configuração de backend está centralizada em `TokenManager.kt` e `KonkerRepository.kt`:

| Constante | Arquivo | Valor padrão |
|---|---|---|
| `BASE_URL` | `TokenManager` | `https://api-ucmp.soneca.dev` |
| `APP` | `KonkerRepository` | `default` |
| `DEVICE_NAME` | `KonkerRepository` | `comfort_places_app` |

Para apontar o app para uma instância diferente do Konker ou outro dispositivo, atualize essas constantes e faça o rebuild.

---

## Payload do Sensor

Cada evento Konker deve conter os seguintes campos no payload. O campo `deviceLocation` mapeia para uma das cinco zonas monitoradas:

| Campo | Tipo | Unidade | Descrição |
|---|---|---|---|
| `deviceLocation` | String | — | Chave da zona (`"Lunch Area"`, `"Close to Kitchen"`, `"Close to Restrooms"`, `"Entrance"`, `"External Area"`) |
| `leqSpl` | Float | dB | Nível sonoro equivalente contínuo (IEC 61672) |
| `lmaxSpl` | Float | dB | Nível sonoro máximo durante o período de medição |
| `lminSpl` | Float | dB | Nível sonoro mínimo durante o período de medição |
| `temperature` | Float | °C | Temperatura ambiente |
| `humidity` | Float | % | Umidade relativa do ar |
| `wifiRssi` | Int | dBm | Intensidade do sinal Wi-Fi recebido |

Os timestamps devem usar o formato Konker: `yyyy.MM.dd'T'HH:mm:ss` no fuso horário `America/Sao_Paulo`.

---

## Limiares de Ruído

O app utiliza os seguintes limiares de Leq para codificação de cores em todas as visualizações:

| Faixa | Cor | Significado |
|---|---|---|
| Sem dados / 0 dB | Cinza | Sem leitura |
| < 60 dB | Verde | Confortável |
| 60 – 75 dB | Âmbar | Moderado |
| > 75 dB | Vermelho | Desconfortável |
| > 80 dB | Vermelho (strobe) | Crítico — borda da planta pisca rapidamente |

---

*Desenvolvido como projeto final do curso "IoT e Android Embarcado" da Unicamp.*
