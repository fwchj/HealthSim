package healthABM;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import repast.simphony.engine.environment.RunEnvironment;
/**
 * The class medical condition refers to actual medical conditions of a patient. Contrary to the class Illness (blueprint of each possible illness), this class is always 
 * linked to a patient. Once the medical condition is cured, the instance of this class disappears. 
 * @author Florian
 *
 */
public class MedicalCondition {

	// INSTANCE VARIABLES

	/** Identifier for Medical Conditions */
	protected int medConditionID;
	
	/** Identifier for Illnesses */
	protected Illness illness;
	
	/** Treatment currently applied for this Medical Condition and Provider who is applying treatment*/
	protected Treatment treatment;
	
	/** Starting tick of the disease*/
	protected int initialTick;
	
	/** Amount by which this medical condition is affecting the patient's health status. The newest value is always stored on index 0, hence the index
	 * can be interpreted as how many ticks in the past. This variable is defined as <b>private</b> to avoid that somewhere else in the code this order is not respected. */ 
	private ArrayList<Double> severity 	 = new ArrayList<Double>();
	
	/** Provider who diagnosed condition */
	protected Provider detector;
	
	/** Provider who applies treatment */
	protected Provider applier;
	
	/** Patient */
	protected Patient patient;
	
	/** Treated */
	protected boolean wasTreated; 
	
	
	
	
	// CONSTRUCTOR
	/**
	 * Main constructor for medical conditions
	 * @param ill link to the {@link Illness} 
	 * @param sev Initial severity
	 * @param patient Patient 
	 * @author LNPP, Florian
	 * @version 09-Nov-2018
	 */
	public MedicalCondition (Illness ill, double sev, Patient patient){
		this.illness = ill;
		if(ill==null) {
			System.out.print("Stopping because requesting medical condition without illness");System.exit(2);
		}
		this.severity.add(0,sev);
		//this.initialTick = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		this.medConditionID = Model.counterMedConditions++;	// add a unit to the counter
		
		this.patient = patient;
		Model.context.add(this); 			
				
		//Model.prevalence.put(ill, Model.prevalence.get(ill)+1); 
		Model.incidence.put(ill, Model.incidence.get(ill)+1);
		this.initialTick = (int)RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		this.wasTreated = false;
		
		//System.out.printf("\n\nI just created a new MC of type %s with initial severity:%s\n",this.illness.name,Arrays.toString(this.severity.toArray()));
		
	}
	
	// SCHEDULED METHODS
		
	// OTHER METHODS	
	public double getMedConditionID(){
		return this.medConditionID;
	}
	
	
	/**
	 * Returns the current level of severity (which is always stored at index = 0)
	 * @return current level of severity as double
	 * @author Florian
	 * @version 09-Nov-2018
	 */
	public double getCurrentSeverity(){
		if(this.severity.get(0)<0.00001) {
			return 0.0;
		}
		else {
			return Double.parseDouble(String.format("%.04f", this.severity.get(0))) ;
		}
		
		
	}
	
	
	/**
	 * This method returns the severity <i>t</i> periods in the past. For instance, if the argument is 2, then the severity of tick-2 is returned. In case the medical condition 
	 * did not yet exist back then, the value of 0.0 is returned. If you wish the severity in the current period, you can either use the argument t=0 or the alternative method {@link #getCurrentSeverity()}
	 * @param t Number of periods in the past (e.g. 3 would refer to 3 ticks before the current tick. Only positive integers are allowed. 
	 * @return severity as double or zero (0.0) if the medical condition did not yet exist back then.
	 * @author Florian
	 * @version 09-Nov-2018 (unit testing performed and passed)
	 */
	public double getSeverity(int t) {
		if(t>this.severity.size()-1){
			return 0.0; //FIXME: this is potentially a problem given that we crop the memory
		}
		else{
			return this.severity.get(t);
		}
		
	}
	
	/**
	 * Returns the duration of the medical condition. 
	 * @return Duration as <b>int</b>
	 * @author Florian 
	 * @version 09-Nov-2018 (unit testing passed)
	 * 
	 */
	public int getDuration(){
		int currentTick = (int)RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		int result = currentTick-this.initialTick+1; 
		
		//System.out.printf("getDuration(currentTick=%s,initialTick=%s,visibSym=%s)=%s\n",currentTick,this.initialTick,this.illness.visibilitySymptoms,result);
		
		
		return result;
	}
	
	/**
	 * Sets the current severity value at position 0 (index=0). The index can be interpreted as number of ticks in the past
	 * @param value current severity of the medical condition
	 * @author Florian
	 * @version 09-Nov-2018 (no unit testing needed)
	 */
	public void setCurrentSeverity(double value){
			value = Math.max(0, value);
			this.severity.add(0,value);
			// Limit the severity array list to 3 entries
			 while(this.severity.size()>3){
				 this.severity.remove(3);
			 }
		}
	
