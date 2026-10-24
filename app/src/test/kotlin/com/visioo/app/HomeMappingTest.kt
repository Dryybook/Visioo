package com.visioo.app

import com.visioo.app.data.SectionLayout
import com.visioo.app.data.toDomain
import com.visioo.core.network.ContentSummaryDto
import com.visioo.core.network.HomeSectionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMappingTest {
    private val item = ContentSummaryDto(
        id = "m1", title = "T", type = "movie",
        posterUrl = "poster", backdropUrl = "backdrop", isVip = true,
    )

    @Test fun landscapeSectionPrefersBackdrop() {
        val section = HomeSectionDto("s", "S", "landscape", listOf(item)).toDomain()
        assertEquals(SectionLayout.Landscape, section.layout)
        assertEquals("backdrop", section.items.single().imageUrl)
        assertTrue(section.items.single().isVip)
    }

    @Test fun posterSectionPrefersPoster() {
        val section = HomeSectionDto("s", "S", "poster", listOf(item)).toDomain()
        assertEquals(SectionLayout.Poster, section.layout)
        assertEquals("poster", section.items.single().imageUrl)
    }

    @Test fun unknownLayoutFallsBackToPoster() {
        assertEquals(SectionLayout.Poster, HomeSectionDto("s", "S", "weird", emptyList()).toDomain().layout)
    }
}
