package healthABM;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Random;
import java.util.Scanner;

import java.util.Map.Entry;

import repast.simphony.context.Context;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.environment.RunState;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.RandomHelper;

public class Model {
	// Instance variables
	/** Counter variable for unique insurance IDs*/
	public static int counterInsurance;
	/** Counter variable for unique illness IDs*/
	public static int counterIllness;
	/** Counter variable for unique patient IDs*/
	static int counterPatients;
	/** Counter variable for unique provider IDs*/
	static int counterProviders;
	/** Counter variable for unique medical condition IDs*/
	static int counterMedConditions;
	/** Counter variable for unique HIPlan IDs*/
	static int counterHIPlans;
	
	/** Context of the simulation */
	public static Context<Object> context;
	
	public static boolean mandatoryInsurance = RunEnvironment.getInstance().getParameters().getBoolean("mandatoryInsurance");
	
	/**List of all existing Illnesses in model */
	public static ArrayList<Illness> listIllnesses;
	
	/** Matrix with the expected health care expenditures imported from an external file: <br>
	 * <b>Key:</b> Age in years as integer<br>
	 * <b>Value:</b> Double[] with expected health care expenditures [women,men] 
	 */
	public static LinkedHashMap<Integer, Double[]> expHCE;
	
	/** Intermediate information of aggregate log expenditures (updated by individuals) to compute HCE
	 * <b>Key:</b> Age in years as integer, gender<br> 
	 * <b>Value: </b> Array of doubles with individual expenditures of individuals of each group
	 */
	protected static  LinkedHashMap<Integer, LinkedHashMap<Integer, ArrayList<Double>>> logExpenditures; 
	
	/** Information of HCE per group (gender-age) updated with Model logExpenditures 
	 * <b>Key:</b> Age in years as integer, gender (1: women, 0: men)<br> 
	 * <b>Value: </b> Array of doubles with mean, variance and percentile 95 of the group
	 */
	protected static LinkedHashMap<Integer, LinkedHashMap<Integer, double[]>> HCE;
	
	/** Number of new cases in one year */
	//public static ArrayList<Integer> incidence = new ArrayList<Integer>(); 
	/** Number of patients with the disease */
	//public static ArrayList<Integer>prevalence = new ArrayList<Integer>();
 
	/**Map with existing Illnesses in model and their respective incidence rate values (reset every 1 period)*/
	public static LinkedHashMap<Illness,Integer> incidence = new LinkedHashMap<Illness,Integer>();
	
		
	/** alpha, beta and gamma from willingness-to-pay equation */
	public static double[] wtpParams = new double[3];
	
	/** Sensitivity parameter of probability of detecting the medical with respect to the number of medical conditions  (see eq:probabilityCorrectDiagnosis)*/ 
	public static double alphaM = RunEnvironment.getInstance().getParameters().getDouble("alpha_M");
	
	/** Database with the probability of having a given medical condition based on gender, age and education.
	 * The key of the main HashMap is a three-element key containing [gender(0=male,1=female),ageMin,ageMax]. 
	 * while the value of the main HashMap is a Hashmap with the link to the illness and its related probability. 
	 */
	protected static LinkedHashMap<Integer[], LinkedHashMap<Illness,Double>> illnessProbability = new LinkedHashMap<Integer[], LinkedHashMap<Illness,Double>>();

	public static Random udist ; 
	
	/** Memory for expected cost calculations (weeks) */
	public static int memory = 52*5;
	
	Parameters params = RunEnvironment.getInstance().getParameters();
	
	/**Variable that stores the tick in which the current year started*/
	public static int currentYearStart = 1;
	
	/** Database for the initial prevalence of the first generation of patients  */
	protected static LinkedHashMap<Integer[], LinkedHashMap<Illness,Double>> initialiserPrevalence = new LinkedHashMap<Integer[], LinkedHashMap<Illness,Double>>();
	
	/** Last current time-stamp, used in Model.timer()*/
	private static long lastCurrentTime;
	

	
	
	
	
