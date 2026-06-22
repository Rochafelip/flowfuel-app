package com.flowfuel.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flowfuel.app.R
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.preview.FFPreviewBox
import com.flowfuel.app.core.designsystem.preview.FFThemePreviews
import com.flowfuel.app.core.designsystem.theme.FFTheme
import kotlinx.coroutines.launch

private data class OnbPage(val titleRes: Int, val descRes: Int, val icon: ImageVector)

private val pages = listOf(
    OnbPage(R.string.onb_title_1, R.string.onb_desc_1, Icons.Default.Dashboard),
    OnbPage(R.string.onb_title_2, R.string.onb_desc_2, Icons.Default.History),
    OnbPage(R.string.onb_title_3, R.string.onb_desc_3, Icons.Default.Assignment),
)

@Composable
fun OnboardingScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    Scaffold { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(FFTheme.spacing.md),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!isLast) {
                    FFButton(
                        stringResource(R.string.onb_skip),
                        onClick = onNavigateToLogin,
                        variant = FFButtonVariant.Text,
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { i ->
                val page = pages[i]
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = FFTheme.spacing.xl),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(128.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Icon(
                                page.icon,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.size(FFTheme.spacing.xl))
                    Text(
                        stringResource(page.titleRes),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.size(FFTheme.spacing.md))
                    Text(
                        stringResource(page.descRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(FFTheme.spacing.md),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(pages.size) { i ->
                    val selected = pagerState.currentPage == i
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (selected) 12.dp else 8.dp)
                            .clip(CircleShape),
                    ) {
                        Surface(
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.fillMaxSize(),
                        ) {}
                    }
                }
            }
            if (isLast) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = FFTheme.spacing.lg,
                        vertical = FFTheme.spacing.md,
                    ),
                    verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                ) {
                    FFButton(
                        text = stringResource(R.string.onb_create_account),
                        onClick = onNavigateToRegister,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FFButton(
                        text = stringResource(R.string.onb_sign_in),
                        onClick = onNavigateToLogin,
                        variant = FFButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                FFButton(
                    text = stringResource(R.string.action_continue),
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FFTheme.spacing.lg, vertical = FFTheme.spacing.md),
                )
            }
        }
    }
}

@FFThemePreviews
@Composable
private fun OnboardingPreview() {
    FFPreviewBox { OnboardingScreen(onNavigateToLogin = {}, onNavigateToRegister = {}) }
}
