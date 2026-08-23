package org.arcana.mobile.signup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Locks the survey schema + gating to the web contract (arcana-web
 * app/beta/survey.ts) — ids, order, required policy, and the wire shape the
 * server's Google-Sheet mirror depends on.
 */
class SignupSurveyQuestionsTest {

    /** Brand rule: no em/en dashes in member-facing copy. Numeric ranges
     *  ("3–4", "$200–$350", "8–11am") are the one exception — an en dash is
     *  correct typography there, so this allows one only between digits. */
    @Test fun `no AI-tell dashes in any member-facing survey string`() {
        // A range's en dash always follows a digit ("3–4", "$200–$350",
        // "8–11am"); a prose one follows a space. That is the whole rule.
        val numericRange = Regex("[0-9]\u2013")
        fun check(what: String, text: String) {
            assertFalse(text.contains('—'), "em dash in $what: $text")
            val strayEnDashes = text.count { it == '–' } - numericRange.findAll(text).count()
            assertEquals(0, strayEnDashes, "non-numeric en dash in $what: $text")
        }
        SURVEY_QUESTIONS.forEach { q ->
            check("label ${q.id}", q.label)
            q.hint?.let { check("hint ${q.id}", it) }
            q.options.forEach { check("option ${q.id}", it) }
            q.section.let { check("section ${q.id}", it) }
        }
    }


    private fun completeAnswers() = SurveyAnswers(
        singles = mapOf(
            "weeklyFrequency" to "3–4",
            "communityState" to "I mostly train solo",
            "monthlySpend" to "$350–$500",
            "soloOrSocial" to "Either, depends on the studio",
            "referralIntent" to "Not yet",
            "howHeard" to "Instagram",
        ),
        multis = mapOf(
            "modalities" to listOf("Hot yoga", "Boxing"),
            "trainingTimes" to listOf("Evening"),
            "neighborhoods" to listOf("Williamsburg"),
            "fitnessMeaning" to listOf("Community"),
            "eventInterest" to listOf("Group run"),
        ),
    )

    @Test
    fun `schema matches the web contract`() {
        assertEquals(13, SURVEY_QUESTIONS.size)
        assertEquals(
            listOf(
                "modalities", "weeklyFrequency", "trainingTimes", "neighborhoods",
                "fitnessMeaning", "communityState", "eventInterest", "monthlySpend",
                "soloOrSocial", "referralIntent", "howHeard", "anythingElse", "referredBy",
            ),
            SURVEY_QUESTIONS.map { it.id },
        )
        // Required policy: everything except the two open-floor text questions.
        assertEquals(
            listOf("anythingElse", "referredBy"),
            SURVEY_QUESTIONS.filterNot { it.required }.map { it.id },
        )
        // howHeard is the only Other-reveals-specify question.
        assertEquals(
            listOf("howHeard"),
            SURVEY_QUESTIONS.filter { it.otherOption != null }.map { it.id },
        )
        assertEquals("__other", SURVEY_OTHER_SUFFIX)
    }

    @Test
    fun `empty answers are missing all eleven required questions`() {
        assertEquals(11, missingRequired(SurveyAnswers()).size)
    }

    @Test
    fun `complete answers pass and optional text stays optional`() {
        assertTrue(missingRequired(completeAnswers()).isEmpty())
    }

    @Test
    fun `howHeard Other requires the specify text`() {
        val other = completeAnswers().let {
            it.copy(singles = it.singles + ("howHeard" to "Other"))
        }
        assertEquals(listOf("howHeard"), missingRequired(other))
        val specified = other.copy(texts = mapOf("howHeard__other" to "A podcast"))
        assertTrue(missingRequired(specified).isEmpty())
    }

    @Test
    fun `empty multi selection counts as missing`() {
        val cleared = completeAnswers().let {
            it.copy(multis = it.multis + ("modalities" to emptyList()))
        }
        assertEquals(listOf("modalities"), missingRequired(cleared))
    }

    @Test
    fun `answersToJson mirrors the web wire shape`() {
        val answers = completeAnswers().let {
            it.copy(
                singles = it.singles + ("howHeard" to "Other"),
                texts = mapOf(
                    "howHeard__other" to " A podcast ",
                    "anythingElse" to "Excited!",
                    "referredBy" to "",  // blank optional omitted from the payload
                ),
            )
        }
        val json = answersToJson(answers)
        assertEquals(JsonArray(listOf(JsonPrimitive("Hot yoga"), JsonPrimitive("Boxing"))), json["modalities"])
        assertEquals(JsonPrimitive("3–4"), json["weeklyFrequency"])
        assertEquals(JsonPrimitive("Other"), json["howHeard"])
        assertEquals(JsonPrimitive("A podcast"), json["howHeard__other"])
        assertEquals(JsonPrimitive("Excited!"), json["anythingElse"])
        assertNull(json["referredBy"])
        assertFalse("referredBy" in json.keys)
    }

    @Test
    fun `answeredCount counts each question at most once`() {
        assertEquals(0, answeredCount(SurveyAnswers()))
        assertEquals(11, answeredCount(completeAnswers()))
        assertEquals(
            12,
            answeredCount(completeAnswers().copy(texts = mapOf("anythingElse" to "hi"))),
        )
    }
}
