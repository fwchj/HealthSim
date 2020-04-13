package healthABM;

public class Treatment {
	// INSTANCE VARIABLES
	/** Unique identifier of the treatment*/
	protected int			ID;
	

	/** Price of treatment faced by patient. This does not include the cost of the consultation. For instance, if a patient sees a 
	 * medical doctor an pays for the consultation 100 and then for the treatment 50, this value here should be equal to 50.*/
	protected double		cost;
	
	/** Short description of the treatment*/
	protected String 		description; 
	
	
	/** Marginal benefit of the treatment to the provider. This is a simplified implementation, as we do not distinguish 
	 *  the part of the total cost going to third parties nor the income/cost for the provider */
	protected double		marginalBenefitProvider;
	
	/** Change on Illness's severity when treatment is applied*/
	protected double 		deltaSeverityUnderTreatment;
	
	/** Type of treatment which also implies who can prescribe it*/
	TreatmentType			type;
	
	/** Minimum severity of illness to be eligible for this treatment*/
	protected double 		minSeverity;
	
	/** Maximum severity of illness to be eligible for this treatment*/
	protected double 		maxSeverity;
	
	

	
	// CONSTRUCTOR
	/**
	 * @param description		Short description of the treatment
	 * @param price				Cost of treatment (for the patient)
	 * @param marginalBenefit	Marginal benefit of the provider: cost to patient - cost for provider 
	 * @param deltaSeverity 	Change in the change of severity per period. This number will be added to deltaSeverity of the illness to get the total change
	 * @param typeTreatment		Type of treatment using {@link TreatmentType}
	 * 

	 */
	public Treatment(int id,String description,double price, double marginalBenefit, double deltaSeverity, TreatmentType typeTreatment,double minSeverity,double maxSeverity){
		// Get ID
		this.ID 							= id;
		this.description					= description;
		this.cost 							= price;
		this.deltaSeverityUnderTreatment 	= deltaSeverity;
		this.type							= typeTreatment;
		this.marginalBenefitProvider		= marginalBenefit;
		this.minSeverity 					= minSeverity;
		this.maxSeverity					= maxSeverity;
		
		
		if(this.marginalBenefitProvider>this.cost){
			System.out.printf("ERROR building a treatment '%s': the marginal benefit of the provider cannot be higher than the price of the treatment!\n",this.description);
			System.exit(0);
		}
		
		
	}

	
	
	// SCHEDULED METHODS
	
	// General methods
	protected double getProviderPayment(Treatment t) {
		return t.marginalBenefitProvider;
	}

			
	
}
