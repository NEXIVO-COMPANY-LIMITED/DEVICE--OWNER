package com.example.deviceowner.utils.helpers

/**
 * Simple test cases for LoanNumberValidator
 * Run these in your development environment to verify validation works correctly
 */
object LoanNumberValidatorTest {
    
    fun runTests(): String {
        val results = mutableListOf<String>()
        
        // Test valid loan numbers
        val validNumbers = listOf(
            "LN-20260128-00001",
            "LN-20241225-12345",
            "LN-20230101-99999"
        )
        
        results.add("=== VALID LOAN NUMBERS ===")
        validNumbers.forEach { loanNumber ->
            val result = LoanNumberValidator.validateLoanNumber(loanNumber)
            val info = LoanNumberValidator.extractLoanInfo(loanNumber)
            results.add("$loanNumber: ${if (result.isValid) "✅ PASS" else "❌ FAIL"}")
            info?.let {
                results.add("  → Date: ${it.date}, Sequence: ${it.sequence}")
            }
        }
        
        // Test invalid loan numbers
        val invalidNumbers = listOf(
            "",                          // Empty
            "LN-2026012-00001",         // Wrong date format
            "LN-20260128-0001",         // Wrong sequence format
            "LN-20261301-00001",        // Invalid month
            "LN-20260132-00001",        // Invalid day
            "ABC-20260128-00001",       // Wrong prefix
            "LN20260128-00001",         // Missing dash
            "LN-20260128",              // Missing sequence
            "LN-20260128-ABCDE"         // Non-numeric sequence
        )
        
        results.add("\n=== INVALID LOAN NUMBERS ===")
        invalidNumbers.forEach { loanNumber ->
            val result = LoanNumberValidator.validateLoanNumber(loanNumber)
            results.add("'$loanNumber': ${if (!result.isValid) "✅ CORRECTLY REJECTED" else "❌ INCORRECTLY ACCEPTED"}")
            results.add("  → ${result.message}")
        }
        
        // Test formatting
        results.add("\n=== FORMATTING TESTS ===")
        val formatTests = mapOf(
            "LN20260128000001" to "LN-20260128-00001",
            "ln20260128000001" to "LN-20260128-00001",
            "LN-20260128000001" to "LN-20260128-00001",
            "LN20260128-00001" to "LN-20260128-00001"
        )
        
        formatTests.forEach { (input, expected) ->
            val formatted = LoanNumberValidator.formatLoanNumber(input)
            results.add("'$input' → '$formatted' ${if (formatted == expected) "✅" else "❌ Expected: $expected"}")
        }
        
        return results.joinToString("\n")
    }
}