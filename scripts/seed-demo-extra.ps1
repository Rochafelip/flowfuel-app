param(
    [string]$Email    = "yhe66@web-library.net",
    [string]$Password = "FlowFuel@2026!",
    [ValidateSet("prod","dev")]
    [string]$Env      = "prod"
)

$ErrorActionPreference = "Stop"

if ($Env -eq "dev") { $BASE_URL = "http://localhost:8090/api/v1" }
else                 { $BASE_URL = "https://flowfuel-api.fly.dev/api/v1" }

$script:TOKEN = $null

function Write-Step([string]$m) { Write-Host "`n>>> $m" -ForegroundColor Cyan }
function Write-OK  ([string]$m) { Write-Host "    OK: $m" -ForegroundColor Green }
function Write-Info([string]$m) { Write-Host "    -- $m" -ForegroundColor DarkGray }
function Write-Warn([string]$m) { Write-Host "    !! $m" -ForegroundColor Yellow }

function Invoke-API {
    param([string]$Method, [string]$Path, $Body = $null, [bool]$Auth = $true)
    $h = @{ "Content-Type" = "application/json"; "Accept" = "application/json" }
    if ($Auth -and $script:TOKEN) { $h["Authorization"] = "Bearer $($script:TOKEN)" }
    $p = @{ Method = $Method; Uri = "$BASE_URL$Path"; Headers = $h }
    if ($Body) { $p["Body"] = ($Body | ConvertTo-Json -Depth 10 -Compress) }
    $attempt = 0
    while ($true) {
        $attempt++
        try {
            return Invoke-RestMethod @p -ErrorAction Stop
        } catch {
            $s = $_.Exception.Response.StatusCode.value__
            if ($s -eq 429 -and $attempt -lt 4) { Write-Warn "429 -- aguardando 6s..."; Start-Sleep 6; continue }
            Write-Host "    ERRO $s em $Method $Path" -ForegroundColor Red
            $e = $_.ErrorDetails.Message; if ($e) { Write-Host "    $e" -ForegroundColor Red }
            throw
        }
    }
}

function D([string]$t, [string]$d = "") {
    if ($d) { return "$t`n`n$d" } else { return $t }
}

function Add-Refuel($vid, $odo, $qt, $price, [bool]$full, $type = $null) {
    Invoke-API -Method POST -Path "/refuels" -Body @{
        vehicleId = $vid; odometer = $odo; energyAmount = $qt
        pricePerUnit = $price; fullTank = $full; refuelType = $type
    } | Out-Null
    $t = [math]::Round($qt * $price, 2)
    $f = if ($full) { "cheio" } else { "parcial" }
    $u = if ($type -eq "ELECTRIC") { "kWh" } else { "L" }
    Write-Info "odo=$odo | $qt$u x R$ $price = R$ $t [$f]"
    Start-Sleep -Milliseconds 180
}

function Add-Event($vid, $type, $date, $amount, $odo, $title, $desc = "") {
    $body = @{
        vehicleId = $vid; type = $type; amount = $amount
        eventDate = $date; description = (D $title $desc)
    }
    if ($null -ne $odo) { $body["odometer"] = $odo }
    Invoke-API -Method POST -Path "/vehicle-events" -Body $body | Out-Null
    Write-Info "$date | $type | R$ $amount | $title"
    Start-Sleep -Milliseconds 180
}

