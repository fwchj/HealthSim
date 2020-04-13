package healthABM;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import repast.simphony.context.Context;
import repast.simphony.context.space.continuous.ContinuousSpaceFactoryFinder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.parameter.IllegalParameterException;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.continuous.ContinuousSpace;
import repast.simphony.space.continuous.RandomCartesianAdder;

public class Initialiser implements ContextBuilder<Object> {

	@Override
	public Context<Object> build(Context<Object> context) {

		Vector<Patient> allPatients = new Vector<Patient>();
		Vector<Provider> allProviders = new Vector<Provider>();

		context.setId("healthABM");

		final ContinuousSpace<Object> space = ContinuousSpaceFactoryFinder.createContinuousSpaceFactory(null)
				.createContinuousSpace("space", context, new RandomCartesianAdder<Object>(),
						new repast.simphony.space.continuous.StrictBorders(), 50, 50);

		Model model = new Model();
		model.timer("Start of initialiser",true);
		context.add(model);
		Model.context = context;

		Parameters params = RunEnvironment.getInstance().getParameters();
		int Npatients = params.getInteger("nPatients");
		double tolerance = params.getDouble("tolerance");
		double xiBase = params.getDouble("xi_base");
		double xiDist = params.getDouble("xi_dist");
		double premiumsScalingFactor = params.getDouble("premiumsScalingFactor");
		
		String inputfolder = params.getString("inputfolder");

		// Initialise the insurance companies (from EXCEL FILE)
		String fileHi = inputfolder+"/hi.xlsx"; 
		
		try {

			LinkedHashMap<Integer, InsuranceCompany> allInsuranceCompanies = new LinkedHashMap<Integer, InsuranceCompany>();

			// Define the file to be imported
			FileInputStream excelFile = new FileInputStream(new File(fileHi));
			XSSFWorkbook workbook = new XSSFWorkbook(excelFile);

			// INSURANCE COMPANIES
			Sheet sheetInsurers = workbook.getSheet("insurers");
			Iterator<Row> rowIterator = sheetInsurers.iterator(); // define the iterator

			// READ THE FIRST LINE (variable names) - companies
			Row firstRow = rowIterator.next(); // Take only the first row
			Iterator<Cell> cellIterator = firstRow.iterator();
			ArrayList<String> varNames = new ArrayList<String>();
			while (cellIterator.hasNext()) {
				Cell currentCell = cellIterator.next();
				String name = currentCell.getStringCellValue();
				varNames.add(name);

			}
			// Read the actual data
			while (rowIterator.hasNext()) {
				Row thisRow = rowIterator.next(); // Take only one row at a time

				// Load value
				double capital = thisRow.getCell(varNames.indexOf("capital")).getNumericCellValue();
				double profitTarget = thisRow.getCell(varNames.indexOf("profitTarget")).getNumericCellValue();
				int id = (int) thisRow.getCell(varNames.indexOf("id")).getNumericCellValue();

				// Create the company
				InsuranceCompany company = new InsuranceCompany(id, capital, profitTarget);
				// Add it to the context
				context.add(company);
				// Add it to a HashMap in order to be able to link the HIplans (below) to the
				// companies
				allInsuranceCompanies.put(id, company);
			} // end loop over all insurance companies

			// Health insurance plans
			Sheet sheetHiPlans = workbook.getSheet("hiplans");
			rowIterator = sheetHiPlans.iterator(); // define the iterator

			// READ THE FIRST LINE (variable names) - companies
			firstRow = rowIterator.next(); // Take only the first row
			cellIterator = firstRow.iterator();
			varNames = new ArrayList<String>();
			while (cellIterator.hasNext()) {
				Cell currentCell = cellIterator.next();
				String name = currentCell.getStringCellValue();
				varNames.add(name);
				// System.out.printf("%s\n",name);
			}
			// Read the actual data
			while (rowIterator.hasNext()) {
				Row thisRow = rowIterator.next(); // Take only one row at a time

				// Load value
				int idInsurer = (int) thisRow.getCell(varNames.indexOf("insurer")).getNumericCellValue();
				int idPlan = (int) thisRow.getCell(varNames.indexOf("id")).getNumericCellValue();
				double premium = thisRow.getCell(varNames.indexOf("prime")).getNumericCellValue()*premiumsScalingFactor;
				int minAge = (int) thisRow.getCell(varNames.indexOf("minAge")).getNumericCellValue();
				int maxAge = (int) thisRow.getCell(varNames.indexOf("maxAge")).getNumericCellValue();
				double deductible = thisRow.getCell(varNames.indexOf("deductible")).getNumericCellValue();
				double copaymentRate = thisRow.getCell(varNames.indexOf("copaymentRate")).getNumericCellValue();
				int stopLoss = (int) thisRow.getCell(varNames.indexOf("stopLoss")).getNumericCellValue();
				int stopClaim = (int) thisRow.getCell(varNames.indexOf("stopClaim")).getNumericCellValue();
				String gender = thisRow.getCell(varNames.indexOf("gender")).getStringCellValue().toLowerCase();

				// By default all insurance plans are for both gender
				boolean women = true;
				boolean men = true;

				// If women (or woman) is indicated, then the insurance plan is not available to
				// men
				if (gender.equals("woman") || gender.equals("women")) {
					men = false;
				}
				// If men (or man) is indicated, then the insurance plan is not available to
				// women
				if (gender.equals("men") || gender.equals("man")) {
					women = false;
				}

				InsuranceCompany insurer = allInsuranceCompanies.get(idInsurer);
				if (insurer != null) {
					// Create the company
					HIPlan plan = new HIPlan(idPlan,insurer, deductible, copaymentRate, stopLoss, stopClaim, minAge, maxAge,
							premium, women, men);
					// Add it to the context
					System.out.printf("\nNew HI Plan with insurer:%s, deduct:%s, copay:%s, premium:%s", insurer, deductible,
							copaymentRate, premium);
					context.add(plan);
				} else {
					System.out.printf("WARNING: HIPlan with ID=%s has an invalid insurer. It was ignored\n", idPlan);
				}

			} // end loop over all insurance companies

		} catch (FileNotFoundException e) {
			System.out.printf(
					"Sorry, I could not find the input file you specified [%s]\nI abort the initalistion of the model.",
					fileHi);
			System.exit(0);
		} catch (IOException e) {
			System.out.printf(
					"Sorry, I could not find the input file you specified [%s]\nI abort the initalistion of the model.",
					fileHi);
			System.exit(0);
		}

		// Initialise all providers
		double providerDensityGP = params.getDouble("providerDensityGP");
		double providerDensitySpecialist = params.getDouble("providerDensitySpecialist");

		int NdoctorsGP = (int) Math.max(2, Npatients * providerDensityGP);
		int NdoctorsSpecialist = (int) Math.max(2, Npatients * providerDensitySpecialist);

		// Generate the GPs
		for (int i = 1; i <= NdoctorsGP; i++) {
			double quality = RandomHelper.nextDoubleFromTo(0.8, 1.0);
			double baseCost = params.getDouble("gpBaseCost");
			Provider provider = new Provider(quality, baseCost, 10000, 80, false, space, 0);
			allProviders.add(provider);
			context.add(provider);
			System.out.printf("gp base cost: %s", baseCost);
		}
		System.out.printf("specialist base cost: hola");
		// Generate the specialists
		for (int i = 1; i <= NdoctorsSpecialist; i++) {
			double quality = RandomHelper.nextDoubleFromTo(0.8, 1.0);
			double baseCost = params.getDouble("specialistBaseCost");
			Provider provider = new Provider(quality, baseCost, 10000, 80, true, space, 0);
			allProviders.add(provider);
			context.add(provider);
			System.out.printf("specialist base cost: %s", baseCost);

		}

		// Initialise all Patients (Population)
		for (int i = 0; i < Npatients; i++) {

			boolean female = RandomHelper.nextDoubleFromTo(0, 1) > 0.5 ? true : false;
			double income = createIncome();
			
			//Math.exp(RandomHelper.createNormal(Math.log(4121.0), 0.4694205).nextDouble()) / 2.2;
			
			
			
			
			
			// Source: www.hbs.bfs.admin.ch based on equivalent disposable income with mean
			// 4601 and median 4121 and
			// an average household size of 2.2

			//income = Math.max(1500, income); // Here we use the minimum of social assistance (~1000 + insruance =>~
												// 1500)

			// Age distribution //TODO: not yet very well fitting (probably not very
			// important)
			double random = RandomHelper.nextDoubleFromTo(0, 1);
			int age = RandomHelper.nextIntFromTo(0, 5200);
			if (random < 0.75) {
				age = RandomHelper.nextIntFromTo(18 * 52, 58 * 52 - 1);
			} else {
				age = (int) (58 * 52 + Math.pow(RandomHelper.nextDoubleFromTo(0, 1), 2) * (52 * 42));
			}

			Patient patient = new Patient(income, age, female, 1, tolerance, space);
			allPatients.add(patient);
			context.add(patient);

		}

		System.out.printf("AllProviders %s\n", allProviders.size());
		System.out.printf("AllPatients %s\n", allPatients.size());

		double sum = 0;
		int count = 0;
		


		// [IMPORT ILLNESSES FROM EXCEL FILE]
		String mainData = inputfolder+"/illnesses.xlsx";
	

		ImporterIllness illnesses = new ImporterIllness(mainData);

		Model.listIllnesses = illnesses.getIllnesses();
		for (Illness i : illnesses.getIllnesses()) {
			context.add(i);
			Model.incidence.put(i, 0);
		}
		
		// get patients sick
		for (Patient i : allPatients) {
			LinkedHashMap<Illness,Double> myIllnessProbabilities = new LinkedHashMap<Illness,Double>();
			int genderInt = i.genderToInteger();
			int age = (int) Math.floor(i.age/52);
			
			for(Integer[] e: Model.initialiserPrevalence.keySet()) {
				if(e[0]==genderInt && e[1]<= age && e[2]>=age) {
					myIllnessProbabilities = Model.initialiserPrevalence.get(e);
					break;
				}
			}		
			// loop over illness to see if patient gets one
			for(Map.Entry<Illness,Double> e: myIllnessProbabilities.entrySet()) {
				Illness illness = e.getKey();		
						double randomValue = RandomHelper.createUniform(0.0,1.0).nextDouble();
						if(randomValue < e.getValue()) {
							i.medConditions.add(new MedicalCondition(illness, illness.initialSev, i));
							i.HS = i.getHealthStatus();
						}
			}
		} // end loop over patients getting sick in initializer 
		
		// LOAD WORKING DIRECTORY
		String syspath = System.getProperty("user.dir");

		if (RunEnvironment.getInstance().isBatch()) {
			syspath = RunEnvironment.getInstance().getParameters().getString("syspath");
		}

		// DELETE ALL OLD EXPORT FILES
		File toClean;
		if (RunEnvironment.getInstance().isBatch()) {
			toClean = new File(syspath + "/output/outputdata");
		} else {
			toClean = new File(syspath + "/outputdata");
		}

		try {
			for (File file : toClean.listFiles()) {
				if (!file.isDirectory()) {
					file.delete();
				}
			}
		} catch (NullPointerException e) {

		}

		// FOR BATCH MODE: stop at xxx period (5 years)
		if (RunEnvironment.getInstance().isBatch()) {
			int endAt = 1560;
			try {
				endAt = params.getInteger("stopBatch");
			}
			catch(IllegalParameterException e) {
				
			}
			RunEnvironment.getInstance().endAt(endAt);
			//RunEnvironment.getInstance().endAt(1040);
		} else { // Pause at 3 years
			RunEnvironment.getInstance().pauseAt(1040);
		}

		/*
		 * for (Object o: context) { NdPoint pt = space.getLocation(o);
		 * //space.moveTo(obj, (int) pt.getX(), (int) pt.getY()); }
		 */

		// Load some paramters
		Patient.incomeSesThreshold = params.getDouble("povertyLine");

		

		System.out.println("<< Context successfully built >>");

		double suma = 0.0;
		for (Object p : context.getObjects(Patient.class)) {
			Patient pro = (Patient) p;
			suma += pro.age;
		}

		ArrayList<Integer> test = new ArrayList<Integer>();
		test.add(1);
		test.add(5);

		System.out.printf("The sum of all quality is %s\n", suma);
		System.out.printf("Random number: %s\n", RandomHelper.nextDoubleFromTo(0, 1));
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");  
		LocalDateTime now = LocalDateTime.now();  
		//Model.showCostByIllness();
		System.out.printf("Manatory health insurance is set to %s",Model.mandatoryInsurance);
		Model.log(false,"---- START OF LOG: %s --------",dtf.format(now));
		model.timer("End of initialiser",false);

		
		return context;
	}

	private static double createIncome() {
		
		
		// Get the parameter and convert it to an array of strings
		String[] ip = RunEnvironment.getInstance().getParameters().getString("income").split(":");
		//System.out.printf("createIncome: %s\n",Arrays.toString(ip));
		double result = 0.0;
		switch(ip[0].toLowerCase()) {
		case "lognormal": 
			
			result = Math.exp(RandomHelper.createNormal(Double.parseDouble(ip[1]),Double.parseDouble(ip[2])).nextDouble()) ;
			
			try{
				System.out.printf("Limiting the minimum to %s",ip[3]);
				result = Math.max(result, Double.parseDouble(ip[3])); 
			}
			catch(ArrayIndexOutOfBoundsException e){
				System.out.println("Warning: The log normal had no lower limit specified. This could generate negative incomes!");
			}

			break;
		case "uniform": 
			result = RandomHelper.nextDoubleFromTo(Double.parseDouble(ip[1]),Double.parseDouble(ip[2]));
			break;
		default:
			System.out.println("ERROR: you asked for an unknown distribution");
			System.exit(2);
		}
		
		System.out.printf("===> income = %s\n", result);
		return result;
	}

}
