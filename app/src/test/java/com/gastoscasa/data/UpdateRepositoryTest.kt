package com.gastoscasa.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRepositoryTest {
    @Test fun comparesSemanticVersions() {
        assertTrue(UpdateRepository.isNewer("1.0.0", "0.1.0"))
        assertTrue(UpdateRepository.isNewer("1.0.10", "1.0.9"))
        assertTrue(UpdateRepository.isNewer("1.1", "1.0.5"))
        assertFalse(UpdateRepository.isNewer("1.0.0", "1.0.0"))
        assertFalse(UpdateRepository.isNewer("0.9.9", "1.0.0"))
    }
}
