package com.flowfuel.app.core.designsystem.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flowfuel.app.R
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.core.media.CropMath
import com.flowfuel.app.core.media.ImagePickerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CropViewportSize = 280.dp

/**
 * Dialog em tela cheia para ajustar posição/zoom de [uri] dentro de um viewport
 * circular antes de confirmar o envio. [onConfirm] recebe o Uri de um arquivo
 * JPEG já recortado (quadrado, orientação EXIF corrigida) em cache.
 */
@Composable
fun PhotoCropDialog(
    uri: Uri,
    onConfirm: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val imagePickerHelper = remember(context) { ImagePickerHelper(context.applicationContext) }
    val viewportSizePx = with(density) { CropViewportSize.toPx() }.toInt()

    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { imagePickerHelper.loadForCropping(uri) }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var minScale by remember { mutableFloatStateOf(1f) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(bitmap) {
        val bmp = bitmap ?: return@LaunchedEffect
        minScale = CropMath.minScale(bmp.width, bmp.height, viewportSizePx)
        scale = minScale
        offset = Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FFTheme.spacing.xs, vertical = FFTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !isSaving) {
                    Text(stringResource(R.string.action_cancel), color = Color.White)
                }
                Text(
                    text = stringResource(R.string.photo_crop_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = bitmap != null && !isSaving,
                    onClick = {
                        val bmp = bitmap ?: return@TextButton
                        isSaving = true
                        val cropRect = CropMath.computeCropRect(
                            bitmapWidthPx = bmp.width,
                            bitmapHeightPx = bmp.height,
                            viewportSizePx = viewportSizePx,
                            scale = scale,
                            offsetX = offset.x,
                            offsetY = offset.y,
                        )
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                imagePickerHelper.cropToCache(bmp, cropRect)
                            }
                            isSaving = false
                            onConfirm(result)
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_confirm), color = Color.White)
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = bitmap
                if (bmp == null || isSaving) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Box(
                        modifier = Modifier
                            .size(CropViewportSize)
                            .clip(CircleShape)
                            .pointerInput(bmp) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(minScale, minScale * 3f)
                                    val (clampedX, clampedY) = CropMath.clampOffset(
                                        bitmapWidthPx = bmp.width,
                                        bitmapHeightPx = bmp.height,
                                        viewportSizePx = viewportSizePx,
                                        scale = newScale,
                                        offsetX = offset.x + pan.x,
                                        offsetY = offset.y + pan.y,
                                    )
                                    scale = newScale
                                    offset = Offset(clampedX, clampedY)
                                }
                            },
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.None,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offset.x
                                    translationY = offset.y
                                },
                        )
                    }
                }
            }
        }
    }
}
