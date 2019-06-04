package gov.nih.nlm.mor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.search.EntitySearcher;

import gov.nih.nlm.mor.RxNorm.RxNormIngredient;
import gov.nih.nlm.mor.RxNorm.RxNormSCD;

public class GenerateSnomedRxClassFiles {
	
	OWLOntologyManager man = null;
	OWLOntology ontology = null;
	OWLReasoner reasoner = null;
	OWLReasonerFactory reasonerFactory = null;
	OWLDataFactory factory = null;
	PrintWriter classTreeFile = null;
	PrintWriter drugMembersFile = null;
	HashMap<String, String> roots = new HashMap<String, String>();
	HashMap<OWLClass, ArrayList<Path>> productPaths = new HashMap<OWLClass, ArrayList<Path>>();
	HashMap<Product, ArrayList<OWLClass>> classRootMap = new HashMap<Product, ArrayList<OWLClass>>();
	TreeMap<OWLClass, ArrayList<Path>> classPathMap = new TreeMap<OWLClass, ArrayList<Path>>();
	TreeMap<OWLClass, TreeMap<OWLClass, ArrayList<Path>>> allRootClassPaths = new TreeMap<OWLClass, TreeMap<OWLClass, ArrayList<Path>>>();
	TreeMap<OWLClass, ArrayList<RxNormIngredient>> classToIngredients = new TreeMap<OWLClass, ArrayList<RxNormIngredient>>();

//	HashMap<OWLClass, ArrayList<Pair>> pairs = new HashMap<OWLClass, ArrayList<Pair>>();
	HashMap<Long, ArrayList<RxNormIngredient>> sct2RxIN = new HashMap<Long, ArrayList<RxNormIngredient>>();
	ArrayList<ClassRow> classesRows = new ArrayList<ClassRow>();
	final String namespace = "http://snomed.info/id/";
	String url = "https://rxnavstage.nlm.nih.gov/REST/allconcepts.json?tty=IN";

	
	
	public static void main(String args[]) {
		GenerateSnomedRxClassFiles generate = new GenerateSnomedRxClassFiles();
		if( args.length < 3 ) {
			System.err.println("GenerateSnomedRxClassFiles requires three parameters:");
			System.err.println("\t[SNOMED CT OWL URI] [CLASS TREE FILE TO SAVE] [DRUG MEMBERS FILE TO SAVE]");
			System.exit(-1);
		}
		generate.configure(args[0], args[1], args[2]);
		generate.run();
		generate.cleanup();
	}
	
	public GenerateSnomedRxClassFiles() {
		
	}
	
	public void configure(String owlFile, String classTreeFile, String drugMembersFile) {
		setPrintWriters(classTreeFile, drugMembersFile);
		
		try {
			man = OWLManager.createOWLOntologyManager();			
			ontology = man.loadOntologyFromOntologyDocument(new File(owlFile));
		} catch (OWLOntologyCreationException e1 ) {
			System.out.println("Unable to load the ontology. Check file, Xmx setting, and available resources.");
			e1.printStackTrace();
		}	
		
		//famous last words for a moving target:
		//make this configurable.  Add a properties file describing every
		//root node needed for these files.  At the moment this is just
		//MOA and CHEM (SC)... TC is not in scope right now.
		
		roots.put("MOA", "766779001");
		roots.put("CHEM", "763760008");
		
		LogManager.getLogger("org.semanticweb.elk").setLevel(Level.ERROR);
		
		reasonerFactory = new ElkReasonerFactory();
		reasoner = reasonerFactory.createReasoner(ontology);		
		factory = man.getOWLDataFactory();
		
		System.out.println("*** Discovering inferences for ontology " + owlFile + " ***");
		
		reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
		
		System.out.println("*** Inference complete ***");
		
	}
	
