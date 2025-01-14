package it.vfsfitvnm.vimusic.ui.screens

import android.net.Uri
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.route.RouteHandler
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.R
import it.vfsfitvnm.vimusic.models.Playlist
import it.vfsfitvnm.vimusic.models.SongPlaylistMap
import it.vfsfitvnm.vimusic.transaction
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.TopAppBar
import it.vfsfitvnm.vimusic.ui.components.themed.*
import it.vfsfitvnm.vimusic.ui.styling.LocalColorPalette
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.enqueue
import it.vfsfitvnm.vimusic.utils.forcePlayAtIndex
import it.vfsfitvnm.vimusic.utils.relaunchableEffect
import it.vfsfitvnm.youtubemusic.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@ExperimentalAnimationApi
@Composable
fun IntentUriScreen(uri: Uri) {
    val albumRoute = rememberAlbumRoute()
    val artistRoute = rememberArtistRoute()

    val lazyListState = rememberLazyListState()

    RouteHandler(listenToGlobalEmitter = true) {
        albumRoute { browseId ->
            AlbumScreen(
                browseId = browseId ?: error("browseId cannot be null")
            )
        }

        artistRoute { browseId ->
            ArtistScreen(
                browseId = browseId ?: error("browseId cannot be null")
            )
        }

        host {
            val menuState = LocalMenuState.current
            val colorPalette = LocalColorPalette.current
            val density = LocalDensity.current
            val binder = LocalPlayerServiceBinder.current

            var itemsResult by remember(uri) {
                mutableStateOf<Result<List<YouTube.Item.Song>>?>(null)
            }

            val onLoad = relaunchableEffect(uri) {
                withContext(Dispatchers.IO) {
                    itemsResult = uri.getQueryParameter("list")?.let { playlistId ->
                        YouTube.queue(playlistId)?.map { songList ->
                            songList ?: emptyList()
                        }
                    } ?: uri.getQueryParameter("v")?.let { videoId ->
                        YouTube.song(videoId)?.map { song ->
                            song?.let { listOf(song) } ?: emptyList()
                        }
                    } ?: Result.failure(Error("Missing URL parameters"))
                }
            }

            var isImportingAsPlaylist by remember(uri) {
                mutableStateOf(false)
            }

            if (isImportingAsPlaylist) {
                TextFieldDialog(
                    hintText = "Enter the playlist name",
                    onDismiss = {
                        isImportingAsPlaylist = false
                    },
                    onDone = { text ->
                        menuState.hide()

                        transaction {
                            val playlistId = Database.insert(Playlist(name = text))

                            itemsResult
                                ?.getOrNull()
                                ?.map(YouTube.Item.Song::asMediaItem)
                                ?.forEachIndexed { index, mediaItem ->
                                    Database.insert(mediaItem)

                                    Database.insert(
                                        SongPlaylistMap(
                                            songId = mediaItem.mediaId,
                                            playlistId = playlistId,
                                            position = index
                                        )
                                    )
                                }
                        }
                    }
                )
            }

            LazyColumn(
                state = lazyListState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 64.dp),
                modifier = Modifier
                    .background(colorPalette.background)
                    .fillMaxSize()
            ) {
                item {
                    TopAppBar(
                        modifier = Modifier
                            .height(52.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.chevron_back),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette.text),
                            modifier = Modifier
                                .clickable(onClick = pop)
                                .padding(vertical = 8.dp)
                                .padding(horizontal = 16.dp)
                                .size(24.dp)
                        )

                        Image(
                            painter = painterResource(R.drawable.ellipsis_horizontal),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette.text),
                            modifier = Modifier
                                .clickable {
                                    menuState.display {
                                        Menu {
                                            MenuCloseButton(onClick = menuState::hide)

                                            MenuEntry(
                                                icon = R.drawable.time,
                                                text = "Enqueue",
                                                onClick = {
                                                    menuState.hide()

                                                    itemsResult
                                                        ?.getOrNull()
                                                        ?.map(YouTube.Item.Song::asMediaItem)
                                                        ?.let { mediaItems ->
                                                            binder?.player?.enqueue(
                                                                mediaItems
                                                            )
                                                        }
                                                }
                                            )

                                            MenuEntry(
                                                icon = R.drawable.list,
                                                text = "Import as playlist",
                                                onClick = {
                                                    isImportingAsPlaylist = true
                                                }
                                            )
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .size(24.dp)
                        )
                    }
                }


                itemsResult?.getOrNull()?.let { items ->
                    if (items.isEmpty()) {
                        item {
                            TextCard(icon = R.drawable.sad) {
                                Title(text = "No songs found")
                                Text(text = "Please try a different query or category.")
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = items,
                            contentType = { _, item -> item }
                        ) { index, item ->
                            SmallSongItem(
                                song = item,
                                thumbnailSizePx = density.run { 54.dp.roundToPx() },
                                onClick = {
                                    binder?.stopRadio()
                                    binder?.player?.forcePlayAtIndex(items.map(YouTube.Item.Song::asMediaItem), index)
                                }
                            )
                        }
                    }
                } ?: itemsResult?.exceptionOrNull()?.let { throwable ->
                   item {
                       LoadingOrError(
                           errorMessage = throwable.javaClass.canonicalName,
                           onRetry = onLoad
                       )
                   }
                } ?: item {
                    LoadingOrError()
                }
            }
        }
    }
}

@Composable
private fun LoadingOrError(
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null
) {
    LoadingOrError(
        errorMessage = errorMessage,
        onRetry = onRetry
    ) {
        repeat(5) { index ->
            SmallSongItemShimmer(
                thumbnailSizeDp = 54.dp,
                modifier = Modifier
                    .alpha(1f - index * 0.175f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 16.dp)
            )
        }
    }
}
