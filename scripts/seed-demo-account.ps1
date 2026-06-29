param(
    [string]$Email    = "demo.flowfuel.cliente@gmail.com",
    [ValidateSet("prod","dev")]
    [string]$Env      = "prod",
    [string]$Password = "FlowFuel@2026!",

    # Pule o registro (conta ja existe) e va direto para o seed de dados
    [switch]$SkipRegistration,

    # Pule a ativacao (conta ja esta ativa) e va direto para o login
    [switch]$SkipActivation
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($Env -eq "dev") {
    $BASE_URL = "http://localhost:8090/api/v1"
} else {
    $BASE_URL = "https://flowfuel-api.fly.dev/api/v1"
}

$ACCOUNT = @{
    name     = "Carlos Eduardo Mendes"
    email    = $Email
    password = $Password
    phone    = "(11) 98521-4730"
}

$script:TOKEN = $null

function Write-Step([string]$msg) { Write-Host "`n>>> $msg" -ForegroundColor Cyan }
function Write-OK  ([string]$msg) { Write-Host "    OK: $msg" -ForegroundColor Green }
function Write-Info([string]$msg) { Write-Host "    -- $msg" -ForegroundColor DarkGray }
function Write-Warn([string]$msg) { Write-Host "    !! $msg" -ForegroundColor Yellow }

function Invoke-API {
    param(
        [string]$Method,
        [string]$Path,
        $Body       = $null,
        [bool]$Auth = $true,
        [int]$RetryCount = 3
    )

    $headers = @{ "Content-Type" = "application/json"; "Accept" = "application/json" }
    if ($Auth -and $script:TOKEN) {
        $headers["Authorization"] = "Bearer $($script:TOKEN)"
    }

    $reqParams = @{
        Method  = $Method
        Uri     = "$BASE_URL$Path"
        Headers = $headers
    }
    if ($Body) {
        $reqParams["Body"] = ($Body | ConvertTo-Json -Depth 10 -Compress)
    }

    $attempt = 0
    while ($true) {
        $attempt++
        try {
            $resp = Invoke-RestMethod @reqParams -ErrorAction Stop
            return $resp
        } catch {
            $status  = $_.Exception.Response.StatusCode.value__
            $errText = $_.ErrorDetails.Message

            if ($status -eq 429 -and $attempt -lt $RetryCount) {
                Write-Warn "Rate limit (429) -- aguardando 5s..."
                Start-Sleep -Seconds 5
                continue
            }

            Write-Host "    ERRO $status em $Method $Path" -ForegroundColor Red
            if ($errText) { Write-Host "    $errText" -ForegroundColor Red }
            throw
        }
    }
}

function Make-Desc([string]$title, [string]$desc = "") {
    if ($desc) { return "$title`n`n$desc" }
    return $title
}

Write-Host ""
Write-Host "====================================================" -ForegroundColor DarkCyan
Write-Host " FlowFuel -- Seed de Conta Demo" -ForegroundColor DarkCyan
Write-Host " Ambiente : $Env" -ForegroundColor DarkCyan
Write-Host " E-mail   : $Email" -ForegroundColor DarkCyan
Write-Host "====================================================" -ForegroundColor DarkCyan
Write-Host ""

# ---- ETAPA 1: Cadastro -------------------------------------------------------
if ($SkipRegistration) {
    Write-Warn "Registro ignorado (-SkipRegistration)"
} else {
    Write-Step "Cadastrando usuario: $($ACCOUNT.email)"
    try {
        $reg = Invoke-API -Method POST -Path "/auth/register" -Body $ACCOUNT -Auth $false
        Write-OK "Conta criada (id=$($reg.id))"
    } catch {
        $rawErr = $_.ErrorDetails.Message
        if ($rawErr -match "EMAIL_ALREADY_REGISTERED") {
            Write-Warn "E-mail ja cadastrado. Continuando com login..."
        } else {
            throw
        }
    }
    Start-Sleep -Milliseconds 500
}

# ---- ETAPA 2: Ativacao -------------------------------------------------------
if ($SkipActivation -or $SkipRegistration) {
    Write-Warn "Ativacao ignorada (assumindo conta ja ativa)"
} else {
    Write-Step "Tentando obter token de ativacao via resend-activation..."
    $activationToken = $null
    try {
        $resend = Invoke-API -Method POST -Path "/auth/resend-activation" `
                             -Body @{ email = $ACCOUNT.email } -Auth $false
        $activationToken = $resend.activationToken
    } catch {
        Write-Warn "resend-activation falhou (ignorando)"
    }

    if ($activationToken) {
        Write-OK "Token de ativacao obtido (servidor em modo dev/teste)"
        Write-Step "Ativando conta com token..."
        try {
            Invoke-API -Method POST -Path "/auth/activate" `
                       -Body @{ token = $activationToken } -Auth $false | Out-Null
            Write-OK "Conta ativada com sucesso"
        } catch {
            Write-Warn "Ativacao via token falhou -- verifique o e-mail manualmente"
        }
    } else {
        Write-Warn "Servidor de producao nao expoe o token de ativacao."
        Write-Host ""
        Write-Host "  ACAO NECESSARIA:" -ForegroundColor Yellow
        Write-Host "  1. Abra o e-mail enviado para: $($ACCOUNT.email)" -ForegroundColor Yellow
        Write-Host "  2. Clique no link de ativacao da conta FlowFuel." -ForegroundColor Yellow
        Write-Host "  3. Rode novamente com: -SkipRegistration" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "  Exemplo:" -ForegroundColor Yellow
        Write-Host "  .\scripts\seed-demo-account.ps1 -Email '$($ACCOUNT.email)' -SkipRegistration" -ForegroundColor Cyan
        Write-Host ""
        exit 0
    }
}
Start-Sleep -Milliseconds 500

