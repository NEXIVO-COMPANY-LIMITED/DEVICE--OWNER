package com.microspace.payo.utils.helpers

object LoanNumberValidator {
    
    /**
     * Validates loan number - only checks if it's not empty
     * Backend will validate format and existence
     * @param loanNumber The loan number to validate
     * @return ValidationResult with success status and error message
     */
    fun validateLoanNumber(loanNumber: String): ValidationResult {
        val trimmed = loanNumber.trim()
        if (trimmed.isBlank()) {
            return ValidationResult(false, "Loan number cannot be empty")
        }
        
        // Accept any non-empty loan number - backend will validate format and existence
        return ValidationResult(true, "Valid loan number")
    }
    
    /**
     * Formats loan number input by converting to uppercase
     * @param input Raw input from user
     * @return Formatted loan number
     */
    fun formatLoanNumber(input: String): String {
        return input.trim().uppercase()
    }
    
    /**
     * Data class for validation result
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
}