package com.flowfuel.app.feature.update.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `returns true when remote major version is newer`() {
        assertTrue(VersionComparator.isNewer("v2.0.0", "1.9.9"))
    }

    @Test
    fun `returns true when remote minor version is newer`() {
        assertTrue(VersionComparator.isNewer("v1.2.0", "1.1.0"))
    }

    @Test
    fun `returns true when remote patch version is newer`() {
        assertTrue(VersionComparator.isNewer("v1.1.1", "1.1.0"))
    }

    @Test
    fun `compares numerically, not lexicographically`() {
        assertTrue(VersionComparator.isNewer("v1.10.0", "1.9.0"))
    }

    @Test
    fun `returns false when versions are equal`() {
        assertFalse(VersionComparator.isNewer("v1.1.0", "1.1.0"))
    }

    @Test
    fun `returns false when remote version is older`() {
        assertFalse(VersionComparator.isNewer("v1.0.0", "1.1.0"))
    }

    @Test
    fun `ignores a debug suffix on the installed version`() {
        assertTrue(VersionComparator.isNewer("v1.2.0", "1.1.0-debug"))
        assertFalse(VersionComparator.isNewer("v1.1.0", "1.1.0-debug"))
    }

    @Test
    fun `normalizes a leading v prefix on the remote tag`() {
        assertTrue(VersionComparator.isNewer("v1.2.0", "1.1.0"))
    }
}
