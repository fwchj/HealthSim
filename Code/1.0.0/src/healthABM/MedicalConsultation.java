package healthABM;

/**
 * The class MedicalConsultation is an auxiliary class to transmit information about a consultation to the patient. It contains infomration about the cost, but also about possible referrals. 
 * @author Florian
 * @version 09-nov-2018
 *
 */
public class MedicalConsultation {

	/** Provider involved in the medical consultation */
	public Provider provider;

	/** Price to be charged to the patient */
	double priceToPatient;
	
	/** Referral to another provider (null if no referral was made)*/
	Provider referral;
	
	
	/**
	 * Constructor of the class MedicalConsultation
	 * @param provider the provider involved in the medical consultation
	 */
	public MedicalConsultation(Provider provider) {
		this.provider = provider;
	}
	
	// get methods
	public int getProviderID() {
		return this.provider.ID;
	}
	
	public double getPrice() {
		return this.priceToPatient;
	}
	
	public int getReferralID() {
		return this.referral.ID;
	}
	
	
	
	
}
