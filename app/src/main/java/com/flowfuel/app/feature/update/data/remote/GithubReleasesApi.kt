package com.flowfuel.app.feature.update.data.remote

import com.flowfuel.app.feature.update.data.remote.dto.GithubReleaseDto
import retrofit2.http.GET

interface GithubReleasesApi {
    @GET("repos/Rochafelip/flowfuel-app/releases/latest")
    suspend fun getLatestRelease(): GithubReleaseDto
}
