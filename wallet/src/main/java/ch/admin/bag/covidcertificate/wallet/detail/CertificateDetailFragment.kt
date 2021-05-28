package ch.admin.bag.covidcertificate.wallet.detail

import android.content.res.ColorStateList
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import ch.admin.bag.covidcertificate.common.util.DEFAULT_DISPLAY_DATE_FORMATTER
import ch.admin.bag.covidcertificate.common.util.parseIsoTimeAndFormat
import ch.admin.bag.covidcertificate.common.verification.CertificateVerifier
import ch.admin.bag.covidcertificate.common.verification.VerificationState
import ch.admin.bag.covidcertificate.common.views.animateBackgroundTintColor
import ch.admin.bag.covidcertificate.common.views.hideAnimated
import ch.admin.bag.covidcertificate.common.views.showAnimated
import ch.admin.bag.covidcertificate.eval.models.Bagdgc
import ch.admin.bag.covidcertificate.eval.utils.*
import ch.admin.bag.covidcertificate.wallet.CertificatesViewModel
import ch.admin.bag.covidcertificate.wallet.R
import ch.admin.bag.covidcertificate.wallet.databinding.FragmentCertificateDetailBinding
import ch.admin.bag.covidcertificate.wallet.util.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CertificateDetailFragment : Fragment() {

	companion object {
		private const val STATUS_HIDE_DELAY = 2000L

		private const val ARG_CERTIFICATE = "ARG_CERTIFICATE"

		fun newInstance(certificate: Bagdgc): CertificateDetailFragment = CertificateDetailFragment().apply {
			arguments = bundleOf(ARG_CERTIFICATE to certificate)
		}
	}

	private val certificatesViewModel by activityViewModels<CertificatesViewModel>()

	private var _binding: FragmentCertificateDetailBinding? = null
	private val binding get() = _binding!!

	private lateinit var certificate: Bagdgc
	private var verifier: CertificateVerifier? = null

	private var hideDelayedJob: Job? = null

	private var isForceValidate = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		certificate = (arguments?.getSerializable(ARG_CERTIFICATE) as? Bagdgc)
			?: throw IllegalStateException("Certificate detail fragment created without Certificate!")
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = FragmentCertificateDetailBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		displayQrCode()
		setupCertificateDetails()
		setupStatusInfo()

		binding.certificateDetailToolbar.setNavigationOnClickListener { v: View? ->
			parentFragmentManager.popBackStack()
		}

		binding.certificateDetailButtonDelete.setOnClickListener { view ->
			AlertDialog.Builder(view.context, R.style.CovidCertificate_AlertDialogStyle)
				.setTitle(R.string.delete_button)
				.setMessage(R.string.wallet_certificate_delete_confirm_text)
				.setPositiveButton(R.string.delete_button) { dialog, which ->
					certificatesViewModel.removeCertificate(certificate.qrCodeData)
					parentFragmentManager.popBackStack()
				}
				.setNegativeButton(R.string.cancel_button) { dialog, which ->
					dialog.dismiss()
				}
				.setCancelable(true)
				.create()
				.show()
		}

		binding.certificateDetailButtonReverify.setOnClickListener {
			binding.certificateDetailButtonReverify.hideAnimated()
			binding.scrollview.smoothScrollTo(0, 0)
			isForceValidate = true
			hideDelayedJob?.cancel()
			verifier?.startVerification()
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	private fun displayQrCode() {
		val qrCodeBitmap = QrCode.renderToBitmap(certificate.qrCodeData)
		val qrCodeDrawable = BitmapDrawable(resources, qrCodeBitmap).apply { isFilterBitmap = false }
		binding.certificateDetailQrCode.setImageDrawable(qrCodeDrawable)
	}

	private fun setupCertificateDetails() {
		val recyclerView = binding.certificateDetailDataRecyclerView
		val layoutManager = LinearLayoutManager(recyclerView.context, LinearLayoutManager.VERTICAL, false)
		recyclerView.layoutManager = layoutManager
		val adapter = CertificateDetailAdapter()
		recyclerView.adapter = adapter

		val name = "${certificate.dgc.nam.fn} ${certificate.dgc.nam.gn}"
		binding.certificateDetailName.text = name
		val dateOfBirth = certificate.dgc.dob.parseIsoTimeAndFormat(DEFAULT_DISPLAY_DATE_FORMATTER)
		binding.certificateDetailBirthdate.text = dateOfBirth

		binding.certificateDetailInfo.setText(R.string.verifier_verify_success_info)

		val detailItems = CertificateDetailItemListBuilder(recyclerView.context, certificate).buildAll()
		adapter.setItems(detailItems)
	}

	private fun setupStatusInfo() {
		certificatesViewModel.certificateVerifierMapLiveData.observe(
			viewLifecycleOwner,
			object : Observer<Map<String, CertificateVerifier>> {
				override fun onChanged(verifierMap: Map<String, CertificateVerifier>) {
					val verifier = verifierMap[certificate.qrCodeData] ?: return
					certificatesViewModel.certificateVerifierMapLiveData.removeObserver(this)
					this@CertificateDetailFragment.verifier = verifier
					binding.certificateDetailButtonReverify.showAnimated()
					verifier.liveData.observe(viewLifecycleOwner) { updateStatusInfo(it) }
					verifier.startVerification()
				}
			})
	}

	private fun updateStatusInfo(verificationState: VerificationState?) {
		val state = verificationState ?: return
		val context = binding.root.context

		ColorStateList.valueOf(ContextCompat.getColor(context, state.getInfoBubbleColor()))
			.let { colorStateList ->
				binding.certificateDetailInfo.backgroundTintList = colorStateList
				if (!isForceValidate) binding.certificateDetailInfoValidityGroup.backgroundTintList = colorStateList
			}

		ColorStateList.valueOf(ContextCompat.getColor(context, state.getInfoBubbleValidationColor()))
			.let { colorStateList ->
				binding.certificateDetailInfoVerificationStatus.backgroundTintList = colorStateList
				if (isForceValidate) binding.certificateDetailInfoValidityGroup.backgroundTintList = colorStateList
			}

		if (isForceValidate) {
			binding.certificateDetailQrCodeColor.backgroundTintList =
				ColorStateList.valueOf(ContextCompat.getColor(context, state.getSolidValidationColor()))
			binding.certificateDetailQrCodeStatusIcon.setImageResource(state.getValidationStatusIconLarge())
			binding.certificateDetailStatusIcon.setImageResource(state.getValidationStatusIcon())

			if (!binding.certificateDetailQrCodeStatusGroup.isVisible) binding.certificateDetailQrCodeStatusGroup.showAnimated()

			binding.certificateDetailInfoVerificationStatus.apply {
				text = state.getValidationStatusString(context)
				if (!isVisible) showAnimated()
			}

			if (state != VerificationState.LOADING) readjustStatusDelayed(state)
		} else {
			binding.certificateDetailInfo.text = state.getStatusString(context)
			binding.certificateDetailStatusIcon.setImageResource(state.getStatusIcon())
		}

		when (state) {
			is VerificationState.INVALID, is VerificationState.SUCCESS, is VerificationState.ERROR -> {
				binding.certificateDetailStatusLoading.isVisible = false
				binding.certificateDetailStatusIcon.isVisible = true

				binding.certificateDetailQrCodeLoading.isVisible = false
				binding.certificateDetailQrCodeStatusIcon.isVisible = true
			}
			VerificationState.LOADING -> {
				binding.certificateDetailStatusLoading.isVisible = true
				binding.certificateDetailStatusIcon.isVisible = false

				binding.certificateDetailQrCodeLoading.isVisible = true
				binding.certificateDetailQrCodeStatusIcon.isVisible = false
			}
		}

		val qrAlpha = state.getQrAlpha()
		binding.certificateDetailQrCode.alpha = qrAlpha

		val textColor = ContextCompat.getColor(context, state.getNameDobColor())
		binding.certificateDetailName.setTextColor(textColor)
		binding.certificateDetailBirthdate.setTextColor(textColor)

		val dateUntilString = certificate.getType()?.let { state.getValidUntilDateString(it) } ?: "–"
		binding.certificateDetailInfoValidityDate.text = dateUntilString
		binding.certificateDetailInfoValidityDateDisclaimer.alpha = qrAlpha
		binding.certificateDetailInfoValidityDateGroup.alpha = qrAlpha

		binding.certificateDetailDataRecyclerView.alpha = qrAlpha
	}

	private fun readjustStatusDelayed(verificationState: VerificationState) {
		hideDelayedJob?.cancel()
		hideDelayedJob = viewLifecycleOwner.lifecycleScope.launch {
			delay(STATUS_HIDE_DELAY)
			if (!isActive || !isVisible) return@launch

			val context = binding.root.context

			binding.certificateDetailQrCodeStatusGroup.hideAnimated()
			binding.certificateDetailInfoVerificationStatus.hideAnimated()
			binding.certificateDetailInfoValidityGroup.animateBackgroundTintColor(
				ContextCompat.getColor(
					context,
					if (verificationState is VerificationState.ERROR) R.color.orangeish else R.color.blueish
				)
			)
			binding.certificateDetailStatusIcon.setImageResource(verificationState.getStatusIcon())

			binding.certificateDetailButtonReverify.showAnimated()
			isForceValidate = false
		}
	}

}