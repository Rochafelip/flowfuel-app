package com.flowfuel.app.feature.update.domain

object VersionComparator {

    /** true se [remoteTag] (ex.: "v1.2.0") for mais novo que [currentVersionName] (ex.: "1.1.0" ou "1.1.0-debug"). */
    fun isNewer(remoteTag: String, currentVersionName: String): Boolean {
        val remote = parse(remoteTag.removePrefix("v"))
        val current = parse(currentVersionName.substringBefore("-"))
        for (i in 0..2) {
            if (remote[i] != current[i]) return remote[i] > current[i]
        }
        return false
    }

    private fun parse(version: String): List<Int> {
        val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
        return parts + List(3 - parts.size) { 0 }
    }
}
