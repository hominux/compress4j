package com.hominux.compress4j.semver

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class SemverBumpTest {

    @Test
    fun `bumps order from smallest to largest`() {
        assertThat(SemverBump.entries).containsExactly(
            SemverBump.NONE,
            SemverBump.PATCH,
            SemverBump.MINOR,
            SemverBump.MAJOR,
        )
    }

    @Test
    fun `highestOf picks the largest bump`() {
        assertThat(SemverBump.highestOf(listOf(SemverBump.PATCH, SemverBump.MAJOR, SemverBump.NONE)))
            .isEqualTo(SemverBump.MAJOR)
    }

    @Test
    fun `highestOf of nothing is NONE`() {
        assertThat(SemverBump.highestOf(emptyList())).isEqualTo(SemverBump.NONE)
    }

    @ParameterizedTest
    @CsvSource(
        "1.2.3,MAJOR,2.0.0",
        "1.2.3,MINOR,1.3.0",
        "1.2.3,PATCH,1.2.4",
        "1.2.3,NONE,1.2.3",
        "0.0.0,MINOR,0.1.0",
        "9.9.9,PATCH,9.9.10",
    )
    fun `derives the next version`(previous: String, bump: SemverBump, expected: String) {
        assertThat(nextVersion(previous, bump)).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource(
        "1.2.3-SNAPSHOT,PATCH,1.2.3",
        "5.0.0-rc.1,PATCH,5.0.0",
        "1.2.3-rc.1+build.7,MAJOR,1.2.3",
        "1.2.3+build.7,MINOR,1.3.0",
    )
    fun `a pre-release finalises its own version and build metadata is ignored`(
        previous: String,
        bump: SemverBump,
        expected: String,
    ) {
        assertThat(nextVersion(previous, bump)).isEqualTo(expected)
    }

    @ParameterizedTest
    @ValueSource(strings = ["1.2", "1.2.3.4", "v1.2.3", "1.2.x", "", "not-a-version"])
    fun `refuses a previous version that is not a release version`(previous: String) {
        assertThatThrownBy { nextVersion(previous, SemverBump.PATCH) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining(previous)
    }
}
