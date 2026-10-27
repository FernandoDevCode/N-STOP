package com.stash.core.auth.store

import com.stash.core.auth.model.ServiceToken
import com.stash.core.auth.model.UserInfo
import kotlinx.coroutines.flow.Flow

/** Platform storage boundary: Android Keystore/DataStore or Windows DPAPI. */
interface TokenStore {
    val spotifyToken: Flow<ServiceToken?>
    val spotifyUser: Flow<UserInfo?>
    val youTubeToken: Flow<ServiceToken?>
    val youTubeUser: Flow<UserInfo?>
    val discordToken: Flow<ServiceToken?>
    val discordUser: Flow<UserInfo?>
    suspend fun saveSpotifyToken(token: ServiceToken, user: UserInfo? = null)
    suspend fun saveYouTubeToken(token: ServiceToken, user: UserInfo? = null)
    suspend fun saveDiscordToken(token: ServiceToken, user: UserInfo? = null)
    suspend fun clearSpotify()
    suspend fun clearYouTube()
    suspend fun clearDiscord()
}
