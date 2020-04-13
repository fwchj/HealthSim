package healthABM;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet; 
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import repast.simphony.context.Context;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.parameter.Parameters;
import repast.simphony.query.space.continuous.ContinuousWithin;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.NdPoint;
import repast.simphony.util.ContextUtils;

public class Patient {
	
	/** Poverty line as defined in a parameter 'povertyLine'*/
	protected static double incomeSesThreshold;

	// INSTANCE VARIABLES
	/** Capital (savings) of the individual */
	private double 							capital;
	
	/** Spatial Location*/
	private ContinuousSpace <Object> 		location;
	
	/**Identifier for Patients */
	protected int 							ID;
	
	/** Age in weeks */
	protected int 							age;
	
	/** Gender: true: female, false: male*/
	protected final boolean 				female;
	
	/** Weekly income (=income per tick)*/
	protected final double 					income;
	
	/** Insurance plans that an individual has currently*/
	private ArrayList<HealthInsurance> 		insurance;
	
	/**Current family doctor of patient, patient could seek attention with another provider*/
	protected Provider 						familyDoctor;
	
	/** General Health Status between 0 (death) and 1 (perfect health). It is updated during the current process */
	public double 							HS;
	
	/** List of diseases that patient has */
	protected ArrayList<MedicalCondition> 	medConditions = new ArrayList<MedicalCondition>(); 
	
	/** Aversion to risk: 1 totally risk averse, 0 no risk aversion */
	protected double 						riskAversion;
	
	/** Out of Pocket Expenditures*/
	double 									OOPExp;
	
	/** List of Providers that patient has visited and rated at least once, and their scores (well diagnosed/ all diagnosed).<br><br>
	 *  <b>HashMap</b>&lt;Provider,int[number of good diagnosis, total number of diagnosis]&gt;>*/
	private LinkedHashMap<Provider,int[]> 		experience= new LinkedHashMap<Provider,int[]>();
	
	/** Patient's tolerance to health severity*/
	double 							tolerance;

	/** Array list with all the other individuals in the social network*/
	//protected ArrayList<Patient> 			socialNetwork;
	
	/** Lists of providers that the patient visits on current period*/
	protected ArrayList<Provider> 			visits;  
	
	/** Perceived medical needs. This value is updated each tick by {@link #getPerceivedMedicalNeeds()} within the scheduled method {@link #stepGetMedicalCare()} */
	protected double 						perceivedMedicalNeeds;
	
	/** Willingness to pay registered during the current tick*/
	protected double 						wtp;
	
	/** Expected costs that patient facing in decision to visit provider*/
	protected double 						expectedOOPExp;
	
	/** Patient's subjective estimate of health expenditures */
	protected double 						subjectiveExpectedExpen;
	
	/** Number of appointments per tick */
	protected int							numAppointments;
	
	/**Cost associated to changing between plans (greater than 1)*/
	protected double						changePlansCost;
	
	protected double						selfMedCosts;

	
	
	

	protected double ytd_expenditures;
	protected LinkedHashMap<Integer,Double> annualExpentitures;
	/** Health status after getSick() before treat(), visible */
	protected double visibleHealthStatusBeforeTreat;
	
	/** Health status after getSick() before treat(), all */
	protected double healthStatusBeforeTreat;
	
	Parameters params = RunEnvironment.getInstance().getParameters();
	
	
	// CONSTRUCTOR
	/** Constructor for the patient
	 * @param income as double: put here the monthly income. It will automatically be converted to weekly (by tick) income. 
	 * @param age as double
	 * @param female as boolean (true for female)
	 * @param healthStatus health status as double
	 * @param tol tolerance to illness severity as double
	 * @param location location as ContinuousSpace
	 * */
	public Patient (double income, int age, boolean female, double healthStatus, double tol, ContinuousSpace<Object> location){
		this.location 	= location;
		this.income 	= income * 12 / 52;  // Convert the monthly income to weekly income
		this.age 		= age;
		this.female 	= female;
		this.HS				= healthStatus;
		
		this.riskAversion	= RandomHelper.createBeta(5, 1.5).nextDouble();
		this.tolerance 		= tol; //FIXME: do all agents have the same tolerance? 
		this.ID 			= Model.counterPatients++;
		this.perceivedMedicalNeeds	= 0.0;
		this.insurance		= new ArrayList<HealthInsurance>();
		
		this.visits			= new ArrayList<Provider>();
		//this.expenditureLog = initiateExpenditureLog();
		this.annualExpentitures = initiateExpenditureLog(); //FIXME: verify this (new on october 31, 2019)
		//this.expenditureLog = new HashMap<Integer[], Double>(); //THIS LINE TO BE UNCOMMENTED FOR UNIT TESTING ONLY 
		this.subjectiveExpectedExpen = 0.0;
		this.numAppointments = 0;
		this.changePlansCost = RandomHelper.createUniform(1, 1.3).nextDouble();
		this.selfMedCosts = 0.0;
		
		
		

	}
	



	// SCHEDULED METHODS     //

	
	
	//STEP 099: stepResetPatient
	@ScheduledMethod(start=1,interval=1,priority=99,shuffle=true)
	/** At the beginning of each tick, the individual's pertinent instance variables are reset */
	public void stepResetPatient(){
		@SuppressWarnings("unchecked")
		Context<Object> context = ContextUtils.getContext(this);
		
		// 1 : resetting or adjusting values
		expectedOOPExp	=	0.0;
		visits 			= 	null;
		OOPExp 			= 	0;
		wtp				=	0;
		HS				=	this.getHealthStatus();
		numAppointments = -1;
		
		// In case the person has debt, the debt is increased by the interest
		if(this.capital<0){
			Parameters params   = RunEnvironment.getInstance().getParameters();
			double irate 		= params.getDouble("interestDebt"); 
			this.capital  = (1+irate)*this.capital;
		}
		else {
			this.capital = 0.1*capital; // FIXME: feb2020: is that ok? 
		}
		// Add income;
		capital+=income;			
		
		
		// Pay insurance
		for(HealthInsurance ins: this.insurance) {
			payHealthInsurance(ins); 
		}
		
		// Add one unit to the age of the patient (weeks)
		age++;
	
		
		// 2: CHECK IF THE PATIENT DIES
		if(this.checkIfDies()){
			this.die();
		}
		
		// 3: Remove cured medical conditions if the patient is not dead
		else{ 
			ArrayList<MedicalCondition> toDelete = new ArrayList<MedicalCondition>();
			for(MedicalCondition mc:this.medConditions) {
				if(mc.getCurrentSeverity()<=0.0000000001) { // hack to avoid keeping medical conditions due to precision issues
					//System.out.printf("I remove medical condition ID=%s\n", mc.medConditionID);
					mc.clear();		// Deletes all the links from the MC to other objects
					//System.out.printf("Context size before: %s ... ",context.size());
					
					context.remove(mc);
					toDelete.add(mc);
					//System.out.printf("and after deleting the MC: %s\n",context.size());
					
				}
			}
			
			// Now remove the medical condition from the arraylist of the patient. 
			for(MedicalCondition mc:toDelete) {
				this.medConditions.remove(mc);
			}
			
		}
	}
	
	
	
