package com.example.poseexercise.data

data class PostureFeedback(
    val exerciseType: String,
    val feedbackMessages: List<String> = emptyList(),
    val severity: FeedbackSeverity = FeedbackSeverity.GOOD,
    val postureScore: Int = 100,
    val bodyPartHints: List<BodyPartHint> = emptyList()
)

enum class FeedbackSeverity(val level: Int) {
    GOOD(0),
    MILD(1),
    WARNING(2),
    BAD(3)
}

data class BodyPartHint(
    val bodyPart: BodyPart,
    val angle: Double,
    val targetMin: Double,
    val targetMax: Double,
    val message: String,
    val correctionAction: String
)

enum class BodyPart(val displayName: String) {
    ELBOW("Coudes"),
    KNEE("Genoux"),
    BACK("Dos"),
    HIP("Hanches"),
    SHOULDER("Épaules"),
    WRIST("Poignets"),
    ANKLE("Chevilles"),
    TORSO("Torse"),
    HEAD("Tête"),
    FEET("Pieds")
}
