package healthABM;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduledMethod;

public class InsuranceCompany {
	
	// Instance variables
	/** ID of the insurance company*/
	private int ID;
	
	/**Indicates if the InsuranceCompany is profit-maximizing (true) or not (false) */
	private boolean profitMaximizing;
	
	/** Current capital of the insurance company */ 
	double capital;
	
	/** Profit of the last period*/	
	private double profit;
	
	/** List of all of the InsuranceCompany's HealthInsurances*/
	protected ArrayList<HealthInsurance> insurances = new ArrayList<HealthInsurance>();
	
	/** List of all of the InsuranceCompany's HIPlans*/
	protected ArrayList<HIPlan> hiPlans = new ArrayList<HIPlan>();
	
	/** Profit target of the insurance company. For instance, a value of 0.2 would mean that the insurance company aims at keeping at least 20% of the prime income.*/
	//TODO: FUTURE: this is not used in current version, since PID controller was implemented
	protected double profitTarget;
	

	
	

	
	
	
	// CONSTRUCTORS
	/**
	 * Simple constructor for insurance companies. The capital must be set by the modeller, while all other values are initiated in zeros (profit, contracts, etc)
	 * @param id ID of the company (used for the data-export
	 * @param cap Initial capital of the insurance company. 
	 * @param pt Profit target
	 * @author Florian Chavez
	 * @version 28mar19
	 */
	public InsuranceCompany(int id,double cap,double pt){
		this.ID = id;
		this.capital 	= cap;
		this.profitTarget = pt;
		//this.profit 	= 0.0;
		//TODO FUTURE 03: initially, we will assume all InsuranceCompanies are profit-maximizing. We should change this afterwards, in order to allow the existance of non-profit-maximizing InsuranceCompanies (IMSS)
		this.profitMaximizing = true;
		
		System.out.printf("I just created a new insurance company with ID=%s, initial capital =%s and a profit target of %s\n",id,cap,pt);
	}


	/**
	 * Adds the insurance in the argument to the list of insurances of the company. This method is called by the constructor of insurances {@link HealthInsurance#HealthInsurance(InsuranceCompany, Patient, double, double, double, double)}
	 * @param healthInsurance Insurance to be added
	 */
	public void addInsurance(HealthInsurance healthInsurance) {
		this.insurances.add(healthInsurance);
	}


	// SCHEDULED METHODS
		

	
	/** This method updates the premiums in each of the InsuranceCompany's HIPlans. It:
	 * 1. Updates the premiums charged by this InsuranceCompany's HIPlans
	 * 2. Resets the number of insurees per HIPlan for the following year
	 * 
	 * At the end, this method also cleans the array of health insurances (removing old contracts)
	 * @version 03-Apr-2019 TODO: unit testing pending!
	 * @author alejandrob
	 */
	@ScheduledMethod(start=1,interval=52,priority=95,shuffle=true)
	public void stepAdaptHIOffer() { //STEP 95: stepAdaptHIOffer()
		/* Loop over each of this InsuranceCompany's HIPlans*/

		
		if ((int)RunEnvironment.getInstance().getCurrentSchedule().getTickCount()>51) {
			for (HIPlan hiPlan : this.hiPlans) {
				this.updatePrimeForHIPlan(hiPlan);
				// Set the cumulative number of insurees for this HIPlan to zero
				this.resetYearsInsurees(hiPlan);
			}
		}
		//Update prime for each of this InsuranceCompany's HIPlans

		
		
		
		
// Comment out updating of primes 
//		for(HIPlan hiPlan : this.hiPlans) {
//			if (this.profitMaximizing) {
//				/* if this InsuranceCompany IS profit-maximizing InsuranceCompany...*/
//				updatePrimeForHIPlan(hiPlan);
//			}
//			
//			/* If this InsuranceCompany IS NOT profit-maximizing...*/
//			if (this.profitMaximizing == false) {
//				/* If financial requirements are not met...*/
//				// TODO: FUTURE (to be implemented in future versions)
//				if (checkFinancialRequirements() == false) {
//					updatePrimeForHIPlan(hiPlan);
//				}
//			}
//			
//		
//			
//		}
		
		//POLICY 1: CHECK IF THE MINIMUM DEDUCTIBLE MUST BE INCREASED
		int initPolicy1 = RunEnvironment.getInstance().getParameters().getInteger("policy1tick");
		int minDeductible = RunEnvironment.getInstance().getParameters().getInteger("policy1minDeductible");
		int currentTick = (int)RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		
		// If the policy is already in place, check each HIPlan
		if (currentTick>=initPolicy1) {
			for(HIPlan h : this.hiPlans) {
				if(h.deductible<minDeductible) { // For plans with a lower deductible, change the deductible
					double oldDeductible = h.deductible;
					h.deductible = minDeductible;
					h.stopLoss = (int) (h.deductible + 700);	
					System.out.printf("I applied the new policy to one of my health insurances. Deductible before: %s, => now %s\n",oldDeductible,h.deductible);
					
				}
			}
			
		}
		

		// Clear the array of insurances (remove those which are no longer valid)
		Iterator<HealthInsurance> iter = this.insurances.iterator();
		while(iter.hasNext()) {
			HealthInsurance i = iter.next();
			if(i.insuree==null) {
				iter.remove();
			}
		}

	}
	
	
	
