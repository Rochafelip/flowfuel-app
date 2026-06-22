package com.flowfuel.app.core.designsystem.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.flowfuel.app.core.designsystem.components.FFBottomBar
import com.flowfuel.app.core.designsystem.components.FFBottomItem
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFCard
import com.flowfuel.app.core.designsystem.components.FFChip
import com.flowfuel.app.core.designsystem.components.FFDateGroupHeader
import com.flowfuel.app.core.designsystem.components.FFEmptyState
import com.flowfuel.app.core.designsystem.components.FFErrorState
import com.flowfuel.app.core.designsystem.components.FFFab
import com.flowfuel.app.core.designsystem.components.FFNumberField
import com.flowfuel.app.core.designsystem.components.FFQuickAction
import com.flowfuel.app.core.designsystem.components.FFRefuelListItem
import com.flowfuel.app.core.designsystem.components.FFSectionHeader
import com.flowfuel.app.core.designsystem.components.FFSkeletonList
import com.flowfuel.app.core.designsystem.components.FFStatTile
import com.flowfuel.app.core.designsystem.components.FFTextField
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.components.FFTrend
import com.flowfuel.app.core.designsystem.components.FFTrendBadge
import com.flowfuel.app.core.designsystem.components.FFVehicleCard
import com.flowfuel.app.core.designsystem.theme.FFTheme

@Composable
fun UiKitDemoScreen() {
    var email by remember { mutableStateOf("") }
    var liters by remember { mutableStateOf("") }
    Scaffold(
        topBar = { FFTopBar(title = "UI Kit", onBack = {}) },
        bottomBar = {
            FFBottomBar(
                items = listOf(
                    FFBottomItem("home", "Início", Icons.Filled.Home),
                    FFBottomItem("history", "Histórico", Icons.Outlined.History, badgeCount = 3),
                    FFBottomItem("vehicles", "Veículos", Icons.Outlined.DirectionsCar),
                    FFBottomItem("profile", "Perfil", Icons.Filled.Person),
                ),
                currentRoute = "home",
                onSelect = {},
            )
        },
        floatingActionButton = {
            FFFab(icon = Icons.Default.Add, contentDescription = "Adicionar", onClick = {}, text = "Abastecer")
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(FFTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
        ) {
            Text("Buttons", style = MaterialTheme.typography.titleMedium)
            FFButton("Primary", onClick = {})
            FFButton("Secondary", onClick = {}, variant = FFButtonVariant.Secondary)
            FFButton("Tonal", onClick = {}, variant = FFButtonVariant.Tonal)
            FFButton("Text", onClick = {}, variant = FFButtonVariant.Text)
            FFButton("Destructive", onClick = {}, variant = FFButtonVariant.Destructive)
            FFButton("Loading", onClick = {}, loading = true)

            Text("Fields", style = MaterialTheme.typography.titleMedium)
            FFTextField(value = email, onValueChange = { email = it }, label = "E-mail", leadingIcon = Icons.Default.Email)
            FFTextField(value = "", onValueChange = {}, label = "Senha", isPassword = true)
            FFTextField(value = "abc", onValueChange = {}, label = "Com erro", errorText = "Inválido")
            FFNumberField(value = liters, onValueChange = { liters = it }, label = "Litros")

            Text("Chips", style = MaterialTheme.typography.titleMedium)
            FFChip("Mês", onClick = {}, selected = true)
            FFChip("Posto", onClick = {})

            Text("Cards", style = MaterialTheme.typography.titleMedium)
            FFCard(title = "Card") { Text("Conteúdo de exemplo") }
            FFStatTile(label = "Consumo médio", value = "12,4", unit = "km/L", trend = FFTrend.Up, deltaText = "+0,8 vs mês passado")
            FFVehicleCard(nickname = "Civic", plate = "ABC-1D23", odometerKm = "85.420", isActive = true)
            FFRefuelListItem(date = "20/05 09:34", stationName = "Shell Avenida", liters = "32,5 L", totalCost = "R$ 195,00", fuelType = "Gasolina aditivada")

            Text("Skeleton", style = MaterialTheme.typography.titleMedium)
            FFSkeletonList(itemCount = 2)

            Text("Section & Date Headers", style = MaterialTheme.typography.titleMedium)
            FFSectionHeader(title = "Histórico", actionLabel = "Ver tudo", onActionClick = {})
            FFSectionHeader(title = "Eventos")
            FFDateGroupHeader(label = "Hoje")
            FFDateGroupHeader(label = "Junho 2026")

            Text("Trend Badge", style = MaterialTheme.typography.titleMedium)
            FFTrendBadge(trend = FFTrend.Up, label = "+12%")
            FFTrendBadge(trend = FFTrend.Down, label = "-8%")
            FFTrendBadge(trend = FFTrend.Flat, label = "0%")
            FFTrendBadge(trend = FFTrend.Up, label = "+5%", positiveIsGood = false)

            Text("Quick Actions", style = MaterialTheme.typography.titleMedium)
            FFQuickAction(label = "Abastecer", icon = Icons.Default.Add, onClick = {})
            FFQuickAction(label = "Histórico", icon = Icons.Outlined.History, onClick = {})

            Text("Empty / Error", style = MaterialTheme.typography.titleMedium)
            FFEmptyState(title = "Sem refuels", description = "Adicione seu primeiro abastecimento.", actionText = "Adicionar", onAction = {})
            FFErrorState(message = "Sem conexão. Verifique sua internet.", onRetry = {})
        }
    }
}

@FFThemePreviews
@Composable
private fun UiKitDemoPreview() {
    FFPreviewBox { UiKitDemoScreen() }
}
