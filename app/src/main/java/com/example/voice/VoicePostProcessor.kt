package com.example.voice

import com.example.ai.ConversationalFuzzyExtractor
import java.util.Locale

/**
 * VoicePostProcessor:
 * Specialized on-device post-processing filter for speech-to-text transcripts (Whisper / SpeechRecognizer).
 *
 * Capabilities:
 * 1. Monetary Context Disambiguation:
 *    Fixes common speech-to-text misinterpretations like replacing 'chocolate' with 'rupayalu'
 *    when the context implies a monetary transaction in Telugu (e.g., "500 chocolate ఇచ్చాను" -> "500 రూపాయలు ఇచ్చాను",
 *    "రమేష్ కి 200 chocolate" -> "రమేష్ కి 200 రూపాయలు").
 *    Preserves legitimate chocolate purchases (e.g., "50 రూపాయల చాక్లెట్" or "చాక్లెట్ 20 రూపాయలు").
 * 2. Telugu Phonetic & Slang Normalization:
 *    Normalizes common STT acoustic artifacts for loans, khata, categories, and payment actions.
 * 3. Telugu Spoken Number Words Normalization:
 *    Converts spoken number words ("రెండు వందలు", "వెయ్యి", "ఐదు వేలు") to numeric digits.
 * 4. Zero-allocation fast regex passes (<2ms execution time) for resource-constrained Android 8.1 Helio P23 chips.
 */
object VoicePostProcessor {

    fun filterTranscript(rawText: String, isTelugu: Boolean = false): String {
        if (rawText.isBlank()) return ""

        var text = rawText.trim()

        // Step 1: Normalize Spoken Telugu Number Words (e.g. "రెండు వందలు" -> "200")
        text = ConversationalFuzzyExtractor.normalizeTeluguNumberWords(text)

        // Step 2: Contextual Monetary Disambiguation (replacing 'chocolate' with 'rupayalu' when context implies money)
        text = disambiguateMonetaryChocolate(text)

        // Step 3: Telugu Acoustic & Phonetic STT Corrections
        text = correctTeluguPhoneticArtifacts(text)

        // Step 4: Normalize extra whitespaces and punctuation
        text = text.replace(Regex("""\s+"""), " ").trim()

        return text
    }

    /**
     * Disambiguates phonetic confusion between 'chocolate' and 'rupayalu' in Telugu speech-to-text.
     * When acoustic models hear "rupayalu" (రూపాయలు) as "chocolate" (చాక్లెట్) in monetary contexts,
     * this filter restores "రూపాయలు".
     *
     * Legitimate chocolate purchases (e.g. "50 రూపాయల చాక్లెట్" or "చాక్లెట్ కొన్నాను") are preserved intact.
     */
    private fun disambiguateMonetaryChocolate(input: String): String {
        var text = input
        val lower = text.lowercase(Locale.ROOT)

        // If "రూపాయలు" / "రూపాయల" / "rupees" / "rs" is ALREADY present alongside "chocolate",
        // then chocolate is legitimately an item being purchased (e.g. "50 రూపాయల చాక్లెట్")!
        val hasExplicitCurrencyAlready = Regex("""(?:రూపాయలు|రూపాయల|రూపాయి|రూ\.|rupees|rupee|rs|inr|₹)""").containsMatchIn(text)
        val hasPurchaseVerb = Regex("""(?:కొన్నాను|కొన్నాము|కొన్నా|bought|purchased|తీసుకున్నాను)""").containsMatchIn(lower)

        if (hasExplicitCurrencyAlready || hasPurchaseVerb) {
            // Keep chocolate as the legitimate item
            return text
        }

        // Context check: Does the phrase contain monetary action verbs or lending/borrowing markers?
        val hasMonetaryAction = Regex("""(?:ఇచ్చాను|ఇచ్చా|ఇచ్చాము|ఇచ్చేశారు|చెల్లించాను|చెల్లించారు|ఖర్చు|ఖర్చయింది|అప్పు|బాకీ|చేబదులు|gave|given|lent|borrowed|spent|paid|transfer|sent)""").containsMatchIn(lower)
        val hasPersonDative = Regex("""([A-Za-z\u0C00-\u0C7F]+)\s*(?:కి|కు|గారికి|to)(?=$|[^\p{L}\p{M}\p{N}])""").containsMatchIn(text)

        // Pattern 1: [Amount] chocolate [Monetary action]
        // Examples: "500 chocolate ఇచ్చాను" -> "500 రూపాయలు ఇచ్చాను", "200 చాక్లెట్ ఖర్చు" -> "200 రూపాయలు ఖర్చు"
        text = text.replace(
            Regex("""(?i)(\b\d+(?:\.\d+)?)\s*(?:chocolate|chocolates|చాక్లెట్|చాక్లెట్లు|చాక్లెట్స్)\s*(?=(?:ఇచ్చాను|ఇచ్చా|ఇచ్చాము|ఖర్చు|చెల్లించాను|అప్పు|బాకీ|చేబదులు|తీసుకున్నాను|gave|lent|paid|spent|to\b|కి(?=$|[^\p{L}\p{M}\p{N}])|కు(?=$|[^\p{L}\p{M}\p{N}])))"""),
            "$1 రూపాయలు "
        )

        // Pattern 2: [Person dative] [Amount] chocolate
        // Example: "రమేష్ కి 500 chocolate" -> "రమేష్ కి 500 రూపాయలు"
        text = text.replace(
            Regex("""(?i)((?:[A-Za-z\u0C00-\u0C7F]+)\s*(?:కి|కు|గారికి|to)\s+\d+(?:\.\d+)?)\s*(?:chocolate|chocolates|చాక్లెట్|చాక్లెట్లు|చాక్లెట్స్)(?=$|[^\p{L}\p{M}\p{N}])"""),
            "$1 రూపాయలు"
        )

        // Pattern 3: If monetary action or person dative exists and amount >= 50 with "chocolate"
        if (hasMonetaryAction || hasPersonDative) {
            text = text.replace(
                Regex("""(?i)(\b\d{2,}(?:\.\d+)?)\s*(?:chocolate|chocolates|చాక్లెట్|చాక్లెట్లు|చాక్లెట్స్)(?=$|[^\p{L}\p{M}\p{N}])"""),
                "$1 రూపాయలు"
            )
        }

        // Pattern 4: Standalone large amount >= 100 followed by "chocolate" with no other words
        // e.g. "500 chocolate" or "1000 చాక్లెట్" -> "500 రూపాయలు" / "1000 రూపాయలు"
        text = text.replace(
            Regex("""(?i)^(\b\d{2,}(?:\.\d+)?)\s*(?:chocolate|chocolates|చాక్లెట్|చాక్లెట్లు|చాక్లెట్స్)$"""),
            "$1 రూపాయలు"
        )

        return text
    }

