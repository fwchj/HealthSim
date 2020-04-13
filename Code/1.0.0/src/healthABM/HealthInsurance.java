package healthABM;

import java.util.ArrayList;
import java.util.Arrays;

import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduledMethod;

public class HealthInsurance {
	// INSTANCE VARIABLES

	// HealthInsurance contract information
	/**
	 * Unique identifier of the insurance plan (used for posterior statistical
	 * analysis)
	 */
	protected int ID;
	/** The patient having contracted the health insurance plan */
	protected Patient insuree;
	/**
	 * Tick when this contract was first introduced (might be useful to compute the
	 * number of years
	 */
	protected int startContract;
	/**
	 * Total amount of health expenditures claimed by the patient (since the
	 * beginning of the year). This is the total amount, independent of the amount
	 * of reimbursement
	 */
	protected double claimsYTD;

	/** Reimbursement to the patient since the beginning of the year */
	protected double reimbursementYTD;

	/**
	 * HIPlan to which this HealthInsurance is related to. This class contains
	 * relevant information for the plan, such as insurer and plan characteristics
	 */
	protected HIPlan assocHIPlan;

	/** Stores all claims that have been requested */
	private ArrayList<HiLogEntry> claimLog7 = new ArrayList<HiLogEntry>();

	/**
	 * Deductible: claims until this amount have a 100% co-payment rate (=> zero
	 * reimbursement).
	 */
	protected double deductible;

	/**
	 * Co-payment rate (put 5% as 0.05) once the deductible is achieved, and before
	 * stoploss/stopclaim
	 */
	protected double copaymentRate;

	/**
	 * Amount after which the patient has no more co-payment. This value is set in
	 * the scale of difference between {@link HealthInsurance#claimsYTD} and
	 * {@link HealthInsurance#reimbursementYTD} Example: if on top of the
	 * deductible, the patient must pay a maximum of 500 units of co-payment, then
	 * the value here should be deductible + stoploss
	 */
	protected int stopLoss;

	/**
	 * Amount of reimbursement after which the client is no longer entitled to
	 * receive reimbursement. In the scale of {@link HealthInsurance#claimsYTD}
	 */
	protected int stopClaim;

	/** Annualpremium of the insurance plan */
	protected double premium;

	/** InsuranceCompany issuing the health insurance */
	protected InsuranceCompany insurer;

	// CONSTRUCTOR
	/**
	 * Constructor for the insurance plans. Automatically adds this insurance to the
	 * list of insurance of the company and the linked insurance of the patient.
	 * 
	 * @param plan    Insurance plan. The plan brings with it: deductible co-payment
	 *                (for 5%, put 0.05) stop-loss (put maximum out-of-pocket
	 *                payment of the patient (deductible + co-payment). If none, put
	 *                0. stop-claim (put maximum reimbursement). If none, put 0.
	 *                Minimum age to be able to get this Maximum age to get this
	 *                insurance Prime to be paid If women are accepted to the plan
	 *                If men are accepted to the plan
	 * @param patient Patient
	 */
	public HealthInsurance(HIPlan plan, Patient patient) {

		// super(plan.insurer, plan.minAge, plan.maxAge,
		// plan.prime,plan.womenAllowed,plan.menAllowed, true);

		this.deductible = plan.deductible;
		this.copaymentRate = plan.copaymentRate;
		this.stopClaim = plan.stopClaim;
		this.stopLoss = plan.stopLoss;

		this.premium = plan.premium;
		this.insurer = plan.insurer;

		// FIXME: for Alejandro from Florian: did not include the follwing in
		// HealthInsurance, I think they are not needed, right?
		/*
		 * this.minAge = minage; this.maxAge = maxage; this.womenAllowed = women;
		 * this.menAllowed = men; this.numInsurees = 0;
		 */

		// Get ID
		this.ID = Model.counterInsurance; // Increases the value automatically by one unit (previous to assignment)
		this.insuree = patient;
		this.startContract = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();

		if (patient.getAgeYears() < plan.minAge || patient.getAgeYears() > plan.maxAge) {
			System.out.printf(
					"WARNING: the program just tried to create an insurance plan (ID=%s) outside the age limits, patient age: %s\n",
					this.ID, patient.age);
		}

		this.insurer.addInsurance(this);
		this.insuree.setInsurance(this);

		this.assocHIPlan = plan;
		this.assocHIPlan.numInsurees++;

		Model.context.add(this);
		Model.counterInsurance++;
	}

	// SCHEDULED METHODS

