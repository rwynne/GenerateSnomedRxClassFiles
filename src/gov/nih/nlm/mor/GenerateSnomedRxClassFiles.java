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
import java.util.Iterator;
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
//		roots.put("CHEM", "763760008");
		
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
			for(int i = 0; i < minConceptArray.length(); i++ ) {
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
		
		printClassMap();
		
//		populateIngredientMap();
		
//		generateDrugMembersFile();
		
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
	
	public void printClassMap() {
		for(OWLClass c : this.classPathMap.keySet()) {
			for( Path p : this.classPathMap.get(c) ) {
				for( int i=0; i < p.getPath().size(); i++ ) {
					this.classTreeFile.print(getRDFSLabel(p.getPath().get(i)) + ".");
				}
				this.classTreeFile.print("\n");
				this.classTreeFile.flush();
			}
		}
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
		}
	}
	
	private void setPaths(OWLClass c, Path p) {
		Set<OWLClass> directs = reasoner.subClasses(c, true).filter(x -> classIsFine(x)).collect(Collectors.toSet());
		
		for( OWLClass clz : directs ) {
			Path x = new Path(p);
			x.addToPath(clz);
			addPathToMap(c, x);
			setPaths(clz, x);
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
			if( getRDFSLabel(c).contains("(product)") || getRDFSLabel(c).contains("(medicinal product)")) return true;
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
