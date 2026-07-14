package com.flowfuel.app.feature.auth.presentation.profile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoLibrary
import com.flowfuel.app.core.designsystem.components.FFDialog
import com.flowfuel.app.core.designsystem.components.FFDialogKind
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flowfuel.app.core.common.DateFormatter
import com.flowfuel.app.core.designsystem.components.FFBottomSheet
import com.flowfuel.app.core.designsystem.components.FFButton
import com.flowfuel.app.core.designsystem.components.FFButtonVariant
import com.flowfuel.app.core.designsystem.components.FFSkeletonBlock
import com.flowfuel.app.core.designsystem.components.FFSkeletonCircle
import com.flowfuel.app.core.designsystem.components.FFSkeletonLine
import com.flowfuel.app.core.designsystem.components.FFSnackbarHost
import com.flowfuel.app.core.designsystem.components.FFSnackbarKind
import com.flowfuel.app.core.designsystem.components.FFSnackbarVisuals
import com.flowfuel.app.core.designsystem.components.FFTopBar
import com.flowfuel.app.core.designsystem.components.FFTopBarVariant
import com.flowfuel.app.core.designsystem.components.PhotoCropDialog
import com.flowfuel.app.core.designsystem.components.UserAvatar
import com.flowfuel.app.core.designsystem.theme.FFTheme
import com.flowfuel.app.feature.auth.domain.model.UserProfile
import com.flowfuel.app.feature.auth.domain.usecase.ProfileStats
import kotlinx.coroutines.flow.collectLatest

// ─── Tela de perfil ────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToChangePassword: () -> Unit = {},
    onNavigateToVehicles: () -> Unit = {},
    passwordChanged: Boolean = false,
    onPasswordChangedConsumed: () -> Unit = {},
    profileUpdated: Boolean = false,
    onProfileUpdatedConsumed: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                ProfileEffect.NavigateToLogin          -> onNavigateToLogin()
                ProfileEffect.NavigateToEditProfile    -> onNavigateToEditProfile()
                ProfileEffect.NavigateToChangePassword -> onNavigateToChangePassword()
                ProfileEffect.NavigateToVehicles       -> onNavigateToVehicles()
                ProfileEffect.ShowUploadError          -> snackbarHostState.showSnackbar(
                    FFSnackbarVisuals("Não foi possível enviar a foto", FFSnackbarKind.Error)
                )
                ProfileEffect.ShowDeleteError          -> snackbarHostState.showSnackbar(
                    FFSnackbarVisuals("Não foi possível remover a foto", FFSnackbarKind.Error)
                )
            }
        }
    }

    LaunchedEffect(passwordChanged) {
        if (passwordChanged) {
            snackbarHostState.showSnackbar(
                FFSnackbarVisuals("Senha alterada com sucesso", FFSnackbarKind.Success)
            )
            onPasswordChangedConsumed()
        }
    }

    LaunchedEffect(profileUpdated) {
        if (profileUpdated) {
            viewModel.load()
            onProfileUpdatedConsumed()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            FFTopBar(
                title   = "Perfil",
                variant = FFTopBarVariant.Small,
            )
        },
        snackbarHost = { FFSnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val s = state) {

                // ── Carregando ────────────────────────────────────────────────
                ProfileUiState.Loading -> ProfileLoadingSkeleton()

                // ── Erro ──────────────────────────────────────────────────────
                is ProfileUiState.Error -> ErrorContent(
                    message  = s.message,
                    onRetry  = viewModel::load,
                    modifier = Modifier.align(Alignment.Center),
                )

                // ── Conteúdo ──────────────────────────────────────────────────
                is ProfileUiState.Content -> ProfileContent(
                    profile               = s.profile,
                    stats                 = s.stats,
                    isLoggingOut          = s.isLoggingOut,
                    isUploadingPhoto      = s.isUploadingPhoto,
                    isDeletingPhoto       = s.isDeletingPhoto,
                    isDeletingAccount     = s.isDeletingAccount,
                    onPickImage           = viewModel::onPickImage,
                    onDeletePicture       = viewModel::onDeletePicture,
                    onEditProfile         = viewModel::onEditProfile,
                    onChangePassword      = viewModel::onChangePassword,
                    onManageVehicles      = viewModel::onManageVehicles,
                    onLogoutRequest       = { showLogoutDialog = true },
                    onDeleteAccountRequest = viewModel::onShowDeleteDialog,
                )
            }
        }
    }

    if (showLogoutDialog) {
        FFDialog(
            title       = "Sair da conta",
            message     = "Tem certeza que deseja sair?",
            confirmText = "Sair",
            onConfirm   = { showLogoutDialog = false; viewModel.logout() },
            onDismiss   = { showLogoutDialog = false },
        )
    }

    val content = state as? ProfileUiState.Content
    if (content?.showDeleteDialog == true) {
        DeleteAccountDialog(
            onConfirm = viewModel::onDeleteAccountConfirmed,
            onDismiss = viewModel::onDismissDeleteDialog,
        )
    }
}

// ─── Conteúdo principal ────────────────────────────────────────────────────────

