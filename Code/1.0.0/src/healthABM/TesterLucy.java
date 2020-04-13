package healthABM;

import java.util.ArrayList;

import repast.simphony.space.continuous.ContinuousSpace;

public class TesterLucy {

		
	public static void main(String[] args) {
		/*	
	
		Patient patient1 = new Patient(6000, 22, true, 0, 0, null);
		// income: 6000, age: 22, female
		//public Patient (double income, int age, boolean female, double healthStatus, double tol, ContinuousSpace<Object> location)
		
		// Add some data
		patient1.saveHCELog(0, 0, 125);
		patient1.saveHCELog(1,0,10);
		patient1.saveHCELog(1,0,10);
		patient1.saveHCELog(1,1,5);
		patient1.saveHCELog(2,1,100);
		patient1.saveHCELog(2,2,10);
		patient1.saveHCELog(3,2,10);
		patient1.saveHCELog(3,1,5);
		patient1.saveHCELog(4,2,10);
		patient1.saveHCELog(4,3,50);
		patient1.saveHCELog(5,4,50);
		
		InsuranceCompany comp1 = new InsuranceCompany(1,10000,500);
		HIPlan plan1 = new HIPlan(comp1, 5, 0.25, 0, 0, 20, 25, 50,true,false );
		//public HIPlan(InsuranceCompany comp, double ded, double cop, int stoploss, int stopclaim, int minage, int maxage, double prime,boolean women,boolean men, boolean ... subclass )
		// ded: 2000, copay: 0.25, no stop loss or stop claim, age range: [20,25], prime: 5000, for women not men
		
		HIPlan plan2 = new HIPlan(comp1, 10, 0.25, 0, 0, 20, 25, 50,true,true );
		
		ArrayList<HIPlan> plans = new ArrayList<HIPlan>();
		plans.add(plan1);
		plans.add(plan2);

		*/
		// Display the database
		//patient1.displayHCELog(0); // complete database (can be long)
		//patient1.displayHCELog(3); // display only first 3 rows
		
		/*
		// Get information on particular tick (all medical conditions combined)
		//System.out.printf("HCE at tick=2: %s\n",patient1.getHCEByTick(2));
		
		// Get information on HCE during a period
		System.out.printf("HCE between tick 2 and 3: %s\n",patient1.getHCEByTickFromTo(2, 3));
		
		
		// Get information on a specific combination of tick and medical condition
		System.out.printf("HCE for MC 2 at tick 3: %s\n",patient1.getHCEByTickAndMC(3, 2)); // Note: if no data found. Double.NaN is returned. 
		
		// Get information on the total cost of a medical condition
		System.out.printf("HCE for MC 3 : %s\n",patient1.getHCEByMC(3)); // Note: if no data found. Double.NaN is returned. 


		// Test moving average: 
		System.out.printf("Moving average: %s\n",patient1.movingAverageTotal());
		
		// test new aux function
		System.out.printf("MC by range: %s\n", patient1.getHCEByRangeAndMC(2, 3, 2));
		
		//test #events
		//System.out.printf("No. events: %s\n", patient1.countEventsAboveDeductible(50));

		//test sum below deductible
		//System.out.printf("Sum events below dedyctible: %s\n", patient1.sumEventsBelowDeductible(50));
		
		// testing moving averages of the two above: 
		//HashMap<Integer, Double> noAboveDeduc = patient1.countEventsAboveDeductible(50);
		//HashMap<Integer, Double> totalBelowDeduc = patient1.sumEventsBelowDeductible(50);
		
		//System.out.printf("Moving average of events above deduct: %s\n", patient1.movingAverageAux(noAboveDeduc));
		//System.out.printf("Moving average of events below deduct: %s\n", patient1.movingAverageAux(totalBelowDeduc));
		
		System.out.printf("Risk aversion: %s\n", patient1.riskAversion);
		System.out.printf("Personal assessment: %s\n", patient1.getPersonalExpenCalcTotal());

		
		//System.out.printf("Plan choice: %s\n", patient1.selectInsurance(plans).ID);
		
		*/
					
		}
		

	}