# ---- ETAPA 3: Login ----------------------------------------------------------
Write-Step "Fazendo login..."
$login = Invoke-API -Method POST -Path "/auth/login" `
                    -Body @{ email = $ACCOUNT.email; password = $ACCOUNT.password } `
                    -Auth $false
$script:TOKEN = $login.accessToken
Write-OK "Login OK -- access token obtido"
Start-Sleep -Milliseconds 300

# ---- ETAPA 4: Criar veiculos -------------------------------------------------
Write-Step "Criando veiculos..."

$vCorolla = Invoke-API -Method POST -Path "/vehicles" -Body @{
    brand           = "Toyota"
    model           = "Corolla XEi 2.0 Flex"
    manufactureYear = 2021
    modelYear       = 2022
    licensePlate    = "ABC1D23"
    color           = "Prata"
    type            = "Carro"
    energyType      = "COMBUSTION"
    fuelSubType     = "Flex"
    currentKm       = 18500
    capacity        = 50
}
Write-OK "Toyota Corolla XEi -- id=$($vCorolla.id)"
Start-Sleep -Milliseconds 400

$vCB500 = Invoke-API -Method POST -Path "/vehicles" -Body @{
    brand           = "Honda"
    model           = "CB 500F"
    manufactureYear = 2020
    modelYear       = 2020
    licensePlate    = "MTR5A67"
    color           = "Preto"
    type            = "Moto"
    energyType      = "COMBUSTION"
    fuelSubType     = "Gasolina comum"
    currentKm       = 8200
    capacity        = 17
}
Write-OK "Honda CB 500F -- id=$($vCB500.id)"
Start-Sleep -Milliseconds 400

$vBYD = Invoke-API -Method POST -Path "/vehicles" -Body @{
    brand           = "BYD"
    model           = "Atto 3 Extended Range"
    manufactureYear = 2023
    modelYear       = 2024
    licensePlate    = "ELT3B45"
    color           = "Branco"
    type            = "Carro"
    energyType      = "ELECTRIC"
    currentKm       = 3200
    capacity        = 1
}
Write-OK "BYD Atto 3 -- id=$($vBYD.id)"
Start-Sleep -Milliseconds 400

$vCompass = Invoke-API -Method POST -Path "/vehicles" -Body @{
    brand           = "Jeep"
    model           = "Compass 4xe"
    manufactureYear = 2023
    modelYear       = 2023
    licensePlate    = "HBR7C89"
    color           = "Cinza Chumbo"
    type            = "Carro"
    energyType      = "HYBRID"
    fuelSubType     = "Gasolina comum"
    currentKm       = 11000
    capacity        = 48
}
Write-OK "Jeep Compass 4xe -- id=$($vCompass.id)"
Start-Sleep -Milliseconds 400

Invoke-API -Method PUT -Path "/vehicles/$($vCorolla.id)/active" | Out-Null
Write-OK "Corolla definido como veiculo ativo"