@Composable
private fun ProfileContent(
    profile: UserProfile,
    stats: ProfileStats?,
    isLoggingOut: Boolean,
    isUploadingPhoto: Boolean,
    isDeletingPhoto: Boolean,
    isDeletingAccount: Boolean,
    onPickImage: (Uri) -> Unit,
    onDeletePicture: () -> Unit,
    onEditProfile: () -> Unit,
    onChangePassword: () -> Unit,
    onManageVehicles: () -> Unit,
    onLogoutRequest: () -> Unit,
    onDeleteAccountRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAvatarSheet by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    val isBusy = isUploadingPhoto || isDeletingPhoto
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { pendingCropUri = it } }

    // Não usa rememberScrollState (saveable): a posição de rolagem não deve
    // sobreviver a trocas de aba — cada visita ao Perfil começa do topo.
    val scrollState = remember { ScrollState(0) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = FFTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(FFTheme.spacing.xl))

        // ── Avatar ────────────────────────────────────────────────────────────
        ProfileAvatar(
            name              = profile.name,
            profilePictureUrl = profile.profilePictureUrl,
            isUploading       = isUploadingPhoto,
            onClick           = { if (!isBusy) showAvatarSheet = true },
        )

        Spacer(Modifier.height(4.dp))
        Text(
            text  = "Alterar foto",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        if (showAvatarSheet) {
            AvatarPickerSheet(
                hasPhoto  = profile.profilePictureUrl != null,
                onGallery = {
                    showAvatarSheet = false
                    launcher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemove  = {
                    showAvatarSheet = false
                    showDeleteDialog = true
                },
                onDismiss = { showAvatarSheet = false },
            )
        }

        if (showDeleteDialog) {
            FFDialog(
                title       = "Remover foto do perfil?",
                message     = "Sua foto de perfil será removida permanentemente.",
                confirmText = "Remover",
                onConfirm   = { showDeleteDialog = false; onDeletePicture() },
                onDismiss   = { showDeleteDialog = false },
                kind        = FFDialogKind.Destructive,
            )
        }

        pendingCropUri?.let { pickedUri ->
            PhotoCropDialog(
                uri = pickedUri,
                onConfirm = { cropped ->
                    pendingCropUri = null
                    onPickImage(cropped)
                },
                onDismiss = { pendingCropUri = null },
            )
        }

        Spacer(Modifier.height(FFTheme.spacing.md))

        // ── Nome ──────────────────────────────────────────────────────────────
        Text(
            text  = profile.name ?: profile.email,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(Modifier.height(FFTheme.spacing.xl))

        // ── Estatísticas de uso ───────────────────────────────────────────────
        StatsRow(stats = stats)

        Spacer(Modifier.height(FFTheme.spacing.xl))

        // ── Campos de informação ──────────────────────────────────────────────
        ProfileInfoSection(profile = profile)

        Spacer(Modifier.height(FFTheme.spacing.xl))

        // ── Ações ─────────────────────────────────────────────────────────────
        ProfileActionRow(
            icon    = Icons.Outlined.DirectionsCar,
            label   = "Meus veículos",
            onClick = onManageVehicles,
        )
        HorizontalDivider()
        ProfileActionRow(
            icon    = Icons.Outlined.Edit,
            label   = "Editar perfil",
            onClick = onEditProfile,
        )
        HorizontalDivider()
        ProfileActionRow(
            icon    = Icons.Outlined.Lock,
            label   = "Trocar senha",
            onClick = onChangePassword,
        )
        HorizontalDivider()
        NotificationStatusRow()
        HorizontalDivider()

        Spacer(Modifier.height(FFTheme.spacing.xl))

        FFButton(
            text        = if (isLoggingOut) "Saindo…" else "Sair",
            onClick     = onLogoutRequest,
            enabled     = !isLoggingOut,
            modifier    = Modifier.fillMaxWidth(),
            leadingIcon = Icons.Outlined.Logout,
        )

        Spacer(Modifier.height(FFTheme.spacing.xl))

        DangerZone(
            isDeletingAccount    = isDeletingAccount,
            isLoggingOut         = isLoggingOut,
            onDeleteAccountRequest = onDeleteAccountRequest,
        )

        Spacer(Modifier.height(FFTheme.spacing.xl))
    }
}

// ─── Zona de Perigo ────────────────────────────────────────────────────────────

@Composable
private fun DangerZone(
    isDeletingAccount: Boolean,
    isLoggingOut: Boolean,
    onDeleteAccountRequest: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color    = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
            )
            Text(
                text  = "  Zona de Perigo  ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color    = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
            )
        }

        Spacer(Modifier.height(FFTheme.spacing.md))

        FFButton(
            text     = if (isDeletingAccount) "Excluindo…" else "Excluir conta permanentemente",
            onClick  = onDeleteAccountRequest,
            enabled  = !isDeletingAccount && !isLoggingOut,
            variant  = FFButtonVariant.Destructive,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─── Avatar ────────────────────────────────────────────────────────────────────

@Composable
private fun ProfileAvatar(
    name: String?,
    profilePictureUrl: String?,
    isUploading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        UserAvatar(
            url     = profilePictureUrl,
            name    = name,
            size    = 96.dp,
            onClick = if (!isUploading) onClick else null,
        )

        // Upload overlay — shown only during photo upload
        if (isUploading) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(32.dp),
                    color       = Color.White,
                    strokeWidth = 3.dp,
                )
            }
        }
    }
}

