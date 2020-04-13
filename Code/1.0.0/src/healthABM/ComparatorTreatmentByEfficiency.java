package healthABM;

import java.util.Comparator;
/**
 * This comparator sorts the treatments in an ArrayList according to their 
 * efficiency (deltaSeverity/cost), where the first element will be the most efficient treatment
 * @author Florian
 * @version 09-Jan-2019 (tested)
 *
 */
public class ComparatorTreatmentByEfficiency implements Comparator<Treatment>{
	
	/**Compares two treatments based on their efficiency in terms of deltaSeverity per cost. 
	 * @param t1 Treatment 1
	 * @param t2 Treatment 2
	 * @return difference in efficiency */
	public int compare(Treatment t1, Treatment t2){
		double efficiency1 = t1.deltaSeverityUnderTreatment/t1.cost;
		double efficiency2 = t2.deltaSeverityUnderTreatment/t2.cost;
		
		return (int) (-1000*(efficiency1-efficiency2));		
	} // end compare

	
	

}