# ---- ETAPA 5: Abastecimentos Toyota Corolla XEi (combustao/Flex) -------------
#  Consumo: ~13 km/L gasolina, ~9 km/L etanol | Tanque: 50L
#  Odometro inicial: 18.500 km
Write-Step "Abastecimentos -- Toyota Corolla XEi..."

$corollaRefuels = @(
    @{ date="2025-07-15"; odo=19850;  lt=44.5; preco=3.89; full=$true  },
    @{ date="2025-08-12"; odo=21200;  lt=46.0; preco=5.89; full=$true  },
    @{ date="2025-09-08"; odo=22550;  lt=43.0; preco=3.79; full=$true  },
    @{ date="2025-10-05"; odo=23900;  lt=46.5; preco=5.99; full=$true  },
    @{ date="2025-11-02"; odo=25250;  lt=44.0; preco=3.85; full=$true  },
    @{ date="2025-11-29"; odo=26600;  lt=45.0; preco=6.09; full=$true  },
    @{ date="2025-12-20"; odo=27900;  lt=43.5; preco=3.92; full=$true  },
    @{ date="2026-01-18"; odo=29300;  lt=46.0; preco=6.15; full=$true  },
    @{ date="2026-02-14"; odo=30650;  lt=44.0; preco=3.95; full=$true  },
    @{ date="2026-03-10"; odo=32050;  lt=45.5; preco=6.19; full=$true  },
    @{ date="2026-04-05"; odo=33400;  lt=44.0; preco=3.99; full=$true  },
    @{ date="2026-05-07"; odo=34800;  lt=46.0; preco=6.25; full=$true  }
)
foreach ($r in $corollaRefuels) {
    Invoke-API -Method POST -Path "/refuels" -Body @{
        vehicleId    = $vCorolla.id
        odometer     = $r.odo
        energyAmount = $r.lt
        pricePerUnit = $r.preco
        fullTank     = $r.full
        refuelType   = $null
    } | Out-Null
    $total = [math]::Round($r.lt * $r.preco, 2)
    Write-Info "$($r.date) | $($r.odo) km | $($r.lt)L x R$ $($r.preco) = R$ $total"
    Start-Sleep -Milliseconds 200
}
Write-OK "12 abastecimentos criados -- Corolla XEi"

# ---- ETAPA 6: Abastecimentos Honda CB 500F -----------------------------------
#  Consumo: ~19 km/L | Tanque: 17L
#  Odometro inicial: 8.200 km
Write-Step "Abastecimentos -- Honda CB 500F..."

$cb500Refuels = @(
    @{ date="2025-08-20"; odo=8520;  lt=16.5; preco=5.89; full=$true  },
    @{ date="2025-09-14"; odo=8840;  lt=15.8; preco=5.95; full=$true  },
    @{ date="2025-10-08"; odo=9150;  lt=16.2; preco=5.89; full=$true  },
    @{ date="2025-11-01"; odo=9460;  lt=15.5; preco=5.99; full=$true  },
    @{ date="2025-11-25"; odo=9775;  lt=16.0; preco=6.09; full=$true  },
    @{ date="2025-12-19"; odo=10080; lt=15.5; preco=6.15; full=$false },
    @{ date="2026-01-12"; odo=10395; lt=16.5; preco=6.19; full=$true  },
    @{ date="2026-02-06"; odo=10710; lt=15.8; preco=6.25; full=$true  },
    @{ date="2026-03-02"; odo=11020; lt=16.0; preco=6.29; full=$true  },
    @{ date="2026-03-28"; odo=11335; lt=15.5; preco=6.35; full=$true  },
    @{ date="2026-04-22"; odo=11650; lt=16.2; preco=6.39; full=$true  },
    @{ date="2026-05-17"; odo=11965; lt=15.8; preco=6.45; full=$true  }
)
foreach ($r in $cb500Refuels) {
    Invoke-API -Method POST -Path "/refuels" -Body @{
        vehicleId    = $vCB500.id
        odometer     = $r.odo
        energyAmount = $r.lt
        pricePerUnit = $r.preco
        fullTank     = $r.full
        refuelType   = $null
    } | Out-Null
    $total = [math]::Round($r.lt * $r.preco, 2)
    Write-Info "$($r.date) | $($r.odo) km | $($r.lt)L x R$ $($r.preco) = R$ $total"
    Start-Sleep -Milliseconds 200
}
Write-OK "12 abastecimentos criados -- CB 500F"

