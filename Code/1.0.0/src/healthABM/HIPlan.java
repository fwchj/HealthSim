package healthABM;

import java.util.ArrayList;

import repast.simphony.engine.environment.RunEnvironment;

/**
 * This class refers to possible health insurance plans of a given insurance company. It basically includes the offer 
 * of the company, but not the actual insurance. Actual insurances will be derived from this class. For instance, 
 * this class might include an age limit for the contraction.
 * @author Florian Chavez Juarez
 *
 */
public class HIPlan {
			// Contract information
			/** InsuranceCompany issuing the health insurance*/
			protected InsuranceCompany 	insurer;
			/** Annual premium of the insurance plan */
			protected double premium;
			/** Minimum age to qualify for this insurance plan*/
			protected int minAge;
			/** Maximum age to qualify for this insurance plan*/
			protected int maxAge;
			/** Binary indicator whether women can purchase this insurance plan*/
			protected boolean womenAllowed;
			/** Binary indicator whether men can purchase this insurance plan*/
			protected boolean menAllowed;
			
			/**array that stores this HIPlan's historic primes (primes[0] for t-1, primes[1] for t-2)*/
			protected double[] primes = new double[2];
			
			/**array that stores the historic profits associated to this HIPlan (primes[0] for t-1, primes[1] for t-2)*/
			protected double[] profits = new double[2];

			/** Deductible: claims until this amount have a 100% co-payment rate (=> zero reimbursement).*/ 
			protected double deductible;
			
			/** Co-payment rate (put 5% as 0.05) once the deductible is achieved, and before stoploss/stopclaim*/
			protected double copaymentRate;
			
			/** Amount after which the patient has no more co-payment. This value is set in the scale of difference between {@link HealthInsurance#claimsYTD} and {@link HealthInsurance#reimbursementYTD}
			 * Example: if on top of the deductible, the patient must pay a maximum of 500 units of co-payment, then the value here should be deductible + stoploss */
			protected int stopLoss;
			
			/** Amount of reimbursement after which the client is no longer entitled to receive reimbursement. In the scale of {@link HealthInsurance#claimsYTD}*/
			protected int stopClaim;
			
			/**Identifier for HIPlans */
			protected int ID;
			
			/**Number of insurees under this plan (cumulative, for a given 52-tick period)*/
			protected int numInsurees;
			
			
			protected ArrayList<Double> averageCostPerInsuree = new ArrayList<Double>();
			
			
			/** Array list of all providers that are covered. If all provider in the system are covered, you can either leave this empty or add all of them*/
			protected ArrayList<Provider> allowedProviders;
			
		// CONSTRUCTOR
			/**
			 * Constructor for the HIPlan. This is only an offer, not an actual insurance contract.  
			 * @param comp		insurance company
			 * @param ded		deductible
			 * @param cop		co-payment (for 5%, put 0.05)
			 * @param stoploss	stop-loss (put maximum out-of-pocket payment of the patient (deductible + co-payment). If none, put 0. 
			 * @param stopclaim stop-claim (put maximum reimbursement). If none, put 0. 
			 * @param minage 	Minimum age of patient to be able to buy this plan
			 * @param maxage 	Maximum age of patient to be able to buy this plan
			 * @param premium		Prime (monthly) of the plan
			 * @param subclass (boolean) This is an optional parameter. When the constructor is called from a class that extends this class, this parameter should be true. Otherwise, it can either be left blank or set to false. When true, this optional parameter avoids creating new HIPlan IDs and does not add new elements to the InsuranceCompany's AL of HIPlans.
			 * @param women		True if women are accepted in the plan
			 * @param men 		True if men are accepted in the plan
			 */
			public HIPlan(int id,InsuranceCompany comp, double ded, double cop, int stoploss, int stopclaim, int minage, int maxage, double premium,boolean women,boolean men, boolean ... subclass ){
				// Get ID
				this.ID				= id;
				this.deductible 	= ded;
				this.copaymentRate 	= cop;
				this.stopClaim		= stopclaim;
				this.stopLoss 		= stoploss;
				
				this.premium		= premium;
				this.minAge			= minage;
				this.maxAge			= maxage;
				this.womenAllowed 	= women;
				this.menAllowed 	= men;
				this.insurer		= comp;
				this.numInsurees    = 0;
				
				averageCostPerInsuree = new ArrayList<Double>();
				
				//The counter for HIPlans unique IDs is only activated when the constructor is not called from a subclass, i. e., when the argument subclass is not true. This avoids creating new unnecessary HIPlan IDs. Also, the HIPlan is only added to the InsuranceCompany's AL of HIPlans when this constructor is not called from a subclass
				System.out.printf("I just created a new HIPlan ID: %s", this.ID);
					
					//System.out.printf("The company with this HI plan has %s initial capial\n", this.insurer.getCapital());
				this.insurer.hiPlans.add(this);
				

					
				
				
				this.allowedProviders = new ArrayList<Provider>();
				
				// Send alert message if both stop-loss and stop-claim are set
				if(stopclaim !=0 && stoploss!=0){
					System.out.printf("WARNING: the program just tried to create an insurance plan (ID=%s) with both stop-claim and stop-loss\n",this.ID);
				}
				
				// Send alert if stoploss<deductible (this should not be possible)
				if(stoploss!=0 & stoploss<deductible) {
					System.out.printf("FATAL ERROR: the program just tried to create an insurance plan (ID=%s) with stop-loss < deductible. This "
							+ "is not possible given that stop-loss = deductible + max-copayment\n",this.ID);
					System.exit(2);
				}
			}
			
	
			