	// OTHER METHODS
	/**
	 * Computes and executes (if boolean to true) the reimbursement of health
	 * expenditures. The expenditure itself (payment by the patient) is not modeled
	 * here. Note that this method <b>does not</b> look at the eligibility of the
	 * provider! This must be done somewhere else (e.g.
	 * {@link #computeReimbursement(MedicalConsultation)})
	 * 
	 * @param claim          Amount of expenditures the patients wants to get
	 *                       reimbursed.
	 * @param executePayment Set this to true if you wish to execute the payment,
	 *                       FALSE if you just want to get the value
	 * @return the value of the (potential reimbursement)
	 */
	protected double getReimbursement(double claim, boolean executePayment, String claimDescription) { // UNIT TESTING:
																										// passed
		// 1: Find breaking points
		double bp1 = this.deductible;
		double bp2;
		if (this.stopLoss != 0) {

			bp2 = bp1 + (this.stopLoss - bp1) / (copaymentRate); // UNIT TESTING: passed
		} else if (this.stopClaim != 0) {
			bp2 = bp1 + (this.stopClaim) / (1 - copaymentRate); // UNIT TESTING: passed

		} else {
			bp2 = 999999999;
			// bp2 = Double.POSITIVE_INFINITY;
		}

		// 2: Evaluate different cases
		double reimbursement = -1.0;
		if (this.claimsYTD < bp1 && this.claimsYTD + claim <= bp1) { // all below deductible
			reimbursement = 0.0;
		} else if (this.claimsYTD < bp1 && this.claimsYTD + claim > bp1 && this.claimsYTD + claim < bp2) { // Jump from
																											// before
																											// BP1 to
																											// after BP1
			reimbursement = (1 - this.copaymentRate) * (claim - (this.deductible - this.claimsYTD));
		} else if (this.claimsYTD < bp1 && this.claimsYTD + claim >= bp2) { // Jump from before BP1 to after BP2
			// Save claim for posterior use
			double claimBackup = claim;
			// Remove part of claim going to deductible
			claim -= this.deductible - this.claimsYTD;

			// Compute reimbursment during co-payment period
			reimbursement = (bp2 - bp1) * (1 - this.copaymentRate);

			// Handle above bp2
			if (this.stopLoss != 0) {
				reimbursement += claim - (bp2 - bp1);
			}

			// reset claim
			claim = claimBackup;
		} else if (this.claimsYTD >= bp1 && this.claimsYTD < bp2 && this.claimsYTD + claim <= bp2) { // all between bp1
																										// and bp2
			reimbursement = claim * (1 - this.copaymentRate);
		} else if (this.claimsYTD >= bp1 && this.claimsYTD < bp2 && this.claimsYTD + claim > bp2) { // all between bp1
																									// and bp2
			reimbursement = (bp2 - this.claimsYTD) * (1 - this.copaymentRate);

			// Handle above bp2
			if (this.stopLoss != 0) {
				reimbursement += claim - (bp2 - this.claimsYTD);
			}

		} else if (this.claimsYTD >= bp2) {
			if (this.stopLoss != 0) {
				reimbursement = claim;
			} else {
				reimbursement = 0.0;
			}
		}

		// Assertion: here we should never have reimbursement == -1
		if (reimbursement == -1) {
			System.out.println("I caught a negative reimbursement (HealthInsurance.getReimbursement())");
			System.out.printf("Claim: %s; ded: %s; bp2: %s", claim, bp1, bp2);
			System.exit(1);
		}

		if (executePayment == true) {

			// 3: Adapt values of the health insurance
			this.claimsYTD += claim;
			this.reimbursementYTD += reimbursement;

			/*this.claimLog.add(new HiLogEntry((int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount(),
					claim, claimDescription));
*/
			// System.out.printf("The result of the claim (%s)
			// is:\n\tInsurance:%s\n\tPatient:%s\n\tClaimsYTD:%s\n\tReimbursementYTD:%s\n--------------------------------\n",claim,reimbursement,claim-reimbursement,this.claimsYTD,this.reimbursementYTD);
			// System.out.printf("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",claim,reimbursement,claim-reimbursement,this.claimsYTD,this.reimbursementYTD,this.claimsYTD-this.reimbursementYTD,caso,bp1,bp2);

			// 4: Adjust the capital of the insurer and the patient (and add reimbursement
			// to InsuranceCompany's reimbursementLog)
			int tick = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
			
			this.insurer.capital	-=reimbursement;
			this.insuree.getReimbursed(reimbursement);
		}

		return reimbursement;

	}

	/**
	 * This method returns the potential reimbursement of a health insurance given
	 * the consultation result [{@link MedicalConsultation}]
	 * 
	 * @param consultationResult The result of a medical consultation in form of an
	 *                           object of class {@link MedicalConsultation}
	 * @return the potential reimbursement (potential because no payment is done
	 *         here)
	 * @author Florian
	 * @version 14-Nov-2018 (no unit testing required)
	 */
	public double computeReimbursement(double expenses) {
		// FIXME PP Appl. 02: Here we can include a check whether the provider qualifies
		// for this insurance plan (e.g. if part of a affiliated PPO).
		double reimbursement = this.getReimbursement(expenses, false, "");
		return reimbursement;
	}