	private void populateIngredientMap() {
		
		System.out.println("Populating the RxNorm Ingredients");
		
		JSONObject allIngredients = null;
		try {
			allIngredients = getresult(url);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if( allIngredients != null ) {
			JSONObject group = null;
			JSONArray minConceptArray = null;		
			
			group = (JSONObject) allIngredients.get("minConceptGroup");
			minConceptArray = (JSONArray) group.get("minConcept");
			int numberIns = minConceptArray.length();
			for(int i = 0; i < minConceptArray.length(); i++ ) {
				if( i % 1000 == 0) {
					System.out.println("Read " + i + " of " + numberIns + " ...");
				}
				JSONObject minConcept = (JSONObject) minConceptArray.get(i);
				
				String cuiString = minConcept.get("rxcui").toString();
				Integer rxcui = new Integer(cuiString);
				String name = minConcept.get("name").toString();
				String type = minConcept.get("tty").toString();
				if( type.equals("IN") || type.equals("PIN") || type.equals("MIN") ) {
					RxNormIngredient rxnormIngredient = new RxNormIngredient(rxcui, name, type);
					rxnormIngredient.setSnomedCodes(rxnormIngredient.getRxcui());
					for(Long id : rxnormIngredient.getSnomedCodes()) {
						if( this.sct2RxIN.containsKey(id) ) {
							ArrayList<RxNormIngredient> list = sct2RxIN.get(id);
							list.add(rxnormIngredient);
							this.sct2RxIN.put(id, list);
						}
						else {
							ArrayList<RxNormIngredient> list = new ArrayList<RxNormIngredient>();
							list.add(rxnormIngredient);
							this.sct2RxIN.put(id, list);							
						}
					}
				}
			}
			System.out.println("All " + numberIns + " RxNorm Ingredients read to memory.");
		}
		
		System.out.println("Populating of RxNorm Ingredients Done.");
		
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
		
	
	public void setPrintWriters(String classFilename, String drugFilename) {
		try {
			classTreeFile = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(classFilename)),StandardCharsets.UTF_8),true);			
		}
		catch(Exception e) {
			System.err.println("Unable to create the classTreeFile.");
			e.printStackTrace();
		}
		
		try {
			drugMembersFile = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(drugFilename)),StandardCharsets.UTF_8),true);			
		}
		catch(Exception e) {
			System.err.println("Unable to create the classTreeFile.");
			e.printStackTrace();
		}		
	}
	
	public void run() {
		
		generateClassTreeFile();
		
		generateDrugMembersFile();
		
	}
	
