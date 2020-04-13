package healthABM;

enum TreatmentType {
	/**Auto-treatment (e.g. buying drugs without consulting a health professional*/
	 SELF,
	 /** Placebo treatment: does not affect the severity of the medical condition*/
	 PLACEBO,
	 /** Treatment that can be prescribed by a GP*/
	 TREATMENT_NORMAL,
	 /** Treatment that requires a specialist*/
	 TREATMENT_SPECIALIST;
	
	
	
	/**
	 * Converts a string to the enum TreatmentType. The method is not case sensitive, but the word (string) must be identical to the name of the enum. 
	 * @param string one of the following: SELF,PLACEBO,TREATMENT_NORMAL,TREATMENT_SPECIALIST
	 * @return enum TreatmentType
	 */
	public static TreatmentType findTypeByString(String string) {
		
		switch(string.toLowerCase().trim()) {
		
		case "self": 					return SELF;
		case "placebo":					return PLACEBO;
		case "treatment_normal":		return TREATMENT_NORMAL;
		case "treatment_specialist":	return TREATMENT_SPECIALIST;
		default: return null;    
		
		}
		
	}	 
}
