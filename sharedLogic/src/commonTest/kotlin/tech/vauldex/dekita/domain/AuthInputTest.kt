package tech.vauldex.dekita.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthInputTest {

    private fun jpNational(raw: String) = AuthInput.parseMobile(DialCode.JP, raw)?.national

    @Test
    fun japaneseMobileNumbersParseInCommonFormats() {
        listOf("090-1234-5678", "09012345678", "9012345678", "+819012345678", "+81 90 1234 5678", "(090) 1234-5678")
            .forEach { assertEquals("9012345678", jpNational(it), "Input: $it") }
        assertEquals("8012345678", jpNational("080 1234 5678"))
        assertEquals("7012345678", jpNational("070-1234-5678"))
    }

    @Test
    fun scenarioNumberParsesToTheSameE164() {
        val phone = AuthInput.parseMobile(DialCode.JP, "+819000000000")
        assertEquals("+819000000000", phone?.e164)
        assertEquals("+81 90-0000-0000", phone?.formatted())
    }

    @Test
    fun fullWidthDigitsFromAJapaneseImeAreNormalized() {
        assertEquals("9012345678", jpNational("０９０－１２３４－５６７８"))
        assertEquals("9012345678", jpNational("０９０　１２３４　５６７８"))
        assertEquals("9012345678", jpNational("＋８１９０１２３４５６７８"))
        // The long-vowel mark is what many IMEs type for a hyphen.
        assertEquals("9012345678", jpNational("090ー1234ー5678"))
    }

    @Test
    fun nonMobileOrMalformedNumbersAreRejected() {
        listOf(
            "0312345678", // Tokyo landline
            "090-1234-567", // too short
            "090-1234-56789", // too long
            "090-abcd-5678",
            "",
            "+639171234567", // a Philippine number with Japan selected
        ).forEach { assertNull(jpNational(it), "Input: $it") }
    }

    @Test
    fun philippineMobileNumbersParse() {
        val phone = AuthInput.parseMobile(DialCode.PH, "0917 123 4567")
        assertEquals("9171234567", phone?.national)
        assertEquals("+63 917 123 4567", phone?.formatted())
        assertNull(AuthInput.parseMobile(DialCode.PH, "0817 123 4567"))
    }

    @Test
    fun e164RoundTrips() {
        val phone = AuthInput.parseMobile(DialCode.JP, "090-1234-5678")!!
        assertEquals(phone, AuthInput.parseE164(phone.e164))
        assertNull(AuthInput.parseE164("+1 555 0100"))
    }

    @Test
    fun phoneNumberHidesItsDigitsWhenPrinted() {
        val phone = AuthInput.parseMobile(DialCode.JP, "090-1234-5678")!!
        assertFalse(phone.toString().contains("1234"))
    }

    @Test
    fun emailFormatIsChecked() {
        assertTrue(AuthInput.isValidEmail("lunchuser@example.com"))
        assertTrue(AuthInput.isValidEmail("lunch.user+tag@sub.example.co.jp"))
        listOf("lunchuser", "lunchuser@example", "lunch user@example.com", "@example.com", "lunchuser@.com", "lunchuser@example.c")
            .forEach { assertFalse(AuthInput.isValidEmail(it), "Input: $it") }
    }

    @Test
    fun fullWidthEmailIsNormalizedBeforeTheCheck() {
        val email = AuthInput.normalizeEmail(" ｌｕｎｃｈｕｓｅｒ＠ｅｘａｍｐｌｅ．ｃｏｍ ")
        assertEquals("lunchuser@example.com", email)
        assertTrue(AuthInput.isValidEmail(email))
    }

    @Test
    fun passwordNeedsEightCharactersALetterAndANumber() {
        assertFalse(AuthInput.isValidPassword("password"), "no number")
        assertFalse(AuthInput.isValidPassword("12345678"), "no letter")
        assertFalse(AuthInput.isValidPassword("Pass123"), "7 characters")
        assertTrue(AuthInput.isValidPassword("Pass1234!"))
        assertTrue(AuthInput.isValidPassword("pass1234"))
    }

    @Test
    fun otpIsSixHalfWidthDigits() {
        assertTrue(AuthInput.isValidOtp("123456"))
        assertFalse(AuthInput.isValidOtp("12345"))
        assertFalse(AuthInput.isValidOtp("12a456"))
        assertFalse(AuthInput.isValidOtp("１２３４５６"), "full-width must be normalized first")
    }

    @Test
    fun otpNormalizationKeepsOnlyHalfWidthDigits() {
        assertEquals("123456", AuthInput.normalizeOtp("１２３４５６"))
        assertEquals("123456", AuthInput.normalizeOtp("123 456"))
        assertEquals("12456", AuthInput.normalizeOtp("12a456"))
        assertEquals("123456", AuthInput.normalizeOtp("1234567"))
    }

    @Test
    fun halfWidthConversionCoversLettersDigitsAndSymbols() {
        assertEquals("Abc123+@.", AuthInput.toHalfWidth("Ａｂｃ１２３＋＠．"))
        assertEquals("a b", AuthInput.toHalfWidth("a　b"))
    }
}
