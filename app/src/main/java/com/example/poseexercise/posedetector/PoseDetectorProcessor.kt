package com.example.poseexercise.posedetector

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.poseexercise.data.PostureFeedback
import com.example.poseexercise.data.PostureResult
import com.example.poseexercise.data.plan.Plan
import com.example.poseexercise.posedetector.classification.PoseClassifierProcessor
import com.example.poseexercise.util.VisionProcessorBase
import com.example.poseexercise.viewmodels.CameraXViewModel
import com.example.poseexercise.views.graphic.GraphicOverlay
import com.example.poseexercise.views.graphic.PoseGraphic
import com.google.android.gms.tasks.Task
import com.google.android.odml.image.MlImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class PoseDetectorProcessor(
    private val context: Context,
    options: PoseDetectorOptionsBase,
    private val showInFrameLikelihood: Boolean,
    private val visualizeZ: Boolean,
    private val rescaleZForVisualization: Boolean,
    private val runClassification: Boolean,
    private val isStreamMode: Boolean,
    private var cameraXViewModel: CameraXViewModel? = null,
    notCompletedExercise: List<Plan>
) : VisionProcessorBase<PoseDetectorProcessor.PoseWithClassification>(context) {

    private val detector: PoseDetector
    private val classificationExecutor: Executor

    private var poseClassifierProcessor: PoseClassifierProcessor? = null
    private var postureCorrector: PostureCorrector? = null
    private var exercisesToDetect: List<String>? = null

    inner class PoseWithClassification(
        val pose: Pose,
        classificationResult: Map<String, PostureResult>
    ) {
        init {
            if (classificationResult.isNotEmpty()) {
                cameraXViewModel?.postureLiveData?.postValue(classificationResult)
            }

            if (classificationResult.isNotEmpty() && pose.getAllPoseLandmarks().isNotEmpty()) {
                val maxConfidenceEntry = classificationResult.maxByOrNull { it.value.confidence }
                if (maxConfidenceEntry != null) {
                    val feedback = postureCorrector?.analyze(maxConfidenceEntry.key, pose)
                    if (feedback != null) {
                        cameraXViewModel?.postureFeedbackLiveData?.postValue(feedback)
                    }
                }
            }
        }
    }

    init {
        detector = PoseDetection.getClient(options)
        classificationExecutor = Executors.newSingleThreadExecutor()
        postureCorrector = PostureCorrector()
        if (notCompletedExercise.isNotEmpty()) {
            exercisesToDetect = notCompletedExercise.map { plan -> plan.exercise }
        }
    }

    fun processBitmap(bitmap: Bitmap, graphicOverlay: GraphicOverlay, rotationDegrees: Int = 0) {
        val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)
        detectInImage(inputImage)
            .addOnSuccessListener { result ->
                graphicOverlay.clear()
                graphicOverlay.add(com.example.poseexercise.views.graphic.CameraImageGraphic(graphicOverlay, bitmap))
                this@PoseDetectorProcessor.onSuccess(result, graphicOverlay)
                graphicOverlay.postInvalidate()
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    override fun stop() {
        super.stop()
        detector.close()
        cameraXViewModel = null
    }

    override fun detectInImage(image: InputImage): Task<PoseWithClassification> {
        return detector
            .process(image)
            .continueWith(classificationExecutor) { task ->
                val pose = task.result
                var classificationResult: Map<String, PostureResult> = HashMap()
                if (runClassification) {
                    if (poseClassifierProcessor == null) {
                        poseClassifierProcessor = PoseClassifierProcessor(context, isStreamMode, exercisesToDetect)
                    }
                    classificationResult = poseClassifierProcessor!!.getPoseResult(pose)
                }
                PoseWithClassification(pose, classificationResult)
            }
    }

    override fun detectInImage(image: MlImage): Task<PoseWithClassification> {
        return detector
            .process(image)
            .continueWith(classificationExecutor) { task ->
                val pose = task.result
                var classificationResult: Map<String, PostureResult> = HashMap()
                if (runClassification) {
                    if (poseClassifierProcessor == null) {
                        poseClassifierProcessor = PoseClassifierProcessor(context, isStreamMode, exercisesToDetect)
                    }
                    classificationResult = poseClassifierProcessor!!.getPoseResult(pose)
                }
                PoseWithClassification(pose, classificationResult)
            }
    }

    override fun onSuccess(
        poseWithClassification: PoseWithClassification,
        graphicOverlay: GraphicOverlay
    ) {
        graphicOverlay.add(
            PoseGraphic(
                graphicOverlay,
                poseWithClassification.pose,
                showInFrameLikelihood,
                visualizeZ,
                rescaleZForVisualization
            )
        )
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Pose detection failed!", e)
    }

    override fun isMlImageEnabled(context: Context?): Boolean = true

    companion object {
        private const val TAG = "PoseDetectorProcessor"
    }
}
