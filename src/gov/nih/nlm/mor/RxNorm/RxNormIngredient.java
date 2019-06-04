package gov.nih.nlm.mor.RxNorm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Vector;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONArray;
import org.json.JSONObject;

public class RxNormIngredient implements java.io.Serializable {

	private static final long serialVersionUID = 5772459584322752041L;
	private Integer rxcui = null;
	private String name = null;
	private String synonym = null;
	private String tty = null;
	private String language = null;
	private String suppress = null;
	private String umlscui = null;
	private boolean isPIN = false;
	private boolean isMIN = false;
	private boolean isAllergenic = false;
	private boolean isSignificant = false;
	private boolean direct = false;
	private Vector<Long> snomedCodes = new Vector<Long>();
	
	public RxNormIngredient(Integer rxcui, String name, String type) {
		this.rxcui = rxcui;
		this.name = name;
		this.tty = type;		
		switch(type) {
			case "IN":
				break;
			case "PIN":
				this.isPIN = true;
				break;
			case "MIN":
				this.isMIN = true;
				break;
			default:
				break;
		}
	}
	
	public RxNormIngredient(JSONObject b) {
		this.rxcui = new Integer(b.get("rxcui").toString());
		this.name = b.get("name").toString();
		this.synonym = b.get("synonym").toString();
		this.tty = b.get("tty").toString();
		this.language = b.get("language").toString();
		this.suppress = b.get("suppress").toString();
		this.umlscui = b.get("umlscui").toString();
	}
	
	public RxNormIngredient() {
		
	}	
	
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
	
	public void setSnomedCodes(Integer code) {
		JSONObject allSnomedCodes = null;
		String cuiString = code.toString();
		try {
			allSnomedCodes = getresult("https://rxnavstage.nlm.nih.gov/REST/rxcui/" + cuiString + "/property.json?propName=SNOMEDCT");			
		}
		catch(Exception e) {
			System.out.println("Unable to fetch snomed codes for rxcui: " + cuiString);
			e.printStackTrace();
		}
		
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
	
	public Vector<Long> getSnomedCodes() {
		return this.snomedCodes;
	}	
	
	public void print() {
		System.out.println(" Ingredient(s):");
		System.out.println("\t" + this.getRxcui().toString() + " : " + this.getName());
		for( Long sc : this.snomedCodes ) {
			System.out.println("\t\tSnomed Code => " + sc.toString() );
		}
	}
	
	public void setDirect(boolean b) {
		this.direct = b;
	}
	
	public boolean getDirect() {
		return this.direct;
	}
	
	public void setIsSignificant(boolean b) {
		this.isSignificant = b;
	}
	
	public boolean getIsSignificant() {
		return this.isSignificant;
	}
	
	public void setIsMIN(boolean b) {
		this.isMIN = b;
	}
	
	public boolean getIsMIN() {
		return this.isMIN;
	}
	
	public void setAllergenic(boolean b) {
		this.isAllergenic = b;
	}
	
	public boolean getIsAllergenic() {
		return this.isAllergenic;
	}
	
	public void setPIN(boolean b) {
		this.isPIN = b;
	}
	
	public boolean getPIN() {
		return this.isPIN;
	}
	
	public Integer getRxcui() {
		return this.rxcui;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getSynonym() {
		return this.synonym;
	}
	
	public String getTty() {
		return this.tty;
	}
	
	public String getLanguage() {
		return this.language;
	}
	
	public String getSuppress() {
		return this.suppress;
	}
	
	public String getUmlscui() {
		return this.umlscui;
	}

}