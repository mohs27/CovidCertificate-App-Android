package ch.admin.bag.covidcertificate.eval.utils

import ch.admin.bag.covidcertificate.eval.chain.VerificationResult
import ch.admin.bag.covidcertificate.eval.data.VaccinationEntry
import ch.admin.bag.covidcertificate.eval.products.Vaccine
import ch.admin.bag.covidcertificate.eval.utils.AcceptanceCriterias.SINGLE_VACCINE_VALIDITY_OFFSET_IN_DAYS
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*


fun VaccinationEntry.doseNumber(): Int = this.dn

fun VaccinationEntry.totalDoses(): Int = this.sd

fun VaccinationEntry.hadPastInfection(vaccine: Vaccine): Boolean {
	//if the total Doses of the vaccine is bigger then the total doses in the certificate, the patient had a past infection
	return vaccine.total_dosis_number > this.totalDoses()
}

fun VaccinationEntry.getNumberOverTotalDose(): String {
	return " ${this.doseNumber()}/${this.totalDoses()}"
}

fun VaccinationEntry.isTargetDiseaseCorrect(): Boolean {
	return this.tg == AcceptanceCriterias.TARGET_DISEASE
}

fun VaccinationEntry.getFormattedVaccinationDate(dateFormatter: DateTimeFormatter): String {
	return try {
		LocalDate.parse(this.dt, DateTimeFormatter.ISO_DATE).format(dateFormatter)
	} catch (e: java.lang.Exception) {
		this.dt
	}
}

fun VaccinationEntry.validFromDate(vaccine: Vaccine): LocalDateTime? {
	val vaccineDate = this.vaccineDate() ?: return null
	val totalNumberOfDosis = vaccine.total_dosis_number
	// if this is a vaccine, which only needs one shot AND we had no previous infections, the vaccine is valid 15 days after the date of vaccination
	return if (!this.hadPastInfection(vaccine) && totalNumberOfDosis == 1) {
		return vaccineDate.plusDays(SINGLE_VACCINE_VALIDITY_OFFSET_IN_DAYS)
	} else {
		// In any other case the vaccine is valid from the date of vaccination
		vaccineDate
	}
}

/// Vaccines are valid for 180 days
fun VaccinationEntry.validUntilDate(): LocalDateTime? {
	val vaccinationImmunityEndDate = this.vaccineDate() ?: return null
	return vaccinationImmunityEndDate.plusDays(AcceptanceCriterias.VACCINE_IMMUNITY_DURATION_IN_DAYS)
}

fun VaccinationEntry.vaccineDate(): LocalDateTime? {
	val date: LocalDate?
	try {
		date = LocalDate.parse(this.dt, DateTimeFormatter.ISO_DATE)
	} catch (e: Exception) {
		return null
	}
	return date.atStartOfDay()
}

fun VaccinationEntry.getVaccinationCountry(): String {
	val loc = Locale("", this.co)
	return loc.displayCountry
}

fun VaccinationEntry.getIssuer(): String {
	return this.`is`
}

fun VaccinationEntry.getCertificateIdentifier(): String {
	return this.ci
}

fun VerificationResult.getIssueAtDate(dateFormatter: DateTimeFormatter): String? {
	this.issuedAt?.let { instant ->
		return instant.atZone(ZoneId.systemDefault()).format(dateFormatter)
	}
	return null
}




