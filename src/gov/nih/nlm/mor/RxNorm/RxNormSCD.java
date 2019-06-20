package gov.nih.nlm.mor.RxNorm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONArray;
import org.json.JSONObject;


// https://rxnavstage.nlm.nih.gov/REST/rxcui/{rxcui}/allrelated.json
// https://rxnavstage.nlm.nih.gov/REST/rxcui/403966/allProperties.json?prop=all
// https://rxnavstage.nlm.nih.gov/REST/rxcuihistory/concept.json?rxcui={rxcui} <--  for Active concepts, get the BoSS(s)
public class RxNormSCD implements java.io.Serializable {

	private static final long serialVersionUID = 2261607265667390359L;
	private Integer rxcui = null;
	private String name = null;
	private Long manufacturedDoseFormCode = null;
	private String manufacturedDoseFormName = null;	
	private Long unitOfPresentationCode = null;
	private String  unitOfPresentationName = null;
	private Vector<RxNormIngredient> vIngredient = new Vector<RxNormIngredient>();
//	private Vector<RxNormBoss> vBoss = new Vector<RxNormBoss>();
	private Set<Integer> baseCuis = new HashSet<Integer>();
	private String quantityFactor = "";
	private String qualitativeDistinction = "";
	private Vector<Long> snomedCodes = new Vector<Long>();
	private boolean isLiquid = false;
	private boolean hasNDC = false;
	private boolean isVetOnly = false;
	private boolean isPrescribable = false;
	private boolean isVaccine = false;
	private boolean isActive = false;
	private String bossIssue = "";
	private String activeIngIssue = "";
	
	
	public RxNormSCD(Long code) {
		
		/*
		 * {
    "idGroup": {
        "idType": "SNOMEDCT",
        "id": "322709006",
        "rxnormId": [
            "895201"
        ]
    }
}
		 */
		
		JSONObject rxResponse = null;
		JSONObject allRelated = null;

		try {
			rxResponse = getresult("https://rxnavstage.nlm.nih.gov/REST/rxcui.json?idtype=SNOMEDCT&id=" + String.valueOf(code));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		/*
		 * {
    "relatedGroup": {
        "rxcui": "142108",
        "termType": [
            "IN",
            "PIN"
        ],
        "conceptGroup": [
            {
                "tty": "IN",
                "conceptProperties": [
                    {
                        "rxcui": "873",
                        "name": "Anthralin",
                        "synonym": "dithranol",
                        "tty": "IN",
                        "language": "ENG",
                        "suppress": "N",
                        "umlscui": "C0003166"
                    },
                    {
                        "rxcui": "9525",
                        "name": "Salicylic Acid",
                        "synonym": "",
                        "tty": "IN",
                        "language": "ENG",
                        "suppress": "N",
                        "umlscui": "C0036079"
                    }
                ]
            },
            {
                "tty": "PIN"
            }
        ]
    }
}
		 * 
		 * 
		 */
		
		if( rxResponse != null ) {
			JSONObject idGroup = null;
			if( !rxResponse.isNull("idGroup") ) {
				idGroup = (JSONObject) rxResponse.get("idGroup");
				JSONArray rxnormId = null;
				if( !idGroup.isNull("rxnormId") ) {
					rxnormId = (JSONArray) idGroup.get("rxnormId");
					this.rxcui = Integer.valueOf(rxnormId.get(0).toString());
					
					//Find all related ingredients
					try {
						allRelated = getresult("https://rxnavstage.nlm.nih.gov/REST/rxcui/" + rxcui + "/related.json?tty=IN+PIN");
					}
					catch(Exception e) {
						System.err.println("REST call " + "https://rxnavstage.nlm.nih.gov/REST/rxcui/" + rxcui + "/related.json?tty=IN+PIN");
						e.printStackTrace();
					}
					if( allRelated != null ) {
						JSONObject relatedGroup = null;
						if( !allRelated.isNull("relatedGroup")) {
							relatedGroup = (JSONObject) allRelated.get("relatedGroup");
							JSONArray conceptGroup = null;
							if( !relatedGroup.isNull("conceptGroup")) {
								conceptGroup = (JSONArray) relatedGroup.get("conceptGroup");
								for( int i=0; i < conceptGroup.length(); i++) {
									JSONObject conceptGroupVal = (JSONObject) conceptGroup.get(i);
									String tty = null;
									JSONArray conceptProperties = null;
									if( !conceptGroupVal.isNull("tty") ) {
										tty = conceptGroupVal.getString("tty");
										if( !conceptGroupVal.isNull("conceptProperties") ) {
											conceptProperties = (JSONArray) conceptGroupVal.get("conceptProperties");
											for(int j=0; j < conceptProperties.length(); j++) {
												JSONObject conceptPropertiesVal = (JSONObject) conceptProperties.get(j);
												String ingCui = null;
												String ingName = null;
												RxNormIngredient ingToAdd = null;
												if( !conceptPropertiesVal.isNull("rxcui") ) {
													ingCui = conceptPropertiesVal.getString("rxcui");
												}
												if( !conceptPropertiesVal.isNull("name") ) {
													ingName = conceptPropertiesVal.getString("name");
												}
												if( ingCui != null && ingName != null ) {
													ingToAdd = new RxNormIngredient(Integer.valueOf(ingCui), ingName, tty);
													if( !this.vIngredient.contains(ingToAdd) ) {
														this.vIngredient.add(ingToAdd);
													}
												}
											}
										}
									}
									
								}
							}
						}
					}
					
				}
				else {
					//do nothing, there is no assertion from the CD to the SCD.
				}
			}
			else {
				//do nothing, there is no reponse
			}
		}
	}
	
	
	public RxNormSCD(Integer cui, String scdName) {
		rxcui = cui;
		name = scdName;
		System.out.println(cui);
		JSONObject allRelated = null;
//		JSONObject allProperties = null;
		JSONObject rxHistory = null;
		JSONObject ndc = null;
		JSONObject humanProperty = null;
//		long start = System.currentTimeMillis();
		
		try {
			
			//TODO: Make these configurable??
//			allRelated = getresult("https://rxnav.nlm.nih.gov/REST/rxcui/" + rxcui + "/allrelated.json");
//			allRelated = getresult("https://rxnavstage.nlm.nih.gov/REST/rxcui/" + rxcui + "/related.json?tty=IN+PIN+DF");
			allRelated = getresult("https://rxnavstage.nlm.nih.gov/REST/rxcui/" + rxcui + "/related.json?tty=IN+PIN");
//			rxHistory = getresult("https://rxnavstage.nlm.nih.gov/REST/rxcuihistory/concept.json?rxcui=" + rxcui );
			humanProperty = getresult("https://rxnavstage.nlm.nih.gov/REST/rxcui/" + rxcui + "/allProperties.json?prop=all");
			ndc = getresult("https://rxnavstage.nlm.nih.gov/REST/rxcui/" + rxcui + "/ndcs.json"); //most efficient		
		} catch (IOException e) {
			System.out.println("Unable to finish building SCD for rxcui " + rxcui);
			e.printStackTrace();
		}
		
		if( allRelated != null ) {
//			JSONObject allRelatedGroup = (JSONObject) allRelated.get("allRelatedGroup");
			JSONObject allRelatedGroup = (JSONObject) allRelated.get("relatedGroup");
			JSONArray conceptGroup = (JSONArray) allRelatedGroup.get("conceptGroup");
			for( int i=0; i < conceptGroup.length(); i++ ) {
				JSONObject element = (JSONObject) conceptGroup.get(i);
				String tty = element.getString("tty");
				if( tty.equals("IN") )  {
					JSONArray conceptProperties = null;
					if(element.has("conceptProperties")) {
					conceptProperties = (JSONArray) element.get("conceptProperties");
						if( conceptProperties != null ) {			
							for(int j=0; j < conceptProperties.length(); j++) {
								JSONObject b = (JSONObject) conceptProperties.get(j);
								RxNormIngredient ing = new RxNormIngredient(b);
								vIngredient.add(ing);
							}						
						}
					}
				}
				else if( tty.equals("PIN") )  {
					JSONArray conceptProperties = null;
					if(element.has("conceptProperties")) {
					conceptProperties = (JSONArray) element.get("conceptProperties");
						if( conceptProperties != null ) {			
							for(int j=0; j < conceptProperties.length(); j++) {
								JSONObject b = (JSONObject) conceptProperties.get(j);
								RxNormIngredient ing = new RxNormIngredient(b);
								ing.setPIN(true);
								vIngredient.add(ing);
							}						
						}
					}
				}		
//				else if( tty.equals("MIN") ) {
//					JSONArray conceptProperties = null;
//					if(element.has("conceptProperties")) {
//					conceptProperties = (JSONArray) element.get("conceptProperties");
//						if( conceptProperties != null ) {			
//							for(int j=0; j < conceptProperties.length(); j++) {
//								JSONObject b = (JSONObject) conceptProperties.get(j);
//								RxNormIngredient ing = new RxNormIngredient(b);
//								ing.setIsMIN(true);
//								vIngredient.add(ing);
//							}						
//						}
//					}			
//				}
			}
		}

//		if( rxHistory != null ) {
////			if( rxcui.equals(1801823) ) {
////				System.out.println("stop");
////			}
//			JSONObject rxcuiHistoryConcept = (JSONObject) rxHistory.get("rxcuiHistoryConcept");
//			JSONObject rxcuiConcept = (JSONObject) rxcuiHistoryConcept.get("rxcuiConcept");
//			if( rxcuiConcept.get("status").toString().equals("Active") ) {
//				JSONArray arrBoss = (JSONArray) rxcuiHistoryConcept.get("bossConcept");
//				for(int i=0; i < arrBoss.length(); i++) {
//					JSONObject b = (JSONObject) arrBoss.get(i);
//					RxNormBoss boss = buildBoss(rxcui, b);
//					vBoss.add(boss);
//				}
////				try {
////					this.unitOfPresentationRxCui = new Integer(rxcuiConcept.get("doseformRxcui").toString());
////				}
////				catch( Exception e) {
////					this.unitOfPresentationRxCui = new Integer(this.vDoseForm.elementAt(0).getRxcui());					
////					System.out.println("Check history. Where is the proper presentation for?: " + rxcui.toString());
////				}
////				
////				try {
////					this.unitOfPresentationName = new String(rxcuiConcept.getString("doseform"));
////				}
////				catch( Exception e) {
////					this.unitOfPresentationName = this.vDoseForm.elementAt(0).getName();					
////					System.out.println("Check history. What is the proper presentation name for?: " + rxcui.toString());
////				}
//				try {
//					this.quantityFactor = rxcuiConcept.get("qf").toString();
//				}
//				catch(Exception e) {
//					quantityFactor = "";
//				}
//				try {
//					this.qualitativeDistinction = rxcuiConcept.getString("qd").toString();
//				}
//				catch(Exception e) {
//					//there are too many cases to output a status for this
//					qualitativeDistinction = "";
//				}
//	
//	
//			}
//			else {
//				System.out.println("Inactive status for rxcui, not finding a BoSS: " + rxcui);
//			}
//			
//		}
		if( ndc != null ) {
			JSONObject ndcGroup = (JSONObject) ndc.get("ndcGroup");
			if( !ndcGroup.isNull("ndcList") ) {
				hasNDC = true;
			}
		}
		
		if( humanProperty != null ) {
			JSONObject propConceptGroup = (JSONObject) humanProperty.get("propConceptGroup");
			if( !propConceptGroup.isNull("propConcept") ) {
				if( this.rxcui.equals(Integer.valueOf(1000024)) ) {
					System.out.println("PAUSE");
				}
				if( this.rxcui.equals(Integer.valueOf(1000001)) ) {
					System.out.println("PAUSE");
				}
				JSONArray propConceptArray = (JSONArray) propConceptGroup.get("propConcept");
				boolean vet = false;
				boolean human = false;
				boolean prescribable = false;
//				boolean vaccine = false;
				for(int i=0; i < propConceptArray.length(); i++) {
					JSONObject b = (JSONObject) propConceptArray.get(i);
					if( b.get("propName").toString().equals("HUMAN_DRUG") ) {
						human = true;
					}					
					if( b.get("propName").toString().equals("VET_DRUG") ) {
						vet = true;
					}
					if( b.get("propName").toString().equals("PRESCRIBABLE") ) {
						prescribable = true;
					}
//					if( b.get("propName").toString().equals("CVX")) {
//						vaccine = true;
//					}
				}
				if( vet && !human ) { 
					this.isVetOnly = true;
				}
				if( prescribable ) {
					this.isPrescribable = true;
				}
				if( this.name.toLowerCase().contains("vaccine") ) {
					this.isVaccine = true;
				}
			}
		}		
	}
	
	public RxNormSCD() {
		
	}
	
//	private RxNormBoss buildBoss(Integer rxcui, JSONObject b) {
//		JSONObject result = b;
//		RxNormBoss boss = null;
//		
//		String baserxcuiString = result.get("baseRxcui").toString();
//		String basename = result.get("baseName").toString();
//		String bossrxcuiString = result.get("bossRxcui").toString();
//		String bossname = result.get("bossName").toString();
//		String nvString = result.get("numeratorValue").toString();
//		String nuString = result.get("numeratorUnit").toString();
//		String dvString = result.get("denominatorValue").toString();
//		String duString = result.get("denominatorUnit").toString();	
//		String actIngredRxcuiString = result.get("actIngredRxcui").toString().isEmpty() ? "-1" : result.get("actIngredRxcui").toString(); 
//		String actIngredName = result.get("actIngredName").toString().isEmpty() ? "null" : result.get("actIngredName").toString();  
//		
//		Integer baserxcui = null;
//		if(!baserxcuiString.isEmpty()) {
//			baserxcui = new Integer(baserxcuiString);			
//		}
//		
//		Integer actIngredRxcui = null;
//		if( !actIngredRxcuiString.isEmpty() ) {
//			actIngredRxcui = new Integer(actIngredRxcuiString);
//		}
//		
//		Integer bossrxcui = (!bossrxcuiString.isEmpty()) ? new Integer(bossrxcuiString) : new Integer(-1); 
//		
//		Double nv = (!nvString.isEmpty()) ? Double.valueOf(nvString) : Double.valueOf("1");
//		
//		Double dv = (!dvString.isEmpty()) ? Double.valueOf(dvString) : Double.valueOf("1"); //If the dv is empty, can we assume this is 1?
//		
//		String nu = (!nuString.isEmpty()) ? new String(nuString) : "1";
//
//		// unit of presentation is set later in the program, so this is a bad thing to do
////		String du = (!duString.isEmpty()) ? new String(duString) : this.unitOfPresentationName;
//		String du = (!duString.isEmpty()) ? new String(duString) : "1";
//		
//
//		
//		boss = new RxNormBoss(new Integer(rxcui), baserxcui, basename, bossrxcui, bossname, nv, nu, dv, du, actIngredRxcui, actIngredName, this.name, this.vDoseForm.get(0).getName());
//		
//		return boss; 
//	}
	
	public static JSONObject getresult(String URLtoRead) throws IOException {
		URL url;
		HttpsURLConnection connexion;
		BufferedReader reader;
		
		String line;
		String result="";
		url= new URL(URLtoRead);
	
		connexion= (HttpsURLConnection) url.openConnection();
		connexion.setRequestMethod("GET");
		reader= new BufferedReader(new InputStreamReader(connexion.getInputStream()));	
		while ((line =reader.readLine())!=null) {
			result += line;
			
		}
		
		JSONObject json = new JSONObject(result);
		return json;
	}
	
	public void setBossIssue(String s) {
		this.bossIssue = s;
	}
	
	public String getBossIssue() {
		return this.bossIssue;
	}
	
	public void setAcitveIngIssue(String s) {
		this.activeIngIssue = s;
	}
	
	public String getActiveIngIssue() {
		return this.activeIngIssue;
	}	
	
	public boolean getIsVaccine() {
		return this.isVaccine;
	}
	
	public boolean getIsPrescribable() {
		return this.isPrescribable;
	}
	
	public void setIsPrescribable(boolean b) {
		this.isPrescribable = b;
	}
	
	public Vector<Long> getSnomedCodes() {
		return this.snomedCodes;
	}
	
	public void setIsLiquid(boolean b) {
		this.isLiquid = b;
	}
	
	public boolean getIsLiquid() {
		return this.isLiquid;
	}
	
	public void setSnomedCodes() {
		JSONObject allSnomedCodes = null;
		String cuiString = this.rxcui.toString();
		try {
			allSnomedCodes = getresult("https://rxnavstage.nlm.nih.gov/REST/rxcui/" + cuiString + "/property.json?propName=SNOMEDCT");			
		}
		catch(Exception e) {
			System.out.println("Unable to fetch snomed codes for rxcui: " + cuiString);
		}
		
		if( allSnomedCodes != null ) {
			if( !allSnomedCodes.isNull("propConceptGroup") ) {
				JSONObject propConceptGroup = (JSONObject) allSnomedCodes.get("propConceptGroup");
				JSONArray propConceptArr = (JSONArray) propConceptGroup.get("propConcept");
				for( int i=0; i < propConceptArr.length(); i++ ) {
					JSONObject conceptValue = (JSONObject) propConceptArr.get(i);
					Long codeToAdd = new Long(conceptValue.get("propValue").toString());
					this.snomedCodes.add(codeToAdd);
				}
			}
		}
	}
	
	public Long getManufacturedDoseFormCode() {
		return this.manufacturedDoseFormCode;
	}
	
	public String getManufacturedDoseFormName() {
		return this.manufacturedDoseFormName;
	}
	
	public Long getUnitOfPresentationCode() {
		return this.unitOfPresentationCode;
	}
	
	public String getUnitOfPresentationName() {
		return this.unitOfPresentationName;
	}	

	
	public void setManufacturedDoseFormCode(Long code) {
		this.manufacturedDoseFormCode = code;
	}
	
	public void setManufacturedDoseFormName(String name) {
		this.manufacturedDoseFormName = name;
	}	
	
	public void setUnitOfPresentationCode(Long code) {
		this.unitOfPresentationCode = code;
	}
	
	public void setUnitOfPresentationName(String name) {
		this.unitOfPresentationName = name;
	}
	
	public Integer getCui() {
		return this.rxcui;
	}
	
	public String getName() {
		return this.name;
	}
	
	public boolean hasNDC() {
		return this.hasNDC;   //remember, it's false by default
							  //if not set on instantiation
							  //quit trying to find it elsewhere
	}
	
	public Vector<RxNormIngredient> getRxNormIngredient() {
		return this.vIngredient;
	}
	
	public String getRxNormQuantityFactor() {
		return this.quantityFactor;
	}
	
	public String getRxNormQualitativeDistinction() {
		return this.qualitativeDistinction;
	}
	
	public void addBaseCui(Integer i) {
		this.baseCuis.add(i);
	}
	
	public int getBaseCuiCount() {
		int count = this.baseCuis.size();
		return count;
	}
	
	public boolean isVetOnly() {
		return this.isVetOnly;
	}
	
	public void print() {
		System.out.println(rxcui + ":\t" + name );
		
		System.out.println(" Ingredient(s):");
		for(RxNormIngredient i : vIngredient ) {
			System.out.print("\t" + i.getRxcui() + "\t=> " + i.getName());
			String eol = i.getPIN() ? " (is PIN)\n" : "\n";
			System.out.print(eol);
		}
		
//		System.out.println(" Dose Form(s):");
//		for(RxNormDoseForm d : vDoseForm) {
//			System.out.println("\t" + d.getRxcui() + "\t=> " + d.getName());
//		}
//		
//		System.out.println(" BoSS(es)");
//		for(RxNormBoss b : vBoss) {
//			System.out.println("\t" + b.getBossRxCui().toString() + "\t=> " + b.getBossName());
//			if( b.getNumeratorUnit() != null) {
//				System.out.println("\t\t" + "nu => " + b.getNumeratorUnit());
//			}
//			else {
//				System.out.println("\t\t" + "nu => NOT FOUND");
//			}
//			if( b.getNumeratorValue() != null ) {
//				System.out.println("\t\t" + "nv => " + b.getNumeratorValue().toString());
//			}
//			else {
//				System.out.println("\t\t" + "nv => NOT FOUND");
//			}			
//			if( b.getDenominatorUnit() != null ) {
//				System.out.println("\t\t" + "du => " + b.getDenominatorUnit()); //where this is empty assume 1?
//			}
//			else {
//				System.out.println("\t\t" + "du => NOT FOUND");
//			}
//			if( b.getDenominatorValue() != null ) {
//				System.out.println("\t\t" + "dv => " + b.getDenominatorValue().toString()); //where this is empty assume... ? dose form?
//			}
//			else {
//				System.out.println("\t\t" + "dv => NOT FOUND");
//			}	
//			System.out.println();
//		}
	}
	
}
