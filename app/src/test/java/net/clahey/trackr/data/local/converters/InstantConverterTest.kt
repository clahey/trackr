package net.clahey.trackr.data.local.converters

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class InstantConverterTest {

    // @spec LS-BE-051
    @Test fun `encode Instant produces epoch millis`() {
        val instant = Instant.ofEpochMilli(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, InstantConverter.encode(instant))
    }

    // @spec LS-BE-051
    @Test fun `decode epoch millis produces correct Instant`() {
        val millis = 1_700_000_000_000L
        assertEquals(Instant.ofEpochMilli(millis), InstantConverter.decode(millis))
    }

    @Test fun `round-trip preserves Instant value`() {
        val original = Instant.ofEpochMilli(1_234_567_890_123L)
        assertEquals(original, InstantConverter.decode(InstantConverter.encode(original)))
    }

    @Test fun `epoch zero round-trips`() {
        val epoch = Instant.EPOCH
        assertEquals(epoch, InstantConverter.decode(InstantConverter.encode(epoch)))
    }
}
