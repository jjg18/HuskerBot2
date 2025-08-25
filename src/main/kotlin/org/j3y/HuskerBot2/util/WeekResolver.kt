package org.j3y.HuskerBot2.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Utility to resolve the current "week" index given a list of week start timestamps.
 * The list is expected to be ordered chronologically and 1-indexed in terms of the
 * semantic week numbers (i.e., weeks[0] corresponds to week 1 start time).
 */
object WeekResolver {
    private val YEAR = LocalDateTime.now().year

    private val collegeWeeks: List<Instant> = listOf(
        LocalDateTime.parse("${YEAR}-01-01T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-09-02T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-09-08T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-09-15T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-09-22T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-09-29T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-10-06T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-10-13T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-10-20T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-10-27T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-11-03T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-11-10T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-11-17T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-11-24T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-12-01T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-12-08T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-12-13T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-12-19T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant()
    )

    private val nflWeeks: List<Instant> = listOf(
        LocalDateTime.parse("${YEAR}-01-01T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-09-04T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-09-11T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-09-18T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-09-25T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-10-02T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-10-09T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-10-16T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-10-23T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-10-30T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-11-06T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-11-13T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-11-20T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-11-27T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-12-04T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-12-11T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-12-18T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR}-12-25T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR + 1}-01-01T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR + 1}-01-08T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR + 1}-01-15T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR + 1}-01-22T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR + 1}-01-29T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant(),
        LocalDateTime.parse("${YEAR + 1}-02-05T00:00:00").atZone(ZoneId.of("America/Chicago")).toInstant()
    )

    @JvmStatic
    fun currentCfbWeek(): Int {
        return currentWeek(collegeWeeks, Instant.now())
    }

    @JvmStatic
    fun getCfbWeek(dateTime: Instant): Int {
        return currentWeek(collegeWeeks, dateTime)
    }

    @JvmStatic
    fun currentNflWeek(): Int {
        return currentWeek(nflWeeks, Instant.now())
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
