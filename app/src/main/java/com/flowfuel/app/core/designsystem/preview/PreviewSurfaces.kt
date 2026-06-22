package com.flowfuel.app.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.designsystem.theme.FlowFuelTheme

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class FFThemePreviews

@Composable
fun FFPreviewBox(content: @Composable () -> Unit) {
    FlowFuelTheme {
        Surface { androidx.compose.foundation.layout.Box(Modifier.padding(FFTheme.spacing.md)) { content() } }
    }
}
