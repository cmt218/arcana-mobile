package org.arcana.mobile.review

/** One feed scope: `type` is all | brand | location | class_type | instructor,
 *  `value` its slug or id ("" for all), `label` the header until the page loads. */
data class FeedbackScope(val type: String, val value: String, val label: String) {
    companion object {
        val All = FeedbackScope("all", "", "Member feedback")
        fun brand(slug: String, name: String) = FeedbackScope("brand", slug, name)
        fun location(id: Int, label: String) = FeedbackScope("location", id.toString(), label)
        fun classType(brandSlug: String, key: String, label: String) = FeedbackScope("class_type", "$brandSlug:$key", label)
        fun instructor(profileId: Int, name: String) = FeedbackScope("instructor", profileId.toString(), name)
    }
}