	/**This method resets the number of insurees of the given HIPlan*/
	private void resetYearsInsurees(HIPlan hiPlan) {
		hiPlan.numInsurees = 0;
	}


	/** Checks if the InsuranceCompany is able to observe its financial requirements for a given HIPlan and a parameter, r
	 * @param r Required probability for the InsuranceCompany to observe its financial liabilities towards its insurees
	 * @return boolean true if the InsuranceCompany is able to observe the financial requirements for the given HIPlan
	 * @autor alejandrob
	 * @version 02-Apr-2019
	 */
	// TODO FUTURE: For future versions
	private boolean checkFinancialRequirements() {
		//TODO FUTURE 06: Not profit-maximizing; program this (esto se puede hacer al final, una vez que esté listo lo demás)
		double r = RunEnvironment.getInstance().getParameters().getDouble("solvency_capital_req");
		return true;
	}
	
	
	/** Compute total profits associated to a given HIPlan and period
	 * @return double Total profits associated to the given HIPlan and period
	 * @author alejandrob
	 * @version 02-Apr-2019
	 */
	/*private double getProfitsByHIPlanAndTickRangeX(int tickFrom, int tickTo, int hipID) {
		 Compute total loss and revenue for given period 
		//double totalLoss = getReimbursementByHIPlanAndTickRange(tickFrom, tickTo, hipID);
		//double totalRevenue = getRevenueByHIPlanAndTickRange(tickFrom, tickTo, hipID);
		//return totalRevenue - totalLoss;
	}*/
	
//	/** Update prime for given HIPlan using PID controller
//	 * @param plan HIPlan whose prime is to be updated
//	 * @author alejandrob
//	 * @version 02-Apr-2019 TODO: unit testing pending!
//	 */
//	private void updatePrimeForHIPlan(HIPlan plan){ 
//		double newPrime;
//		/* get sensibility parameter for PID */
//		double alpha = RunEnvironment.getInstance().getParameters().getDouble("alpha_pid_prime_update");
//		int currentTick = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
//		/* update the HIPlan's array for historic primes: 
//		 * primes[1] = prime for t-2
//		 * primes[0] = prime for t-1     */
//		plan.primes[1] = plan.primes[0];
//		plan.primes[0] = plan.prime;
//		/* update the provided HIPlan's array for historic profits:
//		 * profits[1] = profits for t-2
//		 * profits[0] = profits for t-1   */
//		plan.profits[1] = plan.profits[0];
//		plan.profits[0] = getProfitsByHIPlanAndTickRange(currentTick - 52, currentTick, plan.ID); 
//		
//		/* prevent PID's denominator from being zero*/
//		if (plan.primes[0] == plan.primes[1]) plan.primes[0] = plan.primes[1] + 0.00000001;
//		/* compute new prime */
//		newPrime = plan.primes[0] + alpha*(plan.profits[0] - plan.profits[1])/(plan.primes[0] - plan.primes[1]);
//		/* prime value must be positive*/
//		newPrime = Math.max(0, newPrime);
//		/* assign new prime to HIPlan */
//		plan.setPrime(newPrime);
//		/* update the HIPlan's profit and primes array*/
//		double[] primes = {plan.primes[0], plan.primes[1]};
//		double[] profits = {plan.profits[0], plan.profits[1]};
//		plan.setPrimes(primes);
//		plan.setProfits(profits);
//		
//		
//	}
	
	
	/** Update prime for given HIPlan using PID controller
	 * @param plan HIPlan whose prime is to be updated
	 * @author alejandrob
	 * @version 06-Jun-2019
	 */
	private void updatePrimeForHIPlan(HIPlan plan) {
		
		// Compute the total expenditures of the insurance
		double totalReimbursements = 0;
		for(HealthInsurance hi: this.insurances) {
			if(plan==hi.assocHIPlan) {
				totalReimbursements += hi.reimbursementYTD;
			}
		}
		
		// Get the number of insurees (to compute the average)
		double totalInsurees = (double) plan.numInsurees;
		System.out.printf("Cost: %s,     Insurees:%s\n",totalReimbursements,totalInsurees);
		
		// Store information in the log
		if(totalInsurees>0) {
			plan.averageCostPerInsuree.add(0,totalReimbursements/totalInsurees);
			
			while(plan.averageCostPerInsuree.size()>5) {
				plan.averageCostPerInsuree.remove(5);
			}
		}
		
		
		double averageCost =0;
		for(double v:plan.averageCostPerInsuree) {
			averageCost +=v;
		}
		averageCost = averageCost / plan.averageCostPerInsuree.size();
		
		
		
		
		
		double oldPremium = plan.premium;
		
		//If no insurees were insured under HIPlan, set prime to a fraction of its current value
		double newPremium =  totalInsurees != 0 ? averageCost : plan.premium*RunEnvironment.getInstance().getParameters().getDouble("maxReductionPremiums");
		//Check that new prime is within lower limit for prime reductions
		newPremium = Math.max(newPremium, plan.premium*RunEnvironment.getInstance().getParameters().getDouble("maxReductionPremiums"));
		//Check that the new premium is within upper limit raise set by current legislation
		newPremium = Math.min(newPremium, plan.premium*RunEnvironment.getInstance().getParameters().getDouble("maxRaisePremiums"));
		
		plan.premium = newPremium;
		System.out.printf("\nPlan (ID=%s) YTD reimbursements: %s; insurees: %s; old prime: %s; new prime: %s\n", plan.ID, totalReimbursements, totalInsurees, oldPremium, newPremium);
		//System.out.printf("\nCumulative reimbursements from function: %s", plan.getYearsCumulativeReimbursements());
		
		System.out.printf("Cost historic:");
		for(double cpi: plan.averageCostPerInsuree) {
			System.out.printf("\t%s",cpi);
		}
		System.out.println();
	}
	
	
	// OTHER METHODS

	
	
