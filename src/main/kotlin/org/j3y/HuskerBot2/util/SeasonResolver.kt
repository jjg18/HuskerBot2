package org.j3y.HuskerBot2.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object SeasonResolver {
    val cfbSeason = LocalDate.now().minusMonths(1).year // CFB seasons (with postseason) go one month into the next year
    val nflSeason = LocalDate.now().minusMonths(2).year // NFL seasons (w/ postseason) go 2 months into the next year

    private val collegeWeeks: List<Instant> = listOf(
        LocalDateTime.parse("${cfbSeason}-02-01T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${cfbSeason}-09-08T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${cfbSeason}-09-15T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${cfbSeason}-09-22T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${cfbSeason}-09-29T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${cfbSeason}-10-06T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${cfbSeason}-10-13T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${cfbSeason}-10-20T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${cfbSeason}-10-27T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${cfbSeason}-11-03T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${cfbSeason}-11-10T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${cfbSeason}-11-17T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${cfbSeason}-11-24T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${cfbSeason}-12-01T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${cfbSeason}-12-08T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${cfbSeason}-12-13T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${cfbSeason}-12-19T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant()
    )

    private val nflWeeks: List<Instant> = listOf(
        LocalDateTime.parse("${nflSeason}-03-01T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason}-09-15T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason}-09-22T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason}-09-29T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason}-10-06T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason}-10-13T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason}-10-20T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason}-10-27T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason}-11-03T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason}-11-10T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason}-11-17T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason}-11-24T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason}-12-01T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason}-12-08T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason}-12-15T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason}-12-22T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason}-12-29T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason + 1}-01-05T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason + 1}-01-12T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason + 1}-01-19T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason + 1}-01-26T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason + 1}-02-02T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason + 1}-02-09T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${nflSeason + 1}-02-15T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant()
    )

    @JvmStatic
    fun currentCfbWeek(): Int {
        return currentWeek(collegeWeeks, Instant.now())
    }

    fun currentCfbSeason(): Int {
        return cfbSeason
    }

    @JvmStatic
    fun getCfbWeek(dateTime: Instant): Int {
        return currentWeek(collegeWeeks, dateTime)
    }

    @JvmStatic
    fun currentNflWeek(): Int {
        return currentWeek(nflWeeks, Instant.now())
    }

    fun currentNflSeason(): Int {
        return nflSeason
    }

    @JvmStatic
    fun getNflWeek(dateTime: Instant): Int {
        return currentWeek(nflWeeks, dateTime)
    }

    /**
     * Returns the current week number based on the provided list of week start times.
     * If the current time is after weeks[i], the week is i+1. If none match, returns weeks.size.
     */
    @JvmStatic
    fun currentWeek(weeks: List<Instant>, dateTime: Instant): Int {
        for (week in weeks.size downTo 1) {
            val start = weeks[week - 1] // subtract 1 because 0-based idx
            if (dateTime.isAfter(start)) return week
        }

        return weeks.size
    }
}
