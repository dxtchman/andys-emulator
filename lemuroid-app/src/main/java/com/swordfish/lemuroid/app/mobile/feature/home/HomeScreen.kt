package com.swordfish.lemuroid.app.mobile.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.common.displayDetailsSettingsScreen
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onOpenCoreSelection: () -> Unit,
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                viewModel.updatePermissions(applicationContext)
            }
            else -> { }
        }
    }

    val permissionsLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted: Boolean ->
            if (!isGranted) {
                context.displayDetailsSettingsScreen()
            }
        }

    val state = viewModel.getViewStates().collectAsState(HomeViewModel.UIState())
    HomeScreen(
        modifier,
        state.value,
        onGameClick,
        onGameLongClick,
        onOpenCoreSelection,
        {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return@HomeScreen
            }

            permissionsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        { permissionsLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        { viewModel.changeLocalStorageFolder(context) },
    )
}

private fun systemShortName(
    context: android.content.Context,
    systemId: String,
): String = runCatching { context.getString(GameSystem.findById(systemId).shortTitleResId) }.getOrDefault(systemId)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    state: HomeViewModel.UIState,
    onGameClicked: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onOpenCoreSelection: () -> Unit,
    onEnableNotificationsClicked: () -> Unit,
    onEnableMicrophoneClicked: () -> Unit,
    onSetDirectoryClicked: () -> Unit,
) {
    val context = LocalContext.current
    var systemFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val systems = remember(state.allGames) { state.allGames.map { it.systemId }.distinct().sortedBy { systemShortName(context, it) } }
    val shownGames =
        remember(state.allGames, systemFilter) {
            if (systemFilter == null) state.allGames else state.allGames.filter { it.systemId == systemFilter }
        }
    val hero = state.continueGame
    val recents = state.recentPlayed.filter { it.id != hero?.id }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        fullWidth {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(state.showNoNotificationPermissionCard) {
                    HomeNotification(
                        titleId = R.string.home_notification_title,
                        messageId = R.string.home_notification_message,
                        actionId = R.string.home_notification_action,
                        onAction = onEnableNotificationsClicked,
                    )
                }
                AnimatedVisibility(state.showNoGamesCard) {
                    HomeNotification(
                        titleId = R.string.home_empty_title,
                        messageId = R.string.home_empty_message,
                        actionId = R.string.home_empty_action,
                        onAction = onSetDirectoryClicked,
                        enabled = !state.indexInProgress,
                    )
                }
                AnimatedVisibility(state.showNoMicrophonePermissionCard) {
                    HomeNotification(
                        titleId = R.string.home_microphone_title,
                        messageId = R.string.home_microphone_message,
                        actionId = R.string.home_microphone_action,
                        onAction = onEnableMicrophoneClicked,
                    )
                }
                AnimatedVisibility(state.showDesmumeDeprecatedCard) {
                    HomeNotification(
                        titleId = R.string.home_notification_desmume_deprecated_title,
                        messageId = R.string.home_notification_desmume_deprecated_message,
                        actionId = R.string.home_notification_desmume_deprecated_action,
                        onAction = onOpenCoreSelection,
                    )
                }
            }
        }

        if (hero != null) {
            fullWidth {
                HeroCard(hero, systemShortName(context, hero.systemId), onPlay = { onGameClicked(hero) }, onLongClick = { onGameLongClick(hero) })
            }
        }

        if (recents.isNotEmpty()) {
            fullWidth { ShelfRow("Recently played", recents, onGameClicked, onGameLongClick) }
        }
        if (state.favoritesGames.isNotEmpty()) {
            fullWidth { ShelfRow(stringResource(id = R.string.favorites), state.favoritesGames, onGameClicked, onGameLongClick) }
        }

        if (state.allGames.isNotEmpty()) {
            fullWidth {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "Library",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${shownGames.size} games",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = systemFilter == null,
                                onClick = { systemFilter = null },
                                label = { Text("All") },
                            )
                        }
                        items(systems) { id ->
                            FilterChip(
                                selected = systemFilter == id,
                                onClick = { systemFilter = if (systemFilter == id) null else id },
                                label = { Text(systemShortName(context, id)) },
                            )
                        }
                    }
                }
            }
            items(shownGames, key = { it.id }) { game ->
                CoverTile(game, systemShortName(context, game.systemId), onGameClicked, onGameLongClick)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** Game cover that never looks stretched: the art is fitted on top of a blurred copy of itself. */
@Composable
fun GameCover(
    game: Game,
    modifier: Modifier = Modifier,
) {
    val fallback = remember(game) { CoverUtils.getFallbackDrawable(game) }
    val fallbackPainter = rememberDrawablePainter(drawable = fallback)
    val request =
        ImageRequest.Builder(LocalContext.current)
            .data(game.displayCoverUrl())
            .crossfade(true)
            .build()
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(18.dp),
            alpha = 0.55f,
        )
        AsyncImage(
            model = request,
            contentDescription = game.displayName(),
            contentScale = ContentScale.Fit,
            fallback = fallbackPainter,
            error = fallbackPainter,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverTile(
    game: Game,
    systemName: String,
    onClick: (Game) -> Unit,
    onLongClick: (Game) -> Unit,
) {
    Column(
        Modifier.combinedClickable(onClick = { onClick(game) }, onLongClick = { onLongClick(game) }),
    ) {
        GameCover(
            game,
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(MaterialTheme.shapes.medium),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            game.displayName(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            systemName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCard(
    game: Game,
    systemName: String,
    onPlay: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onPlay, onLongClick = onLongClick),
    ) {
        AsyncImage(
            model = game.displayCoverUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(24.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(colors.background.copy(alpha = 0.92f), colors.background.copy(alpha = 0.45f)),
                    ),
                ),
        )
        Row(
            Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameCover(
                game,
                Modifier
                    .width(118.dp)
                    .aspectRatio(0.72f)
                    .clip(MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "CONTINUE PLAYING",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    game.displayName(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.onBackground,
                )
                Text(systemName, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onPlay) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfRow(
    title: String,
    games: List<Game>,
    onGameClicked: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(games, key = { it.id }) { game ->
                Column(
                    Modifier
                        .width(104.dp)
                        .combinedClickable(onClick = { onGameClicked(game) }, onLongClick = { onGameLongClick(game) }),
                ) {
                    GameCover(
                        game,
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .clip(MaterialTheme.shapes.medium),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        game.displayName(),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeNotification(
    titleId: Int,
    messageId: Int,
    actionId: Int,
    enabled: Boolean = true,
    onAction: () -> Unit = { },
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(titleId),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(messageId),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                modifier = Modifier.align(Alignment.End),
                onClick = onAction,
                enabled = enabled,
            ) {
                Text(stringResource(id = actionId))
            }
        }
    }
}