	public Model(){
		counterInsurance = 1;
		counterPatients = 1;
		counterProviders = 1;
		counterMedConditions = 1;
		counterHIPlans = 1;
		
		listIllnesses = new ArrayList<Illness>();
		
		incidence 	= new LinkedHashMap<Illness,Integer>();
		initialiserPrevalence = new LinkedHashMap<Integer[], LinkedHashMap<Illness,Double>>();
		illnessProbability = new LinkedHashMap<Integer[], LinkedHashMap<Illness,Double>>();
		loadDataExpectedHCE();
		mandatoryInsurance = RunEnvironment.getInstance().getParameters().getBoolean("mandatoryInsurance");
		int randomSeed = RunEnvironment.getInstance().getParameters().getInteger("randomSeed");
		udist = new Random(randomSeed);

		wtpParams[0] = params.getDouble("wtpAlpha");
		wtpParams[1] = params.getDouble("wtpBeta");
		wtpParams[2] = params.getDouble("wtpGamma");
		
		Model.logExpenditures 	= new LinkedHashMap<Integer, LinkedHashMap<Integer, ArrayList<Double>>>();
		Model.HCE 				= new LinkedHashMap<Integer, LinkedHashMap<Integer, double[]>>();
		
		for(int i = 0; i < 150; i++){
			LinkedHashMap<Integer, ArrayList<Double>> exp = new LinkedHashMap<Integer, ArrayList<Double>>(); 
			LinkedHashMap<Integer, double[]>stats = new LinkedHashMap<Integer, double[]>();
			exp.put(0,null);
			exp.put(1,null);
			stats.put(1, null);
			stats.put(0, null);
			logExpenditures.put(i, exp);
			HCE.put(i, stats);
		}
			
	}
	
	
	
	@ScheduledMethod(start=1,interval=52,priority=4,shuffle=true)
	public void resetYearStart() {
		Model.currentYearStart = (int)RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
	}

	//STEP 100 stepResetModel()
	/**This method prints the current tick in the model and resets (to 0) the incidence rates for the current tick  and everz 52 ticks the prevalence tick*/
	@ScheduledMethod(start=0,interval=1,priority=100,shuffle=true) 
	public void stepResetModel(){
		Model.timer("Init of tick",false);
		if(RunEnvironment.getInstance().isBatch()) {
			System.out.printf("Start of Tick %s of run %s\n", RunEnvironment.getInstance().getCurrentSchedule().getTickCount(),RunState.getInstance().getRunInfo().getRunNumber());
		}
		else {
			System.out.printf("Start of Tick %s\n", RunEnvironment.getInstance().getCurrentSchedule().getTickCount());
		}
		
		// RESET INCINCE and costs
		for(Illness i:listIllnesses){
			incidence.replace(i,0);
			i.currentCost=0.0;
		}
		
		
		
		
		
	}
	
	@ScheduledMethod(start=0,interval=1,priority=96,shuffle=true)
	public void step_timer96() {
		Model.timer("End reset methods",false);
	}
	
	@ScheduledMethod(start=0,interval=1,priority=94,shuffle=true)
	public void step_timer94() {
		Model.timer("End adapt HI offer",false);
	}
	
	@ScheduledMethod(start=0,interval=1,priority=92,shuffle=true)
	public void step_timer92() {
		Model.timer("End update global log expenditures",false);
	}
	
	@ScheduledMethod(start=0,interval=1,priority=85,shuffle=true)
	public void step_timer85() {
		Model.timer("End step contract insurance",false);
	}
	@ScheduledMethod(start=0,interval=1,priority=75,shuffle=true)
	public void step_timer75() {
		Model.timer("End get sick",false);
	}
	@ScheduledMethod(start=0,interval=1,priority=65,shuffle=true)
	public void step_timer65() {
		Model.timer("End step get medical care",false);
	}
	@ScheduledMethod(start=0,interval=1,priority=0,shuffle=true)
	public void step_timer0() {
		Model.timer("End of tick",false);
	}

	//STEP 055 updateHCEexpenditures()
	/**This method loops over Model.logExpenditures and update HCE values: mean, variance, 95 percentile
	 * @version 19-Feb-2019 (unit testing passed)
	 * @author Georgina
	 */
	@ScheduledMethod(start = 1, interval = 1, priority = 91, shuffle = true) 
	public void updateHCEexp(){
		for(int i = 1; i < 150; i++) { // loop over all age-
			for(int j = 0; j < 2;  j++) { //gender groups
				// And compute group mean, variance and 95 percentile
				if(Optional.ofNullable(logExpenditures.get(i).get(j)).isPresent()){
					
					//Mean
					double mean = 
							 logExpenditures.get(i).get(j).stream().mapToDouble((x) -> x).summaryStatistics().getAverage();
					//System.out.printf("\nMean for this group is %s\n", mean); 
				
					 ArrayList<Double> ssx = new ArrayList<Double>();
					 for(double expenditure: logExpenditures.get(i).get(j)) {
						  ssx.add(Math.pow(expenditure - mean, 2));
					 }
					 
					 //Variance
					 double variance = 0;
					 if(logExpenditures.get(i).get(j).size()>1){
						 variance = ssx.stream().mapToDouble((x)->x).summaryStatistics().getSum() / (ssx.size()- 1.0);
					 }
					 //System.out.printf("\nStandard deviation for this group is %s\n", (variance)); 
					 
					 //Percentile 95
					 Collections.sort(logExpenditures.get(i).get(j));
					 double p95 =  			 
							 logExpenditures.get(i).get(j).get((int)Math.floor(logExpenditures.get(i).get(j).size()*0.95));
	
					 double[] statistics = {mean, variance, p95};
					 LinkedHashMap<Integer, double[]> updateValue = new LinkedHashMap<Integer, double[]>();
					 Integer p = (j == 1) ? 1: 0;
					 updateValue.put(p, statistics);
					 HCE.put(i, updateValue);
				}
 
			} // end loop over gender
		} // end of loop over age	
		//System.out.printf("HCE log: %s",HCE.toString());
	}
	