	/** (1) The Patient select and pay for an annual health insurance.<br>
	 * 	(2) The Patient's capital is updated after payment<br>
	 *  (3) The selected HealthInsurance is added to the Patient's AL of HealthInsurances <br>
	 *  @version 26-Jun-2019 --> alejandrob added parameter for cost associated to changing between HIPlans
	 * Process of insurance selection is waiting for discussion {@link Patient#selectInsurance()} */
	@ScheduledMethod(start=1,interval=52,priority=90,shuffle=true)	//STEP 090: stepContractInsurance
	public void stepContractInsurance(){
		
		//Create and fill AL with associated HIPlans IDs of Patient's current HealthInsurances --> this will be used in the process of contracting a new HealthInsurance: there will be a non-monetary cost associated to changing between HIPlans
		ArrayList<Integer> oldHIPs = new ArrayList<Integer>();
		
		for(HealthInsurance hi : this.insurance) {
			oldHIPs.add(hi.assocHIPlan.ID);
		}
			
		
		// REMOVE OLD CONTRACTS
		Iterator<HealthInsurance> iter = this.insurance.iterator();
		while(iter.hasNext()) {
			HealthInsurance i = iter.next();
			int duration = (int)RunEnvironment.getInstance().getCurrentSchedule().getTickCount()-i.startContract;
			if(duration>=0) { // here we kill all insurance contract because in the CH system all start the same day. 
				Model.context.remove(i);
				i.insuree=null;
				i.insurer=null;
				iter.remove();
			}
		}
		
		
		
		// (1) Find the best health insurance plan
		HIPlan selectedPlan= this.selectInsurance(oldHIPs);
		
		// (2) Contract the insurance plan
		if(selectedPlan!=null){
				HealthInsurance ins= new HealthInsurance(selectedPlan, this);

				// Pay the insurance plan up-front
				//payHealthInsurance(ins); // MOVED TO THE WEEKLY PAYMENTS
			
				// (2.1) Add new health insurance to the respective ArrayLists
				if(selectedPlan.insurer!=null){
					// COMMENTED OUT BECAUSE ALREADY DONE IN constructor of HEALTHINSURANCE
					//selectedPlan.insurer.addInsurance(ins);
					//this.setInsurance(ins);	
				}
				else{
					//ASSERTION
					System.out.printf("Individual %s just contracted a HIPlan which had no insurer (FATAL ERROR)\n",this.ID);
					System.exit(0);
				}
			}
		else {
			if(Model.mandatoryInsurance) {
				System.out.printf("Individual %s did not find a health insurance",this.ID);
				System.exit(0);				
			}else {
				
			}
		}
		
		// ASSERTION (swiss system): the agent should not have more than 1 insurance
		if(this.insurance.size()!=1) {
			//System.out.printf("The number of insurances of individual %s is not equal to 1!\n",this.ID);
			for(HealthInsurance ins:this.insurance) {
				System.out.printf("=> ID: %s, Start tick =%s\n", ins.ID,ins.startContract);
			}
			if(Model.mandatoryInsurance) {
			System.exit(2);;
			}else if(!Model.mandatoryInsurance & this.insurance.size()>1){
				System.exit(2);;
			}else {
				
			}
		}
	}


	
	/**This method determines if this Patient contracts any of the existing Illnesses in the model
	 * for every tick. If so, a new MedicalCondition for this Patient is created and added to the 
	 * List of her MedicalConditions */

@ScheduledMethod(start=1,interval=1,priority=80,shuffle=true)	//STEP 080: stepGetSick()
	public void stepGetSick() {
	 
	//declare variables

	//create AL of Illnesses the Patient has in the current tick
	ArrayList<Illness> patientsIllnesses = new ArrayList<Illness>();
	
	//Loop over this Patient's current MedicalConditions to fill patientsIllnesses
	for(MedicalCondition mc: this.medConditions) {
		patientsIllnesses.add(mc.illness);
	}
	
	
	// GET THE HASHMAP WITH THE ILLNESS PROBABILITIES
	LinkedHashMap<Illness,Double> myIllnessProbabilities = new LinkedHashMap<Illness,Double>();
	int genderInt = this.genderToInteger();
	int age = (int) Math.floor(this.age/52);
	
	for(Integer[] e:Model.illnessProbability.keySet()) {
		if(e[0]==genderInt && e[1]<= age && e[2]>=age) {
			myIllnessProbabilities = Model.illnessProbability.get(e);
			break;
		}
	}
	
	// CONTRACT ILLNESSES
	for(Map.Entry<Illness,Double> e:myIllnessProbabilities.entrySet()) {
		Illness illness = e.getKey();
		
		// If this Patient already has this illness, continue to next illness
				if(patientsIllnesses.contains(illness)) {continue;}
				
				double probOfContractingIllness = e.getValue()/52.0;
			
				//Determine if this Patient contracts this Illness. If so, generate new MedicalCondition for this Patient
				
				double randomValue = RandomHelper.createUniform(0.0,1.0).nextDouble();
				
		
				
				if(randomValue<probOfContractingIllness) {
					
					
					this.medConditions.add(new MedicalCondition(illness,illness.initialSeverity, this));
					
					//print to console
//						System.out.printf("\n\nPATIENT NO. %s CONTRACTED %s!!!", this.getID(), illness.name);
//						System.out.printf("\nPatient no. %s is female = %s, is %s years old, HS = %s, poor=%s", this.getID(), this.getFemale(), this.getAgeYears(), this.getHealthStatus(), this.isPoor());
//						System.out.printf("\nPatient no. %s\'s probability of contracting %s is %s", this.getID(), illness.name, probOfContractingIllness);
//						System.out.printf("\nPatient no. %s now has %s medical conditions:", this.getID(), this.medConditions.size());
						for(MedicalCondition mc1: this.medConditions) {
							//System.out.printf(" %s,", mc1.illness.name);
						}
						
				} // end if indeed getting the new medical condition
						
		
		
	}
	
	//CHECK IF THE PATIENT DIES
	/*if(this.checkIfDies()){
		this.die();
	}*/	
	
	
	/* [OLD VERSION WITH THE PROBIT PROBABILITIES
	//for each existing Illness in the model...
	for(Illness illness:Model.listIllnesses) {
		
		// If this Patient already has this illness, continue to next illness
		if(patientsIllnesses.contains(illness)) {continue;}
		
		//estimate probability of contracting illness for this Patient (Probit model)
		probitArgument = illness.betas.get("constant") +
						illness.betas.get("female")*(this.female == true ? 1 : 0) +
						illness.betas.get("age")*this.getAgeYears() +
						illness.betas.get("age2")*Math.pow(getAgeYears(),2) + 
						illness.betas.get("ses")*(isPoor()==true? 0 : 1)+ 	
						illness.betas.get("hs")*this.HS+
						illness.betas.get("contact")*getNumberOfPatientsWithinContagionRadius(params);		
		
		probOfContractingIllness = new NormalDistribution().cumulativeProbability(probitArgument);
	
		//Determine if this Patient contracts this Illness. If so, generate new MedicalCondition for this Patient
		if(RandomHelper.nextDoubleFromTo(0, 1)<probOfContractingIllness) {
			
			this.medConditions.add(new MedicalCondition(illness,illness.initialSeverity, this));
			
			//print to console
				System.out.printf("\n\nPATIENT NO. %s CONTRACTED %s!!!", this.getID(), illness.name);
				System.out.printf("\nPatient no. %s is female = %s, is %s years old, HS = %s, poor=%s", this.getID(), this.getFemale(), this.getAgeYears(), this.getHealthStatus(), this.isPoor());
				System.out.printf("\nPatient no. %s\'s probability of contracting %s is %s", this.getID(), illness.name, probOfContractingIllness);
				System.out.printf("\nPatient no. %s now has %s medical conditions:", this.getID(), this.medConditions.size());
				for(MedicalCondition mc1: this.medConditions) {
					System.out.printf(" %s,", mc1.illness.name);
				}
				
		} // end if indeed getting the new medical condition
		
		//Reset values
		probitArgument = Double.NEGATIVE_INFINITY;
		probOfContractingIllness = 0;
	}	*/
}

/** In this step...
 * <ol><li>Patients decide whether or not to visit a Provider</li> 
 * <li>Patients decide which Provider they will visit.</li>
 * <li>Provider analyze Patient and Patient pays Provider. Patient's income is updated and income is updated.</li>
 * <li>Patients get treated, and their MedicalCondition(s) get better if treated correctly or worsen if not. </ol>
 * @author LNPP, Florian  
 * @version 09-Nov-2018
 * */
@ScheduledMethod(start=1,interval=1,priority=70,shuffle=true) //STEP 070: stepGetMedicalCare
public void stepGetMedicalCare(){ 
	
	// [1] Patient decides whether or not to visit a provider and which
		// Compute the perceived medical need
		this.numAppointments = 0; // reset the value 
		this.perceivedMedicalNeeds  = this.getPerceivedMedicalNeeds(); 
		this.wtp 					= this.computeWillingnessToPay();
		
		this.visibleHealthStatusBeforeTreat  = getHealthStatus(true);
		this.healthStatusBeforeTreat			= getHealthStatus(false);
		
		//System.out.printf("Patient %s has perceived med needs of %s and a WTP of %s\n",this.ID,this.perceivedMedicalNeeds,this.wtp);
		
		// Find the provider (can return null if no provider is available at the WTP value)
		Provider chosenProvider = this.decideVisitProvider();
		
		
		
		if(chosenProvider!=null){
			//System.out.printf("=> He/she found a provider: %s\n",chosenProvider.ID);
			
			// Get appointment with the GP
			double budget = this.computeBudget();
			MedicalConsultation consultationResult = chosenProvider.appointment(this,budget);
			//System.out.printf("Consultation result: ppp: %s\n", consultationResult.priceToPatient);
			//Update Patient's running budget (subtract priceToPatient = price of consultation + diagnosis investment)
			//this.runningBudget -= consultationResult.priceToPatient;
			//this.totalMedicalExpenses += consultationResult.priceToPatient;
			this.payConsultation(consultationResult);	
			
			// Check if the result is a referral
			if(consultationResult.referral!=null){
				Provider specialist = consultationResult.referral;
				budget = this.computeBudget();
				MedicalConsultation consultSpecialist = specialist.appointment(this,budget);
				this.payConsultation(consultSpecialist);
			}
			
			
			
			
			
		}
		else{ // => no provider for the WTP value: decide whether to self-treat or to wait&see
			//System.out.printf("=> He/she DID NOT FIND a provider. Now looking for self treatment\n");

			LinkedHashMap<MedicalCondition,Treatment> allPossibleTreatments = new LinkedHashMap<MedicalCondition,Treatment>();
			 // Check if a self-treatment is available (here the idea is that the patient gets the diagnosis right even without knowing it) for each medical conditions
			for(MedicalCondition mc:this.medConditions){
				//only gets self-treatment for medical conditions without treatments (i.e., if a Provider assigned a treatment, the mc will not be taken into acount here)
				if(mc.getDuration()>0 && mc.treatment==null){
					ArrayList<Treatment> selfTreatment = mc.illness.getTreatments(TreatmentType.SELF, mc.getCurrentSeverity());
								
					if(selfTreatment.size()>0){
						//Sort them by efficiency to get the best treatment
						Collections.sort(selfTreatment,new ComparatorTreatmentByEfficiency());
						// Take only the single most efficient treatment
						allPossibleTreatments.put(mc, selfTreatment.get(0));  
					}
				}
			}
			
			
			// If self-treatments are available: compute cost, check against WTP and take them if price<WTP
			if(allPossibleTreatments.size()>0){
				double cost= 0.0;
				
				for(Entry<MedicalCondition,Treatment> t:allPossibleTreatments.entrySet()){
					cost+=t.getValue().cost;
				}
				
				// If economically viable, the patient will take all these treatments. 
				if(cost<=this.wtp){
					for(Entry<MedicalCondition,Treatment> t:allPossibleTreatments.entrySet()){
						t.getKey().treatment = t.getValue();
					}
				}
				else { // wtp<cost => consume only up to wtp 					
					// CURRENTLY JUST IN A RANDOM ORDER (not optimal. ToDiscuss: discuss in which order).
					double remainingWTP = this.wtp;
					for(Entry<MedicalCondition,Treatment> t:allPossibleTreatments.entrySet()){
						if(t.getValue().cost<= remainingWTP) { // Check if there is money left
							t.getKey().treatment = t.getValue();	// Take the treatment
							remainingWTP -= t.getValue().cost;      // Reduce the remaining willingess to pay
						}
					}
				} // end else (not enought WTP to all treatment)
				
				
				
				
			} // end: size of available treatments >0
			
			
			
			
		}
		
		
		// [4] Apply all the treatments
		
		for(MedicalCondition mc:this.medConditions){
		
			mc.treat(); // we 'treat' all, because treatment with not actual treatment will increase the severity
		}
		
		}


/**
 * This methods fills the expenditure log upon initialisation of the model (and individual). 
 * At the beginning of the model we simply use the external data.
 * This backward looking is essentially used at the very beginning of the model. In fact, the method throws an error
 * if invoked later in the simulation
 * @author Florian Chavez
 * @version 14-Mar-2019 (unit testing pending)
 * @return Hash map with elements corresponding to 5 years (5 elements). 
 */
private LinkedHashMap<Integer, Double> initiateExpenditureLog() {
	LinkedHashMap<Integer, Double> log = new LinkedHashMap<Integer,Double>();
	 int currentTick = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
	 //System.out.printf("current tick: %s\n", currentTick);
	 
	 // should be -1 instead of 0!!
	 if(currentTick<=0) { // fill this up only upon initialisation
				 
		 // Find the minimum and maximum age for which we have information
		 int minKey = 9999;
		 int maxKey = -9999;
		 for(int key:Model.expHCE.keySet()) {
			 minKey = Math.min(key, minKey);
			 maxKey = Math.max(key, maxKey);
		 }
		 
		 // Compute the age of the agent today
		 int ageInYears = (int)Math.floor((this.age)/52);
		
		 // Loop backwards for the average cost they would have had (up to 6 years)
		 for(int y=0;y>=-5;y--) {
			 int ageBackThen = ageInYears - y;	// Get the age they would have had
			 ageBackThen = Math.min(maxKey, Math.max(minKey, ageBackThen));	// Limit to the age range for which we have data
			 
			 Double[] data = Model.expHCE.get(ageInYears);	// Get the average cost for that age
     		 Double hce = this.female ? data[0] : data[1]; // extract the correct gender
			 
     		log.put(y, hce);	// Store in the log
		 }
		 
		
	 }
	 
	 // 
	 return log; 
	 
}

/**
 * This methods computes the maximal value the provider will be able to charge (budget constraint). It is the sum of what the patient can pay and what the insurance 
 * company can pay. This value can be very low in case the individual has no funds but faces a co-payment restriction. 
 * @return double: maximal budget
 */
protected double computeBudget() {
	double maxBudget=0.0;
	// If Patient has no HealthInsurance, her budget is simply her capital
	if (this.insurance.isEmpty()) {
		return this.capital;
	}
	
	for(HealthInsurance ins: this.insurance){
		double budget = ins.getBudget(this.capital); 
		if(budget>maxBudget){
			maxBudget=budget;
		}
	}

	return maxBudget; 
}



/**
 * This method is used after a medical consultation and performs the payment. It specifically does the following steps: 
 * <ol>
 * <li>Searches the health insurance with will make the largest reimbursement</li>
 * <li>Requests and receives the money from the insurance company and sends it to the Provider</li>
 * <li>Adjusts the out-of-pocket variable {@link #OOPExp}
 * </ol>
 * @param consultationResult The outcome of a medical consultation which is the return value of {@link Provider#appointment(Patient, double)}
 * @author Florian
 * @version 15-Nov-2018 (unit testing passed)
 * 
 */
private void payConsultation(MedicalConsultation consultationResult) {

	//[1] FIND THE BEST INSURANCE COMPANY
	double maxReimbursement=-1.0;
	HealthInsurance bestInsurance=null;
	for(HealthInsurance ins: this.insurance){
		double reimbursement = ins.computeReimbursement(consultationResult.priceToPatient); 
		if(reimbursement>maxReimbursement){
			maxReimbursement=reimbursement;
			bestInsurance = ins;
		}
	}
	
	//System.out.printf("best-insurance: %s\n",bestInsurance);
	// If there is a best insurance company, request the reimbursement and make the payment. 
	if(bestInsurance!=null){
		// Actually execute the payment (this will transfer the funds to the patient
		
		// STORE COST IN DATABASE
		//this.saveHCELog((int)RunEnvironment.getInstance().getCurrentSchedule().getTickCount(),0,consultationResult.priceToPatient);	
		this.ytd_expenditures+=consultationResult.priceToPatient;
		double refund = bestInsurance.getReimbursement(consultationResult.priceToPatient, true,"Consultation");
		
		
		// Make the payment to the provider
		this.capital						-=(consultationResult.priceToPatient);	// Make payment (deduce from patient.capital)
		this.OOPExp 						+=(consultationResult.priceToPatient-refund);   // Register the out-of-pocket expenditures
		//System.out.printf("After reimbursement I have capital =%s\n",this.capital);
	}
	else{ // make the payment completely out-of-pocket
		this.capital						-=consultationResult.priceToPatient;			// Make payment (deduce from patient.capital)
		this.OOPExp 						+=consultationResult.priceToPatient;   			// Register the out-of-pocket expenditures
		
	}
	
	
}

/**
 * This method is used to pay medical treatment (!appointments). It specifically does the following steps: 
 * <ol>
 * <li>Searches the health insurance with will make the largest reimbursement</li>
 * <li>Requests and receives the money from the insurance company</li>
 * <li>Adjusts the out-of-pocket variable {@link #OOPExp}
 * </ol>
 * @param cost The cost (to the patient) of the treatment
 * @param TreatmentType type of treatment (required to avoid requesting reimbursement for self-treatment)
 * @param description name of the treatment (only used for analytical purposes)
 * @author Florian
 * @version 5-Jun-2019 (unit testing pending)
 * 
 */
public void payTreatment(double cost,TreatmentType type, String description) {
	//[1] FIND THE BEST INSURANCE COMPANY
		//System.out.printf("I am paying my treatment '%s' now (my capital before: %s):\n ",description,this.capital);
		double maxReimbursement=-1.0;
		HealthInsurance bestInsurance=null;
		if(type!=TreatmentType.SELF) {
			for(HealthInsurance ins: this.insurance){
				double reimbursement = ins.computeReimbursement(cost); 
				if(reimbursement>maxReimbursement){
					maxReimbursement=reimbursement;
					bestInsurance = ins;
				}
			}
		}
		
		//System.out.printf("best-insurance: %s\n",bestInsurance);
		// If there is a best insurance company, request the reimbursement and make the payment. 
		if(bestInsurance!=null){
			// Actually execute the payment (this will transfer the funds to the patient
			
			// STORE COST IN DATABASE
			//this.saveHCELog((int)RunEnvironment.getInstance().getCurrentSchedule().getTickCount(),0,cost);	
			this.ytd_expenditures+=cost;

			double refund = bestInsurance.getReimbursement(cost, true,"Treatment: "+description);
			
			
			// Make the payment to the provider
			this.capital						-=(cost);			// Make payment (deduce from patient.capital) refund not considered, already in getReimbursement
			this.OOPExp 						+=(cost-refund);   // Register the out-of-pocket expenditures
			//System.out.printf("Cost:%s, Refund=%s,Capital after =%s\n",cost,refund,this.capital);
		}
		else{ // make the payment completely out-of-pocket
			this.capital						-=cost;			// Make payment (deduce from patient.capital)
			this.OOPExp 						+=cost;   			// Register the out-of-pocket expenditures
			this.selfMedCosts					+=cost;
			
		}
		
		//System.out.printf("---> I finished the payment, now my capital is %s\n",this.capital);
		
}




 












/***
 * This method computed the perceived medical needs of the individual according to the equation in the paper (eq:medicalNeeds)
 * @version 07-Nov-2018 (unit testing performed and passed)
 * @author Florian Chavez
 * @return returns the result of the equation as <b>double</b> 

 */
public double getPerceivedMedicalNeeds() {
	
	double firstElement=0.0;	// First part of the equation
	double secondElement=0.0;	// Second part of the equation
	
	
	
	// Loop over all medical conditions
	for(MedicalCondition mc: this.medConditions){
		//mc.describe();
		if(mc.getDuration()>0){ // check if visible
			
			// Compute the first element
			if(mc.getCurrentSeverity()>=mc.getSeverity(1) && !mc.illness.treatments.contains(mc.treatment)){ // only add something if severity was not reduced
				double dm = mc.getDuration();
				firstElement += mc.getCurrentSeverity() * (dm/(1+dm));
				
			}
			
			//Compute second element 
			secondElement += mc.getCurrentSeverity();
					
		} // end check if visible
		
	} // end loop over all severities
	
	return firstElement * secondElement;

}


/**
 * This method computes the willingness-to-pay of the individual based on his/her {@link #perceivedMedicalNeeds}. The value is returned and not directly stored in the instance variable.
 * @version 07-Nov-2018 (unit testing performed and passed)
 * @author Florian Chavez
 * @return willingness-to-pay as <b>double</b>
 */
public double computeWillingnessToPay(){
	
	//System.out.printf("wtpParams: %s\n",Arrays.toString(Model.wtpParams));
	double wtp = Model.wtpParams[2] 	// gamma
			* Math.pow(Math.max(0,this.perceivedMedicalNeeds-this.tolerance),Model.wtpParams[0]) // n^alpha
			* Math.pow(income,Model.wtpParams[1]); 	// y^beta
	return wtp;
	
}

// EXPORT VALUES OF PATIENT
/*@ScheduledMethod(start=1,interval=1,priority=10,shuffle=false)	
public void export(){
	double [] conditions = new double[this.medConditions.size()];
	if (conditions.length>0){
		for(int j=0; j<this.medConditions.size(); j++){
			conditions[j]=this.medConditions.get(j).illness.ID;
		}
	}
		
	
	int tick = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
	int runnum=RunState.getInstance().getRunInfo().getRunNumber();
	
	String batchname = "Single";
	if(RunEnvironment.getInstance().isBatch()){
		batchname=(String) RunEnvironment.getInstance().getParameters().getValue("batchName");
	}
	
	try{
		// INDIVIDUAL DATA EXPORT
			String path="C:/healthABM/data/"+batchname+"_patient"+runnum+".txt";
			FileOutputStream myfile_pat;
			
			boolean append= true;
			
			if(RunEnvironment.getInstance().getCurrentSchedule().getTickCount()==1){
				append = false;
			}
			
			myfile_pat = new FileOutputStream(path,append);
			
			Formatter output_pat = new Formatter(myfile_pat);	
			
			// Header will be created by the initialiser
			
			if(append==false){
				output_pat.format("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n","run","tick","ID","age","female","health_status","tolerance","capital","income",
						"OOP_expenditures","number_insurances","medical_conditions");
			}
			
			    output_pat.format("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n",runnum,tick,this.ID,this.age,this.female,this.getHealthStatus(),this.tolerance,this.capital,
			    		this.income,this.OOPExp,this.getNumberInsurance(),Arrays.toString(conditions));
			
			output_pat.close();
			
			
		}	
	catch (FileNotFoundException e) {
			System.err.printf("FileNotFound Exception\n");
			System.exit(1);
		}
	}
	*/

// ###################################################################################################################
// ###################################################################################################################
// OTHER METHODS
	/**
	 * Adds the {@link HealthInsurance} in the argument to the list of insurances of the patient. 
	 * This method is called by the constructor of insurances {@link HealthInsurance#HealthInsurance(InsuranceCompany, Patient, double, double, double, double)}. 
	 * <br><b>This method does not automatically cancel any previous insurance (if available)</b>
	 * @param healthInsurance HealthInsurance to be added
	 */
	public void setInsurance(HealthInsurance healthInsurance) {
		this.insurance.add(healthInsurance);	
	}
	
