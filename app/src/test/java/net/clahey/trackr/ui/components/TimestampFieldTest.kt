package net.clahey.trackr.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TimestampFieldTest {

    // @spec EL-UI-032
    @Test fun `utcMillisToLocalDate reads the date at UTC midnight`() {
        val millis = Instant.parse("2024-03-15T00:00:00Z").toEpochMilli()
        assertEquals(LocalDate.of(2024, 3, 15), utcMillisToLocalDate(millis))
    }

    // @spec EL-UI-032
    @Test fun `localDateToUtcMillis and utcMillisToLocalDate round-trip`() {
        val date = LocalDate.of(2026, 12, 31)
        assertEquals(date, utcMillisToLocalDate(localDateToUtcMillis(date)))
    }

    // @spec EL-UI-032
    @Test fun `combineDateAndTime builds the instant in the given zone`() {
        val zone = ZoneId.of("America/New_York")
        val instant = combineDateAndTime(LocalDate.of(2026, 6, 17), 14, 30, zone)
        assertEquals(Instant.parse("2026-06-17T18:30:00Z"), instant)
    }

    // @spec EL-UI-032
    @Test fun `combineDateAndTime at midnight`() {
        val zone = ZoneId.of("UTC")
        val instant = combineDateAndTime(LocalDate.of(2026, 1, 1), 0, 0, zone)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), instant)
    }
}
