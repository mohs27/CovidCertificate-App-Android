package ch.admin.bag.covidcertificate.common.qr

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QRCodeMLAnalyzer(
	val coroutineScope: CoroutineScope,
	private val onDecodeCertificate: (decodeCertificateState: DecodeCertificateState) -> Unit
) :
	ImageAnalysis.Analyzer {
	val options = BarcodeScannerOptions.Builder()
		.setBarcodeFormats(
			Barcode.FORMAT_QR_CODE
		)
		.build()


	private val scanner = BarcodeScanning.getClient(options)

	override fun analyze(imageProxy: ImageProxy) {
		coroutineScope.launch { decode(imageProxy) }
	}

	@SuppressLint("UnsafeOptInUsageError")
	suspend fun decode(imageProxy: ImageProxy) = withContext(Dispatchers.IO) {
		val inputImage: InputImage = InputImage.fromMediaImage(imageProxy.image, imageProxy.imageInfo.rotationDegrees)
		val bitmap = inputImage.bitmapInternal //null
		val task = scanner.process(inputImage)
		try {
			val barcodes = Tasks.await(task)
			if (barcodes.size > 0) {
				val result = barcodes[0].displayValue
				onDecodeCertificate(DecodeCertificateState.SUCCESS((result), bitmap))
			} else {
				onDecodeCertificate(DecodeCertificateState.SCANNING(bitmap))
			}
		} catch (e: Exception) {
			onDecodeCertificate(DecodeCertificateState.SCANNING(bitmap))
			e.printStackTrace()
		} finally {
			imageProxy.close()
		}
	}


}