# ---- ETAPA 7: Recargas BYD Atto 3 (eletrico) ---------------------------------
#  Consumo: ~6.2 km/kWh | Bateria: 60.48 kWh | Autonomia: ~375 km
#  Odometro inicial: 3.200 km
Write-Step "Recargas -- BYD Atto 3..."

$bydRefuels = @(
    @{ date="2025-09-05"; odo=3560;  kwh=56.0; preco=1.95; full=$true },
    @{ date="2025-09-28"; odo=3920;  kwh=52.0; preco=0.96; full=$true },
    @{ date="2025-10-22"; odo=4280;  kwh=57.0; preco=2.05; full=$true },
    @{ date="2025-11-15"; odo=4640;  kwh=53.0; preco=0.96; full=$true },
    @{ date="2025-12-09"; odo=5000;  kwh=55.5; preco=2.10; full=$true },
    @{ date="2026-01-02"; odo=5360;  kwh=52.5; preco=0.97; full=$true },
    @{ date="2026-01-26"; odo=5720;  kwh=57.0; preco=2.15; full=$true },
    @{ date="2026-02-19"; odo=6080;  kwh=54.0; preco=0.97; full=$true },
    @{ date="2026-03-15"; odo=6440;  kwh=55.0; preco=2.20; full=$true },
    @{ date="2026-04-08"; odo=6800;  kwh=53.5; preco=0.98; full=$true },
    @{ date="2026-05-02"; odo=7160;  kwh=56.0; preco=2.25; full=$true },
    @{ date="2026-05-27"; odo=7520;  kwh=54.5; preco=0.99; full=$true }
)
foreach ($r in $bydRefuels) {
    Invoke-API -Method POST -Path "/refuels" -Body @{
        vehicleId    = $vBYD.id
        odometer     = $r.odo
        energyAmount = $r.kwh
        pricePerUnit = $r.preco
        fullTank     = $r.full
        refuelType   = "ELECTRIC"
    } | Out-Null
    $total = [math]::Round($r.kwh * $r.preco, 2)
    Write-Info "$($r.date) | $($r.odo) km | $($r.kwh) kWh x R$ $($r.preco) = R$ $total"
    Start-Sleep -Milliseconds 200
}
Write-OK "12 recargas criadas -- BYD Atto 3"

# ---- ETAPA 8: Abastecimentos Jeep Compass 4xe (hibrido plug-in) --------------
#  Tanque: 48L | Bateria: 11.4 kWh | Range eletrico: ~50 km
#  Odometro inicial: 11.000 km
Write-Step "Abastecimentos -- Jeep Compass 4xe (hibrido)..."

