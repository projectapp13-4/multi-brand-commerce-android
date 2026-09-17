@file:Suppress("FunctionNaming")

package com.gurbakir.mobile.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gurbakir.foundation.ui.LocalBrandSpacing
import com.gurbakir.storefront.HomeResourceKind

internal fun androidx.compose.foundation.lazy.LazyListScope.imageSection(
    section: HomeRenderedSection.Image,
    actions: HomeActions,
    playbackCoordinator: HomePlaybackCoordinator?
) {
    item(key = section.stableId) {
        val targetAction = section.target?.let { target -> actions.targetAction(target) }
        Column(
            modifier = Modifier.fillMaxWidth().testTag(HomeTestTags.image(section.stableId)),
            verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
        ) {
            HomeSectionHeading(section.title)
            val mediaModifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(
                        if (section.presentation == HomeImagePresentation.BANNER) {
                            HOME_BANNER_ASPECT_RATIO
                        } else {
                            HOME_PHOTO_ASPECT_RATIO
                        }
                    )
                    .then(
                        if (targetAction == null) {
                            Modifier
                        } else {
                            Modifier.clickable(role = Role.Button, onClick = targetAction)
                        }
                    )
            if (playbackCoordinator == null) {
                HomeMedia(
                    media = section.media,
                    fallbackDescription = section.altText,
                    modifier = mediaModifier,
                    contentScale = ContentScale.Crop,
                    shape = MaterialTheme.shapes.large
                )
            } else {
                HomeV2Image(
                    content = HomeV2ImageContent(section.media, section.altText, section.revisionKey),
                    coordinator = playbackCoordinator,
                    modifier = mediaModifier,
                    contentScale = ContentScale.Crop,
                    shape = MaterialTheme.shapes.large
                )
            }
            section.caption?.let { caption -> Text(caption, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

internal fun androidx.compose.foundation.lazy.LazyListScope.videoSection(
    section: HomeRenderedSection.Video,
    actions: HomeActions,
    playbackCoordinator: HomePlaybackCoordinator
) {
    item(key = section.stableId) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LocalBrandSpacing.current.compactDp.dp)
        ) {
            HomeSectionHeading(section.title)
            HomeVideoPlayer(section, playbackCoordinator)
            section.target?.let { target ->
                TextButton(onClick = actions.targetAction(target)) {
                    Text(target.handle)
                }
            }
        }
    }
}

private fun HomeActions.targetAction(target: RemoteHomeTarget): () -> Unit = when (target.key.kind) {
    HomeResourceKind.COLLECTION -> ({ openCollection(target.handle) })
    HomeResourceKind.PRODUCT -> ({ openProduct(target.handle) })
    HomeResourceKind.MEDIA_IMAGE, HomeResourceKind.VIDEO -> ({})
}

private const val HOME_BANNER_ASPECT_RATIO = 16f / 9f
private const val HOME_PHOTO_ASPECT_RATIO = 4f / 3f