	/** Add reimbursement to the Patient's capital
	 * @param reimbursement as double*/
	public void getReimbursed(double reimbursement) {
		this.capital +=reimbursement;	
		
	}
	
	/** Returns the Patient's current capital
	 * @return capital as double*/
	public double getCapital(){
		return Double.parseDouble(String.format("%.02f", this.capital)) ;
	}
	
	/**
	 * Method to change the capital of the patient
	 * @param capital Capital to be set to patient
	 */
	public void setCapital(double capital){
		this.capital = capital;
	}
	
	/**
	 * Reduces the capital of the patient by the indicated amount. 
	 * @param c
	 */
	public void reduceCapitalBy(double c) {
		this.capital -=c;
	}
	
	
		
	
	/**Remove patient from the context. For now, death probability is a function of current health status.*/
	public void die(){
		Context<Object> context = ContextUtils.getContext(this);		
		context.remove(this);
		if(this.medConditions!= null){
			for(MedicalCondition condition: this.medConditions){
				context.remove(condition);
			}
		}
		if(this.insurance!= null){
			for(HealthInsurance ins: this.insurance){
				context.remove(ins);
			}	
		}
		this.medConditions.clear();
		
		this.familyDoctor=null;
		
	
		// Replace this agent by a new one with the same characteristics
		Patient newPatient = new Patient(this.income*52/12,18*52,this.female,1.0,this.tolerance,this.location);
		Model.context.add(newPatient);
		newPatient.stepContractInsurance();
		
	}
	
	
	/** Returns the cost of transportation from the Patient location to the (specified) Provider location
	 * @param prov a Provider, whose location is to be used in the calculation of the Patient's cost of transportation 
	 * @return the amount of money that a visit to this provider would cost
	 * @author USim, Florian
	 * @version 07-Jan-19 (//TODO FUTURE 05: Unit test pending)
	 * */
	public double getTransportCost(Provider prov){
		double cost = -1;
		double distance;
		if(prov!=null){
			NdPoint provLoc = prov.space.getLocation(prov);
			NdPoint loc 	= this.location.getLocation(this);
			distance		= location.getDistance(provLoc,loc);
			cost			= distance * RunEnvironment.getInstance().getParameters().getDouble("transportCost");
			}
		return cost;
		}