	//STEP 093: updateGlobalLogExpenditures
	/**
	 * This method loops over patients and adds their total expenditures from the last year 
	 * to an arrayList with the age, gender and expenditure of each patient.
	 */
	@ScheduledMethod(start = 1, interval = 1, priority = 93, shuffle = true) 
	public void updateGlobalLogExpenditures(){ 	
		for(Object o: context.getObjects(Patient.class)){
			Patient p = (Patient)o;
			
			if(!p.annualExpentitures.isEmpty()) {
				
	
				// find the newest entry and save it to 'totalExpenditure'
				int newest=-9999;
				for(int key: p.annualExpentitures.keySet()) {
					if(key>newest) {
						newest = key;
					}
				}
				double totalExpenditure = p.annualExpentitures.get(newest); 
				
					
				//System.out.printf("\nTotal expenditure of patient %s is %s", p.ID, totalExpenditure);	
				
				if(logExpenditures.get(p.age/52).get(p.genderToInteger())!=null) { 
					
					logExpenditures.get(p.age/52).get(p.genderToInteger()).add(totalExpenditure);
					
				}else {//Group does not contain any value for group of patient 
					ArrayList<Double> expenditures = new ArrayList<Double>();
					expenditures.add(totalExpenditure);
					logExpenditures.get(p.age/52).put(p.genderToInteger(), expenditures);
				}
				
			}

				
		}
	}	
	
	

	/**
	 * Method to load the data on expected health care expenditures form a CSV file. The CSV file must have the following structure: 
	 * <ul>
	 * 	<li>First row with column titles (does not matter much what is written there)</li>
	 * 	<li>First column: age in years (integer)</li>
	 * 	<li>Second row: expected health care expenditures for women</li>
	 * 	<li>Second row: expected health care expenditures for men</li>
	 * </ul>
	 */
	private void loadDataExpectedHCE() {
		
				Model.expHCE = new LinkedHashMap<Integer,Double[]>(); 
		// Start defining the necessary objects for the input
				String inputfolder = params.getString("inputfolder");

				String path = inputfolder+"/expHCE.csv"; 	// Define the location of the file
				
				
				
				Scanner scanner = null;				// Declare the scanner
				
				try { // We need the try-catch in case of not finding the file
					// Here we define the scanner, instead of System.in (console)
					// we now use the FileReader
					scanner = new Scanner(new BufferedReader(new FileReader(path)));
				} catch (FileNotFoundException e) {
					System.out.println("I could not find the file containing the expected HCE (expHCE.csv): I was looking at: " + inputfolder); //error msg sent if file not found
					e.printStackTrace(); 
					
				}
				scanner.useDelimiter(",|\\n");  // Define the symbol , as the separator between two elements
				scanner.nextLine(); // Jump to the next line (in case the first line has only titles
		
				while(scanner.hasNext()){ // Check if there is a next element (continue until the end of file (EOF))
					int age 			= scanner.nextInt();	 				// load the age to a integer
					double female 		= Double.parseDouble(scanner.next());	// convert the string to a double
					double male  		= Double.parseDouble(scanner.next()); 	// convert the string to a double
					
					Double[] exp = {female,male};
					Model.expHCE.put(age, exp);
					//System.out.printf("%s => [%s,%s] (Now the size of the map is: %s\n",age,female,male,Model.expHCE.size());
		}
				
		
		
	}
	
	
	// GENERIC METHODS (TO BE USED IN ANY CLASS)
	/**
	 * This is a generic method in Model Class that can be called from any other class to add a a value to a log database
	 * of type HashMap[Integer[], Double]
	 * @param keyArr  array of integers with the index values for the value that is to be added 
	 * @param newVal (double) value to be added to the log
	 * @param log Log database where the value is to be added
	 * @author alejandrob
	 * @version 21-Feb-2019
	 */
	public static void addValueToLog(Integer[] keyArr, Double newVal, LinkedHashMap<Integer[], Double> log) {
		boolean found = false;
		for(Entry<Integer[], Double> e: log.entrySet()) {
			// If entry is found, add value to existing value
			if(Arrays.equals(e.getKey(), keyArr)) {
				found = true;
				e.setValue(e.getValue()+ newVal);
			}
		}
		//If entry is not found, create new entry with given value
		if(!found) log.put(keyArr, newVal);	
	}
	
/**
 * @param gender Gender (integer)
 * @param age age (weeks)
 * @return average HC expenditures for the population of patients grouped by gender, age
 */
	public static double getAvgExpenPop(int gender, int age) {
		return Model.HCE.get(age/52).get(gender)[0];
	}
	
/**
 * 
 * @param gender gender (integer)
 * @param age age (weeks)
 * @return Populational variance of HC expenditures grouped by age, gender
 */
	public static double getVarianceExpenPop(int gender, int age) {
		return Model.HCE.get(age/52).get(gender)[1];
	}
	
/**
 * 
 * @param gender Gender as integer
 * @param age age (weeks)
 * @return The 95th percentile of HC expenditures grouped by age, gender
 */
	public static double getP95ExpenPop(int gender, int age) {
		
		boolean exists =  Optional.ofNullable(HCE.get(age/52)).isPresent() && Optional.ofNullable(Model.HCE.get(age/52).get(gender)).isPresent();

		if(exists) {
			return Model.HCE.get(age/52).get(gender)[2];			
		} else {
			return 0.0;
		}
	}

