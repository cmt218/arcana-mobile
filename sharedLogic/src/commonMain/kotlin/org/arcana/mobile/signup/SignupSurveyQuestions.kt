package org.arcana.mobile.signup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The onboarding survey a NEW member answers between the signup deep link and
 * the claim-your-name screen (August cohort+). Previously asked on the /beta
 * web page before checkout; moved here to keep the purchase flow frictionless.
 *
 * Originally a 1:1 port of arcana-web `app/beta/survey.ts`. That survey is
 * retired and this is now the only one, so the copy no longer has to match it;
 * reword freely. **Ids, order, and the `__other` suffix still must not change**
 * — the server's Google-Sheet mirror keys its columns off them (arcana-server
 * `memberships/survey_views.py`). Option TEXT is stored as the answer value, so
 * rewording one splits that column's history across two spellings; acceptable
 * (the responses are read by eye), but do it knowingly.
 *
 * Required policy (per founder): every question is required EXCEPT the two
 * final open-floor questions (`anythingElse`, `referredBy`). For `howHeard`,
 * choosing "Other" makes the free-text specify field required.
 */
enum class SurveyQuestionType { Single, Multi, Text }

data class SurveyQuestion(
    /** Stable key — the answers map key and the Google Sheet column. */
    val id: String,
    /** Display number shown to the member. */
    val n: Int,
    /** Section heading this question sits under. */
    val section: String,
    val label: String,
    val hint: String? = null,
    val type: SurveyQuestionType,
    /** Choices for Single / Multi questions. */
    val options: List<String> = emptyList(),
    /** Single only: selecting this option reveals a required "please specify" field. */
    val otherOption: String? = null,
    val required: Boolean,
)

/** Suffix appended to a question id to hold its "Other — please specify" text. */
const val SURVEY_OTHER_SUFFIX = "__other"

val SURVEY_QUESTIONS: List<SurveyQuestion> = listOf(
    SurveyQuestion(
        id = "modalities",
        n = 1,
        section = "Your training",
        label = "Which modalities do you train in regularly?",
        hint = "Select all that apply",
        type = SurveyQuestionType.Multi,
        options = listOf(
            "Hot yoga",
            "Reformer Pilates",
            "Mat Pilates",
            "Indoor cycling",
            "Strength training",
            "Running",
            "Boxing",
            "Barre",
            "HIIT",
            "Dance cardio",
            "Other",
        ),
        required = true,
    ),
    SurveyQuestion(
        id = "weeklyFrequency",
        n = 2,
        section = "Your training",
        label = "How many boutique fitness classes do you take per week on average?",
        type = SurveyQuestionType.Single,
        options = listOf("1–2", "3–4", "5–6", "7+"),
        required = true,
    ),
    SurveyQuestion(
        id = "trainingTimes",
        n = 3,
        section = "Your training",
        label = "When do you typically train?",
        type = SurveyQuestionType.Multi,
        options = listOf(
            "Early morning (before 8am)",
            "Morning (8–11am)",
            "Midday",
            "Evening",
            "It varies",
        ),
        required = true,
    ),
    SurveyQuestion(
        id = "neighborhoods",
        n = 4,
        section = "Your training",
        label = "Which neighborhoods do you work out in most?",
        hint = "Select all that apply",
        type = SurveyQuestionType.Multi,
        options = listOf(
            "Flatiron / Chelsea",
            "Soho / Tribeca",
            "Upper East Side",
            "Upper West Side",
            "Williamsburg",
            "Downtown Brooklyn",
            "Midtown",
            "West Village",
            "Other",
        ),
        required = true,
    ),
    SurveyQuestion(
        id = "fitnessMeaning",
        n = 5,
        section = "Your training",
        label = "Beyond the physical, what does fitness give you?",
        type = SurveyQuestionType.Multi,
        options = listOf(
            "Mental clarity",
            "Community",
            "Routine and structure",
            "Stress release",
            "Identity / who I am",
            "Performance",
            "Social life",
        ),
        required = true,
    ),
    SurveyQuestion(
        id = "communityState",
        n = 6,
        section = "You and your community",
        label = "What does community look like in your fitness life right now?",
        type = SurveyQuestionType.Single,
        options = listOf(
            "I have a core group I train with regularly",
            "I know people at my studios but we don't connect outside",
            "I mostly train solo",
            "I'm actively looking for a fitness community",
        ),
        required = true,
    ),
    SurveyQuestion(
        id = "eventInterest",
        n = 7,
        section = "You and your community",
        label = "Outside your regular classes, which of these would you actually show up for?",
        hint = "Select all that apply. Be honest, not aspirational.",
        type = SurveyQuestionType.Multi,
        options = listOf(
            "Group run",
            "Studio takeover with a featured instructor",
            "Wellness workshop or speaker",
            "Recovery experience (sauna, cold plunge, contrast therapy)",
            "Happy hour / casual drinks",
            "Brunch",
            "Fitness challenge or friendly competition",
            "Product or brand experience",
        ),
        required = true,
    ),
    SurveyQuestion(
        id = "monthlySpend",
        n = 8,
        section = "You and your community",
        label = "What's your current monthly spend on boutique fitness?",
        type = SurveyQuestionType.Single,
        options = listOf("Under $200", "$200–$350", "$350–$500", "$500+"),
        required = true,
    ),
    SurveyQuestion(
        id = "soloOrSocial",
        n = 9,
        section = "You and your community",
        label = "Are you more likely to try a new studio alone or with someone you know?",
        type = SurveyQuestionType.Single,
        options = listOf(
            "Alone, I like exploring on my own",
            "Either, depends on the studio",
            "With someone, more likely to commit",
        ),
        required = true,
    ),
    SurveyQuestion(
        id = "referralIntent",
        n = 10,
        section = "You and your community",
        label = "Is there someone in your life you'd already want to bring into Arcana?",
        hint = "No commitment, just curious if someone comes to mind.",
        type = SurveyQuestionType.Single,
        options = listOf("Yes, someone specific", "Maybe a few people", "Not yet"),
        required = true,
    ),
    SurveyQuestion(
        id = "howHeard",
        n = 11,
        section = "One last thing",
        label = "How did you hear about Arcana?",
        type = SurveyQuestionType.Single,
        options = listOf("A friend", "Instagram", "TikTok", "Facebook", "Reddit", "A studio or instructor", "Other"),
        otherOption = "Other",
        required = true,
    ),
    SurveyQuestion(
        id = "anythingElse",
        n = 12,
        section = "One last thing",
        label = "Anything else you want us to know?",
        hint = "Open floor. What would make Arcana actually worth it to you?",
        type = SurveyQuestionType.Text,
        required = false,
    ),
    SurveyQuestion(
        id = "referredBy",
        n = 13,
        section = "One last thing",
        label = "Did someone refer you to Arcana?",
        hint = "Optional, let us know who so we can thank them.",
        type = SurveyQuestionType.Text,
        required = false,
    ),
)