	/** Patient selects the HIPlan with the least cost (only when Patient satisfies plan requirements 
	 * and has enough money to pay for it). If no HI plan is cheaper than their expected expenditures,
	 * the patient does not contract insurance.
	 * @param hipsIDs List of HIPlans IDs associated to each of the HealthInsurances the Patient had last year (if this is the first time the Patient contracts an insurance, this list will be empty)
	 * @return HIPlan with the least cost
	 * TODO FUTURE 04: This method does not include non-monetary costs. Unit tested for a limited number of manual cases in LucyTester
	 * @author Lucy Hackett
	 * @version 20-02-2019
	 * */
	protected HIPlan selectInsurance(ArrayList<Integer> hipIDs){
		HIPlan selectedPlan=null;
		
		Context<Object> context = ContextUtils.getContext(this);
		
		//Create AL and fill it with all the HealthInsurancePlans in the Context that are elegible to the patient
		ArrayList<HIPlan> eligiblePlans= new ArrayList<HIPlan> ();
		//System.out.printf("I am female (%s), %s yrs. looking for insurance\n", this.female, this.age/52.0);
		for(Object o:context.getObjects(HIPlan.class)){
			HIPlan cPlan = (HIPlan)o;
			// only add plans that exist and that include the patients age and gender in their offer
			//System.out.printf("I am a plan for: age [%s, %s], women: %s men: %s\n", cPlan.getMinAge(), cPlan.getMaxAge(), cPlan.womenAllowed, cPlan.menAllowed);
			
			if(cPlan.insurer!=null && (double)cPlan.getMinAge()<=this.getAgeYears() && (double)cPlan.getMaxAge()>=this.getAgeYears() && ((this.female == true & cPlan.womenAllowed == true) | (this.female == false & cPlan.menAllowed == true))) {
				eligiblePlans.add(cPlan);
				//System.out.printf("My eligible plan has [prime, ded., copay]: [%s, %s, %s]\n", cPlan.prime, cPlan.deductible, cPlan.copaymentRate);
			}
		}
		
		// cost variable for comparing plans with no plan
		// don't compare with no insurance because its not an option
		
		//double lowestCost = this.getPersonalExpenCalcTotal();
		this.subjectiveExpectedExpen = getPersonalExpenCalcTotal();
		
		/** This is comparing to the no-insurance case*/
		double bestCost = 974377654;
		/** This will compute the lowest price, irrespective of being desirable*/
		double lowestCost;
		
		
		if(Model.mandatoryInsurance) {
			lowestCost =Double.POSITIVE_INFINITY;	// We want to find the cheapest, irrespective of reserve price
			bestCost 	= Double.POSITIVE_INFINITY; // Set reserve price to infinity, to force finding an insurance
		}else {
			lowestCost = Double.POSITIVE_INFINITY;		// idem above
			bestCost   = this.subjectiveExpectedExpen;	// we only select insurances with better outcomes than no-insurance
		}
		
		
		HIPlan cheapestPlan = null;
		
		if(eligiblePlans.size()>0){
			for(HIPlan plan: eligiblePlans){
				
				double planCost = this.subjectiveExpectedExpen - plan.computeReimbursement(this.subjectiveExpectedExpen) + plan.premium;
				
				//if evaluated plan is not contained in Patients AL of HIPlans for last year, multiply cost by a transaction cost factor
				if(!hipIDs.contains(plan.ID)) {
					planCost = planCost*this.changePlansCost;
				}
				
				// if the prime is less than their annual income and the cost is lower than the previous optimum, choose
				if(plan.premium<= this.income*52 && planCost < bestCost){
					selectedPlan=plan;
					bestCost = planCost;
				}
				
				// Just find the cheapest (independent of willingness to contract)
				if(planCost < lowestCost) {
					lowestCost = planCost;
					cheapestPlan = plan;
				}
			}
			if(selectedPlan != null){
				//System.out.printf("I chose plan [ID: %s, ded: %s, prime: %s] with cost %s. I had expected expen. %s, Income =%s\n\n",selectedPlan.ID,selectedPlan.deductible,selectedPlan.premium ,bestCost, this.getPersonalExpenCalcTotal(),this.income);			
			}
			else{
				if(Model.mandatoryInsurance) {
					//System.out.printf("I did not want to have an insurance, but I am forced to, so I buy the cheapest one\n");
					selectedPlan = cheapestPlan;
					//System.out.printf("I chose plan [ID: %s, ded: %s, prime: %s] with cost %s. I had expected expen. %s\n\n",selectedPlan.ID,selectedPlan.deductible,selectedPlan.prime ,lowestCost, this.getPersonalExpenCalcTotal());			

					
				}
				else {
					//System.out.printf("I chose NO plan [ID: %s, ded: %s, prime: %s] with cost %s. I had expected expen. %s, Income =%s\n\n",selectedPlan.ID,selectedPlan.deductible,selectedPlan.premium ,bestCost, this.getPersonalExpenCalcTotal(),this.income);			
					//System.out.printf("I did not buy insurance:I had expected expen. %s, Income =%s\n\n" , this.subjectiveExpectedExpen,this.income);
				}
				// System.out.printf("I chose not to contract insurance (expected expen: %s, capital: %s)\n\n",this.subjectiveExpectedExpen,this.capital);						
			}
			
			
			
		}
		else{
			System.err.println("There are no available plans for the following person:");
			System.out.printf("ID: %s, Age: %s, Female:%s\n",this.ID,this.getAgeYears(),this.female);
			
			System.exit(2);
		}	
		
		return selectedPlan;
	}
	