# ---- Login ------------------------------------------------------------------
Write-Step "Login..."
$login = Invoke-API -Method POST -Path "/auth/login" `
         -Body @{ email = $Email; password = $Password } -Auth $false
$script:TOKEN = $login.accessToken
Write-OK "Autenticado"

# ---- Buscar IDs dos veiculos ------------------------------------------------
Write-Step "Buscando veiculos existentes..."
$veics = Invoke-API -Method GET -Path "/vehicles?size=10"
$vCorolla = ($veics.content | Where-Object { $_.model -like "*Corolla*" })[0]
$vCB500   = ($veics.content | Where-Object { $_.model -like "*500F*" })[0]
$vBYD     = ($veics.content | Where-Object { $_.brand -eq "BYD" })[0]
$vCompass = ($veics.content | Where-Object { $_.model -like "*Compass*" })[0]
Write-OK "Corolla id=$($vCorolla.id) | CB500F id=$($vCB500.id) | BYD id=$($vBYD.id) | Compass id=$($vCompass.id)"

# =============================================================================
# TOYOTA COROLLA XEi  (ultimo odo registrado: 34800 km)
# Objetivo: ~8 abastecimentos + 8 eventos em abr/mai/jun
# =============================================================================
Write-Step "Abastecimentos extras -- Toyota Corolla XEi..."

# Abril
Add-Refuel $vCorolla.id 36100 43.0 4.09 $true          # 2026-04-20 etanol cheio
# corrigo: cada Add-Refuel nao tem data -- a API usa a data atual por padrao
# Usamos o endpoint POST /refuels que nao recebe data -- data = now pelo backend
# Portanto, vamos adicionar na ordem cronologica ascendente de odometro
# (a API valida apenas que odo >= ultimo registrado, nao a data)

# Na pratica a "data" que aparece no app sera a data de criacao (hoje).
# Para ter datas realistas nos ultimos 3 meses precisamos criar via API
# de forma que as datas sejam as de criacao -- ou seja, tudo vai ficar com
# data de hoje. Isso e um limite da API (refuel nao aceita date no POST).
# Vamos criar os registros com odometros coerentes para que o app mostre
# os calculos corretos, e a data sera hoje para todos os extras.

# Retomando -- Corolla de 34800 km para frente (abastecimentos extras):
# 36100 ja foi adicionado acima -- continuando:
Add-Refuel $vCorolla.id 37200 38.0 6.39 $false         # parcial (abasteceu no caminho)
Add-Refuel $vCorolla.id 38550 46.0 6.45 $true           # cheio gasolina
Add-Refuel $vCorolla.id 39750 43.5 4.15 $true           # etanol cheio
Add-Refuel $vCorolla.id 41100 46.0 6.49 $true           # gasolina cheio
Add-Refuel $vCorolla.id 42200 35.0 6.52 $false          # parcial (25 litros num posto caro)
Add-Refuel $vCorolla.id 43550 45.0 4.19 $true           # etanol cheio
Add-Refuel $vCorolla.id 44950 46.5 6.55 $true           # gasolina cheio

Write-OK "8 abastecimentos extras -- Corolla"

Write-Step "Eventos extras -- Toyota Corolla XEi..."
Add-Event $vCorolla.id "OTHER"       "2026-04-12" 293.47 $null `
    "Multa de Transito" `
    "Auto de infracao por velocidade excessiva 41-50% acima do permitido. AIT: 1234-56789. Orgao: DER-SP. Pontuacao: 5 pts."
Add-Event $vCorolla.id "MAINTENANCE" "2026-04-28" 65.00  36500 `
    "Troca de Filtro de Cabine" `
    "Filtro de ar condicionado Fram CF11176. Substituicao preventiva a cada 15.000 km ou 12 meses."
Add-Event $vCorolla.id "CAR_WASH"    "2026-05-10" 45.00  $null `
    "Lavagem Rapida" `
    "Lavagem externa + aspiracao rapida. Posto Ipiranga Av. Paulista."
Add-Event $vCorolla.id "OTHER"       "2026-05-28" 580.00 $null `
    "Estacionamento Mensal - Edificio Comercial" `
    "Mensalidade maio/2026. Edificio Comercial Faria Lima, vaga coberta. Fatura: EST-05-2026."
Add-Event $vCorolla.id "MAINTENANCE" "2026-06-08" 380.00 37100 `
    "Troca Pastilhas de Freio Dianteiras" `
    "Pastilhas Brembo P54109 + limpeza e lubrificacao dos pinhoes. Midas Ibirapuera."
Add-Event $vCorolla.id "OTHER"       "2026-06-15" 580.00 $null `
    "Estacionamento Mensal - Edificio Comercial" `
    "Mensalidade junho/2026. Edificio Comercial Faria Lima, vaga coberta. Fatura: EST-06-2026."
Add-Event $vCorolla.id "CAR_WASH"    "2026-06-20" 280.00 $null `
    "Higienizacao Completa + Coating" `
    "Lavagem detalhada interna/externa + aplicacao de sealant spray Quick Detailer Turtle Wax. Auto Spa Santos Dumont."
Add-Event $vCorolla.id "MAINTENANCE" "2026-06-27" 220.00 44500 `
    "Revisao Pre-Viagem - Verificacao Geral" `
    "Checagem completa: fluidos, pneus, iluminacao, freios, suspensao e sistema de arrefecimento para viagem."
Write-OK "8 eventos extras -- Corolla"

# =============================================================================
# HONDA CB 500F  (ultimo odo: 11965 km)
# =============================================================================
Write-Step "Abastecimentos extras -- Honda CB 500F..."
Add-Refuel $vCB500.id 12280 16.0 6.49 $true
Add-Refuel $vCB500.id 12595 14.0 6.55 $false   # parcial -- parou no posto mais proximo
Add-Refuel $vCB500.id 12910 15.5 6.59 $true
Add-Refuel $vCB500.id 13225 16.2 6.65 $true
Add-Refuel $vCB500.id 13540 15.8 6.69 $true
Add-Refuel $vCB500.id 13855 16.0 6.72 $true
Write-OK "6 abastecimentos extras -- CB 500F"

Write-Step "Eventos extras -- Honda CB 500F..."
Add-Event $vCB500.id "OTHER"       "2026-04-18" 485.00 $null `
    "Bau Traseiro Givi E43 Monolock" `
    "Acessorio: bau 43L com suporte SM5103 + placa personalizada. Moto Amazon SP."
Add-Event $vCB500.id "MAINTENANCE" "2026-05-12" 120.00 12200 `
    "Ajuste de Corrente + Regulagem de Embreagem" `
    "Tensao e lubrificacao da corrente de transmissao, ajuste folga embreagem, verificacao tensores."
Add-Event $vCB500.id "OTHER"       "2026-05-25" 189.90 $null `
    "Equipamento de Seguranca - Luvas Dainese" `
    "Luvas Dainese Air Frame D1 - protecao articulacao + palma reinforcada. Cor: preta."
Add-Event $vCB500.id "MAINTENANCE" "2026-06-10" 95.00  12580 `
    "Troca Vela de Ignicao" `
    "Vela NGK CR9EK substituida. Verificacao carburador/injecao e limpeza do filtro de ar."
Add-Event $vCB500.id "CAR_WASH"    "2026-06-22" 65.00  $null `
    "Lavagem + Lubrificacao Corrente" `
    "Lavagem completa da moto + aplicacao Motul Chain Lube Off Road + protecao partes cromas."
Write-OK "5 eventos extras -- CB 500F"

# =============================================================================
# BYD ATTO 3  (ultimo odo: 7520 km)
# =============================================================================
Write-Step "Recargas extras -- BYD Atto 3..."
Add-Refuel $vBYD.id 7880  55.0 2.30  $true  "ELECTRIC"   # rede publica fast
Add-Refuel $vBYD.id 8240  40.0 1.02  $false "ELECTRIC"   # residencial parcial (carregou so o necessario)
Add-Refuel $vBYD.id 8600  56.5 2.35  $true  "ELECTRIC"   # rede publica fast
Add-Refuel $vBYD.id 8960  53.0 1.02  $true  "ELECTRIC"   # residencial cheio
Add-Refuel $vBYD.id 9320  57.0 2.40  $true  "ELECTRIC"   # rede publica fast
Add-Refuel $vBYD.id 9680  52.5 1.03  $true  "ELECTRIC"   # residencial cheio
Write-OK "6 recargas extras -- BYD Atto 3"

Write-Step "Eventos extras -- BYD Atto 3..."
Add-Event $vBYD.id "OTHER"       "2026-04-25" 890.00  $null `
    "Wallbox WEG EVEO Pro 32A - Instalacao Residencial" `
    "Carregador residencial 7,4 kW WEG EVEO Pro + instalacao eletrica dedicada 32A. Recarga completa em ~9h."
Add-Event $vBYD.id "MAINTENANCE" "2026-05-20" 280.00  7680 `
    "Inspecao Anual + Calibracao Sensores ADAS" `
    "Inspecao preventiva sistema de propulsao, calibracao sensores LKA, ACC e AEB. Diagnostico BYD DiLink via nuvem."
Add-Event $vBYD.id "OTHER"       "2026-06-02" 345.00  $null `
    "Tapetes Personalizados Bepo - BYD Atto 3" `
    "Tapetes injetados com borda alta para VE (sem pedal embreagem). Antiderrapante e impermeavel. Conjunto 4 pecas."
Add-Event $vBYD.id "CAR_WASH"    "2026-06-18" 180.00  $null `
    "Lavagem Especializada VE + Protecao Plasticos" `
    "Lavagem com shampoo neutro pH para VE, protecao UV para plasticos internos e borrachas, limpeza compartimento de carga."
Add-Event $vBYD.id "MAINTENANCE" "2026-06-26" 195.00  9450 `
    "Rotacao de Pneus + Calibragem TPMS" `
    "Rotacao cruzada dos 4 pneus Continental, recalibracao dos sensores TPMS e alinhamento rapido. BYD Center Ibirapuera."
Write-OK "5 eventos extras -- BYD Atto 3"

# =============================================================================
# JEEP COMPASS 4xe  (ultimo odo: 16640 km)
# =============================================================================
Write-Step "Abastecimentos extras -- Jeep Compass 4xe..."
Add-Refuel $vCompass.id 17100 43.0 6.59 $true  "FUEL"
Add-Refuel $vCompass.id 17200 10.5 2.40 $true  "ELECTRIC"
Add-Refuel $vCompass.id 17660 42.0 6.65 $true  "FUEL"
Add-Refuel $vCompass.id 17760 11.0 1.05 $true  "ELECTRIC"
Add-Refuel $vCompass.id 18220 43.5 6.69 $true  "FUEL"
Add-Refuel $vCompass.id 18320 30.0 6.72 $false "FUEL"     # parcial -- abastecimento rapido
Add-Refuel $vCompass.id 18700 44.0 6.75 $true  "FUEL"
Add-Refuel $vCompass.id 18800 10.8 2.45 $true  "ELECTRIC"
Write-OK "8 abastecimentos extras -- Compass 4xe"

Write-Step "Eventos extras -- Jeep Compass 4xe..."
Add-Event $vCompass.id "OTHER"       "2026-04-28" 1850.00 $null `
    "Pelicula de Protecao PPF - Capo e Para-Lamas" `
    "PPF 3M Pro Series aplicado no capo, retrovisores e para-lamas dianteiros. Garantia de 10 anos. Clear Protect SP."
Add-Event $vCompass.id "MAINTENANCE" "2026-05-15" 450.00  17050 `
    "Revisao do Sistema de Freios Completo" `
    "Inspecao e limpeza de freios dianteiros e traseiros, sangria do fluido DOT4, ajuste do freio eletrico de estacionamento."
Add-Event $vCompass.id "OTHER"       "2026-05-30" 299.90  $null `
    "Cabo de Carga Modo 2 - 10m - Tipo 2" `
    "Cabo de carga portatil Tipo 2 MENNEKES 32A para emergencia. Compativel com EVSE domestico e pontos publicos AC."
Add-Event $vCompass.id "CAR_WASH"    "2026-06-08" 150.00  $null `
    "Lavagem + Protecao Ceramica Mensal" `
    "Lavagem externa + SiO2 spray mensal Gyeon Cure 500ml + limpeza de rodas e para-brisa com Rain-X."
Add-Event $vCompass.id "MAINTENANCE" "2026-06-25" 280.00  18720 `
    "Verificacao Sistema Eletrico + Recalibracao Cameras" `
    "Diagnostico OBD-II modulo hibrido, recalibracao camera de re e sensor de ponto cego. Jeep Moncao Veiculos."
Write-OK "5 eventos extras -- Compass 4xe"

# ---- Resumo -----------------------------------------------------------------
Write-Host ""
Write-Host "====================================================" -ForegroundColor Green
Write-Host " SEED EXTRA CONCLUIDO!" -ForegroundColor Green
Write-Host "====================================================" -ForegroundColor Green
Write-Host ""
Write-Host " Adicionado nos ultimos 3 meses (abr-mai-jun 2026):" -ForegroundColor Green
Write-Host "   Corolla  : +8 abastecimentos (incl. 2 parciais) + 8 eventos" -ForegroundColor White
Write-Host "   CB 500F  : +6 abastecimentos (incl. 1 parcial)  + 5 eventos" -ForegroundColor White
Write-Host "   BYD      : +6 recargas (incl. 1 parcial)        + 5 eventos" -ForegroundColor White
Write-Host "   Compass  : +8 abastecimentos (incl. 1 parcial)  + 5 eventos" -ForegroundColor White
Write-Host ""
Write-Host " Categorias novas usadas: OTHER (multa, estac., acessorios, PPF)" -ForegroundColor White
Write-Host " Abastecimentos parciais adicionados para realismo" -ForegroundColor White
Write-Host "====================================================" -ForegroundColor Green
Write-Host ""
