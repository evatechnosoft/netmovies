package com.evaitec.netmovies.tv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureFlagsTest {
    @Test fun defaultsAreTrue() {
        val flags = FeatureFlags()
        Feature.values().forEach { assertTrue(it.key, flags.enabled(it)) }
        assertTrue(Feature.values().all { it.key == "feature_" + it.key.removePrefix("feature_") })
    }

    @Test fun remoteFalseDisablesOnlyThatFeature() {
        val flags = FeatureFlags { if (it == Feature.OTA_UPDATE.key) false else null }
        assertFalse(flags.enabled(Feature.OTA_UPDATE))
        assertTrue(flags.enabled(Feature.WAKE_ON_LAN))
    }

    @Test fun failingSourceFallsBackToDefault() {
        assertTrue(FeatureFlags { error("boom") }.enabled(Feature.GEMINI_NANO))
    }
}
