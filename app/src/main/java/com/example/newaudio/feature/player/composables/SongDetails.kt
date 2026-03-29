package com.example.newaudio.feature.player.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.newaudio.R
import com.example.newaudio.ui.theme.Dimens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongDetails(
    title: String?,
    artist: String?,
    useMarquee: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title ?: stringResource(R.string.no_song_selected),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = (if (useMarquee) Modifier.basicMarquee() else Modifier)
                .padding(vertical = Dimens.PaddingMedium)
        )
        Text(
            text = artist ?: stringResource(R.string.unknown_artist),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(vertical = Dimens.PaddingMedium)
        )
    }
}
