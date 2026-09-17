package lk.salli.domain

import java.time.LocalDate

/**
 * National Fuel Pass (QR) scheduling as run in Sri Lanka: a vehicle may fill up on calendar
 * days whose parity matches the last digit of its plate — even plates on even days, odd on
 * odd days. The weekly quota resets on the date the SMS calls "Resets on", so the last chance
 * to use what is left is the latest matching-parity day strictly before that date.
 */
object FuelPassRules {

    /** Stable display/grouping key across SMS vintages with inconsistent casing or spaces. */
    fun canonicalVehicle(vehicle: String): String = vehicle.trim().uppercase()

    /** Last digit of the registration number, or null when the plate has no trailing digit. */
    fun lastDigit(vehicle: String): Int? =
        canonicalVehicle(vehicle).lastOrNull { it.isDigit() }?.digitToInt()

    fun isEligible(vehicle: String, date: LocalDate): Boolean {
        val digit = lastDigit(vehicle) ?: return true
        return digit % 2 == date.dayOfMonth % 2
    }

    /** Latest eligible day strictly before [resetsOn], or null if the plate has no digit. */
    fun lastEligibleDayBefore(vehicle: String, resetsOn: LocalDate): LocalDate? {
        if (lastDigit(vehicle) == null) return null
        var day = resetsOn.minusDays(1)
        var guard = 0
        while (!isEligible(vehicle, day) && guard < 3) {
            day = day.minusDays(1); guard++
        }
        return day
    }
}