$compassRefuels = @(
    @{ date="2025-08-10"; odo=11460; qt=42.0; preco=6.09; full=$true;  tipo="FUEL"     },
    @{ date="2025-08-22"; odo=11565; qt=11.0; preco=2.15; full=$true;  tipo="ELECTRIC" },
    @{ date="2025-09-05"; odo=12020; qt=43.5; preco=6.15; full=$true;  tipo="FUEL"     },
    @{ date="2025-09-18"; odo=12125; qt=10.8; preco=0.96; full=$true;  tipo="ELECTRIC" },
    @{ date="2025-10-02"; odo=12580; qt=42.0; preco=6.19; full=$true;  tipo="FUEL"     },
    @{ date="2025-10-20"; odo=12685; qt=11.2; preco=2.20; full=$true;  tipo="ELECTRIC" },
    @{ date="2025-11-05"; odo=13145; qt=44.0; preco=6.25; full=$true;  tipo="FUEL"     },
    @{ date="2025-11-22"; odo=13250; qt=10.5; preco=0.97; full=$true;  tipo="ELECTRIC" },
    @{ date="2025-12-05"; odo=13705; qt=42.5; preco=6.29; full=$true;  tipo="FUEL"     },
    @{ date="2025-12-22"; odo=13810; qt=11.0; preco=2.25; full=$true;  tipo="ELECTRIC" },
    @{ date="2026-01-08"; odo=14270; qt=43.0; preco=6.35; full=$true;  tipo="FUEL"     },
    @{ date="2026-01-26"; odo=14375; qt=10.8; preco=0.98; full=$true;  tipo="ELECTRIC" },
    @{ date="2026-02-10"; odo=14835; qt=42.0; preco=6.39; full=$true;  tipo="FUEL"     },
    @{ date="2026-02-27"; odo=14940; qt=11.5; preco=2.30; full=$true;  tipo="ELECTRIC" },
    @{ date="2026-03-12"; odo=15400; qt=43.5; preco=6.45; full=$true;  tipo="FUEL"     },
    @{ date="2026-03-29"; odo=15505; qt=10.8; preco=0.98; full=$true;  tipo="ELECTRIC" },
    @{ date="2026-04-15"; odo=15965; qt=42.0; preco=6.49; full=$true;  tipo="FUEL"     },
    @{ date="2026-05-01"; odo=16070; qt=11.2; preco=2.35; full=$true;  tipo="ELECTRIC" },
    @{ date="2026-05-18"; odo=16535; qt=44.0; preco=6.55; full=$true;  tipo="FUEL"     },
    @{ date="2026-06-02"; odo=16640; qt=10.5; preco=1.00; full=$true;  tipo="ELECTRIC" }
)
foreach ($r in $compassRefuels) {
    $un = if ($r.tipo -eq "ELECTRIC") { "kWh" } else { "L" }
    Invoke-API -Method POST -Path "/refuels" -Body @{
        vehicleId    = $vCompass.id
        odometer     = $r.odo
        energyAmount = $r.qt
        pricePerUnit = $r.preco
        fullTank     = $r.full
        refuelType   = $r.tipo
    } | Out-Null
    $total = [math]::Round($r.qt * $r.preco, 2)
    Write-Info "$($r.date) | $($r.odo) km | $($r.qt)$un x R$ $($r.preco) [$($r.tipo)] = R$ $total"
    Start-Sleep -Milliseconds 200
}
Write-OK "20 abastecimentos/recargas criados -- Compass 4xe"

# ---- ETAPA 9: Eventos Toyota Corolla XEi ------------------------------------
Write-Step "Eventos -- Toyota Corolla XEi..."

$corollaEvents = @(
    @{ type="TAX";         date="2025-01-20"; amount=1243.50; odo=$null; title="IPVA 2025 - Toyota Corolla";            desc="IPVA 2025 parcela unica com desconto. RENAVAM: 000123456. Estado SP." },
    @{ type="INSURANCE";   date="2025-02-10"; amount=3850.00; odo=$null; title="Seguro Auto 2025 - Porto Seguro";       desc="Cobertura compreensiva + terceiros + roubo/furto. Franquia: R$ 3.500. Vigencia: fev/2025-fev/2026. Apolice: 7821045." },
    @{ type="OIL_CHANGE";  date="2025-03-22"; amount=285.00;  odo=19500; title="Troca de Oleo + Filtro";                desc="Oleo Mobil 1 5W30 Full Synthetic 4L + filtro de oleo Mahle. Toyota Center SP." },
    @{ type="MAINTENANCE"; date="2025-05-15"; amount=180.00;  odo=21500; title="Alinhamento e Balanceamento";           desc="Geometria de 4 rodas e balanceamento. Pneus Michelin Primacy 4 205/55R16." },
    @{ type="CAR_WASH";    date="2025-07-20"; amount=350.00;  odo=$null; title="Higienizacao Completa + Polimento";     desc="Lavagem interna/externa, polimento tecnico e aplicacao de cera de carnauba. 6h de servico." },
    @{ type="MAINTENANCE"; date="2025-10-18"; amount=520.00;  odo=25800; title="Revisao 25.000 km - Toyota";           desc="Velas NGK Iridium, correia aux., fluido de freio DOT4, revisao do freio ABS. Concessionaria oficial." },
    @{ type="OIL_CHANGE";  date="2026-01-12"; amount=295.00;  odo=28500; title="Troca de Oleo + Filtros";              desc="Oleo Castrol Edge 5W30 Full Synthetic 5L, filtro de oleo e filtro de ar. Intervalo: 15.000 km." },
    @{ type="TAX";         date="2026-01-25"; amount=1312.00; odo=$null; title="IPVA 2026 - Toyota Corolla";            desc="IPVA 2026 pago a vista com 3% de desconto. Base de calculo: R$ 87.500." },
    @{ type="TIRES";       date="2026-02-20"; amount=2080.00; odo=29200; title="Troca dos 4 Pneus - Michelin Primacy 4"; desc="4x Michelin Primacy 4 205/55R16 91V. Montagem, balanceamento e alinhamento incluso. Garantia: 40.000 km." },
    @{ type="INSURANCE";   date="2026-02-15"; amount=3970.00; odo=$null; title="Renovacao Seguro 2026 - Porto Seguro";  desc="Renovacao apolice. Cobertura compreensiva + terceiros + RCF-V R$ 200k. Vigencia: fev/2026-fev/2027." },
    @{ type="DOCUMENTS";   date="2026-03-02"; amount=87.00;   odo=$null; title="CRLV-e 2026";                          desc="Taxa de licenciamento anual e emissao do CRLV-e digital. Vistoria realizada. DeTran SP." },
    @{ type="CAR_WASH";    date="2026-06-15"; amount=85.00;   odo=$null; title="Lavagem Completa";                     desc="Lavagem externa + interna com aspiracao e aromatizante. Auto Spa Santos Dumont." }
)
foreach ($ev in $corollaEvents) {
    $body = @{
        vehicleId   = $vCorolla.id
        type        = $ev.type
        amount      = $ev.amount
        eventDate   = $ev.date
        description = (Make-Desc $ev.title $ev.desc)
    }
    if ($null -ne $ev.odo) { $body["odometer"] = $ev.odo }
    Invoke-API -Method POST -Path "/vehicle-events" -Body $body | Out-Null
    Write-Info "$($ev.date) | $($ev.type) | R$ $($ev.amount)"
    Start-Sleep -Milliseconds 200
}
Write-OK "12 eventos criados -- Corolla XEi"