	/**
	 * Auxiliar method: returns the value of required statistic
	 * @param statistic: 1 for mean, 2 for sd, 3 for 95 percentile
	 * @param gender: gender as int: 1 female, 0 male
	 * @param age:  age integer
	 * @author georgina
	 * @return Desired statistic from the map of health care expenditures
	 * @version 31-Mar-2019
	 */
	
	public double getStatistic(int statistic, int gender, int age) {
		double stat= -99.0;
		switch(statistic) {
			case 1:
				stat=HCE.get(age/52).get(gender)[0];
				break;
			case 2:
				stat=HCE.get(age/52).get(gender)[1];
				break;
			case 3:
				stat=HCE.get(age/52).get(gender)[2];
				break;
		}
		return stat;
	}
	
	/**
	 * Static method to save a log file to the root directory of the project. 
	 * @param append If TRUE, then a new file is started (overwriting the old one)
	 * @param format works like the first argument in {@link System.out.printf}
	 * @param arguments works like the second argument in {@link System.out.printf}
	 */
	public static void log(boolean append, String format, Object... arguments) { 
		
		String path = System.getProperty("user.dir");
		
		path+="/log.txt";
		
		// Prepare log output files
				try{
					// INDIVIDUAL DATA EXPORT
						FileOutputStream myfile_log;
						
						myfile_log = new FileOutputStream(path,append);
						Formatter output_log = new Formatter(myfile_log);
						
						// Header will be created by the initialiser
						output_log.format(format,arguments);
						output_log.close();
					} 
					catch (FileNotFoundException e) {
						System.err.printf("FileNotFound Exception\n");
						System.exit(1);
					}	
	}

	/** Prints the current cost by illness to the console*/
	public static void showCostByIllness() {
		System.out.println("--------- COST BY ILLNESS -----------------------");
		for(Illness i: Model.listIllnesses) {
			System.out.printf("%20s => %12.2f\n",i.name,i.currentCost);
		}
		System.out.println("-------------------------------------------------");

	}
	

	public static void timer(String text, boolean startNew) { 
		if(!RunEnvironment.getInstance().isBatch()) {
		String path = System.getProperty("user.dir");
		
		path+="/timer.txt";
		
		// Prepare log output files
				try{
					// INDIVIDUAL DATA EXPORT
						FileOutputStream myfile_log;
						
						myfile_log = new FileOutputStream(path,!startNew);
						Formatter output_log = new Formatter(myfile_log);
						
						long currentTime = System.currentTimeMillis();
						long duration = currentTime - Model.lastCurrentTime;
						Model.lastCurrentTime = currentTime;

						if(startNew) {
							duration=0;
						}
						
						
					
						int tick = (int) RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
						
						// Header will be created by the initialiser
						output_log.format("%s\t%7.5f\t%s\n",tick,(double)duration/1000,text);
						output_log.close();
					} 
					catch (FileNotFoundException e) {
						System.err.printf("FileNotFound Exception\n");
						System.exit(1);
					}	
		}

	}
	
}