			/**
			 * Return the minimum age required to contract this insurance plan
			 * @return int: age
			 */
			protected int getMinAge(){
				return this.minAge;
			}
			
			
			/**
			 * Sets this HIPlan's associated profits array values
			 * @param profits Profits 
			 */
			public void setProfits(double[] profits) {
				this.profits = profits;
			}
			
			/**
			 * Returns the maximum age (at beginning of plan) the HI plan allows. 
			 * @return int: age
			 */
			protected int getMaxAge(){
				return this.maxAge;
			}
			
			/**
			 * This method computes the potential reimbursement for a given cost, but does not refer to an actual contract. Hence, this method is rather
			 * used for the evaluation which HIP might be the best. Nothing is executed within this method, it only returns the reimbursement that the patient would get. 
			 * @param Total claims per period (if the insurance is per event, you might want to put the cost by event)
			 * @author Florian 
			 * @version 2019-02-18 (Unit testing passed)
			 * @return The amount the health insurance would reimburse
			 */
			protected double computeReimbursement(double claim) {
				// 1: Find breaking points
				double bp1 = this.deductible;
				double bp2;
				if(this.stopLoss!=0){
					
					bp2 = bp1 + (this.stopLoss - bp1 ) / (copaymentRate);	// UNIT TESTING: passed
				}
				else if(this.stopClaim!=0){
					bp2 = bp1 +	(this.stopClaim ) / (1-copaymentRate);	 // UNIT TESTING: passed	
					
				}
				else{
					bp2 = Double.POSITIVE_INFINITY;
				}
				
				
				
			// 2: Evaluate different cases
				double reimbursement=-1.0;
				if(0 < bp1 && claim <= bp1){ // all below deductible
					reimbursement = 0.0;
				}
				else if(0 < bp1 && claim> bp1 && claim<bp2){ // Jump from before BP1 to after BP1
					reimbursement = (1-this.copaymentRate) * (claim-(this.deductible));
				}
				else if(0< bp1 && claim >=bp2){ // Jump from before BP1 to after BP2
					// Save claim for posterior use
					double claimBackup = claim;
					// Remove part of claim going to deductible
					claim -=this.deductible;
					
					// Compute reimbursment during co-payment period
					reimbursement = (bp2-bp1)*(1-this.copaymentRate);
					
					// Handle above bp2
					if(this.stopLoss!=0){
						reimbursement += claim - (bp2-bp1);
					}
											
					// reset claim 
					claim = claimBackup;
				}
				else if(0>=bp1 && 0<bp2 && claim<=bp2){ // all between bp1 and bp2
					reimbursement = claim*(1-this.copaymentRate);
				}
				else if(0>=bp1 && 0<bp2 && claim>bp2){ // all between bp1 and bp2
					reimbursement = (bp2)*(1-this.copaymentRate);
					
					// Handle above bp2
					if(this.stopLoss!=0){
						reimbursement += claim - (bp2);
					}
					
				}
				else if(0>=bp2){
					if(this.stopLoss!=0){
						reimbursement = claim; 
					}
					else{
						reimbursement=0.0;
					}
				}
				
				
				// Assertion: here we should never have reimbursement == -1
				if(reimbursement == -1 ){
					System.out.println("I caught a negative reimbursement (HealthInsurance.getReimbursement())\n");
					System.out.printf("Claim: %s; ded: %s; bp2: %s", claim, bp1, bp2);
					System.exit(1);
				}
			
			
				return reimbursement;
			}
			
	// GET METHODS
	public double getYearsCumulativeInsurees() {
		return this.numInsurees;
	}
	
	
	//FIXME: 0000: DELETE
	public double getYearsCumulativeReimbursements() {
		/*int thisTick = (int)RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		return this.insurer.getReimbursementByHIPlanAndTickRange(Model.currentYearStart, thisTick, this.ID);
		*/
		double sum = 0;
		for(HealthInsurance hi : this.insurer.insurances) {
			if(hi.assocHIPlan==this) {
				sum +=hi.reimbursementYTD;
			}
		}
		return sum;
	}
	
	
	
	
	public double getPrime() {
		return this.premium;
	}
	
	public double getDeductible() {
		return this.deductible;
	}
	
	public double getCopaymentRate() {
		return this.copaymentRate;
	}

	public double getStopLoss() {
		return this.stopLoss;
	}
	
	public double getStopClaim() {
		return this.stopClaim;
	}
	
	public int getID() {
		return this.ID;
	}
			
}