# ---- ETAPA 10: Eventos Honda CB 500F ----------------------------------------
Write-Step "Eventos -- Honda CB 500F..."

$cb500Events = @(
    @{ type="TAX";         date="2025-01-10"; amount=285.00;  odo=$null; title="IPVA 2025 - Honda CB 500F";     desc="IPVA 2025 pago a vista com desconto. Placa: MTR5A67. RENAVAM: 000234567." },
    @{ type="INSURANCE";   date="2025-03-15"; amount=1250.00; odo=$null; title="Seguro Moto 2025 - HDI Seguros"; desc="Cobertura compreensiva + terceiros. Franquia: R$ 1.800. Vigencia: mar/2025-mar/2026." },
    @{ type="OIL_CHANGE";  date="2025-09-01"; amount=145.00;  odo=8620;  title="Troca de Oleo";                 desc="Oleo Honda HP4 10W30 Semi-Sintetico 2L + filtro de oleo original Honda. Concessionaria Honda Moto SP." },
    @{ type="MAINTENANCE"; date="2025-10-20"; amount=380.00;  odo=9150;  title="Revisao 9.000 km - Honda";      desc="Regulagem de valvulas, verificacao de corrente, relacao de transmissao, ajuste de freios e embreagem." },
    @{ type="TIRES";       date="2025-12-10"; amount=520.00;  odo=9730;  title="Troca Pneu Traseiro - Pirelli"; desc="Pirelli Diablo Rosso II 160/60ZR17 69W. Montagem e balanceamento incluso." },
    @{ type="TAX";         date="2026-01-05"; amount=298.00;  odo=$null; title="IPVA 2026 - Honda CB 500F";     desc="IPVA 2026 pago a vista. Base de calculo: R$ 19.840. Desconto 3%." },
    @{ type="OIL_CHANGE";  date="2026-02-10"; amount=155.00;  odo=10420; title="Troca de Oleo + Filtro";        desc="Oleo Motul 7100 10W40 Full Synthetic 2L + Hiflofiltro HF111. Moto Shop Liberdade." },
    @{ type="CAR_WASH";    date="2026-04-15"; amount=65.00;   odo=$null; title="Lavagem + Conservacao";         desc="Lavagem completa, limpeza de corrente e aplicacao de lubrificante Motul Chain Lube Road." }
)
foreach ($ev in $cb500Events) {
    $body = @{
        vehicleId   = $vCB500.id
        type        = $ev.type
        amount      = $ev.amount
        eventDate   = $ev.date
        description = (Make-Desc $ev.title $ev.desc)
    }
    if ($null -ne $ev.odo) { $body["odometer"] = $ev.odo }
    Invoke-API -Method POST -Path "/vehicle-events" -Body $body | Out-Null
    Write-Info "$($ev.date) | $($ev.type) | R$ $($ev.amount)"
    Start-Sleep -Milliseconds 200
}
Write-OK "8 eventos criados -- CB 500F"

