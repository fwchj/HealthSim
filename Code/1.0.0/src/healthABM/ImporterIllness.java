package healthABM;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map; 
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * The purpose of this class is only to import information of illnesses and their treatments from an Excel file and convert them into the classes 'Illness'  and 'Treatment' used in the model. 
 * Upon initialisation of the class, the data import is carried out. You can then get an ArrayList of all illnesses using the method getIllnesses().
 * @author Florian Chavez
 * @version 08-Jan-2019 (revised)
 *
 */
public class ImporterIllness {
	/** Import file, must be an xlsx file, with the first sheet containing the information on illnesses and a second sheet called 'treatments' with the treatment. See manual for detailed information.*/
	private String file;
	/** ArrayList of all imported illnesses*/
	private ArrayList<Illness> illnesses;
	
	
	public ImporterIllness(String file){
		this.file = file;
		this.illnesses = new ArrayList<Illness>();
		
		try {
			
			// Define the file to be imported
			FileInputStream excelFile = new FileInputStream(new File(this.file));
			XSSFWorkbook workbook = new XSSFWorkbook (excelFile);
		
			// Load the first worksheet (we go by index rather name to avoid problems if somebody changes the name)
			Sheet sheetIllnesses = workbook.getSheetAt(0);
			Sheet sheetTreatments = workbook.getSheet("treatments");
			
			       
			Iterator<Row> rowIterator = sheetIllnesses.iterator(); // define the iterator
			
			Iterator<Row> rowIteratorTreatments = sheetTreatments.iterator();
			
			// READ THE FIRST LINE (variable names) - Illnesses
				Row firstRow = rowIterator.next();		// Take only the first row
				Iterator<Cell> cellIterator = firstRow.iterator();
				ArrayList<String> varNames = new ArrayList<String>();
				ArrayList<String> betasNames = new ArrayList<String>();	// Only to capture the names of coefficients
	            
	            while (cellIterator.hasNext()) {
		            	Cell currentCell = cellIterator.next();
		            	String name = currentCell.getStringCellValue();
		            	varNames.add(name);
		            	
		            	if(name.contains("beta_")){
		            		betasNames.add(name);
		            	}
	            }
	            
	         // READ THE FIRST LINE (variable names) - Treatments
				firstRow = rowIteratorTreatments.next();		// Take only the first row
				cellIterator = firstRow.iterator();
				ArrayList<String> varNamesTreatments = new ArrayList<String>();
				 
	            while (cellIterator.hasNext()) {
		            	Cell currentCell = cellIterator.next();
		            	String name = currentCell.getStringCellValue();
		            	varNamesTreatments.add(name);
		           }
				
	            
	            
			// Temporary list of illnesses with link to object (to be used in the import of incidence data)	
	            LinkedHashMap<String,Illness> illnessList = new LinkedHashMap<String,Illness>();
			
			// LOOP OVER REMAINING ROWS
			while(rowIterator.hasNext()){
				Row thisRow = rowIterator.next();		// Take only one row at a time
								
				// Load all betas 
				LinkedHashMap<String,Double> betas = new LinkedHashMap<String,Double>();
				for(String b:betasNames){
					betas.put(b.replace("beta_",""), thisRow.getCell(varNames.indexOf(b)).getNumericCellValue());
				}
				
				// Load other values
				int 	id 					= (int) thisRow.getCell(varNames.indexOf("id")).getNumericCellValue();

				String name = thisRow.getCell(varNames.indexOf("illness")).getStringCellValue();
				
				boolean contagious 		= (thisRow.getCell(varNames.indexOf("contagious")).getNumericCellValue()>0);
				boolean chronical 		= (thisRow.getCell(varNames.indexOf("chronical")).getNumericCellValue()>0);
				
				double 	deltaSeverityWoTreatment 	= thisRow.getCell(varNames.indexOf("severityWoTreatment")).getNumericCellValue();
				double 	probabilityDetection		= thisRow.getCell(varNames.indexOf("probabilityDetection")).getNumericCellValue();
				double 	deltaProbDetectionInvest	= thisRow.getCell(varNames.indexOf("deltaProbDetectionInvest")).getNumericCellValue();
				double 	probMaxDectection			= thisRow.getCell(varNames.indexOf("probMaxDectection")).getNumericCellValue();
				double 	initSeverity 				= thisRow.getCell(varNames.indexOf("initialSeverity")).getNumericCellValue();
				int 	visibility 					= (int) thisRow.getCell(varNames.indexOf("visibilitySymptoms")).getNumericCellValue();
				double  initialSeverity				= thisRow.getCell(varNames.indexOf("sevInitial")).getNumericCellValue();
				boolean emergency					= thisRow.getCell(varNames.indexOf("sevInitial")).getNumericCellValue()>0;
				
				
				Illness thisIllness = new Illness(id,name,betas, contagious, chronical, initSeverity, 
						deltaSeverityWoTreatment,visibility, probabilityDetection,deltaProbDetectionInvest,probMaxDectection,null,initialSeverity,emergency);	
				
				this.illnesses.add(thisIllness);
				
				
				illnessList.put(name, thisIllness);
				
			
				
								
				//System.out.printf("\t Treatments:\n");
				
				
				// SEARCH TREATMENTS
				Iterator<Row> iteratorTreatment = workbook.getSheet("treatments").iterator();
				iteratorTreatment.next();	// Skip the first row
				while(iteratorTreatment.hasNext()) {
					Row currentRow = iteratorTreatment.next();
					//CHECK IF THE TREATMENT IS FOR THE GIVEN ILLNESS
					if(currentRow.getCell(varNamesTreatments.indexOf("illness")).getStringCellValue().equals(name)) {
						String description 		= currentRow.getCell(varNamesTreatments.indexOf("description")).getStringCellValue();
						//System.out.println(description);;
						double 	cost 			= currentRow.getCell(varNamesTreatments.indexOf("cost")).getNumericCellValue();
						double 	margBenefit		= currentRow.getCell(varNamesTreatments.indexOf("marginalBenefitProvider")).getNumericCellValue();
						double  delta 			= currentRow.getCell(varNamesTreatments.indexOf("deltaSeverity")).getNumericCellValue();
						double  minSeverity		= currentRow.getCell(varNamesTreatments.indexOf("minSeverity")).getNumericCellValue();
						double  maxSeverity 			= currentRow.getCell(varNamesTreatments.indexOf("maxSeverity")).getNumericCellValue();
						int  idTreatment		= (int) currentRow.getCell(varNamesTreatments.indexOf("id")).getNumericCellValue();

						TreatmentType type 			= TreatmentType.findTypeByString(currentRow.getCell(varNamesTreatments.indexOf("type")).getStringCellValue());
						
						if(cost<=0.0) {
							System.out.println("ERROR: you tried to load a treatment with zero cost. This is not possible");
							System.exit(0);
						}
						
						Treatment t = new Treatment(idTreatment,description, cost,margBenefit,delta,type,minSeverity,maxSeverity);
						thisIllness.treatments.add(t);
						System.out.printf("\t\t-%s: cost:%s, margBen:%s, deltaSeverity:%s, type:%s, severity range=[%s,%s]\n",description,cost,margBenefit,delta,type,minSeverity,maxSeverity);
						
					} // end read current row
				} // end if for the current illness
			} // end loop through illnesses
			
			
			System.out.println("LIST OF IMPORTED ILLNESSES\n------------------------------");
			int counter=1;
			for(Illness i:this.illnesses) {
				System.out.printf("%s) %s [%s]\n",counter++,i.name,i);
			}
			
			
			// LOAD: INCIDENCE DATA
				// LOOP OVER ROWS (age-gender groups) and then over imported illnesses
			for(Map.Entry<String,Illness> i:illnessList.entrySet()) {
				System.out.printf("%s ==> %s (%s)\n",i.getKey(),i.getValue().name,i.getValue().toString());
			}
			
			// Load the sheet with the incidence data
			Sheet sheetIncidence = workbook.getSheet("incidence");
			Iterator<Row> rowIteratorIncidence = sheetIncidence.iterator(); // define the iterator
			
			// Load the first row with the column names
			firstRow  = rowIteratorIncidence.next();		// Take only the first row
			cellIterator = firstRow.iterator();
			ArrayList<String> incidenceNames = new ArrayList<String>();
			while (cellIterator.hasNext()) {
	            	Cell currentCell = cellIterator.next();
	            	String name = currentCell.getStringCellValue();
	            	incidenceNames.add(name);
	           }
			
			
			// LOOP OVER KEYS (gender-age)
			
			while(rowIteratorIncidence.hasNext()) {
				Row currentRow = rowIteratorIncidence.next();
				String 	gender	= currentRow.getCell(incidenceNames.indexOf("gender")).getStringCellValue();
				int 	minAge	= (int) currentRow.getCell(incidenceNames.indexOf("age_start")).getNumericCellValue();
				int 	maxAge	= (int) currentRow.getCell(incidenceNames.indexOf("age_end")).getNumericCellValue();
				int genderInt = gender.equals("male") ? 0: 1;
				Integer[] key = {genderInt,minAge,maxAge};
				
				LinkedHashMap<Illness,Double> value = new LinkedHashMap<Illness,Double>();
				
				// LOOP OVER ALL ILLNESSES
				for(Illness i:this.illnesses) {
					
					int idx = incidenceNames.indexOf(i.name);
					double p = -9.0;
					if(idx>=0) {
						 p = currentRow.getCell(idx).getNumericCellValue();
						 value.put(i, Math.max(0.0,p));
					}
						
						
//					System.out.printf("%s for %s between %s and %s years:  probability = %s\n",i.name,gender,minAge,maxAge,p);
					
				}
				
				Model.illnessProbability.put(key, value);
				
			}
			
			///// get prevalence for initialiser
			// Load the sheet with the incidence data
			Sheet sheetPrevalence = workbook.getSheet("prevalence");
			Iterator<Row> rowIteratorPrevalence = sheetPrevalence.iterator(); // define the iterator
			
			// Load the first row with the column names
			firstRow  = rowIteratorPrevalence.next();		// Take only the first row
			cellIterator = firstRow.iterator();
			ArrayList<String> prevalenceNames = new ArrayList<String>();
			while (cellIterator.hasNext()) {
	            	Cell currentCell = cellIterator.next();
	            	String name = currentCell.getStringCellValue();
	            	prevalenceNames.add(name);
	           }
			
			
			// LOOP OVER KEYS (gender-age)
			
			while(rowIteratorPrevalence.hasNext()) {
				Row currentRow = rowIteratorPrevalence.next();
				String 	gender	= currentRow.getCell(prevalenceNames.indexOf("gender")).getStringCellValue();
				int 	minAge	= (int) currentRow.getCell(prevalenceNames.indexOf("age_start")).getNumericCellValue();
				int 	maxAge	= (int) currentRow.getCell(prevalenceNames.indexOf("age_end")).getNumericCellValue();
				int genderInt = gender.equals("male") ? 0: 1;
				Integer[] key = {genderInt,minAge,maxAge};
				
				LinkedHashMap<Illness,Double> value = new LinkedHashMap<Illness,Double>();
				
				// LOOP OVER ALL ILLNESSES
				for(Illness i:this.illnesses) {
					
					int idx = prevalenceNames.indexOf(i.name);
					double p = -9.0;
					if(idx>=0) {
						 p = currentRow.getCell(idx).getNumericCellValue();
						 value.put(i, Math.max(0.0,p));
					}
						
						
					//System.out.printf("%s for %s between %s and %s years:  prevalence P = %s\n",i.name,gender,minAge,maxAge,p);
					
				}
				
				Model.initialiserPrevalence.put(key, value);
				//System.out.printf("Initialiser prevalence: %s : %s",key,  Model.initialiserPrevalence.get(key).toString() );

			}


			
			//System.out.println("\n--FINISHED WITH THE IMPORT OF INCIDENCE DATA");
			/*for(Map.Entry<Integer[],HashMap<Illness,Double>> x:Model.illnessProbability.entrySet()) {
				String gender = x.getKey()[0] ==0 ? "male" : "female";
				System.out.printf("-> %s from %s to %s years:\n",gender,x.getKey()[1],x.getKey()[2]);
				for(Map.Entry<Illness,Double> e:x.getValue().entrySet()) {
					System.out.printf("\t%s: %s\n",e.getKey().name,e.getValue());
				}
			}*/
			
			
			
			
               
		
		
		} catch (FileNotFoundException e) {
			System.out.printf("Sorry, I could not find the input file you specified [%s]\nI abort the initalistion of the model.",this.file);
			System.exit(0);
		} catch (IOException e) {
			System.out.printf("Sorry, I could not find the input file you specified [%s]\nI abort the initalistion of the model.",this.file);
			System.exit(0);
		}
		
	}
	
	/**
	 * Returns the list of imported illnesses
	 * @return ArrayList of illnesses
	 */
	public ArrayList<Illness> getIllnesses(){
		return this.illnesses;
	}
	

}