	// overloaded for testing 
//	protected HIPlan selectInsurance(ArrayList<HIPlan> plans){
//		HIPlan selectedPlan=null;
//		
//		//Context<Object> context = ContextUtils.getContext(this);
//		
//		//Create AL and fill it with all the HealthInsurancePlans in the Context that are elegible to the patient
//		ArrayList<HIPlan> elegiblePlans= new ArrayList<HIPlan> ();
//		for(Object o:plans){
//			HIPlan cPlan = (HIPlan)o;
//			// only add plans that exist and that include the patients age and gender in their offer
//			if(cPlan.insurer!=null && cPlan.getMinAge()<=this.age && cPlan.getMaxAge()>=this.age && (this.female == cPlan.womenAllowed | (this.female == false & cPlan.menAllowed == true))) {
//				elegiblePlans.add(cPlan);				
//			}
//		}
//		//System.out.printf("Eligible plans: %s\n",elegiblePlans);
//		// cost variable for comparing plans with no plan
//		double lowestCost = this.getPersonalExpenCalcTotal();
//		
//		if(elegiblePlans!=null){
//			for(HIPlan plan: elegiblePlans){
//					double planCost = this.getPersonalExpenCalcTotal() - plan.computeReimbursement(this.getPersonalExpenCalcTotal()) + plan.prime;
//					System.out.printf("Estimated cost: %s\n", planCost);
//					// if the prime is less than their annual income and the cost is lower than the previous optimum, choose
//					if(plan.prime<= this.income && planCost < lowestCost){
//						selectedPlan=plan;
//						lowestCost = planCost;
//						System.out.printf("selectedPlan & cost: %s %s\n", selectedPlan.ID, lowestCost);
//				}
//			}
//		}else{
//			System.err.println("There are no available plans");
//		}	
//		
//		return selectedPlan;
//	}

		
	
	
	/** In this method Patient evaluates whether she needs to visit a general practitioner, depending on her medical self 
	 * evaluation and the expected cost of the visit (including Provider cost and transportation). 
	 * and which Provider she will visit (notice that this Provider is not necessarily the family doctor)
	 * @version 07-Nov-2018 (unit testing pending)
	 * @author LNPP, Florian Chavez
	 * @return in case of finding a provider it returns the provider, otherwise <b>null</b>
	 * */
	private Provider decideVisitProvider(){
		
		Provider selection = null;
		
		// Define real-WTP as the minimum of willingness-to-pay and the financial capability (e.g. income)
		double realWTP = (this.wtp<=this.capital) ? this.wtp : this.capital;  
		
		
		
		// Check if family doctor
		if(this.familyDoctor!=null && this.getTrust(familyDoctor)>0){
			// Now check if patient can afford and is willing to pay this
			double expectedCost = this.getVisitCost(familyDoctor, this.bestInsurance(familyDoctor));
			if(expectedCost <= realWTP && realWTP>0.001){
				selection = this.familyDoctor; // Select the family doctor
				//System.out.printf("He/she chose their family doctor: Provider %s with exp. cost %s", selection.ID,expectedCost);
			}
		}
		
		
		// If patient did not decide to visit the family doctor, check if there is another doctor
		double referenceTrust = (this.familyDoctor!=null) ? this.getTrust(familyDoctor) : Double.NEGATIVE_INFINITY;
		
		if(selection==null){
			Provider bestCandidate = this.getNetworkAdvise(realWTP, referenceTrust);
			double expectedCost = this.getVisitCost(bestCandidate, this.bestInsurance(bestCandidate));
			if(bestCandidate == null) {
				//System.out.printf("The expected cost of the best candidate (Provider %s) is: %s\n",bestCandidate,expectedCost);				
			}else {
				//System.out.printf("The expected cost of the best candidate (Provider %s) is: %s\n",bestCandidate.ID,expectedCost);

			}
			if(expectedCost <= realWTP && realWTP>0.001){
				
				selection = bestCandidate; // Select the family doctor
				//System.out.println("A provider was chosen: "+selection.ID);
			}
			else {
				//System.out.println("No provider was found");
			}
			
		} // end if selection==null
		
		
		return selection;
	
		
	}
	

