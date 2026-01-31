package com.example.deviceowner.utils.helpers

import java.util.regex.Pattern

object LoanNumberValidator {
    
    // Loan number format: LN-YYYYMMDD-NNNNN
    // Example: LN-20260128-00001
    private const val LOAN_NUMBER_PATTERN = "^LN-\\d{8}-\\d{5}$"
    private val loanNumberRegex = Pattern.compile(LOAN_NUMBER_PATTERN)
    
    /**
     * Validates loan number format
     * @param loanNumber The loan number to validate
     * @return ValidationResult with success status and error message
     */
    fun validateLoanNumber(loanNumber: String): ValidationResult {
        // Check if empty
        if (loanNumber.isBlank()) {
            return ValidationResult(false, "Loan number cannot be empty")
        }
        
        // Check format
        if (!loanNumberRegex.matcher(loanNumber).matches()) {
            return ValidationResult(false, "Invalid loan number format. Expected format: LN-YYYYMMDD-NNNNN (e.g., LN-20260128-00001)")
        }
        
        // Extract and validate date part
        val datePart = loanNumber.substring(3, 11) // Extract YYYYMMDD
        if (!isValidDate(datePart)) {
            return ValidationResult(false, "Invalid date in loan number. Please check the date format.")
        }
        
        return ValidationResult(true, "Valid loan number")
    }
    
    /**
     * Validates if the date part is a valid date
     * @param dateString Date in YYYYMMDD format
     * @return true if valid date, false otherwise
     */
    private fun isValidDate(dateString: String): Boolean {
        return try {
            val year = dateString.substring(0, 4).toInt()
            val month = dateString.substring(4, 6).toInt()
            val day = dateString.substring(6, 8).toInt()
            
            // Basic validation
            when {
                year < 2020 || year > 2030 -> false
                month < 1 || month > 12 -> false
                day < 1 || day > 31 -> false
                else -> {
                    // More detailed validation for days in month
                    when (month) {
                        2 -> day <= if (isLeapYear(year)) 29 else 28
                        4, 6, 9, 11 -> day <= 30
                        else -> day <= 31
                    }
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Checks if a year is a leap year
     */
    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }
    
    /**
     * Formats loan number input by adding dashes automatically
     * @param input Raw input from user
     * @return Formatted loan number
     */
    fun formatLoanNumber(input: String): String {
        // Remove any existing dashes and spaces
        val cleanInput = input.replace("-", "").replace(" ", "").uppercase()
        
        return when {
            cleanInput.length <= 2 -> cleanInput
            cleanInput.length <= 10 -> {
                if (cleanInput.startsWith("LN")) {
                    "LN-${cleanInput.substring(2)}"
                } else {
                    cleanInput
                }
            }
            cleanInput.length <= 15 -> {
                if (cleanInput.startsWith("LN")) {
                    val datePart = cleanInput.substring(2, 10)
                    val numberPart = cleanInput.substring(10)
                    "LN-$datePart-$numberPart"
                } else {
                    cleanInput
                }
            }
            else -> cleanInput
        }
    }
    
    /**
     * Extracts information from a valid loan number
     * @param loanNumber Valid loan number
     * @return LoanNumberInfo with extracted details
     */
    fun extractLoanInfo(loanNumber: String): LoanNumberInfo? {
        if (!validateLoanNumber(loanNumber).isValid) {
            return null
        }
        
        val datePart = loanNumber.substring(3, 11)
        val sequencePart = loanNumber.substring(12, 17)
        
        val year = datePart.substring(0, 4)
        val month = datePart.substring(4, 6)
        val day = datePart.substring(6, 8)
        
        return LoanNumberInfo(
            fullNumber = loanNumber,
            date = "$day/$month/$year",
            sequence = sequencePart.toInt(),
            year = year.toInt(),
            month = month.toInt(),
            day = day.toInt()
        )
    }
    
    /**
     * Data class for validation result
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
    
    /**
     * Data class for loan number information
     */
    data class LoanNumberInfo(
        val fullNumber: String,
        val date: String,
        val sequence: Int,
        val year: Int,
        val month: Int,
        val day: Int
    )
}