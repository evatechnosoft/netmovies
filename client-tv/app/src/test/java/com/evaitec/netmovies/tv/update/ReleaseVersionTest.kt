package com.evaitec.netmovies.tv.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {

    @Test
    fun parseIgnoresPrefixAndSuffix() {
        assertEquals(listOf(0, 1, 35), ReleaseVersion.parse("v0.1.35-poc"))
        assertEquals(listOf(1, 0), ReleaseVersion.parse("1.0"))
    }

    @Test
    fun newerPatchIsAnUpdate() {
        assertTrue(ReleaseVersion.isNewerThan("v0.1.36-poc", "v0.1.35-poc"))
    }

    @Test
    fun newerMinorBeatsBiggerPatch() {
        assertTrue(ReleaseVersion.isNewerThan("v0.2.0-poc", "v0.1.35-poc"))
    }

    // Kök neden: liste sırası değişince eski release "güncelleme" sanılıyordu.
    @Test
    fun olderReleaseIsNotAnUpdate() {
        assertFalse(ReleaseVersion.isNewerThan("v0.1.31-poc", "v0.1.35-poc"))
    }

    @Test
    fun sameVersionIsNotAnUpdate() {
        assertFalse(ReleaseVersion.isNewerThan("v0.1.35-poc", "v0.1.35-poc"))
        assertFalse(ReleaseVersion.isNewerThan("v0.1.35", "v0.1.35-poc"))
    }

    @Test
    fun unparsableTagStaysSilent() {
        assertFalse(ReleaseVersion.isNewerThan("nightly", "v0.1.35-poc"))
        assertFalse(ReleaseVersion.isNewerThan("v0.1.36-poc", "dev"))
    }
}