/**
 * One member's in-progress answers. Singles/texts live in [singles]/[texts];
 * multi selections in [multis]. `__other` specify texts live in [texts] under
 * `<id>__other`, mirroring the web's flat answers map.
 */
data class SurveyAnswers(
    val singles: Map<String, String> = emptyMap(),
    val multis: Map<String, List<String>> = emptyMap(),
    val texts: Map<String, String> = emptyMap(),
)

/**
 * Ids of required questions not yet answered — the port of survey.ts
 * `missingRequired`. Empty means the survey is complete and may submit.
 */
fun missingRequired(answers: SurveyAnswers): List<String> = buildList {
    for (q in SURVEY_QUESTIONS) {
        if (!q.required) continue
        when (q.type) {
            SurveyQuestionType.Multi ->
                if (answers.multis[q.id].isNullOrEmpty()) add(q.id)

            SurveyQuestionType.Single -> {
                val value = answers.singles[q.id]
                if (value.isNullOrBlank()) {
                    add(q.id)
                } else if (q.otherOption != null && value == q.otherOption &&
                    answers.texts[q.id + SURVEY_OTHER_SUFFIX].isNullOrBlank()
                ) {
                    // "Other" on a single-choice question requires the specify text.
                    add(q.id)
                }
            }

            SurveyQuestionType.Text ->
                if (answers.texts[q.id].isNullOrBlank()) add(q.id)
        }
    }
}

/** Count of questions with any answer — for the progress stamp + telemetry. */
fun answeredCount(answers: SurveyAnswers): Int = SURVEY_QUESTIONS.count { q ->
    when (q.type) {
        SurveyQuestionType.Multi -> !answers.multis[q.id].isNullOrEmpty()
        SurveyQuestionType.Single -> !answers.singles[q.id].isNullOrBlank()
        SurveyQuestionType.Text -> !answers.texts[q.id].isNullOrBlank()
    }
}

/**
 * The flat wire payload the server stores opaquely: question id → string or
 * list-of-strings, plus `<id>__other` entries — the same shape the July web
 * survey submitted. Blank optional texts are omitted (not sent as "").
 */
fun answersToJson(answers: SurveyAnswers): JsonObject {
    val entries = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
    for (q in SURVEY_QUESTIONS) {
        when (q.type) {
            SurveyQuestionType.Multi -> answers.multis[q.id]
                ?.takeIf { it.isNotEmpty() }
                ?.let { entries[q.id] = JsonArray(it.map(::JsonPrimitive)) }

            SurveyQuestionType.Single -> answers.singles[q.id]
                ?.takeIf { it.isNotBlank() }
                ?.let { value ->
                    entries[q.id] = JsonPrimitive(value)
                    if (q.otherOption != null && value == q.otherOption) {
                        answers.texts[q.id + SURVEY_OTHER_SUFFIX]
                            ?.takeIf { it.isNotBlank() }
                            ?.let { entries[q.id + SURVEY_OTHER_SUFFIX] = JsonPrimitive(it.trim()) }
                    }
                }

            SurveyQuestionType.Text -> answers.texts[q.id]
                ?.takeIf { it.isNotBlank() }
                ?.let { entries[q.id] = JsonPrimitive(it.trim()) }
        }
    }
    return JsonObject(entries)
}