	/** Return the expected cost of a medical visit. The amount of money include non monetary costs, transportation and consult. 
	 * Patients consider consult cost with their current HI status (potential reimbursement). 
	 * @param prov Potential provider
	 * @param ins health insurance 
	 * @FIXME visitCost: (Lucy) Change reaction to provider ==null? 
	 * @TODO vistiCost: dould this be simplified with calculateReimbursement() ? 
	 * @return Amount of money: cost of a visit with the specified provider (considering coverage, deducible target and available
	 * stop-loss/claim)*/
	private double getVisitCost(Provider prov, HealthInsurance ins){			
		double expectedCost = 0.0;
		
		if(prov==null){
			//System.out.println("I need a provider to compute visit cost");
			expectedCost = 987654321.00;;  //Hack 
		}else{
			
			double visitCosts=prov.NMCost*income+ this.getTransportCost(prov);
			
			// Case 0: HI does not affect expectedCost	
			if (ins==null || (ins.assocHIPlan.allowedProviders.size()>0 && !ins.assocHIPlan.allowedProviders.contains(prov)) ||  (ins!=null && !insurance.isEmpty() && !insurance.contains(ins))){
				
				expectedCost= prov.priceMedicalConsultation+ visitCosts;
			
			}else{
				int limit=-1;
				if(ins.stopLoss!=0 && ins.stopClaim==0){
					limit= ins.stopLoss;
				}
				if(ins.stopLoss==0 && ins.stopClaim!=0){
					limit= ins.stopClaim;
				}
					// Case 1: Claims under deductible
					if(ins.claimsYTD<ins.deductible){
						if(ins.claimsYTD+prov.priceMedicalConsultation<ins.deductible){
							expectedCost = prov.priceMedicalConsultation+ visitCosts;
						}else{
							// Case 1A: Claims over deductible but under stop-loss/ stop-claim 	

								if(prov.priceMedicalConsultation+ins.claimsYTD<limit){
									
									double afterDed= ins.claimsYTD+prov.priceMedicalConsultation-ins.deductible;
									expectedCost = prov.priceMedicalConsultation-afterDed+ afterDed*(1-ins.copaymentRate)+ visitCosts;
									
								}else{
									//Case 1B: Claims reach deductible and stop-loss/ stop claim
									double copayment= limit-ins.deductible;
									expectedCost=ins.deductible-ins.claimsYTD+ copayment*(1-ins.copaymentRate)+visitCosts;
									if(ins.stopLoss==0 && ins.stopClaim!=0){
									expectedCost+=prov.priceMedicalConsultation+ins.claimsYTD-ins.stopClaim;
									}
								
								}
								
							}
					}else{ 			//Case 2: claimsYTD over deductible
						
						if(prov.priceMedicalConsultation+ins.claimsYTD<limit){ // Case 2A. under stoploss/stopclaim
							expectedCost= prov.priceMedicalConsultation*(1-ins.copaymentRate)+visitCosts;
						}else{ // CASE 2B: over stoploss/stopclaim
							expectedCost=(limit-ins.claimsYTD)*ins.copaymentRate+visitCosts;
							if(ins.stopLoss==0 && ins.stopClaim!=0){
								expectedCost+= prov.priceMedicalConsultation-ins.stopClaim-ins.claimsYTD;
						}
						}
					}
 	 
	
			}
		
			
		}// End of else (if provider parameter != null)
		
			
		
		return expectedCost;
	}
	

	
	/** Loops over all Providers visible to patient {@link #getListOfProviders()} and returns the provider 
	 * with best average trust affordable to patient and with a minimum level of average trust specified by 
	 * minTrust (current trust on family doctor). When all advises are worse than minTrust, the method 
	 * returns the family doctor.   
	 * @param willingnessToPay Willingness to pay of patient (given current visible needs, tolerance and income of patient)  
	 * @param minTrust Target of trust that evaluated providers should reach to be selected
	 * @author USim, Florian Chavez
	 * @version 10-Jan-2019 (verified, but unit testing not possible outside RePast)
	 * @return Provider with best average trust among all providers  */
	protected Provider getNetworkAdvise(double willingnessToPay, double minTrust) {
		// Get all possible candidates (various sources)
		LinkedHashSet<Provider> allProviders = getListOfProviders();
		
		Provider 	bestOption  = null;
		double 		bestTrust   = Double.NEGATIVE_INFINITY;
		double 		minCost		= Double.POSITIVE_INFINITY; 
		
		// Search the provider with the highest level of trust and price<WTP
		for(Provider p:allProviders) {
			Double cost = this.getVisitCost(p, this.bestInsurance(p));
			if(cost<= willingnessToPay && p.capacity>p.appointments) { // only sufficiently cheap providers WITH availability are considered
				
				// GET TRUST INFORMATION
				double trust = this.getTrust(p);  
				
				if(trust>bestTrust) { // IF higher than previously best => replace
					bestTrust 	= trust;
					bestOption = p;
					minCost 	= cost;
				}
				else if(trust==bestTrust && cost<minCost) { // in case of a tie regarding trust, the cheaper option is preferred
					bestOption 	= p;
					minCost 	= cost;
				}
			} // end if cost<wtp
		}
		
		if(minTrust>bestTrust && this.getVisitCost(familyDoctor, this.bestInsurance(familyDoctor))<willingnessToPay){
			bestOption= familyDoctor;
		}
		
		/* Updating expectedOOPExp*/
		if(bestOption!=null) {
			expectedOOPExp= this.getVisitCost(bestOption, this.bestInsurance(bestOption));
		}
		
		return bestOption;
	}
	
	
	/** Finds all Providers visible to patient through three sources: 
	 * <ul>
	 * <li>geographical distance: patients can 'see' providers located near their location</li>
	 * <li>insurance network availability [NOT YET IMPLEMENTED]</li><ul>. 
	 * If the social network is deactivated, add all providers  
	 * @author USim, Florian Chavez 
	 * @version 10-Jan-2019 (verified)
	 * @return List containing all visible providers to patient
	 * */
	private LinkedHashSet<Provider> getListOfProviders() { 
		
		// Define the HashSet to be returned at the end
		LinkedHashSet<Provider> allProviders = new LinkedHashSet<Provider>();

		/* [2] Geographical neighbourhood */ 
		allProviders.addAll(this.nearProviders());
		
		/* [3] Add provider suggestions from the insurance company */
		//FIXME PP Appl. 01: To be implemented
		Context<Object> context = ContextUtils.getContext(this);
			for(Object p: context.getObjects(Provider.class)) {
				allProviders.add((Provider)p);
			}
		
		
		return allProviders;
	}

		
	/**
	 * Returns the level of income of the individual. This is essentially used for the GUI. 
	 * @return income as double
	 */
	public double getIncome() {
		return 	 Double.parseDouble(String.format("%.02f", this.income)) ;

	}
	