	/**
	 * Method to analyse whether the insurance company accepts the patient as client. The current version 
	 * simply return a TRUE if the insuree is accepted and FALSE otherwise. The method also checks if the 
	 * age range for which the plan is designed has been respected. 
	 * @param patient the potential insuree
	 * @param plan Health insurance plan the patient want to contract {@link HealthABM.HIPlan}
	 * @return true=accept, false=reject
	 */
	public boolean checkAcceptInsuree(Patient patient, HIPlan plan){
		
		// First check if the legislation allows the insurance company to reject
		boolean allowReject = RunEnvironment.getInstance().getParameters().getBoolean("allowRejectInsuree");
		
		boolean answer = true; // default answer (e.g. when insurance companies must accept clients 
		
		// Check if the plan is available for this age group
		if(patient.age<plan.getMinAge() || patient.age>plan.getMaxAge()){
			answer = false;
		}
		
		
		// In case the company is allowed to reject clients based on expected health care expenditure
		if(answer==true && allowReject==true){
			// Now the firm can first estimate the expected HCE and then decide
			double expectedHCE = this.computeExpectedHCE(patient);
			if(expectedHCE>=(1-this.profitTarget)*plan.premium){
				answer = false;
			}
		}
		
		
		return answer; 
	}
	
	/**
	 * Computes the expected health care expenditure of the individual based on information that is available to the 
	 * insurance company. There might be similar methods using other information sets. 
	 * @param patient The individual for which we compute the expected health insurance cost. 
	 * @return double value of expected HCE
	 */
	public double computeExpectedHCE(Patient patient){
		return 0.0; 
		// TODO FUTURE 01: florian: program price discrimination
		/*
		 * PSEUDO-CODE (Proposals)
		 *  basically the idea is to compute E[HCE | X ] where the big question is what 
		 *  to put in X and what estimator to use. 
		 *  
		 *  Approach 1: 	Non-parametric approach by groups of the population => limited number of vars in X
		 *  				E[HCE | gender, age-group]
		 *  Approach 2: 	Use of a regression analysis using past information => allows us to include more variables
		 *  Approach 3: 	1 or 2 plus additional information on past consumption (if available)
		 */
	}
	