// ─── Avatar picker bottom sheet ────────────────────────────────────────────────

@Composable
private fun AvatarPickerSheet(
    hasPhoto: Boolean,
    onGallery: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    FFBottomSheet(onDismiss = onDismiss) {
        Text(
            text     = "Foto de perfil",
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = FFTheme.spacing.sm),
        )
        Surface(onClick = onGallery, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier            = Modifier.padding(vertical = FFTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                verticalAlignment   = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Text("Escolher da galeria", style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (hasPhoto) {
            Surface(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier            = Modifier.padding(vertical = FFTheme.spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
                    verticalAlignment   = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text  = "Remover foto",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Spacer(Modifier.height(FFTheme.spacing.sm))
    }
}

// ─── Seção de informações ──────────────────────────────────────────────────────

@Composable
private fun ProfileInfoSection(profile: UserProfile) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
    ) {
        ProfileInfoField(label = "E-mail", value = profile.email)
        ProfileInfoField(
            label = "Telefone",
            value = profile.phone ?: "Não informado",
            valueColor = if (profile.phone == null)
                MaterialTheme.colorScheme.onSurfaceVariant
            else
                MaterialTheme.colorScheme.onSurface,
        )
        if (profile.createdAt != null) {
            ProfileInfoField(
                label = "Membro desde",
                value = DateFormatter.formatBr(profile.createdAt),
            )
        }
    }
}

@Composable
private fun ProfileInfoField(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text  = value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
        )
    }
}

// ─── Linha de ação ─────────────────────────────────────────────────────────────

@Composable
private fun ProfileActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    trailingText: String? = null,
) {
    Surface(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = FFTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FFTheme.spacing.sm),
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(
                text     = label,
                style    = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (trailingText != null) {
                Text(
                    text  = trailingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector        = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotificationStatusRow() {
    val context = LocalContext.current
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ProfileActionRow(
        icon         = Icons.Outlined.Notifications,
        label        = "Notificações",
        trailingText = if (notificationsEnabled) "Ativadas" else "Desativadas",
        onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            )
        },
    )
}

// ─── Estatísticas de uso ───────────────────────────────────────────────────────

@Composable
private fun StatsRow(
    stats: ProfileStats?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (stats == null) {
            repeat(3) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f).padding(horizontal = FFTheme.spacing.sm),
                ) {
                    FFSkeletonLine(height = 24.dp, widthFraction = 0.5f)
                    FFSkeletonLine(height = 12.dp, widthFraction = 0.8f)
                }
            }
        } else {
            StatItem(count = stats.vehiclesCount, label = "Veículos")
            StatItem(count = stats.refuelsCount, label = "Abastecimentos")
            StatItem(count = stats.eventsCount, label = "Eventos")
        }
    }
}

@Composable
private fun StatItem(count: Int, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text  = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text      = label,
            style     = MaterialTheme.typography.labelSmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ─── Skeleton de carregamento ──────────────────────────────────────────────────

@Composable
private fun ProfileLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier
            .fillMaxSize()
            .padding(horizontal = FFTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(FFTheme.spacing.xl))

        FFSkeletonCircle(size = 96.dp)

        Spacer(Modifier.height(4.dp))
        FFSkeletonLine(height = 12.dp, widthFraction = 0.25f)

        Spacer(Modifier.height(FFTheme.spacing.md))
        FFSkeletonLine(height = 22.dp, widthFraction = 0.45f)

        Spacer(Modifier.height(FFTheme.spacing.xl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            repeat(3) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f).padding(horizontal = FFTheme.spacing.sm),
                ) {
                    FFSkeletonLine(height = 24.dp, widthFraction = 0.5f)
                    FFSkeletonLine(height = 12.dp, widthFraction = 0.8f)
                }
            }
        }

        Spacer(Modifier.height(FFTheme.spacing.xl))

        repeat(3) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FFSkeletonLine(height = 12.dp, widthFraction = 0.25f)
                FFSkeletonLine(height = 18.dp, widthFraction = 0.6f)
            }
            Spacer(Modifier.height(FFTheme.spacing.sm))
        }

        Spacer(Modifier.height(FFTheme.spacing.xl))

        repeat(2) {
            FFSkeletonBlock(height = 52.dp)
            Spacer(Modifier.height(FFTheme.spacing.sm))
        }

        Spacer(Modifier.height(FFTheme.spacing.xl))

        FFSkeletonBlock(height = 48.dp)
    }
}

// ─── Estado de erro ────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier            = modifier.padding(FFTheme.spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FFTheme.spacing.md),
    ) {
        Text(
            text  = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FFButton(text = "Tentar novamente", onClick = onRetry)
    }
}