	/**
	 * Returns the maximum budget given the maximum amount of out-of-pocket
	 * expenditures, given the specific insurance plan. Hence, the result of the
	 * method is maxOOP + expected reimbursement.
	 * 
	 * @param maxOOP maximum amount the patient can pay out-of-pocket
	 * @return OOP + reimbursement.
	 * @author Florian
	 * @version 14-Nov-2018 (unit test passed)
	 * 
	 */
	public double getBudget(double maxOOP) {

		// Find breaking points (on the OOP axis)
		double bp1 = this.deductible;
		double bp2 = 999999999;
		// double bp2 = Double.POSITIVE_INFINITY;
		if (this.stopLoss != 0) {
			bp2 = this.stopLoss; // UNIT TESTING: passed
		} else if (this.stopClaim != 0) {
			bp2 = bp1 + (this.copaymentRate) / (1 - this.copaymentRate) * this.stopClaim; // UNIT TESTING: passed
		}

		// Find the budget
		double budget = 0.0;
		if (maxOOP + this.claimsYTD <= bp1) { // all before bp1
			budget = maxOOP;
		} else if (this.claimsYTD < bp1 && this.claimsYTD + maxOOP > bp1 && this.claimsYTD + maxOOP < bp2) { // starting
																												// before
																												// bp1,
																												// jumping
																												// between
																												// bp1
																												// and
																												// bp2
			budget = (bp1 - this.claimsYTD) + (maxOOP - (bp1 - this.claimsYTD)) / this.copaymentRate;
		} else if (this.claimsYTD < bp1 && this.claimsYTD + maxOOP > bp1 && this.claimsYTD + maxOOP >= bp2) { // starting
																												// before
																												// bp1,
																												// jumping
																												// beyond
																												// bp2
			if (this.stopLoss != 0) {
				budget = 999999999;
				// budget = Double.POSITIVE_INFINITY;
			} else {
				budget = maxOOP + (1 - this.copaymentRate) / this.copaymentRate * (bp2 - bp1);
			}
		} else if (this.claimsYTD >= bp1 && this.claimsYTD < bp2 && this.claimsYTD + maxOOP > bp1
				&& this.claimsYTD + maxOOP < bp2) { // starting and remaining between bp1 and bp2
			budget = 1 / this.copaymentRate * (maxOOP);
		} else if (this.claimsYTD >= bp1 && this.claimsYTD < bp2 && this.claimsYTD + maxOOP > bp1
				&& this.claimsYTD + maxOOP >= bp2) { // starting between bp1 and bp2, jumping beyond bp2
			if (this.stopLoss != 0) {
				budget = 999999999;
				// budget = Double.POSITIVE_INFINITY;
			} else {
				double alreadyPaid = (this.claimsYTD - this.deductible) * (1 - this.copaymentRate);
				// System.out.printf("already paid: %s\n",alreadyPaid);
				budget = maxOOP + Math.min(this.stopClaim - alreadyPaid,
						(1 - this.copaymentRate) / this.copaymentRate * maxOOP);
			}
		} else if (this.claimsYTD >= bp2) { // all beyond bp2
			if (this.stopLoss != 0) {
				budget = 999999999;
				// budget = Double.POSITIVE_INFINITY;
			} else {
				budget = maxOOP;
			}
		}

		return budget;

	}




	// get methods
	public int getPatientID() {
		return this.insuree.ID;
	}

	public int getPlanID() {
		return this.ID;
	}

	public double getClaimsYTD() {
		return this.claimsYTD;
	}

	/** FOR EXPORT TO REPAST ONLY*/
	public double getClaimsYTDExport() {
		return Double.parseDouble(String.format("%.04f",this.claimsYTD));
	}

	public double getProfits() {
		return this.assocHIPlan.profits[0]; // FIXME: for Alejandro by Florian: here I added assocHIPlan to make it
											// work, not sure if this is ok.
	}

	public double getReimbursementes() {
		return this.reimbursementYTD;
	}
	/** FOR EXPORT TO REPAST ONLY*/
	public double getReimbursementesExport() {
		return Double.parseDouble(String.format("%.04f",this.reimbursementYTD));
	}

	public double getDeductible() {
		return this.deductible;
	}

	public int isHealthInsurance() {
		return 1;
	}

	public int getAssocHIPlanID() {
		return this.assocHIPlan.ID;
	}

}