//	public void addToClassPathMap(OWLClass c, OWLClass root) {
//		Long id = Long.valueOf(getId(c));
//		if( !this.classPathMap.containsKey(id) ) {
//			Set<OWLClass> classesAboveRoot = reasoner.getSuperClasses(root, false).entities().collect(Collectors.toSet());
//			ArrayList<Path> list = getAllPaths(c, classesAboveRoot);
//		}
//		else {
//			
//		}
//		
//		
//	}
	
	public void generateDrugMembersFile() {
		/*
		 * 2.	Drug Members File.  This describes the drug members in the classes. Each line has bar (|) delimited fields.  For example:

			MEDRT|has_pk|N0000000023|Metabolism|2670|Codeine|IN|2670|Codeine|INDIRECT|000003.000022.000023|2670|Codeine|Y|IN
			
			Field Descriptions:
			
			1: Source of Drug information (MEDRT, DAILYMED, VA, ATC, MESH, FDASPL)
			2: Relationship of drug to class - using is_a for SNOMED
			3: Class id
			4: Class name
			5: RxCUI of drug - not the SCD from SNOMED, the IN or PIN
			6: Drug Name (from RxNorm)
			7: Drug TTY - TTY - IN||PIN
			8: Source ID - the SNCT code 
			9: Source Name - the SNCT FSN
			10: Class relation (DIRECT = direct member of class; INDIRECT = member of descendant class)
			11: Class Tree ID - Path to root (reuse previous paths?)
			12: RxCUI of IN/MIN - CUI of the IN.  Will worry about MINs in the future.
			13: Name of IN/MIN
			14: Significant flag (N= not significant; Y= significant – is prescribable and has SCDs)
			15: TTY of Field 12 (IN or MIN)

		 */
		
		populateIngredientMap();
		String _1_source = "SNOMED";
		String _2_relationship = "is_a";
		
		
		for(OWLClass root : this.allRootClassPaths.keySet()) {
			TreeMap<OWLClass, ArrayList<Path>> rootMap = this.allRootClassPaths.get(root);
			for( OWLClass c : rootMap.keySet()) {
				String _3_classId = getId(c);
				String _4_className = getRDFSLabel(c).replace(" (product)", "").replace(" (medicinal product)", "");
				Long classIdLong = Long.valueOf(_3_classId);
				for( Path p : rootMap.get(c)) {					
					if(this.sct2RxIN.containsKey(classIdLong)) {
						String _5_rxCui = null;
						String _6_rxName = null;
						String _7_rxTty = null;
						String _8_sourceId = null;
						String _9_sourceName = null;
						String _10_classRelation = null;
						String _11_classTreeId = null;
						String _12_rxCui = null;
						String _13_inName = null;
						String _14_significant = null;
						String _15_ttyAgain = null;
						
						ArrayList<RxNormIngredient> inList = getChildIngredients(c);
						for( RxNormIngredient in : inList ) {
							_8_sourceId = null;
							_9_sourceName = null;
							
							_5_rxCui = String.valueOf(in.getRxcui());
							_6_rxName = in.getName();
							_7_rxTty = in.getTty();
//							for( Long aLong : in.getSnomedCodes() ) {
//								if( !aLong.equals(classIdLong) && !p.getPath().contains(factory.getOWLClass(namespace, String.valueOf(aLong))) ) {
//									_8_sourceId = String.valueOf(aLong);
//									_9_sourceName = getRDFSLabel(factory.getOWLClass(namespace, _8_sourceId)).replace(" (substance)", "");									
//								}
//							}
							
							//TODO: Make this method   _10_classRelation = getClassRelation(c, in);
//							_10_classRelation = getClassRelation(c, in);
							_11_classTreeId = getClassTreeId(p);
							_12_rxCui = _5_rxCui;
							_13_inName = _6_rxName;
							//TODO: Make this method   _14_significant = getIsSignificant(c, in);
							_14_significant = "Y";
							_15_ttyAgain = _7_rxTty;
							
							this.drugMembersFile.println(_1_source + "|" +
									_2_relationship + "|" +
									_3_classId + "|" +
									_4_className + "|" +
									_5_rxCui + "|" +
									_6_rxName + "|" +
									_7_rxTty + "|" +
									_8_sourceId + "|" +
									_9_sourceName + "|" +
									_10_classRelation + "|" +
									_11_classTreeId + "|" +
									_12_rxCui + "|" +
									_13_inName + "|" +
									_14_significant + "|" +
									_15_ttyAgain
									);	
							this.drugMembersFile.flush();
							
						}					
					}
				}									
			}
		}
	}
	
	public Set<RxNormIngredient> getChildIngredients(OWLClass c) {
		Set<RxNormIngredient> ingredients = new HashSet<>();
		
		Set<OWLClass> productDirectSubClasses = reasoner.subClasses(c, true).filter(x -> classIsMpOrCd(x)).collect(Collectors.toSet());
		Set<OWLClass> productIndirectSubClasses = reasoner.subClasses(c, false).filter(x -> classIsMpOrCd(x)).collect(Collectors.toSet());
		productIndirectSubClasses.removeAll(productDirectSubClasses);
		

		
		for( OWLClass clz : productDirectSubClasses ) {
			if( getRDFSLabel(clz).contains("(medicinal product)") ) {
	
				ArrayList<RxNormIngredient> list = this.sct2RxIN.get(getId(clz));		
				for( RxNormIngredient in : list ) {
					for( Long code : in.getSnomedCodes() ) {
						if( !getId(clz).equals(code) ) {
							RxNormIngredient rxin = in;
							rxin.setDirect(true);
							if( this.classToIngredients.containsKey(c) ) {
								ArrayList<RxNormIngredient> classList = this.classToIngredients.get(c);
								classList.add(rxin);
								this.classToIngredients.put(c, classList);
							}
							else {
								ArrayList<RxNormIngredient> classList = new ArrayList<RxNormIngredient>();
								classList.add(rxin);
								this.classToIngredients.put(c, classList);
							}
						}
					}
				}				
			}
		}
		
		for( OWLClass clz : productIndirectSubClasses ) {
			if( getRDFSLabel(clz).contains("(clinical drug)") ) {
				//Pick-up here: create the SCD object here by passing the long id into the RxNormSCD constructor
				RxNormSCD scd = new RxNormSCD(getId(clz));
			}
		}
		
		
				
		
		return ingredients;
	}
	
	private RxNormSCD getMappedScd(Long code) {
		
		/* https://rxnavstage.nlm.nih.gov/REST/rxcui.json?idtype=SNOMEDCT&id=322709006
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
		
		RxNormSCD scd = new RxNormSCD(code);
		if( scd != null ) {
			
		}
		
		
		return scd;
	}
	
	public String getClassTreeId(Path p) {
		String treeId = "";
		for( int i=0; i < p.getPath().size(); i++ ) {
			treeId = treeId + getId(p.getPath().get(i));
			if( i < (p.getPathSize() - 1) ) {
				treeId = treeId + ".";
			}					
		}
		return treeId;
	}
	
//	public String getClassRelation(OWLClass c, RxNormIngredient i) {
//		String dirOrIn = "";
//		
//		
//	}
	
	public void printClassMap(String root) {
		for(OWLClass c : this.classPathMap.keySet()) {
			for( Path p : this.classPathMap.get(c) ) {
				String parentClassTreeId = new String();
				this.classTreeFile.print(root + "|" + "|"); //nothing ever in field 2
				for( int i=0; i < p.getPath().size(); i++ ) {
					this.classTreeFile.print(getId(p.getPath().get(i)) );
					if( i < (p.getPathSize() - 1 ) ) {
						parentClassTreeId = parentClassTreeId + getId(p.getPath().get(i));
					}
					if( i < (p.getPathSize() - 2) ) {
						parentClassTreeId = parentClassTreeId + ".";
					}										
					if( i < (p.getPathSize() - 1) ) {
						this.classTreeFile.print(".");
					}					
				}
				this.classTreeFile.print("|");
				this.classTreeFile.print(getRDFSLabel(c).replace(" (product)", "").replace(" (medicinal product)", "") + "|" + getId(c) + "|" + getDrugMemberCount(c) + "|" + "0" + "|" + getChildrenCount(c) + "|" + parentClassTreeId + "|" + "0");	
				this.classTreeFile.print("\n");
				this.classTreeFile.flush();
				
				//We need to associate the class to RxNorm Ingredients
				
				
			}
		}
	}
	
	private int getChildrenCount(OWLClass c) {
		int i = 0;
		
		i = (int) reasoner.subClasses(c, true).count();
		
		return i;
	}
	
	private int getDrugMemberCount(OWLClass c) {
		int i = 0;
		//TODO: Bring in the drug member map to get this
		
		return i;
	}
		
	
	public void generateClassTreeFile() {
		
		/*
		 1.	Class Tree File.  This file describes the classes in RxClass Each line has bar (|) delimited fields.  For example:

			VA||010574.029066.029068|CYANIDE ANTIDOTES|AD200|0|9|0|010574.029066|0

			Field Descriptions:
				1: Class Type (VA, ATC1_4, MESHPA, DISEASE, CHEM, MOA, PK, PE, TC, EPC)
				2: Not used
				3: Tree id
				4: Class name - without the semantic tag included in SNCT concepts (e.g., remove parentheticals on Product containing amyl cinnamaldehyde (medicinal product))
				5: Class id
				6: Count of drug members from DailyMed/ATC/MeSH - SNCT count of classes
				7: Count of drug members from MED-RT/VA -  always 0?
				8: Count of children classes - direct descendents
				9: Tree id of parent class
				10: Count of drug members from FDASPL/FMTSME
		 */

		for( String root : roots.keySet()  ) {
			OWLClass rootClass = factory.getOWLClass(namespace, roots.get(root));
			setPaths(rootClass, new Path(rootClass));
			printClassMap(root);
			addMapToMaster(rootClass, this.classPathMap);
			this.classPathMap.clear();
		}
		
	}
	
	public void addMapToMaster(OWLClass root, TreeMap<OWLClass, ArrayList<Path>> map) {
		TreeMap<OWLClass, ArrayList<Path>> tmpMap = new TreeMap<OWLClass, ArrayList<Path>>();
		for( OWLClass c : map.keySet() ) {
			tmpMap.put(c, map.get(c));
		}
		this.allRootClassPaths.put(root, tmpMap);	
	}
	
	private void setPaths(OWLClass c, Path p) {
		Set<OWLClass> directs = reasoner.subClasses(c, true).filter(x -> classIsFine(x)).collect(Collectors.toSet());
		
		for( OWLClass clz : directs ) {
			Path x = new Path(p);
			x.addToPath(clz);
			addPathToMap(clz, x);
			setPaths(clz, x);
//			addPathToMap(c, x);			
		}
		
	}
	
	private void addPathToMap(OWLClass c, Path p) {
		if( this.classPathMap.containsKey(c) ) {
			ArrayList<Path> list = this.classPathMap.get(c);
			list.add(p);
			this.classPathMap.put(c, list);
		}
		else {
			ArrayList<Path> list = new ArrayList<Path>();
			list.add(p);
			this.classPathMap.put(c, list);
		}
	}
	

	public boolean classIsFine(OWLClass c) {
		if( !c.equals(factory.getOWLNothing()) && getRDFSLabel(c) != null )  {
			if( getRDFSLabel(c).contains("(product)") ) return true;
			else return false;
		}
		else return false;
	}
	
	public boolean classIsMpOrCd(OWLClass c) {
		if( !c.equals(factory.getOWLNothing()) && getRDFSLabel(c) != null) {
			if( getRDFSLabel(c).contains("(medicinal product)") || getRDFSLabel(c).contains("(clinical drug)") ) return true;
			else return false;
		}
		else return false;
	}
	
	public String getId(OWLClass c) {
		String id = null;
		if( c != null ) {
			id = c.getIRI().getIRIString().replace(namespace, "");
		}
		return id;		
	}
	
	public String getRDFSLabel(OWLClass cls) {
		if( cls != null ) {
			for (OWLAnnotation a : EntitySearcher.getAnnotations(cls, ontology, factory.getRDFSLabel()).collect(Collectors.toSet())) {
				OWLAnnotationValue val = a.getValue();
				if (val instanceof OWLLiteral) return ((OWLLiteral) val).getLiteral().toString();
				else return val.toString();
			}
		}
		return null;
	}	
	
	public void cleanup() {
		reasoner.dispose();		
		this.classTreeFile.close();
		this.drugMembersFile.close();
	}

}