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
import org.semanticweb.owlapi.reasoner.NodeSet;
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
//	HashMap<Long, ArrayList<Path>> classPathMap = new HashMap<Long, ArrayList<Path>>();
	HashMap<OWLClass, ArrayList<Pair>> pairs = new HashMap<OWLClass, ArrayList<Pair>>();
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
		
		populateIngredientMap();
		
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
	
	public void addToClassPathMap(ArrayList<OWLClass> classList, OWLClass root) {
		
	}
	
	public void getAllPaths(OWLClass c, OWLClass rootClass, Set<OWLClass> classesAboveRoot) {
		Vector<OWLClass> seen = new Vector<OWLClass>();
		reasoner.subClasses(c, false).forEachOrdered(x -> {
			if( !x.equals(factory.getOWLNothing()) && !classesAboveRoot.contains(x) && !seen.contains(x)) {
				seen.add(x);
				getPairs(x, rootClass, classesAboveRoot);
			}
		});
		reasoner.subClasses(c, true).forEachOrdered(x -> {
			if( !x.equals(factory.getOWLNothing())) {
				Pair pair = new Pair(c, x);
				if( pairs.containsKey(c) ) {
					ArrayList<Pair> list = pairs.get(c);
					list.add(pair);
					pairs.put(c, list);
				}
				else {
					ArrayList<Pair> list = new ArrayList<Pair>();
					list.add(pair);
					pairs.put(c, list);
				}
			}
		});	
	}
	
	public void getPairs(OWLClass c, OWLClass root, Set<OWLClass> classesAboveRoot) {
		
		Set<OWLClass> directSuperClasses = reasoner.getSuperClasses(c, true).entities().collect(Collectors.toSet());
		
		directSuperClasses.remove(factory.getOWLThing());
		directSuperClasses.remove(c);
		directSuperClasses.removeAll(classesAboveRoot);

		if( directSuperClasses.size() > 0 ) {
			Iterator<OWLClass> itr = directSuperClasses.iterator();
			while( itr.hasNext() ) {
				OWLClass d = itr.next();
				if( this.pairs.get(c) != null) {
					ArrayList<Pair> list = pairs.get(c);
					Pair pair = new Pair(d, c);
					list.add(pair);
					this.pairs.put(d, list);
				}
				else {
					ArrayList<Pair> list = new ArrayList<Pair>();
					Pair pair = new Pair(d, c);
					list.add(pair);
					this.pairs.put(d, list);
				}
				
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

// This is slippery and probably over engineered.
// The issue is this works from any class and goes to the root.  There
// can be MULTIPLE paths to root, and the reasoner merely returns a NodeSet of
// classes.  This node set has absolutely no bearing on where a path diverges into
// more than one.  So, now we try it from the top and ignore clinical drugs.
//		for( String root : roots.keySet() ) {
//			root = roots.get(root);
//			String field2 = "";
//			OWLClass rootClass = factory.getOWLClass(namespace, root);
//			
//			Set<OWLClass> classesAboveRoot = reasoner.getSuperClasses(rootClass, false).entities().collect(Collectors.toSet());
//			getAllPaths(rootClass, rootClass, classesAboveRoot);
//			
//			for( Pair p : this.pairs.get(rootClass)) {
//				printPaths(p);
//			}
//			
//		}
		
		for( String root : roots.keySet()  ) {
			printThePath(factory.getOWLClass(namespace, roots.get(root)));
		}
	}
	
	public void printThePath(OWLClass c) {
		if( reasoner.subClasses(c, true).filter(x -> classIsFine(c, x) ).count() > 0 ) {
			reasoner.subClasses(c, true).filter(x -> classIsFine(c, x) ).forEach(y -> {
				System.out.print(getRDFSLabel(c) + " -> " + getRDFSLabel(y) + " -> " );
				printThePath(y);
			});
		}
		else {
			System.out.print("\n");
		}
	}	
	
	public boolean classIsFine(OWLClass b, OWLClass c) {
		if( !c.equals(factory.getOWLNothing()) && getRDFSLabel(c) != null && !c.equals(b) && !getRDFSLabel(c).contains("(clinical drug)") ) {
			return true;
		}
		else return false;
	}
	
//	private void printPaths(Pair p) {
//		OWLClass child = p.getChild();
//		System.out.print(getRDFSLabel(child) + " -> ");
//		
//		ArrayList<Pair> list = pairs.get(child);
//		if( list != null ) {
//			for( Pair r : list ) {
//				System.out.println(getRDFSLabel(r.getParent()) + " -> ");
//				printPaths(r);
//			}
//		}
//
//	}

	
	
//	public void setPaths(Path path, OWLClass classForTree, OWLClass root) {
//		if( classForTree.equals(root) ) {
//			path.addToPath(root);
//			if( paths.containsKey(classForTree) ) {
//				ArrayList<Path> pthz = paths.get(classForTree);
//				pthz.add(path);
//				paths.put(classForTree, pthz);
//			}
//			else {
//				ArrayList<Path> pthz = new ArrayList<Path>();
//				pthz.add(path);
//				paths.put(classForTree, pthz);
//			}			
//			return;
//		}
//		reasoner.superClasses(classForTree, true).forEach(x -> {
//			if( x != null && !x.equals(classForTree) && !x.equals(factory.getOWLNothing()) ) {			
//				path.addToPath(x);
//				setPaths(new Path(path), x, root);
//			}			
//		});	
//	}
	
	public void generateDrugMembersFile() {
	}
	
	public String getId(OWLClass c) {
		String id = null;
		if( c != null ) {
			id = c.getIRI().getIRIString().replace(namespace, "");
		}
		return id;		
	}
	
//	public void printPaths() {
//		for( OWLClass key : this.pairs.keySet() ) {
//			if( pairs.get(key) != null ) {
//				ArrayList<OWLClass> list = pairs.get(key);
//				for( int i = list.size() - 1; i > 0; i--) {
//					printPath(key, list.get(i));					
//				}
//			}
//		}		
//	}
//	
//	public void printPath(OWLClass child, OWLClass parent) {
//		System.out.print(getRDFSLabel(parent) + " -> " + getRDFSLabel(child) );
//		if( this.pairs.get(child) != null ) {
//			for( OWLClass c : this.pairs.get(child) ) {
//
//			}
//		}
//		System.out.print("\n");
//	}
	
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
