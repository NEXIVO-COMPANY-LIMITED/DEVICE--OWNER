package com.example.deviceowner.utils

/**
 * Validates loan numbers in format: LN-YYYYMMDD-NNNNN
 * Example: LN-20260121-00001
 *
 * - LN- prefix
 * - YYYYMMDD: valid date (year 2020–2030)
 * - NNNNN: 5-digit sequence number
 */
object LoanNumberValidator {

    /** Regex: LN- + 8 digits + - + 5 digits */
    private val LOAN_NUMBER_PATTERN = Regex("^LN-\\d{8}-\\d{5}$")

    /**
     * Validates loan number format and date before sending to API/DB.
     * Trims whitespace before validation.
     *
     * @param loanNumber raw input (e.g. "LN-20260121-00001" or "  LN-20260121-00001  ")
     * @return true if valid
     */
    fun isValid(loanNumber: String?): Boolean {
        val trimmed = loanNumber?.trim() ?: return false
        if (trimmed.isEmpty()) return false

        if (!trimmed.matches(LOAN_NUMBER_PATTERN)) return false

        val parts = trimmed.split("-")
        if (parts.size != 3) return false

        val datePart = parts[1]
        if (datePart.length != 8) return false

        return isValidDatePart(datePart)
    }

    /**
     * Returns trimmed loan number if valid, or null if invalid.
     * Use this when you need to normalize input before sending to DB/API.
     */
    fun validateAndNormalize(loanNumber: String?): String? {
        val trimmed = loanNumber?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        return if (isValid(trimmed)) trimmed else null
    }

    private fun isValidDatePart(datePart: String): Boolean {
        return try {
            val year = datePart.substring(0, 4).toInt()
            val month = datePart.substring(4, 6).toInt()
            val day = datePart.substring(6, 8).toInt()

            if (year < 2020 || year > 2030) return false
            if (month !in 1..12) return false
            if (day !in 1..31) return false

            val maxDay = when (month) {
                2 -> if (isLeapYear(year)) 29 else 28
                4, 6, 9, 11 -> 30
                else -> 31
            }
            day <= maxDay
        } catch (e: NumberFormatException) {
            false
        }
    }

    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0))
    }
}
