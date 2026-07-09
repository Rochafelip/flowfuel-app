package com.flowfuel.app.feature.update.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadProgressTest {

    @Test
    fun `fraction is null when totalBytes is zero`() {
        val progress = DownloadProgress(bytesDownloaded = 0, totalBytes = 0)

        assertNull(progress.fraction)
    }

    @Test
    fun `fraction is the ratio of bytesDownloaded to totalBytes`() {
        val progress = DownloadProgress(bytesDownloaded = 50, totalBytes = 200)

        assertEquals(0.25f, progress.fraction)
    }
}