# ---- ETAPA 11: Eventos BYD Atto 3 -------------------------------------------
Write-Step "Eventos -- BYD Atto 3..."

$bydEvents = @(
    @{ type="TAX";         date="2025-01-15"; amount=1620.00; odo=$null; title="IPVA 2025 - BYD Atto 3";                 desc="IPVA com isencao parcial eletrico SP. Base: R$ 180.000. Aliquota reduzida 1,5%. Desconto 3% a vista." },
    @{ type="INSURANCE";   date="2025-02-20"; amount=4200.00; odo=$null; title="Seguro Auto 2025 - Azul Seguros";        desc="Cobertura compreensiva + danos eletricos + carregador. Franquia: R$ 4.200. Vigencia: fev/2025-fev/2026." },
    @{ type="MAINTENANCE"; date="2025-10-05"; amount=320.00;  odo=3820;  title="Revisao 5.000 km - BYD";                 desc="Inspecao sistema eletrico e bateria de alta tensao, calibragem pneus, verificacao freio regenerativo e BMS." },
    @{ type="MAINTENANCE"; date="2026-01-22"; amount=450.00;  odo=5250;  title="Revisao 10.000 km + Inspecao Bateria";   desc="Diagnostico via BYD DiLink, verificacao do BMS, calibracao sensores ADAS e atualizacao firmware OTA." },
    @{ type="TAX";         date="2026-01-10"; amount=1680.00; odo=$null; title="IPVA 2026 - BYD Atto 3";                 desc="IPVA 2026 com desconto eletrico SP. Base de calculo: R$ 172.000. Pago a vista." },
    @{ type="INSURANCE";   date="2026-02-15"; amount=4350.00; odo=$null; title="Renovacao Seguro 2026 - Azul Seguros";   desc="Renovacao anual. Cobertura compreensiva, RCF-V R$ 300k, danos a bateria inclusos. Vigencia: fev/2026-fev/2027." },
    @{ type="TIRES";       date="2026-04-10"; amount=2400.00; odo=6510;  title="Troca dos 4 Pneus - Continental EcoContact 6"; desc="4x Continental EcoContact 6 235/50R18 101V. Baixa resistencia ao rolamento para VE pesado." },
    @{ type="CAR_WASH";    date="2026-05-15"; amount=280.00;  odo=$null; title="Higienizacao Premium + Vitrificacao";    desc="Lavagem interna/externa, descontaminacao de pintura, aplicacao de vitrificador ceramico SiO2 com garantia 12 meses." }
)
foreach ($ev in $bydEvents) {
    $body = @{
        vehicleId   = $vBYD.id
        type        = $ev.type
        amount      = $ev.amount
        eventDate   = $ev.date
        description = (Make-Desc $ev.title $ev.desc)
    }
    if ($null -ne $ev.odo) { $body["odometer"] = $ev.odo }
    Invoke-API -Method POST -Path "/vehicle-events" -Body $body | Out-Null
    Write-Info "$($ev.date) | $($ev.type) | R$ $($ev.amount)"
    Start-Sleep -Milliseconds 200
}
Write-OK "8 eventos criados -- BYD Atto 3"

# ---- ETAPA 12: Eventos Jeep Compass 4xe -------------------------------------
Write-Step "Eventos -- Jeep Compass 4xe..."