	/**
	 * This method computes the trust an individual should have in a given provider. If there is no information on the provider, 
	 * a value of Double.NEGATIVE_INFINITY
	 * 
	 * @param provider The {@link Provider} for which the level of trust should be computed
	 * @param includeSocialNetwork boolean Takes the value of TRUE if the advise from the social network should be considered or not. If FALSE
	 * only the own experience is considered. If TRUE, the own experience is considered if there has been a previous experience and the average trust
	 * of the social network if the patient herself did not yet have an experience. This parameter is the global parameter of whether or not the social
	 * network is utilized in the model.
	 * @author Florian Chavez
	 * @version 10-Jan-2019 (unit test passed) 
	 * @return double level of trust (higher values mean more trust) 
	 */
	protected double getTrust(Provider provider) {
		double trust=Double.NEGATIVE_INFINITY;
		
		if(this.experience.containsKey(provider)) { // The estimation of trust is based on the own experience only. 
			//Parameters params = RunEnvironment.getInstance().getParameters();
			double eta = params.getDouble("eta_trustReduction"); 
			
			int nSuccess 	= this.experience.get(provider)[0];
			int nTotal 		= this.experience.get(provider)[1];
			trust = (double)(nSuccess - eta * (nTotal-nSuccess)) / Math.sqrt(nTotal);
				
			
		}

		
		return trust;
	}
	
	/** 
	 * Pays the premium for the health insurance (converted into weekly payments) by adding it to the capital of the insurance company, registering the payment 
	 * at the insurance company ({@link InsuranceCompany#primes}) and deducing it from the patient's capital. It also adds 
	 * the paid prime to the InsuranceCompany's primeLog
	 * @param plan
	 * @author alejandrob
	 * @version 01-Apr-2019
	 */
	private void payHealthInsurance(HealthInsurance plan){ 
		this.capital -=plan.premium/52;
	}
	
	
	private boolean isPoor() {
		return this.getIncome()<Patient.incomeSesThreshold ? false : true;	
	}
	
	/** 
	 * Calculates the sum of changes in severity from tick-1 to tick over all medical conditions, regardless of the visibility of symptoms.  
	 * @return deltaMedicalCondition
	 * @author Florian
	 * @version 18-Nov-2018
	 */
	public double getSeverityEvolution() {
		double severityEv = 0;
		for(MedicalCondition i : this.medConditions){
			severityEv+=i.getSeverity(0) - i.getSeverity(1);  //computes the change in severity from last tick to this tick
		}
		return severityEv;
	}

	/** 
	 * Determines whether the individual dies or not
	 * @return
	 */
	private boolean checkIfDies(){
		if(this.getHealthStatus()<=0){
			return true;
		}
		else{
			return false;
		}
	}
	
	
	/**
	 * Data export method
	 * @return Returns the numberof insurances
	 */
	public int getNumberInsurance(){
		int i=0;
		if(this.insurance!=null){
			i=this.insurance.size();
		}
		return i;
	}
	
	public int getID() {
		return this.ID;
	}
	
	/* 
	 * @return Returns age in weeks
	 */
	public int getAge() {
		return this.age; //(int) Math.floor(this.age/52);
	}
	
	/*
	 * @return age in years
	 */
	public int getAgeYears() {
		return (int) Math.floor(this.age/52); //(int) Math.floor(this.age/52);
	}
	
	

	
	/**
	 * This method computes the current health status by deducing from unity all the current severities of all medical conditions. 
	 * @return health status on a scale from 1.0 (perfect health) up to negative values, where the value of 0.0 is the threshold of death. 
	 */
	public double getHealthStatus() {
		double healthStatus = 1;
		for(MedicalCondition i : this.medConditions){
			healthStatus -=i.getCurrentSeverity();
		}
		return healthStatus;
	}
	
	public int getFemale() {
		if(this.female) {
			return 1;
		}
		else {
			return 0;
		}
	}
	
	public double getTolerance() {
		return this.tolerance;
	}
	
	public double getOOPExpenditure() {
		return this.OOPExp;
	}
	
	public int getNumberMedicalConditions() {
		return this.medConditions.size();	
	}
	
	/**
	  * Data export method
	  * In case patient receive medical care on current period it returns IDs of related provider, otherwise returns an array with -1
	  * @return an Array with IDs of provider which patient decide to visit on current period
	  */
	public String getVisits() {

		
		ArrayList<Integer> visitsID = new ArrayList<Integer>();
		
		if(this.visits!=null){
			for(Provider prov:visits){
				visitsID.add(prov.ID);
				if(prov.priceMedicalConsultation > Model.wtpParams[2]*Math.pow((this.getVisNeeds()-tolerance),Model.wtpParams[0])*Math.pow(income,Model.wtpParams[1])){
//					System.out.printf("Patient %s decided to visit Provider %s with price %s and wtp %s\n", this.ID, prov.ID, prov.price, Model.wtpParams[2]*Math.pow((this.getVisNeeds()-tolerance),Model.wtpParams[0])*Math.pow(income,Model.wtpParams[1]));
				}
				if(prov.priceMedicalConsultation > this.income){
//					System.out.printf("Patient %s decided to visit Provider %s with price %s and income %s\n", this.ID, prov.ID, prov.price, this.income);
				}
				if(RunEnvironment.getInstance().getCurrentSchedule().getTickCount()==14 && this.ID==11){
					//System.out.printf("Patient %s decided to visit Provider %s with price %s (params %s) and income %s on tick %s\n", this.ID, prov.ID, prov.price, costProvider, this.income,RunEnvironment.getInstance().getCurrentSchedule().getTickCount());
				}if(prov.priceMedicalConsultation!=prov.priceMedicalConsultation){
					//System.out.printf("(In Patient.getVisits) Provider %s has different prices %s (params %s) on tick %s\n", prov.ID, prov.price, costProvider,RunEnvironment.getInstance().getCurrentSchedule().getTickCount());
				}
			}
		}
		else{
			visitsID.add(-1);
		}
		return visitsID.toString();
	}
	

	
	public double getVisNeeds(){
		return this.perceivedMedicalNeeds;
	}
	/**
	 * This method should only be used to export data (repast) and not during the simulation itself. 
	 * @return Formatted version of visible needs (only 4 digits)
	 */
	public double getVisNeedsExport(){
		return Double.parseDouble(String.format("%.04f",this.perceivedMedicalNeeds));
	}
	
	public double getExpOOPExp(){
		return expectedOOPExp;
	}
	
	public double getwtp(){
		return this.wtp;
	}
	/**
	 * This method should only be used to export data (repast) and not during the simulation itself. 
	 * @return Formatted version of visible needs (only 4 digits)
	 */
	public double getwtpExport(){
		return Double.parseDouble(String.format("%.04f",this.wtp));
	}
	

	
	

	
	
	/**In case provider is covered for more than one insurances of patient, this method returns the best option for visit specified provider*/
	private HealthInsurance bestInsurance(Provider prov){
		HealthInsurance selectedInsurance=null;
		double cost=Double.POSITIVE_INFINITY;
		if(this.insurance!=null){
			for(HealthInsurance ins: this.insurance){
				if(this.getVisitCost(prov, ins)<cost){
					selectedInsurance=ins;
					cost= this.getVisitCost(prov, ins);
				}
			}

		}
		return selectedInsurance;		
	}
	
