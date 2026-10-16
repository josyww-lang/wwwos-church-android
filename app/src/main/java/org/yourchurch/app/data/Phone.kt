package org.yourchurch.app.data

/**
 * Turns what someone typed into a standard international number like +4791234567, or returns null if it
 * doesn't look like a phone number.
 *  - "+47 912 34 567" or "0047 912 34 567" -> +4791234567
 *  - exactly 8 digits with no country code is taken to be a Norwegian number (+47)
 */
fun normalizePhone(input: String): String? {
    var s = input.trim().replace(Regex("[\\s().-]"), "")
    if (s.isEmpty()) return null
    if (s.startsWith("00")) s = "+" + s.drop(2)
    if (!s.startsWith("+")) {
        if (s.length == 8 && s.all { it.isDigit() }) s = "+47$s" else return null
    }
    val digits = s.drop(1)
    if (digits.length !in 8..15 || !digits.all { it.isDigit() }) return null
    return s
}