	/**
	 * This method performs two tasks:
	 * 1. It updates the Insurance Company's capital  and the primes counter by adding to it a prime paid by an insuree
	 * 2. It adds the paid prime to the Insurance Company's primeLog
	 * @param tick Tick in which the prime is paid
	 * @param ptID The unique ID of the Patient that is paying the prime
	 * @param hipID HIPlan to which the Patient's HealthInsurance belongs
	 * @param prime value of the prime
	 * @author alejandrob
	 * @version 01-Apr-2019
	 */
	public void getPaidPrimeAndAddValueToPrimeLogX(int tick, int ptID, int hipID, double prime) {
		/*this.capital +=prime;	
		
		// Add new prime to primeLog
		Integer[] arrID = {tick, ptID, hipID};
		Model.addValueToLog(arrID, prime, this.primeLog);*/
	}
	
	
	/**
	 * Returns reimbursement amount issued by the HealthInsurance's InsuranceCompany for given tick, Patient and HIPlan
	 * @param tick Tick in which the reimbursement is issued
	 * @param ptID Reimbursed Patient's ID
	 * @param hipID HIPlan ID under which the reimbursement is issued
	 * @return If entry is found, returns amount reimbursed for the given tick, Patient and HIPlan; if entry is not found, returns Double.NaN
	 * @author alejandrob 
	 * @version 21-Feb-2019 unit test: passed
	 */
	/*protected double getReimbursementByTickPatientAndHIPlan(int tick, int ptID, int hipID) {
		double sum = 0;
		boolean found = false;
		for(Entry<Integer[], Double> e: this.reimbursementLog.entrySet()) {
			if(e.getKey()[0] == tick && e.getKey()[1]== ptID && e.getKey()[2] == hipID) {
				found = true;
				sum += e.getValue();
			}
		} 
		if (found) return sum;
		return Double.NaN;
	}*/
	
	/**
	 * Returns reimbursement amount issued by the HealthInsurance's InsuranceCompany for a given tick and HIPlan 
	 * @param tick
	 * @param hipID
	 * @return reimbursed amount for given HIPlan and tick
	 * @author alejandrob
	 * @version 21-Feb-2019 unit test: passed
	 */
	/*protected double getReimbursementByTickAndHIPlan(int tick, int hipID) {
		double sum = 0;
		for(Entry<Integer[], Double> e: this.reimbursementLog.entrySet()) {
			if(e.getKey()[0] == tick && e.getKey()[2] == hipID) {
				sum += e.getValue();
			}
		}
		return sum;
	}*/
	

	/**
	 * Returns the total reimbursements for a given HIPlan, from specified range of ticks
	 * @param tickFrom specified range start tick (included)
	 * @param tickTo specified range end tick (included)
	 * @param hipID specified HIPlan for which the computation is done
	 * @return sum total reimbursements for given HIPlan, start and end ticks
	 * @author alejandrob
	 * @version 21-Feb-2019 unit test: passed
	 */
	/*protected double getReimbursementByHIPlanAndTickRange(int tickFrom, int tickTo, int hipID) {
		double sum = 0;
		for(Entry<Integer[], Double> e: this.reimbursementLog.entrySet()) {
			if(e.getKey()[0] >= tickFrom && e.getKey()[0]<= tickTo && e.getKey()[2] == hipID) {
				sum += e.getValue();
			}
		}
		return sum;
	}*/
	
	/**
	 * Returns the total revenue (sum of revenue) for a given HIPlan, from a specified range of ticks
	 * @param tickFrom specified range start tick (included)
	 * @param tickTo specified range end tick (included)
	 * @param hipID specified HIPlan for which the computation is executed
	 * @return sum total revenue for given HIPlan, start and end ticks
	 * @author alejandrob
	 * @version 03-Apr-2019 unit test: passed
	 */
	/*public double getRevenueByHIPlanAndTickRange(int tickFrom, int tickTo, int hipID) {
		double sum = 0;
		for (Entry<Integer[], Double> e: this.primeLog.entrySet()) {
			if(e.getKey()[0] >= tickFrom && e.getKey()[0] <= tickTo && e.getKey()[2] == hipID) {
				sum += e.getValue();
			}
		}
		return sum;
	}*/
	

	/** @return the capital of the insurance company*/
	public double getCapital(){
		return this.capital;
	}
	/*
	public double getProfit() {
		return this.profit;
	}
*/
		
	
	
	
	public double getTotalReimbursements(){
		double reimbursement = 0.0;

		for(HealthInsurance hi:this.insurances) {
			reimbursement+=hi.reimbursementYTD;
		}
		return reimbursement;
	}
	
	public double getRevenue() {
		double sum = 0;
		for(HealthInsurance hi:this.insurances) {
			sum +=hi.premium / 52;
		}
		
		return sum;
	}
	
	
}