	/**
	 * This method searches all providers within a certain radius (defined by the model parameter <b>visibilityAgents</b>
	 * @author Usim, Florian Chavez
	 * @version 10-Jan-2019 (verified)
	 * @return HashSet of type Provider with all the eligible provider
	 */
	private LinkedHashSet<Provider> nearProviders(){ 
		// Define a hash set into which we put all the eligible providers
		LinkedHashSet<Provider> allProviders = new LinkedHashSet<Provider>();
		
		//Get the location of this agent
		NdPoint loc = this.location.getLocation(this);
		Context<Object> context = ContextUtils.getContext(this);
		
		// Loop over all providers in the context and check if they are within the visibility range
		int visibility = RunEnvironment.getInstance().getParameters().getInteger("visibilityAgents");
		for(Object o:context.getObjects(Provider.class)){
			Provider provider = (Provider)o;
			NdPoint provLoc = provider.space.getLocation(provider);
			double distance= location.getDistance(provLoc,loc);
			if(distance<visibility){
				allProviders.add(provider);
			}
		}
		
		
		return allProviders;
	}
	 
	
	
	
	
	
	
	


	/**
	 * This method updates the experience the patient has with a given provider. It directly manipulates the counters for the
	 * total number of possible correct diagnoses and the number of truly correct diagnoses.
	 * @param provider The provider to be evaluated
	 * @param good If <b>true</b> then a positive experience is added, otherwise a negative one. 
	 * @author Florian Ch�vez
	 * @version 10-Jan-2019 (verified)
	 */
	public void addPositiveExperience(Provider provider,boolean good) {
		if(this.experience.containsKey(provider)) {
			//System.out.printf("Existing experience: [%s, %s]\n", this.experience.get(provider)[0],this.experience.get(provider)[1]);
			if(good) {
				this.experience.get(provider)[0]++; // add a counter to the good experiences
			}
			this.experience.get(provider)[1]++;		// add a counter to all experiences
			//System.out.printf("Updated experience: [%s, %s]\n", this.experience.get(provider)[0],this.experience.get(provider)[1]);
			}
		else {
			//System.out.printf("NO previous experience\n");
			int[] init = {1,1};
			if(!good) {
				init[0] = 0;
			}
			this.experience.put(provider,init);
			//System.out.printf("Updated experience: [%s, %s]\n", this.experience.get(provider)[0],this.experience.get(provider)[1]);

		}
		
	}
	
	/**
	 * Converts the boolean female to an integer taking the values: 
	 * <ul><li>0 Male</li><li>1 Female</li></ul>
	 * @return dummy variables for women
	 */
	protected int genderToInteger() {
		if(this.female) {
			return 1;
		}else {
			return 0;
		}
	}
	/**
	 * This method calculates the moving average of the patients most recent (in memory)
	 * Expenses
	 * @return Moving average of expenditures
	 * @version 19-02-2019
	 * @author Lucy Hackett, Last change by Florian Chavez
	 * Notes: Successfully unit tested for the case of memory greater than expen log length (children), memory=expen log length, 
	 * and memory less than expen log length (shouldn't happen in the model)
	 */
	public double movingAverageTotal() { //FIXME: 0: check to drop this
		double movingAvg = 0.0;
		double numerator = 0.0;
		int memory = Model.memory/52; // here we need it in years
		
		
		// get the newst key
		int newestKey = -999;
		for(int key:this.annualExpentitures.keySet()) {
			if(key>newestKey) {
				newestKey=key;
			}
		}
		//System.out.printf("Size of annualexpenditures=%s, newestKey=%s\n",this.annualExpentitures.size(),newestKey);
		int periods = Math.min(memory, this.annualExpentitures.size());
		
		double weight = periods;
		for(int year=newestKey;year>newestKey-periods;year--) {
			numerator += weight*this.annualExpentitures.get(year);
			//System.out.printf("Year=%s,Weight%s=>Cost%s\n", year,weight,this.annualExpentitures.get(year));
			weight --;	
		}
		
		//System.out.printf("weight mAX: %s\n",weightMax);
		movingAvg = numerator/(0.5*periods*(periods+1));
		if(Double.isNaN(movingAvg)) {
			movingAvg=0;
		}
		//System.out.printf("====> THE MOVING AVERAGE IS %s\n\n",movingAvg);
		//System.exit(0);
		return movingAvg;
	}
	
	/**
	 * This method is another movingAverage whose inputs are suited for the input of an int, double 
	 * HashMap, such as the one that stores the number of events above the deductible per period
	 * and the HashMap that store the total expenditures for events below the deductible per period
	 * @param map HashMap of Integer, Double of events/expenditures
	 * @return linearly weighted moving average of events (double)
	 * Notes: Unit tested in LucyTester
	 */
	public double movingAverageAux(LinkedHashMap<Integer, Double> map) {
		double movingAvg = 0.0;
		double numerator = 0.0;
		int memory = Model.memory;
		
		// Find the largest period for calculations. 
		// int maxTick = 0;
		// for(Integer[] key: this.expenditureLog.keySet()) {
		//	if(key[0]> maxTick) {
		//		maxTick = key[0];
		//	}
		//}	
		//int maxTick = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount() -1
		// -1 for if they have not yet incurred costs in this period??
		
		double weightMax = memory/52;
		double weight = weightMax;
		
		for(Double expen : map.values()) {
			numerator += weight*expen;
			weight --;
		}	
		movingAvg = numerator/(0.5*weightMax*(weightMax+1));
		
		return movingAvg;
	}
	
	


	
	
	/**
	 * This method calculates the patient's expected health expenditures based on their moving average,
	 * the risk aversion, and the population's 95th percentile of expenditures.
	 * @return Expected subjective health expenditures (double)
	 * @version 18-02-2019
	 * @author Lucy Hackett
	 * Notes: Unit tested in tester, but not with global data. 
	 */
	public double getPersonalExpenCalcTotal() { 
				
		if(Model.getP95ExpenPop(this.genderToInteger(), this.age)!=0.0) {
			return this.riskAversion*Math.max(Model.getP95ExpenPop(this.genderToInteger(), (int)Math.floor(this.age)), this.movingAverageTotal()) + (1-this.riskAversion)*this.movingAverageTotal();	
		}else {
			//FIXME: (from Alejandro) --> the next warning is activated
			//System.err.println("Warning: 95th percentile not found!");
			return this.riskAversion*this.movingAverageTotal() + (1-this.riskAversion)*this.movingAverageTotal();	
		}
	}
	

	

	
	public double getSubjectiveExpenCalc() {
		return this.subjectiveExpectedExpen;
	}
	
	
	
	


	public int getNumAppts() {
		return this.numAppointments;
	}
	
	public int getNoTreated() {
		int treated = 0;
		for(MedicalCondition mc: this.medConditions) {
			if(mc.wasTreated == true) {
				treated++;
			}
		}
		return treated;
	}

	/**
	 * 
	 * @param visible Boolean; TRUE if you want only visible needs; false for all severities
	 * @return health status (1- sum of severities)
	 * @author Lucy Hackett
	 */
	protected double getHealthStatus(boolean visible) {
		double sev = 0.0;
		
	if(visible) {
		for(MedicalCondition mc: this.medConditions){
			if(mc.getDuration()>0){ // check if visible
				sev += mc.getCurrentSeverity();	
			} // end check if visible
		} // end loop over all severities
	}else {
		for(MedicalCondition mc: this.medConditions){
				sev += mc.getCurrentSeverity();	
		} // end loop over all severities		
	}
	
	return 1- sev;
	
	}
	
	public double getHealthStatPreTreat(){
		return this.healthStatusBeforeTreat;
	}
	
	public double getVisibleHealthStatPreTreat(){
		return this.visibleHealthStatusBeforeTreat;
	}
	
	public double getHSexport() {
		return Double.parseDouble(String.format("%.04f", this.getHealthStatus())) ; // String.format("%1.4f", this.HS);
		
	}
	
	public double getSelfMedCosts() {
		return this.selfMedCosts;
	}

	





	

	
	
}