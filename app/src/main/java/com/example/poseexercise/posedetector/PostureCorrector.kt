package com.example.poseexercise.posedetector

import com.example.poseexercise.data.BodyPart
import com.example.poseexercise.data.BodyPartHint
import com.example.poseexercise.data.FeedbackSeverity
import com.example.poseexercise.data.PostureFeedback
import com.example.poseexercise.posedetector.classification.PoseClassifierProcessor
import com.google.mlkit.vision.common.PointF3D
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class PostureCorrector {

    companion object {
        private const val FRAME_SKIP = 3
        private const val ANGLE_TOLERANCE = 12.0
        private const val KNEE_OVER_TOE_THRESHOLD = 10f
        private var frameCounter = 0
        private var lastFeedback: PostureFeedback? = null
    }

    fun analyze(exerciseClass: String, pose: Pose): PostureFeedback {
        frameCounter++
        if (frameCounter % FRAME_SKIP != 0) {
            return lastFeedback ?: PostureFeedback(exerciseClass)
        }

        val feedback = when (exerciseClass) {
            PoseClassifierProcessor.PUSHUPS_CLASS -> analyzePushup(pose)
            PoseClassifierProcessor.SQUATS_CLASS -> analyzeSquat(pose)
            PoseClassifierProcessor.LUNGES_CLASS -> analyzeLunge(pose)
            PoseClassifierProcessor.SITUP_UP_CLASS -> analyzeSitup(pose)
            PoseClassifierProcessor.DEAD_LIFT_CLASS -> analyzeDeadlift(pose)
            PoseClassifierProcessor.CHEST_PRESS_CLASS -> analyzeChestPress(pose)
            PoseClassifierProcessor.SHOULDER_PRESS_CLASS -> analyzeShoulderPress(pose)
            PoseClassifierProcessor.PULL_UP_CLASS -> analyzePullUp(pose)
            PoseClassifierProcessor.DIP_CLASS -> analyzeDip(pose)
            else -> PostureFeedback(exerciseClass)
        }
        lastFeedback = feedback
        return feedback
    }

    private fun analyzePushup(pose: Pose): PostureFeedback {
        val hints = mutableListOf<BodyPartHint>()
        val messages = mutableListOf<String>()

        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val le = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val lw = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val re = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val rw = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val la = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val ra = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        checkElbowAngle(ls, le, lw, BodyPart.ELBOW, 75.0, 110.0, "Pliez les coudes à 90°", hints, messages)
        checkElbowAngle(rs, re, rw, BodyPart.ELBOW, 75.0, 110.0, "Pliez les coudes à 90°", hints, messages)

        checkBodyAlignment(ls, lh, la, "dos", hints, messages)
        checkBodyAlignment(rs, rh, ra, "dos", hints, messages)

        val handsWidth = distance(
            lw?.position3D ?: return feedback(hints, messages),
            rw?.position3D ?: return feedback(hints, messages)
        )
        val shoulderWidth = distance(
            ls?.position3D ?: return feedback(hints, messages),
            rs?.position3D ?: return feedback(hints, messages)
        )
        val widthRatio = handsWidth / max(shoulderWidth, 1f)
        if (widthRatio < 1.2f) {
            hints.add(BodyPartHint(BodyPart.WRIST, widthRatio.toDouble(), 1.2, 2.0, "Mains trop serrées", "Écartez les mains plus que les épaules"))
            messages.add("Écartez les mains (largeur épaules + 20%)")
        } else if (widthRatio > 2.5f) {
            hints.add(BodyPartHint(BodyPart.WRIST, widthRatio.toDouble(), 1.2, 2.0, "Mains trop écartées", "Rapprochez les mains"))
            messages.add("Mains trop écartées")
        }

        return feedback(hints, messages)
    }

    private fun analyzeSquat(pose: Pose): PostureFeedback {
        val hints = mutableListOf<BodyPartHint>()
        val messages = mutableListOf<String>()

        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val lk = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rk = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val la = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val ra = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        checkKneeAngle(lh, lk, la, BodyPart.KNEE, 70.0, 110.0, "Pliez les genoux à ~90°", hints, messages)
        checkKneeAngle(rh, rk, ra, BodyPart.KNEE, 70.0, 110.0, "Pliez les genoux à ~90°", hints, messages)

        checkKneeOverToe(lk, la, BodyPart.KNEE, hints, messages)
        checkKneeOverToe(rk, ra, BodyPart.KNEE, hints, messages)

        checkBackAngle(ls, lh, lk, BodyPart.BACK, 35.0, 80.0, "Gardez le dos droit et le torse relevé", hints, messages)
        checkBackAngle(rs, rh, rk, BodyPart.BACK, 35.0, 80.0, "Gardez le dos droit et le torse relevé", hints, messages)

        if (lh != null && rh != null && la != null && ra != null) {
            val hipWidth = distance(lh.position3D, rh.position3D)
            val stanceWidth = distance(la.position3D, ra.position3D)
            val stanceRatio = stanceWidth / max(hipWidth, 1f)
            if (stanceRatio < 1.1f) {
                hints.add(BodyPartHint(BodyPart.FEET, stanceRatio.toDouble(), 1.1, 1.5, "Pieds trop serrés", "Écartez les pieds à largeur des épaules"))
                messages.add("Écartez plus les pieds")
            }
        }

        return feedback(hints, messages)
    }

    private fun analyzeLunge(pose: Pose): PostureFeedback {
        val hints = mutableListOf<BodyPartHint>()
        val messages = mutableListOf<String>()

        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val lk = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rk = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val la = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val ra = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        checkKneeAngle(lh, lk, la, BodyPart.KNEE, 75.0, 110.0, "Genou avant à 90°", hints, messages)
        checkKneeAngle(rh, rk, ra, BodyPart.KNEE, 75.0, 110.0, "Genou avant à 90°", hints, messages)

        checkKneeOverToe(lk, la, BodyPart.KNEE, hints, messages)
        checkKneeOverToe(rk, ra, BodyPart.KNEE, hints, messages)

        if (lh != null && lk != null && la != null) {
            val backKneeAngle = angleBetween3D(rh?.position3D ?: lh.position3D, rk?.position3D ?: lk.position3D, ra?.position3D ?: la.position3D)
            if (backKneeAngle > 140.0) {
                hints.add(BodyPartHint(BodyPart.KNEE, backKneeAngle, 75.0, 110.0, "Genou arrière trop droit", "Pliez plus le genou arrière vers le sol"))
                messages.add("Pliez plus le genou arrière")
            }
            if (backKneeAngle < 40.0) {
                hints.add(BodyPartHint(BodyPart.KNEE, backKneeAngle, 75.0, 110.0, "Genou arrière trop plié", "Redressez légèrement le genou arrière"))
                messages.add("Genou arrière trop près du sol")
            }
        }

        val shoulderAvg = avgOrNull(ls?.position3D, rs?.position3D)
        val hipAvg = avgOrNull(lh?.position3D, rh?.position3D)
        if (shoulderAvg != null && hipAvg != null) {
            val verticalAngle = abs(angleFromVertical(shoulderAvg, hipAvg))
            if (verticalAngle > 15.0) {
                hints.add(BodyPartHint(BodyPart.TORSO, verticalAngle, 0.0, 15.0, "Torse penché en avant", "Redressez le torse, engagez les abdominaux"))
                messages.add("Gardez le torse droit")
            }
        }

        return feedback(hints, messages)
    }

    private fun analyzeSitup(pose: Pose): PostureFeedback {
        val hints = mutableListOf<BodyPartHint>()
        val messages = mutableListOf<String>()

        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val lk = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rk = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)

        if (ls != null && lh != null && lk != null) {
            val torsoAngle = angleBetween3D(ls.position3D, lh.position3D, lk.position3D)
            if (torsoAngle < 25.0) {
                hints.add(BodyPartHint(BodyPart.TORSO, torsoAngle, 25.0, 90.0, "Tor se pas assez relevé", "Montez plus le torse en contractant les abdos"))
                messages.add("Montez plus le torse")
            } else if (torsoAngle > 110.0) {
                hints.add(BodyPartHint(BodyPart.TORSO, torsoAngle, 25.0, 90.0, "Tor se trop relevé", "Redescendez doucement"))
                messages.add("Ne forcez pas trop la montée")
            }
        }

        if (lh != null && rh != null && lk != null && rk != null) {
            val hipToKnee = avgPos(lh.position3D, rh.position3D)
            val kneePos = avgPos(lk.position3D, rk.position3D)
            val dist = distance(hipToKnee, kneePos)
            if (dist > 55f) {
                hints.add(BodyPartHint(BodyPart.FEET, dist.toDouble(), 0.0, 55.0, "Pieds trop loin des fesses", "Rapprochez les pieds"))
                messages.add("Rapprochez les pieds des fesses")
            }
        }

        val nk = pose.getPoseLandmark(PoseLandmark.NOSE)
        if (nk != null && lh != null) {
            val tuckAngle = angleBetween3D(nk.position3D, lh.position3D, lk?.position3D ?: lh.position3D)
            if (tuckAngle < 30.0) {
                hints.add(BodyPartHint(BodyPart.HEAD, tuckAngle, 30.0, 90.0, "Menton pas assez relevé", "Regardez vers le haut"))
            }
        }

        return feedback(hints, messages)
    }

    private fun analyzeDeadlift(pose: Pose): PostureFeedback {
        val hints = mutableListOf<BodyPartHint>()
        val messages = mutableListOf<String>()

        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val lk = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rk = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val la = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val ra = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        if (ls != null && lh != null && lk != null) {
            val hipHingeAngle = angleBetween3D(ls.position3D, lh.position3D, lk.position3D)
            if (hipHingeAngle < 10.0) {
                hints.add(BodyPartHint(BodyPart.HIP, hipHingeAngle, 10.0, 40.0, "Hanches pas assez en arrière", "Poussez les hanches en arrière (hip hinge)"))
                messages.add("Poussez les hanches en arrière")
            }
            if (hipHingeAngle > 45.0) {
                hints.add(BodyPartHint(BodyPart.BACK, hipHingeAngle, 10.0, 40.0, "Dos trop parallèle au sol", "Levez plus les hanches"))
                messages.add("Dos trop bas, levez les hanches")
            }
        }

        if (lh != null && lk != null && la != null) {
            val kneeAngle = angleBetween3D(lh.position3D, lk.position3D, la.position3D)
            if (kneeAngle > 160.0) {
                hints.add(BodyPartHint(BodyPart.KNEE, kneeAngle, 140.0, 160.0, "Genoux trop verrouillés", "Gardez une micro-flexion des genoux"))
                messages.add("Gardez les genoux légèrement fléchis")
            }
        }

        if (ls != null && lh != null && rs != null && rh != null) {
            val shoulderPos = avgPos(ls.position3D, rs.position3D)
            val hipPos = avgPos(lh.position3D, rh.position3D)
            val shoulderHipDist = distance(shoulderPos, hipPos)
            val vertDist = abs(shoulderPos.y - hipPos.y)
            val horizDist = abs(shoulderPos.x - hipPos.x)
            if (horizDist / max(vertDist, 1f) > 0.8f) {
                hints.add(BodyPartHint(BodyPart.BACK, (horizDist / max(vertDist, 1f)).toDouble(), 0.0, 0.8, "Épaules trop en avant", "Tirez les épaules en arrière"))
                messages.add("Tirez les épaules en arrière")
            }
        }

        val head = pose.getPoseLandmark(PoseLandmark.NOSE)
        if (head != null && ls != null) {
            if (abs(head.position3D.x - ls.position3D.x) > 15f) {
                hints.add(BodyPartHint(BodyPart.HEAD, abs(head.position3D.x - ls.position3D.x).toDouble(), 0.0, 15.0, "Tête pas alignée", "Gardez la tête dans le prolongement du dos"))
                messages.add("Gardez la tête alignée avec le dos")
            }
        }

        checkKneeAngle(lh, lk, la, BodyPart.KNEE, 140.0, 175.0, "Détendez les genoux", hints, messages)

        return feedback(hints, messages)
    }

    private fun analyzeChestPress(pose: Pose): PostureFeedback {
        val hints = mutableListOf<BodyPartHint>()
        val messages = mutableListOf<String>()

        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val le = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val lw = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val re = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val rw = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        checkElbowAngle(ls, le, lw, BodyPart.ELBOW, 60.0, 100.0, "Descendez les coudes à 90°", hints, messages)
        checkElbowAngle(rs, re, rw, BodyPart.ELBOW, 60.0, 100.0, "Descendez les coudes à 90°", hints, messages)

        if (le != null && ls != null && re != null && rs != null) {
            val leftDepth = ls.position3D.y - le.position3D.y
            val rightDepth = rs.position3D.y - re.position3D.y
            if (leftDepth < 0f || rightDepth < 0f) {
                hints.add(BodyPartHint(BodyPart.ELBOW, min(leftDepth, rightDepth).toDouble(), 0.0, 50.0, "Coudes pas assez bas", "Abaissez les coudes jusqu'à 90°"))
                messages.add("Ne descendez pas assez les coudes")
            }
            if (leftDepth > 60f || rightDepth > 60f) {
                hints.add(BodyPartHint(BodyPart.ELBOW, max(leftDepth, rightDepth).toDouble(), 0.0, 60.0, "Coudes trop bas", "Ne descendez pas trop les coudes"))
                messages.add("Ne descendez pas trop les coudes")
            }
        }

        val wristDist = distance(lw?.position3D ?: return feedback(hints, messages), rw?.position3D ?: return feedback(hints, messages))
        val shoulderDist = distance(ls?.position3D ?: return feedback(hints, messages), rs?.position3D ?: return feedback(hints, messages))
        if (wristDist / max(shoulderDist, 1f) < 0.7f) {
            hints.add(BodyPartHint(BodyPart.WRIST, (wristDist / max(shoulderDist, 1f)).toDouble(), 0.7, 1.3, "Mains trop serrées", "Écartez les mains à largeur d'épaules"))
            messages.add("Mains plus écartées")
        }

        return feedback(hints, messages)
    }

    private fun analyzeShoulderPress(pose: Pose): PostureFeedback {
        val hints = mutableListOf<BodyPartHint>()
        val messages = mutableListOf<String>()

        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val le = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val lw = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val re = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val rw = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        checkElbowAngle(ls, le, lw, BodyPart.ELBOW, 65.0, 100.0, "Descendez les coudes à 90°", hints, messages)
        checkElbowAngle(rs, re, rw, BodyPart.ELBOW, 65.0, 100.0, "Descendez les coudes à 90°", hints, messages)

        if (lw != null && ls != null && rw != null && rs != null) {
            val leftOverhead = lw.position3D.y < ls.position3D.y
            val rightOverhead = rw.position3D.y < rs.position3D.y
            if (!leftOverhead || !rightOverhead) {
                hints.add(BodyPartHint(BodyPart.WRIST, 0.0, 0.0, 1.0, "Bras pas au-dessus de la tête", "Tendez les bras vers le plafond"))
                messages.add("Levez les bras au-dessus de la tête")
            }
        }

        if (le != null && ls != null && re != null && rs != null) {
            if (le.position3D.x < ls.position3D.x || re.position3D.x > rs.position3D.x) {
                hints.add(BodyPartHint(BodyPart.ELBOW, 0.0, 0.0, 1.0, "Coudes pas alignés", "Gardez les coudes alignés avec les épaules"))
                messages.add("Gardez les coudes alignés avec les épaules")
            }
        }

        return feedback(hints, messages)
    }

    private fun analyzePullUp(pose: Pose): PostureFeedback {
        val hints = mutableListOf<BodyPartHint>()
        val messages = mutableListOf<String>()

        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val le = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val lw = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val re = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val rw = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)

        checkElbowAngle(ls, le, lw, BodyPart.ELBOW, 30.0, 90.0, "Tirez jusqu'à ce que le menton dépasse la barre", hints, messages)
        checkElbowAngle(rs, re, rw, BodyPart.ELBOW, 30.0, 90.0, "Tirez jusqu'à ce que le menton dépasse la barre", hints, messages)

        val wristDist = distance(lw?.position3D ?: return feedback(hints, messages), rw?.position3D ?: return feedback(hints, messages))
        val shoulderDist = distance(ls?.position3D ?: return feedback(hints, messages), rs?.position3D ?: return feedback(hints, messages))
        val gripRatio = wristDist / max(shoulderDist, 1f)
        if (gripRatio < 0.8f) {
            hints.add(BodyPartHint(BodyPart.WRIST, gripRatio.toDouble(), 0.8, 1.5, "Prise trop serrée", "Écartez les mains à largeur d'épaules"))
            messages.add("Écartez plus les mains")
        } else if (gripRatio > 1.8f) {
            hints.add(BodyPartHint(BodyPart.WRIST, gripRatio.toDouble(), 0.8, 1.5, "Prise trop large", "Rapprochez les mains"))
            messages.add("Rapprochez les mains")
        }

        if (le != null && ls != null && re != null && rs != null) {
            val leftChinPull = lw?.position3D?.y?.let { lwPosY ->
                if (lh != null) ls.position3D.y - lh.position3D.y > 30f else false
            } ?: false
            if (!leftChinPull) {
                hints.add(BodyPartHint(BodyPart.HEAD, 0.0, 0.0, 1.0, "Pas assez haut", "Tirez jusqu'à ce que le menton dépasse la barre"))
                messages.add("Tirez plus haut")
            }
        }

        checkBodyAlignment(ls, lh, pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE), "corps", hints, messages)
        checkBodyAlignment(rs, rh, pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE), "corps", hints, messages)

        return feedback(hints, messages)
    }

    private fun analyzeDip(pose: Pose): PostureFeedback {
        val hints = mutableListOf<BodyPartHint>()
        val messages = mutableListOf<String>()

        val ls = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val le = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val lw = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rs = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val re = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val rw = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val lh = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rh = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val la = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val ra = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        checkElbowAngle(ls, le, lw, BodyPart.ELBOW, 60.0, 100.0, "Descendez les coudes à 90°", hints, messages)
        checkElbowAngle(rs, re, rw, BodyPart.ELBOW, 60.0, 100.0, "Descendez les coudes à 90°", hints, messages)

        if (le != null && re != null) {
            if (le.position3D.x > ls?.position3D?.x ?: le.position3D.x ||
                re.position3D.x < rs?.position3D?.x ?: re.position3D.x
            ) {
                hints.add(BodyPartHint(BodyPart.ELBOW, 0.0, 0.0, 1.0, "Coudes écartés", "Gardez les coudes près du corps"))
                messages.add("Ne laissez pas les coudes s'écarter")
            }
        }

        if (ls != null && lh != null && la != null && rs != null && rh != null && ra != null) {
            val shoulderToHipDist = distance(avgPos(ls.position3D, rs.position3D), avgPos(lh.position3D, rh.position3D))
            val hipToAnkleDist = distance(avgPos(lh.position3D, rh.position3D), avgPos(la.position3D, ra.position3D))
            val legRatio = hipToAnkleDist / max(shoulderToHipDist, 1f)
            if (legRatio < 2.0f) {
                hints.add(BodyPartHint(BodyPart.FEET, legRatio.toDouble(), 2.0, 4.0, "Mauvaise position verticale", "Gardez les jambes tendues vers le bas"))
                messages.add("Gardez les jambes tendues")
            }
        }

        checkBodyAlignment(ls, lh, la, "corps", hints, messages)
        checkBodyAlignment(rs, rh, ra, "corps", hints, messages)

        return feedback(hints, messages)
    }

    private fun checkElbowAngle(
        shoulder: PoseLandmark?, elbow: PoseLandmark?, wrist: PoseLandmark?,
        bodyPart: BodyPart, targetMin: Double, targetMax: Double,
        messagePrefix: String, hints: MutableList<BodyPartHint>, messages: MutableList<String>
    ) {
        if (shoulder != null && elbow != null && wrist != null) {
            val angle = angleBetween3D(shoulder.position3D, elbow.position3D, wrist.position3D)
            if (angle < targetMin - ANGLE_TOLERANCE) {
                hints.add(BodyPartHint(bodyPart, angle, targetMin, targetMax, "Angle trop fermé", "$messagePrefix (${angle.toInt()}°)"))
                messages.add("$messagePrefix (${angle.toInt()}°)")
            } else if (angle > targetMax + ANGLE_TOLERANCE) {
                hints.add(BodyPartHint(bodyPart, angle, targetMin, targetMax, "Angle trop ouvert", "$messagePrefix (${angle.toInt()}°)"))
                messages.add("$messagePrefix (${angle.toInt()}°)")
            }
        }
    }

    private fun checkKneeAngle(
        hip: PoseLandmark?, knee: PoseLandmark?, ankle: PoseLandmark?,
        bodyPart: BodyPart, targetMin: Double, targetMax: Double,
        messagePrefix: String, hints: MutableList<BodyPartHint>, messages: MutableList<String>
    ) {
        if (hip != null && knee != null && ankle != null) {
            val angle = angleBetween3D(hip.position3D, knee.position3D, ankle.position3D)
            if (angle < targetMin - ANGLE_TOLERANCE) {
                hints.add(BodyPartHint(bodyPart, angle, targetMin, targetMax, "Angle genou trop fermé", "$messagePrefix (${angle.toInt()}°)"))
                messages.add("$messagePrefix (${angle.toInt()}°)")
            } else if (angle > targetMax + ANGLE_TOLERANCE) {
                hints.add(BodyPartHint(bodyPart, angle, targetMin, targetMax, "Angle genou trop ouvert", "$messagePrefix (${angle.toInt()}°)"))
                messages.add("$messagePrefix (${angle.toInt()}°)")
            }
        }
    }

    private fun checkKneeOverToe(
        knee: PoseLandmark?, ankle: PoseLandmark?,
        bodyPart: BodyPart, hints: MutableList<BodyPartHint>, messages: MutableList<String>
    ) {
        if (knee != null && ankle != null) {
            val forwardDist = knee.position3D.x - ankle.position3D.x
            if (forwardDist > KNEE_OVER_TOE_THRESHOLD) {
                hints.add(BodyPartHint(bodyPart, forwardDist.toDouble(), 0.0, KNEE_OVER_TOE_THRESHOLD.toDouble(), "Genou trop avancé", "Reculez les hanches, gardez les genoux derrière les orteils"))
                messages.add("Genoux trop avancés, reculez les hanches")
            } else if (forwardDist < -KNEE_OVER_TOE_THRESHOLD * 2) {
                hints.add(BodyPartHint(bodyPart, forwardDist.toDouble(), 0.0, KNEE_OVER_TOE_THRESHOLD.toDouble(), "Genou trop reculé", "Avancez un peu les genoux"))
                messages.add("Genoux trop en arrière")
            }
        }
    }

    private fun checkBackAngle(
        shoulder: PoseLandmark?, hip: PoseLandmark?, knee: PoseLandmark?,
        bodyPart: BodyPart, targetMin: Double, targetMax: Double,
        message: String, hints: MutableList<BodyPartHint>, messages: MutableList<String>
    ) {
        if (shoulder != null && hip != null && knee != null) {
            val angle = angleBetween3D(shoulder.position3D, hip.position3D, knee.position3D)
            val vertAngle = angleFromVertical(shoulder.position3D, hip.position3D)
            if (angle < targetMin - ANGLE_TOLERANCE || angle > targetMax + ANGLE_TOLERANCE) {
                hints.add(BodyPartHint(bodyPart, angle, targetMin, targetMax, message, if (angle < targetMin) "Penchez-vous plus aux hanches" else "Redressez plus le dos"))
                messages.add(message)
            }
        }
    }

    private fun checkBodyAlignment(
        shoulder: PoseLandmark?, hip: PoseLandmark?, ankle: PoseLandmark?,
        bodyPartName: String, hints: MutableList<BodyPartHint>, messages: MutableList<String>
    ) {
        if (shoulder != null && hip != null && ankle != null) {
            val s = shoulder.position3D
            val h = hip.position3D
            val a = ankle.position3D
            val vertAngle = abs(angleFromVertical(avgPos(s, h), h))
            if (vertAngle > 20.0) {
                hints.add(BodyPartHint(BodyPart.BACK, vertAngle, 0.0, 20.0, "$bodyPartName pas droit", "Contractez les abdominaux et gardez le $bodyPartName droit"))
                messages.add("Gardez le $bodyPartName droit")
            }
        }
    }

    private fun angleBetween3D(a: PointF3D, b: PointF3D, c: PointF3D): Double {
        val v1x = a.x - b.x; val v1y = a.y - b.y; val v1z = a.z - b.z
        val v2x = c.x - b.x; val v2y = c.y - b.y; val v2z = c.z - b.z
        val dot = v1x * v2x + v1y * v2y + v1z * v2z
        val mag1 = sqrt(v1x * v1x + v1y * v1y + v1z * v1z)
        val mag2 = sqrt(v2x * v2x + v2y * v2y + v2z * v2z)
        if (mag1 < 1e-6f || mag2 < 1e-6f) return 0.0
        val cosAngle = (dot / (mag1 * mag2)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosAngle).toDouble())
    }

    private fun angleFromVertical(top: PointF3D, bottom: PointF3D): Double {
        val dx = top.x - bottom.x
        val dy = top.y - bottom.y
        if (abs(dy) < 1e-6f) return 90.0
        return Math.toDegrees(atan(abs(dx / dy)))
    }

    private fun atan(value: Float): Double {
        return kotlin.math.atan(value.toDouble())
    }

    private fun distance(a: PointF3D, b: PointF3D): Float {
        return sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
    }

    private fun avgPos(a: PointF3D, b: PointF3D): PointF3D {
        return PointF3D.from((a.x + b.x) / 2f, (a.y + b.y) / 2f, (a.z + b.z) / 2f)
    }

    private fun avgOrNull(a: PointF3D?, b: PointF3D?): PointF3D? {
        if (a == null && b == null) return null
        if (a == null) return b
        if (b == null) return a
        return avgPos(a, b)
    }

    private fun feedback(hints: MutableList<BodyPartHint>, messages: MutableList<String>): PostureFeedback {
        val numHints = hints.size
        val severity = when {
            numHints >= 3 -> FeedbackSeverity.BAD
            numHints == 2 -> FeedbackSeverity.WARNING
            numHints == 1 -> FeedbackSeverity.MILD
            else -> FeedbackSeverity.GOOD
        }
        val score = max(0, 100 - (numHints * 15))
        return PostureFeedback(
            exerciseType = "",
            feedbackMessages = messages.distinct(),
            severity = severity,
            postureScore = score,
            bodyPartHints = hints.distinctBy { it.bodyPart }
        )
    }
}