	/**
	 * This method modifies the severity of the medical condition. If no treatment is available (null), the severity is increased by the amount stored in the class {@link Illness#deltaSeverityWoTreatment}. 
	 * If a treatment is available, the severity is reduced by {link {@link Treatment#deltaSeverityUnderTreatment} (actually summed given that the value is negative)
	 *If treatment was assigned by a Provider, the Patient's runningBudget is updated
	 */
	public void treat(){
		//System.out.printf("I am treating medical condition %s ; Severity before=%s\n ", this.medConditionID,this.severity.get(0));
		
		if(this.treatment==null) { // NO treatment => no payment
			double newSeverity = this.severity.get(0)+this.illness.deltaSeverityWoTreatment; 
			this.setCurrentSeverity(newSeverity);
			//System.out.printf("=> no treatment available, severity after: %s\n",this.severity.get(0));
		}
		else {
			// There is a treatment available. Let's first see how much the patient can pay
			double budget = 0 ;
			if(this.treatment.type==TreatmentType.SELF) { // self treatments are not subject to reimbursement
				budget = this.patient.getCapital(); 
			}
			else {
				budget = this.patient.computeBudget();
			}
			//System.out.println("My budget for this treatment is: "+budget);
			
			// The patient computes if she can afford the treatment assigned by the Provider
			if(this.treatment.cost <= budget | this.illness.emergency) {
				if(this.detector!=null && this.treatment.marginalBenefitProvider>0.0) { // The provider gets some treatment from the treatment
					this.detector.addIncometoNetIncome(this.treatment.marginalBenefitProvider);	
				}
				
				//Pay the treatment and request refund by insurance company
				/*this.patient.reduceCapitalBy(this.treatment.cost);
				this.patient.claimReimbursementTreatment(this.treatment.cost);
				this.patient.totalMedicalExpenses += this.treatment.cost;*/
				this.patient.payTreatment(this.treatment.cost,this.treatment.type,this.treatment.description);
				// Add the cost of treatment to the data on cost of treatment
				this.illness.currentCost += this.treatment.cost;
				
				// Get treated
				double newSeverity = this.severity.get(0)+this.treatment.deltaSeverityUnderTreatment;
				this.setCurrentSeverity(newSeverity);
				this.wasTreated = true;
				
				//System.out.printf("Got the treatment %s, severity before: %s (max: %s min: %s)\n",this.treatment.description,this.severity.get(1),this.treatment.minSeverity,this.treatment.maxSeverity);

			
				
			}
			else {
				double newSeverity = this.severity.get(0)+this.illness.deltaSeverityWoTreatment; 
				this.setCurrentSeverity(newSeverity);
				//System.out.printf("=> treatment available, but no budget: %s\n",this.severity.get(0));
			}
			
			
			
			
			
		}
		

	}
	
	public double getInitialTick(){
		return this.initialTick;
	}
	
	
	
	

	
	/**
	 * Returns the numerical ID of the illness
	 * @return Illness ID as int
	 */
	public int getIllness(){
		return illness.id;
	}
	
	/**
	 * Returns the ID of the patient who has this medical condition
	 * @return integer ID of patient
	 */
	public int getPatient(){
		return this.patient.ID;
	}
	
	
	/**
	 * Removes the medical condition from the context. 
	 */
	public void removeFromContext(){
		this.patient.medConditions.remove(this);
		Model.context.remove(this);
	}

	/**
	 * Returns a numerical version of the treatment type: 
	 * <ul>
	 * 	<li>0: None</li>
	 * 	<li>1: SELF</li>
	 * 	<li>2: NORMAL</li>
	 * 	<li>3: SPECIALIST</li>
	 * </ul>
	 * 
	 * @return numerical version of the treatment type
	 */
	public int getTreatmentType(){
		if(this.treatment==null){
			return 0;
		}
		else if(this.treatment.type==TreatmentType.SELF){
			return 1;
		}
		else if(this.treatment.type==TreatmentType.TREATMENT_NORMAL) {
			return 2;
		}
		else if(this.treatment.type==TreatmentType.TREATMENT_SPECIALIST) {
			return 3;
		}
		else {
			return -1; // should not happen!
		}
	}
	
	/**
	 * Returns the ID of the treatment
	 * @return 
	 */
	public int getTreatmentDescr() {
		if(this.treatment == null) {
			return 0;
		}
		else {
			return this.treatment.ID;
		}
	}

	/**
	 * Deletes all links from the medical condition to other objects. To be used right before the object is dropped from the context. 
	 */
	public void clear() {
		this.illness	= null;
		this.treatment	= null;
		this.detector 	= null;
		this.patient	= null;
		this.applier	= null;

		
		
	}
	/**
	 * Prints a short description of the medical condition to the console. Used only for testing purposes
	 */
	public void describe() {
		//System.out.printf("MC-%s: %s\tSeverity:%s\t Visible duration:%s Actual duration:%s\n", this.medConditionID,this.illness.name,this.getCurrentSeverity(),this.getDuration(true),this.getDuration(false));
		
	}
	


}
