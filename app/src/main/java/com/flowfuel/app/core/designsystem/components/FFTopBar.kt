package com.flowfuel.app.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class FFTopBarVariant { Small, Centered, Large }

@Composable
fun FFTopBar(
    title: String,
    modifier: Modifier = Modifier,
    variant: FFTopBarVariant = FFTopBarVariant.Small,
    onBack: (() -> Unit)? = null,
    /** Quando fornecido, torna o título clicável com ícone de seta (seletor de veículo). */
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val nav: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
        }
    }
    val titleSlot: @Composable () -> Unit = {
        if (onTitleClick != null) {
            Row(
                modifier = Modifier
                    .clickable(
                        onClick = onTitleClick,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    )
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Trocar veículo",
                    modifier = Modifier.size(20.dp),
                )
                Text(title)
            }
        } else {
            Text(title)
        }
    }

    when (variant) {
        FFTopBarVariant.Small -> TopAppBar(
            title = titleSlot, navigationIcon = nav, actions = actions,
            scrollBehavior = scrollBehavior, modifier = modifier,
            colors = TopAppBarDefaults.topAppBarColors()
        )
        FFTopBarVariant.Centered -> CenterAlignedTopAppBar(
            title = titleSlot, navigationIcon = nav, actions = actions,
            scrollBehavior = scrollBehavior, modifier = modifier
        )
        FFTopBarVariant.Large -> LargeTopAppBar(
            title = titleSlot, navigationIcon = nav, actions = actions,
            scrollBehavior = scrollBehavior, modifier = modifier
        )
    }
}
