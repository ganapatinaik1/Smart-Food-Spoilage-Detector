package com.freshnessai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import com.google.mlkit.vision.common.InputImage
import com.freshnessai.analysis.QRDataParser
import com.freshnessai.analysis.ColorCalibrator
import com.freshnessai.analysis.SensorColorExtractor
import com.freshnessai.data.FoodInfo
import android.graphics.Color
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.TranslateAnimation
import android.view.animation.AlphaAnimation
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.freshnessai.R
import com.freshnessai.analysis.GeminiAnalyzer
import com.freshnessai.data.ScanHistoryDatabase
import com.freshnessai.databinding.FragmentScanBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanFragment : Fragment() {

    private var _binding: FragmentScanBinding? = null
    private val binding get() = _binding!!

    private var camera: Camera? = null
    private var isFlashOn = false
    private lateinit var cameraExecutor: ExecutorService

    private var imageCapture: ImageCapture? = null
    private val geminiAnalyzer = GeminiAnalyzer()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            showPermissionRequired()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnFlash.setOnClickListener {
            toggleFlash()
        }

        binding.btnGrantPermission.setOnClickListener {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            binding.permissionLayout.visibility = View.VISIBLE
            binding.bottomPanel.visibility = View.INVISIBLE
            binding.scanOverlay.visibility = View.INVISIBLE
        }
    }

    private fun showPermissionRequired() {
        binding.permissionLayout.visibility = View.VISIBLE
        binding.bottomPanel.visibility = View.INVISIBLE
        binding.scanOverlay.visibility = View.INVISIBLE
    }

    private fun startCamera() {
        binding.permissionLayout.visibility = View.GONE
        binding.bottomPanel.visibility = View.VISIBLE
        binding.scanOverlay.visibility = View.VISIBLE

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun toggleFlash() {
        val hasFlash = camera?.cameraInfo?.hasFlashUnit() ?: false
        if (hasFlash) {
            isFlashOn = !isFlashOn
            camera?.cameraControl?.enableTorch(isFlashOn)
            val tint = if (isFlashOn) R.color.primary else R.color.text_on_dark
            binding.btnFlash.setColorFilter(ContextCompat.getColor(requireContext(), tint))
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // 1. Shutter Flash Animation
        binding.flashOverlay.visibility = View.VISIBLE
        binding.flashOverlay.alpha = 1f
        binding.flashOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction { binding.flashOverlay.visibility = View.GONE }
            .start()

        // Disable capture and show processing state
        binding.btnCapture.isEnabled = false
        binding.processingLayout.visibility = View.VISIBLE
        binding.tvScanInstruction.text = getString(R.string.scan_processing)
        binding.tvScanHint.visibility = View.INVISIBLE

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                @ExperimentalGetImage
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        processCapturedImage(imageProxy, mediaImage)
                    } else {
                        imageProxy.close()
                        resumeScanningSafely("Failed to capture image")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    resumeScanningSafely("Error taking photo: ${exception.message}")
                }
            }
        )
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processCapturedImage(imageProxy: ImageProxy, mediaImage: android.media.Image) {
        val bitmap = imageProxyToBitmap(imageProxy)

        if (bitmap != null) {
            // 2. Show frozen frame
            binding.ivCapturedPreview.setImageBitmap(bitmap)
            binding.ivCapturedPreview.visibility = View.VISIBLE

            // 3. Start Scanning Animation
            startScanningAnimation()

            // Construct InputImage from the rotated bitmap rather than mediaImage to prevent buffer conflicts
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val scanner = BarcodeScanning.getClient()

            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    val qrBarcode = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                    if (qrBarcode != null && qrBarcode.rawValue != null) {
                        val content = qrBarcode.rawValue!!
                        val parser = QRDataParser()
                        val foodInfo = parser.parse(content)

                        if (foodInfo != null && qrBarcode.boundingBox != null) {
                            // 4a. Calibrate and extract the physical color from the sensor in the exact center of the QR
                            val calibrator = ColorCalibrator()
                            val calibration = calibrator.calibrate(bitmap, qrBarcode.boundingBox!!)
                            val extractor = SensorColorExtractor(calibrator)
                            val calColor = extractor.extractSensorColor(bitmap, qrBarcode.boundingBox!!, calibration)

                            var extractedHex: String? = null
                            if (calColor != null) {
                                // Fallback to raw RGB if calibration is heavily skewed, or just use raw since we just need the hex
                                extractedHex = String.format("#%06X", (0xFFFFFF and Color.rgb(calColor.calibratedR, calColor.calibratedG, calColor.calibratedB)))
                            }

                            // 5. Send to Gemini for analysis with QR context and exact extracted physical color
                            analyzeWithGemini(bitmap, foodInfo, extractedHex)
                        } else {
                            resumeScanningSafely(getString(R.string.scan_invalid_qr))
                        }
                    } else {
                        resumeScanningSafely("QR Code with food data not found. Please frame it clearly.")
                    }
                }
                .addOnFailureListener {
                    resumeScanningSafely("Failed to detect QR code")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
            resumeScanningSafely("Failed to capture image")
        }
    }

    private fun startScanningAnimation() {
        binding.scannerLine.visibility = View.VISIBLE
        val anim = TranslateAnimation(
            0f, 0f, 
            0f, binding.root.height.toFloat()
        ).apply {
            duration = 2000
            repeatCount = Animation.INFINITE
            interpolator = LinearInterpolator()
        }
        binding.scannerLine.startAnimation(anim)
    }

    private fun stopScanningAnimation() {
        binding.scannerLine.clearAnimation()
        binding.scannerLine.visibility = View.GONE
    }

    private fun analyzeWithGemini(bitmap: Bitmap, foodInfo: FoodInfo, extractedHex: String?) {
        lifecycleScope.launch {
            try {
                // Run the Gemini analysis on IO dispatcher
                val record = withContext(Dispatchers.IO) {
                    geminiAnalyzer.analyze(bitmap, foodInfo, extractedHex)
                }

                // Save to database
                val dao = ScanHistoryDatabase.getDatabase(requireContext()).scanDao()
                val id = dao.insertScan(record)

                // Navigate to result screen
                val action = ScanFragmentDirections.actionScanToResult(id)
                findNavController().navigate(action)

            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg = when {
                    e.message?.contains("Unable to resolve host", true) == true ||
                    e.message?.contains("No address associated", true) == true ->
                        "No internet connection. Please check your network."
                    e.message?.contains("API key", true) == true ||
                    e.message?.contains("PERMISSION_DENIED", true) == true ->
                        "Invalid API key. Please check configuration."
                    e.message?.contains("quota", true) == true ||
                    e.message?.contains("RESOURCE_EXHAUSTED", true) == true ->
                        "API quota exceeded. Please try again later."
                    e.message?.contains("not found", true) == true ||
                    e.message?.contains("NOT_FOUND", true) == true ->
                        "Model not available. Please check model name."
                    else -> "Analysis failed: ${e.message?.take(100) ?: "Unknown error"}"
                }
                resumeScanningSafely(errorMsg)
            }
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val buffer = imageProxy.planes[0].buffer
            buffer.rewind()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            if (imageProxy.imageInfo.rotationDegrees != 0) {
                val matrix = Matrix().apply {
                    postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun resumeScanningSafely(msg: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            stopScanningAnimation()
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            binding.processingLayout.visibility = View.GONE
            binding.ivCapturedPreview.visibility = View.GONE
            binding.btnCapture.isEnabled = true
            binding.tvScanInstruction.text = getString(R.string.scan_instruction)
            binding.tvScanHint.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}
