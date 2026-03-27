/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings.integrations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.EnableSoundCloudKey
import com.metrolist.music.constants.SoundCloudAccessTokenKey
import com.metrolist.music.constants.SoundCloudAvatarUrlKey
import com.metrolist.music.constants.SoundCloudRefreshTokenKey
import com.metrolist.music.constants.SoundCloudTokenExpiryKey
import com.metrolist.music.constants.SoundCloudUserIdKey
import com.metrolist.music.constants.SoundCloudUsernameKey
import com.metrolist.music.constants.UseSoundCloudHomeKey
import com.metrolist.music.constants.UseSoundCloudLibraryKey
import com.metrolist.music.constants.UseSoundCloudSearchKey
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.PreferenceEntry
import com.metrolist.music.ui.component.PreferenceGroupTitle
import com.metrolist.music.ui.component.SwitchPreference
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundCloudSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val (enableSoundCloud, onEnableSoundCloudChange) = rememberPreference(
        key = EnableSoundCloudKey,
        defaultValue = false,
    )
    val (useForSearch, onUseForSearchChange) = rememberPreference(
        key = UseSoundCloudSearchKey,
        defaultValue = false,
    )
    val (useForHome, onUseForHomeChange) = rememberPreference(
        key = UseSoundCloudHomeKey,
        defaultValue = false,
    )
    val (useForLibrary, onUseForLibraryChange) = rememberPreference(
        key = UseSoundCloudLibraryKey,
        defaultValue = false,
    )
    val (accessToken, _) = rememberPreference(SoundCloudAccessTokenKey, "")
    val (username, _) = rememberPreference(SoundCloudUsernameKey, "")
    val (avatarUrl, _) = rememberPreference(SoundCloudAvatarUrlKey, "")

    val isLoggedIn = remember(accessToken) { accessToken.isNotBlank() }
    val accountLabel = remember(isLoggedIn, username) {
        if (isLoggedIn) username.ifBlank { "SoundCloud" } else null
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            )
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
            ),
        )

        PreferenceGroupTitle(title = stringResource(R.string.account))

        PreferenceEntry(
            title = {
                Text(
                    text = accountLabel ?: stringResource(R.string.soundcloud_not_logged_in),
                    modifier = Modifier.alpha(if (isLoggedIn) 1f else 0.5f),
                )
            },
            description = avatarUrl.ifBlank { null },
            icon = { Icon(painterResource(R.drawable.cloud), null) },
            trailingContent = {
                if (isLoggedIn) {
                    OutlinedButton(
                        onClick = {
                            CoroutineScope(Dispatchers.Main).launch {
                                context.dataStore.edit { prefs ->
                                    prefs.remove(SoundCloudAccessTokenKey)
                                    prefs.remove(SoundCloudRefreshTokenKey)
                                    prefs.remove(SoundCloudTokenExpiryKey)
                                    prefs.remove(SoundCloudUserIdKey)
                                    prefs.remove(SoundCloudUsernameKey)
                                    prefs.remove(SoundCloudAvatarUrlKey)
                                    prefs[EnableSoundCloudKey] = false
                                    prefs[UseSoundCloudSearchKey] = false
                                    prefs[UseSoundCloudHomeKey] = false
                                    prefs[UseSoundCloudLibraryKey] = false
                                }
                            }
                        },
                    ) {
                        Text(stringResource(R.string.action_logout))
                    }
                } else {
                    OutlinedButton(
                        onClick = { navController.navigate("settings/soundcloud/login") },
                    ) {
                        Text(stringResource(R.string.action_login))
                    }
                }
            },
        )

        PreferenceGroupTitle(title = stringResource(R.string.options))

        SwitchPreference(
            title = { Text(stringResource(R.string.soundcloud_enable)) },
            description = stringResource(R.string.soundcloud_enable_description),
            checked = enableSoundCloud,
            onCheckedChange = onEnableSoundCloudChange,
            isEnabled = isLoggedIn,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.soundcloud_use_for_search)) },
            description = stringResource(R.string.soundcloud_use_for_search_description),
            checked = useForSearch,
            onCheckedChange = onUseForSearchChange,
            isEnabled = isLoggedIn && enableSoundCloud,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.soundcloud_use_for_home)) },
            description = stringResource(R.string.soundcloud_use_for_home_description),
            checked = useForHome,
            onCheckedChange = onUseForHomeChange,
            isEnabled = isLoggedIn && enableSoundCloud,
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.soundcloud_use_for_library)) },
            description = stringResource(R.string.soundcloud_use_for_library_description),
            checked = useForLibrary,
            onCheckedChange = onUseForLibraryChange,
            isEnabled = isLoggedIn && enableSoundCloud,
        )

        if (!isLoggedIn) {
            PreferenceEntry(
                title = {
                    Text(text = stringResource(R.string.soundcloud_login_required))
                },
                description = null,
                icon = {
                    Icon(painter = painterResource(R.drawable.info), contentDescription = null)
                },
            )
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.soundcloud_integration)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
