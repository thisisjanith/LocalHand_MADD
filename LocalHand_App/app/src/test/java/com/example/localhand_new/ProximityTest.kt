package com.example.localhand_new

import com.example.localhand_new.data.location.LatLng
import com.example.localhand_new.data.location.distanceMetres
import com.example.localhand_new.data.location.formatDistance
import com.example.localhand_new.data.model.Category
import com.example.localhand_new.data.model.Listing
import com.example.localhand_new.data.model.ListingType
import com.example.localhand_new.data.repo.rankByDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proximity is the core of the product, so the distance maths and the ranking
 * it drives are covered directly.
 */
class ProximityTest {

    private val kaduwelaRoad = LatLng(6.9061, 79.9701)
    private val sliitJunction = LatLng(6.9147, 79.9725)

    private fun listing(
        id: String,
        locality: String,
        point: LatLng?,
    ) = Listing(
        id = id,
        type = ListingType.SERVICE,
        title = "Listing $id",
        category = Category.REPAIRS,
        providerName = "Neighbour",
        providerPhone = "+94771234567",
        rating = 4.5,
        price = "Rs 1,000",
        locality = locality,
        latitude = point?.latitude,
        longitude = point?.longitude,
        condition = null,
        description = "Test fixture listing",
        imageRes = 0,
        imageUrls = emptyList(),
        isMine = false,
        isFavourite = false,
        createdAt = 0L,
    )

    private fun fixtureListings() = listOf(
        listing("1", "Kaduwela Road", kaduwelaRoad),
        listing("2", "SLIIT Junction", sliitJunction),
        listing("3", "Unknown Area", null),
    )

    @Test
    fun `distance between two Malabe localities is a sensible walk`() {
        val metres = distanceMetres(kaduwelaRoad, sliitJunction)
        // ~1 km apart; allow slack for the haversine approximation.
        assertTrue("was $metres m", metres in 800.0..1_200.0)
    }

    @Test
    fun `distance is zero to itself and symmetric`() {
        assertEquals(0.0, distanceMetres(kaduwelaRoad, kaduwelaRoad), 0.001)
        assertEquals(
            distanceMetres(kaduwelaRoad, sliitJunction),
            distanceMetres(sliitJunction, kaduwelaRoad),
            0.001,
        )
    }

    @Test
    fun `known separation matches a reference value`() {
        // One degree of latitude is ~111 km anywhere on the globe.
        val a = LatLng(0.0, 0.0)
        val b = LatLng(1.0, 0.0)
        assertEquals(111_195.0, distanceMetres(a, b), 500.0)
    }

    @Test
    fun `ranking puts the nearest listing first`() {
        val ranked = fixtureListings().rankByDistance(kaduwelaRoad)

        // The listing with no coordinates sorts last, with an unknown distance.
        assertEquals("Unknown Area", ranked.last().listing.locality)
        assertEquals(null, ranked.last().metres)

        val known = ranked.dropLast(1)
        val distances = known.mapNotNull { it.metres }
        assertEquals("every other listing has coordinates", known.size, distances.size)
        assertEquals("sorted ascending", distances.sorted(), distances)

        // The user is standing on Kaduwela Road, so that listing wins.
        assertEquals("Kaduwela Road", ranked.first().listing.locality)
    }

    @Test
    fun `ranking without a user location leaves order untouched and distances unknown`() {
        val seeds = fixtureListings()
        val ranked = seeds.rankByDistance(null)

        assertEquals(seeds.map { it.id }, ranked.map { it.listing.id })
        assertTrue(ranked.all { it.metres == null })
    }

    @Test
    fun `distances are phrased the way a neighbour would say them`() {
        assertEquals("just around the corner", formatDistance(40.0))
        assertEquals("300 m away", formatDistance(310.0))
        assertEquals("1.4 km away", formatDistance(1_420.0))
        assertEquals("12 km away", formatDistance(12_300.0))
    }
}