$compassEvents = @(
    @{ type="TAX";         date="2025-01-12"; amount=2150.00; odo=$null; title="IPVA 2025 - Jeep Compass 4xe";               desc="IPVA 2025 com reducao SP para PHEV. Base: R$ 215.000. Pago a vista com 3% desconto. RENAVAM: 000456789." },
    @{ type="INSURANCE";   date="2025-02-05"; amount=5600.00; odo=$null; title="Seguro Auto 2025 - Tokio Marine";             desc="Cobertura compreensiva + danos ao sistema de propulsao eletrica. Franquia: R$ 5.000. Vigencia: fev/2025-fev/2026." },
    @{ type="MAINTENANCE"; date="2025-09-20"; amount=780.00;  odo=11520; title="Revisao 10.000 km - Jeep";                    desc="Troca filtros (ar, oleo, combustivel, cabine), verificacao motor eletrico, transmissao e calibracao do sistema PHEV." },
    @{ type="OIL_CHANGE";  date="2025-11-15"; amount=320.00;  odo=13060; title="Troca de Oleo + Filtros";                    desc="Oleo Shell Helix Ultra 0W20 Full Synthetic 5L. Filtro de oleo e filtro de combustivel originais Jeep. Monaco Veiculos." },
    @{ type="DOCUMENTS";   date="2026-03-05"; amount=87.00;   odo=$null; title="CRLV-e 2026 + Vistoria";                     desc="Taxa de licenciamento e vistoria veicular aprovada. Emissao do CRLV-e digital. DeTran SP." },
    @{ type="TAX";         date="2026-01-15"; amount=2280.00; odo=$null; title="IPVA 2026 - Jeep Compass 4xe";               desc="IPVA 2026 isencao parcial PHEV SP. Base: R$ 199.900. Pago a vista. RENAVAM: 000456789." },
    @{ type="INSURANCE";   date="2026-02-10"; amount=5750.00; odo=$null; title="Renovacao Seguro 2026 - Tokio Marine";       desc="Renovacao. Coberturas: compreensivo, RCF-V R$ 500k, protecao da bateria de alta tensao. Vigencia: fev/2026-fev/2027." },
    @{ type="MAINTENANCE"; date="2026-03-25"; amount=950.00;  odo=15250; title="Revisao 15.000 km + Inspecao Eletrica";      desc="Revisao completa + inspecao bateria 11,4 kWh via OBD-II, verificacao motor eletrico 60 hp, calibracao carregador AC 7,2 kW." },
    @{ type="TIRES";       date="2026-04-20"; amount=3200.00; odo=15420; title="Troca dos 4 Pneus - Goodyear Eagle F1 Asymmetric 5"; desc="4x Goodyear Eagle F1 Asymmetric 5 SUV 235/55R18 100V. Montagem, balanceamento dinamico e alinhamento 4 rodas." }
)
foreach ($ev in $compassEvents) {
    $body = @{
        vehicleId   = $vCompass.id
        type        = $ev.type
        amount      = $ev.amount
        eventDate   = $ev.date
        description = (Make-Desc $ev.title $ev.desc)
    }
    if ($null -ne $ev.odo) { $body["odometer"] = $ev.odo }
    Invoke-API -Method POST -Path "/vehicle-events" -Body $body | Out-Null
    Write-Info "$($ev.date) | $($ev.type) | R$ $($ev.amount)"
    Start-Sleep -Milliseconds 200
}
Write-OK "9 eventos criados -- Compass 4xe"

# ---- Resumo ------------------------------------------------------------------
Write-Host ""
Write-Host "====================================================" -ForegroundColor Green
Write-Host " SEED CONCLUIDO COM SUCESSO!" -ForegroundColor Green
Write-Host "====================================================" -ForegroundColor Green
Write-Host ""
Write-Host " Conta demo:" -ForegroundColor Green
Write-Host "   Nome  : Carlos Eduardo Mendes" -ForegroundColor White
Write-Host "   Email : $($ACCOUNT.email)" -ForegroundColor White
Write-Host "   Senha : $($ACCOUNT.password)" -ForegroundColor White
Write-Host ""
Write-Host " Veiculos:" -ForegroundColor Green
Write-Host "   [id=$($vCorolla.id)] Toyota Corolla XEi 2.0 Flex 2022 (COMBUSTION)" -ForegroundColor White
Write-Host "          12 abastecimentos (mix gasolina/etanol) + 12 eventos" -ForegroundColor DarkGray
Write-Host "   [id=$($vCB500.id)] Honda CB 500F 2020 (COMBUSTION/Moto)" -ForegroundColor White
Write-Host "          12 abastecimentos (gasolina) + 8 eventos" -ForegroundColor DarkGray
Write-Host "   [id=$($vBYD.id)] BYD Atto 3 Extended Range 2023 (ELECTRIC)" -ForegroundColor White
Write-Host "          12 recargas (rede publica + residencial) + 8 eventos" -ForegroundColor DarkGray
Write-Host "   [id=$($vCompass.id)] Jeep Compass 4xe 2023 (HYBRID)" -ForegroundColor White
Write-Host "          20 abastecimentos (FUEL + ELECTRIC) + 9 eventos" -ForegroundColor DarkGray
Write-Host ""
Write-Host " Veiculo ativo: Toyota Corolla XEi" -ForegroundColor Green
Write-Host "====================================================" -ForegroundColor Green
Write-Host ""