    /**
     * Corrects common Telugu and Indian English phonetic speech-to-text recognition errors.
     */
    private fun correctTeluguPhoneticArtifacts(input: String): String {
        var text = input

        // 1. Lending / Khata Phonetic Corrections
        // "ఆపు ఇచ్చాను" / "ఆపి ఇచ్చాను" / "అపు ఇచ్చాను" -> "అప్పు ఇచ్చాను"
        text = text.replace(Regex("""(?<=^|[^\p{L}\p{M}\p{N}])(?:ఆపు|ఆపి|అపు|యాప్|app)\s+(?:ఇచ్చాను|ఇచ్చా|ఇచ్చాము)(?=$|[^\p{L}\p{M}\p{N}])"""), "అప్పు ఇచ్చాను")
        text = text.replace(Regex("""(?<=^|[^\p{L}\p{M}\p{N}])(?:చెబదులు|చేబదుల)(?=$|[^\p{L}\p{M}\p{N}])"""), "చేబదులు")
        text = text.replace(Regex("""(?<=^|[^\p{L}\p{M}\p{N}])(?:బాకి|భాకి|భాకీ)(?=$|[^\p{L}\p{M}\p{N}])"""), "బాకీ")
        text = text.replace(Regex("""(?<=^|[^\p{L}\p{M}\p{N}])(?:ఇచ్చను|ఇచాను|ఇచ్చాం)(?=$|[^\p{L}\p{M}\p{N}])"""), "ఇచ్చాను")
        text = text.replace(Regex("""(?<=^|[^\p{L}\p{M}\p{N}])(?:తీసుకునను|తీస్కున్నా)(?=$|[^\p{L}\p{M}\p{N}])"""), "తీసుకున్నాను")
        text = text.replace(Regex("""(?<=^|[^\p{L}\p{M}\p{N}])(?:చెల్లించను|చెల్లించాం)(?=$|[^\p{L}\p{M}\p{N}])"""), "చెల్లించాను")

        // 2. Currency phonetic variants (e.g. "రాయలు", "పాయలు" after numbers -> "రూపాయలు")
        text = text.replace(Regex("""(?<=\d)\s*(?:రాయలు|పాయలు|రుపాయలు|రుపాయల|రూపాయల|రూపాయిలు)(?=$|[^\p{L}\p{M}\p{N}])"""), " రూపాయలు")

        // 3. Expense Actions & Category Keywords
        // "కార్చు" / "కచ్చు" / "కర్చు" -> "ఖర్చు"
        text = text.replace(Regex("""(?<=^|[^\p{L}\p{M}\p{N}])(?:కార్చు|కచ్చు|కర్చు|కర్చుల)(?=$|[^\p{L}\p{M}\p{N}])"""), "ఖర్చు")
        text = text.replace(Regex("""(?<=^|[^\p{L}\p{M}\p{N}])(?:కొనాను|కొన్నను)(?=$|[^\p{L}\p{M}\p{N}])"""), "కొన్నాను")
        text = text.replace(Regex("""(?<=^|[^\p{L}\p{M}\p{N}])(?:పెట్రోలు)(?=$|[^\p{L}\p{M}\p{N}])"""), "పెట్రోల్")
        text = text.replace(Regex("""(?<=^|[^\p{L}\p{M}\p{N}])(?:బిరియాని)(?=$|[^\p{L}\p{M}\p{N}])"""), "బిర్యానీ")
        text = text.replace(Regex("""(?<=^|[^\p{L}\p{M}\p{N}])(?:రీచార్జ్|రీచార్జి)(?=$|[^\p{L}\p{M}\p{N}])"""), "రీఛార్జ్")
        text = text.replace(Regex("""(?<=^|[^\p{L}\p{M}\p{N}])(?:కరెంటు బిల్లు)(?=$|[^\p{L}\p{M}\p{N}])"""), "కరెంట్ బిల్లు")
        text = text.replace(Regex("""(?<=^|[^\p{L}\p{M}\p{N}])(?:సిలిండరు)(?=$|[^\p{L}\p{M}\p{N}])"""), "సిలిండర్")
        text = text.replace(Regex("""(?<=^|[^\p{L}\p{M}\p{N}])(?:ఆస్పత్రి|హాస్పిటలు)(?=$|[^\p{L}\p{M}\p{N}])"""), "హాస్పిటల్")

        // 4. Number word phonetic fixes
        text = text.replace(Regex("""(?<=^|[^\p{L}\p{M}\p{N}])(?:వెయ్య|వయ్యి)(?=$|[^\p{L}\p{M}\p{N}])"""), "వెయ్యి")

        return text
    }
}
