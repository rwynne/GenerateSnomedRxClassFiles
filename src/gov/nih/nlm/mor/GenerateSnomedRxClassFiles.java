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
	PrintWriter synonymsFile = null;  //this will be removed when process for querying SNCT RRF DB for Synonyms is solidified
	HashMap<OWLClass, RootClass> roots = new HashMap<OWLClass, RootClass>();
	HashMap<OWLClass, ArrayList<Path>> productPaths = new HashMap<OWLClass, ArrayList<Path>>();
	HashMap<OWLClass, ArrayList<RxNormIngredient>> product2In = new HashMap<OWLClass, ArrayList<RxNormIngredient>>();	
	HashMap<OWLClass, ArrayList<OWLClass>>  product2Mp = new HashMap<OWLClass, ArrayList<OWLClass>>();
	HashMap<OWLClass, Integer> productCountOfDescendants = new HashMap<OWLClass, Integer>();
	TreeMap<OWLClass, ArrayList<Path>> classPathMap = new TreeMap<OWLClass, ArrayList<Path>>();
	HashMap<RootClass, TreeMap<OWLClass, ArrayList<Path>>> allRootClassPaths = new HashMap<RootClass, TreeMap<OWLClass, ArrayList<Path>>>();
	TreeMap<OWLClass, ArrayList<RxNormIngredient>> classToIngredients = new TreeMap<OWLClass, ArrayList<RxNormIngredient>>();
	HashMap<Long, ArrayList<RxNormIngredient>> sct2RxIN = new HashMap<Long, ArrayList<RxNormIngredient>>();
	HashMap<String, HashMap<String, ArrayList<RxNormIngredient>>> p2mp2INs = new HashMap<String, HashMap<String, ArrayList<RxNormIngredient>>>();
	HashMap<OWLClass, HashMap<Long, Set<RxNormIngredient>>> root2mp2RxInMap = new HashMap<OWLClass, HashMap<Long, Set<RxNormIngredient>>>();
	HashMap<OWLClass, ArrayList<DrugMember>> drugMemberMap = new HashMap<OWLClass, ArrayList<DrugMember>>();
	HashMap<OWLClass, ArrayList<OWLClass>> allMps = new HashMap<OWLClass, ArrayList<OWLClass>>();
	HashMap<OWLClass, Integer> mp2Count = new HashMap<OWLClass, Integer>();
	ArrayList<ClassMember> classMemberList = new ArrayList<ClassMember>();
	ArrayList<ClassMember> classMemberListWithCounts = new ArrayList<ClassMember>();
	ArrayList<DrugMember>  drugMemberList = new ArrayList<DrugMember>();

	final String namespace = "http://snomed.info/id/";

	String url = "https://rxnavstage.nlm.nih.gov/REST/allconcepts.json?tty=IN+PIN";



	public static void main(String args[]) throws IOException {
		GenerateSnomedRxClassFiles generate = new GenerateSnomedRxClassFiles();
		if( args.length < 4 ) {
			System.err.println("GenerateSnomedRxClassFiles requires three parameters:");
			System.err.println("\t[SNOMED CT OWL URI] [CLASS TREE FILE TO SAVE] [DRUG MEMBERS FILE TO SAVE] [SYNONYMS FILE TO SAVE]");  //NB: The synonyms will be loaded into a database to pull from
																																        //for now we will use the FSN per OB
			System.exit(-1);
		}
		generate.configure(args[0], args[1], args[2], args[3]);
		generate.run();
		generate.cleanup(args[1]);
	}

	public GenerateSnomedRxClassFiles() {

	}

	public void configure(String owlFile, String classTreeFile, String drugMembersFile, String synonymsFile) {
		setPrintWriters(classTreeFile, drugMembersFile, synonymsFile);

		try {
			man = OWLManager.createOWLOntologyManager();			
			ontology = man.loadOntologyFromOntologyDocument(new File(owlFile));
		} catch (OWLOntologyCreationException e1 ) {
			System.out.println("Unable to load the ontology. Check file, Xmx setting, and available resources.");
			e1.printStackTrace();
		}
		
		LogManager.getLogger("org.semanticweb.elk").setLevel(Level.ERROR);

		reasonerFactory = new ElkReasonerFactory();
		reasoner = reasonerFactory.createReasoner(ontology);		
		factory = man.getOWLDataFactory();		

		//TODO: Make configurable
		//RootClass(OWLClass c, String n, Long code, String rel, String source)
		
		//This contructor will take a substring of 6 characters for the SRC column as it is char(8) - to which we could alter/expand the column that could potentially add unnecessary
		//bloat and storage increase.
		RootClass rootMOA = new RootClass(factory.getOWLClass(namespace, "766779001"), "Disposition", Long.valueOf("766779001"), new String("isa_disposition"), new String("DISPOSITION"), new String("SNOMEDCT") );
		RootClass rootCHEM = new RootClass(factory.getOWLClass(namespace, "763760008"), "Structure", Long.valueOf("763760008"), new String("isa_structure"), new String("STRUCTURE"), new String("SNOMEDCT") );		
		
		roots.put(factory.getOWLClass(namespace, "766779001"), rootMOA);
		roots.put(factory.getOWLClass(namespace, "763760008"), rootCHEM);

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
				if( type.equals("IN") || type.equals("PIN") ) {
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

	public JSONObject getresult(String URLtoRead) throws IOException {
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


	public void setPrintWriters(String classFilename, String drugFilename, String synonymFilename) {
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
			System.err.println("Unable to create the drugMemberFile.");
			e.printStackTrace();
		}
		
		try {
			synonymsFile = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(synonymFilename)),StandardCharsets.UTF_8),true);
		}
		catch(Exception e) {
			System.err.println("Unable to create the synonymsFile.");
			e.printStackTrace();
		}		
		
	}

	public void run() {

		generateClasses();

		generateDrugMembers();
		
		addCountsToClasses();
		
		serialize();

	}
	
	public void serialize() {
		
		for( OWLClass cls : roots.keySet()) {
			RootClass rootClass = roots.get(cls);
			for( ClassMember member : classMemberListWithCounts) {
				if( member.getClassType().equals(rootClass.getClassType())) {
					printClassMember(member);	
				}
			}
		}
		
		for( DrugMember member : drugMemberList ) {
			printDrugMember(member.getSource(), member.getRelationship(), member.getProductForDirect() , member.getIn(), member.getMpProduct(), member.isDirect(), member.getPath());
		}
	}
	
	public void addCountsToClasses() {
	
		classMemberList.stream().filter(x -> x.classType.equals("DISPOS")).forEach(y -> {
			drugMemberList.stream().forEach(z -> {
				if( y.getClassId().equals(getId(z.getProductForDirect())) ) {
					y.setCountDrugMembers(y.getCountDrugMembers() + 1);
				}
			});
			classMemberListWithCounts.add(y);
		});
		
		classMemberList.stream().filter(x -> x.classType.equals("STRUCT")).forEach(y -> {
			drugMemberList.stream().forEach(z -> {
				if( y.getClassId().equals(getId(z.getProductForDirect())) ) {
					y.setCountDrugMembers(y.getCountDrugMembers() + 1);
				}
			});
			classMemberListWithCounts.add(y);
		});		
		
	}

	public void generateDrugMembers() {
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
			9: Source Name - the SNCT MP FSN without parentheticals
			10: Class relation (DIRECT = direct member of class; INDIRECT = member of descendant class)
			11: Class Tree ID - Path to root (reuse previous paths?)
			12: RxCUI of IN/MIN - CUI of the IN.  Will worry about MINs in the future.
			13: Name of IN/MIN
			14: Significant flag (N= not significant; Y= significant – is prescribable and has SCDs)
			15: TTY of Field 12 (IN or MIN)

		 */

		populateIngredientMap();

		for(RootClass root : this.allRootClassPaths.keySet()) {
			
			// OWLClass rootClass = root.getRootClass();
			String _1_source = root.getSource();
			String _2_relationship = root.getRela();				

			TreeMap<OWLClass, ArrayList<Path>> rootMap = this.allRootClassPaths.get(root);

			for( OWLClass c : rootMap.keySet()) {

				//				String _3_classId = getId(c);
				//				String _4_className = getRDFSLabel(c).replace(" (product)", "");
				//				String _5_rxCui = null;
				//				String _6_rxName = null;
				//				String _7_rxTty = null;
				//				String _8_sourceId = null;      //MP id
				//				String _9_sourceName = null;    //MP name
				//				String _10_classRelation = null;
				//				String _11_classTreeId = null;  //Path
				//				String _12_rxCui = null;        //normalized PIN cui
				//				String _13_inName = null;       //normalized PIN name
				//				String _14_significant = null;  //always Y per Lee
				//				String _15_ttyAgain = null;		//always IN

				for( Path p : rootMap.get(c)) {
					ArrayList<OWLClass> path = p.getPath();
					ArrayList<OWLClass> order = new ArrayList<OWLClass>();					
					Set<OWLClass> mps = getMpsForLastProduct(path.get(path.size()-1));
					
					for(int i=0; i < path.size(); i++) {
						OWLClass cls = path.get(i);
						if( cls !=null && getRDFSLabel(cls).contains("(product)") ) {
							order.add(0, cls);
						}
					}
					
					//add directs
					OWLClass productForDirect = order.remove(0);					
					for( OWLClass mp : mps ) {
						RxNormIngredient in = null;
						if( this.sct2RxIN.containsKey(Long.valueOf(getId(mp))) ) {
							in = sct2RxIN.get(Long.valueOf(getId(mp))).get(0);
							if( in != null ) {
								DrugMember drugMember = new DrugMember(_1_source, _2_relationship, productForDirect, in, mp, true, p);
								if( !drugMemberList.contains(drugMember) ) {
									drugMemberList.add(drugMember);
								}
//								printDrugMember(_1_source, _2_relationship, productForDirect, in, mp, true, p );
							}
						}						
					}
					
					//Include the root for explicit indirects
					while(order.size() > 0) {
						RxNormIngredient in = null;
						OWLClass productForIndirect = order.remove(0);
							for( OWLClass mp : mps ) {
								if( this.sct2RxIN.containsKey(Long.valueOf(getId(mp))) ) {
									in = sct2RxIN.get(Long.valueOf(getId(mp))).get(0);
									if( in != null ) {
										DrugMember drugMember = new DrugMember(_1_source, _2_relationship, productForIndirect, in, mp, false, p);
										if( !drugMemberList.contains(drugMember) ) {
											drugMemberList.add(drugMember);
										}
//										printDrugMember(_1_source, _2_relationship, productForIndirect, in, mp, false, p );
									}
								}																				
							}
					}

				}
			}
		}
	}
	
	private void printClassMember(ClassMember c) {
				
					String _1_classType = c.getClassType();
					String _2_notUsed = c.getField2();
					String _3_treeId = c.getTreeId();
					String _4_className = c.getClassName().replace(" (product)", "");
					String _5_classId = c.getClassId();
					String _6_countFromDailyMed = String.valueOf(c.getCountDrugMembers());  //we think this can be hi-jacked for SNOMED counts
					String _7_countFromMedrt = c.getCountFromMedrt();
					String _8_countOfChildren = c.getCountChildren();  //classes in the hierarchy structure that are direct product descendants
					String _9_treeIdOfParent = c.getTreeIdOfParent();
					String _10_countFromSPLSME = "0"; //add to model?
					
					
					this.classTreeFile.println(String.join(
						"|", _1_classType, _2_notUsed, _3_treeId, _4_className,
						_5_classId, _6_countFromDailyMed, _7_countFromMedrt,
						_8_countOfChildren, _9_treeIdOfParent, _10_countFromSPLSME)
					);
					this.classTreeFile.flush();
					
					printSynonym(_1_classType, _5_classId, _4_className);

	}

		
	private void printDrugMember(String source, String rela, OWLClass product, RxNormIngredient in, OWLClass mp, boolean direct, Path p) {
		
//		incrementClassMember(product);
		
		String[] normalizedIn = normalizeIngredient(in.getRxcui());
		String _1_source = source;
		String _2_relationship = rela;
		String _3_classId = getId(product);
		String _4_className = getRDFSLabel(product).replace(" (product)", "");
		String _5_rxCui = String.valueOf(in.getRxcui());
		String _6_rxName = in.getName();
		String _7_rxTty = in.getTty();
		String _8_sourceId = String.valueOf(getId(mp));      //MP id
		String _9_sourceName = getRDFSLabel(mp).replace(" (medicinal product)", "");    //MP name
		String _10_classRelation = direct ? "DIRECT" : "INDIRECT";
		String _11_classTreeId = getClassTreeId(p);  //Path
		String _12_rxCui = normalizedIn[1];        //normalized PIN cui
		String _13_inName = normalizedIn[0];       //normalized PIN name
		String _14_significant = "Y";  //always Y per Lee
		String _15_ttyAgain = "IN";		//always IN
		
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
		
		if( _2_relationship.contains("disposition") ) { 
			printSynonym("DISPOS", _8_sourceId, _9_sourceName);
		}
		else {
			printSynonym("STRUCT", _8_sourceId, _9_sourceName); 
		}
		
	}
	
	private void printSynonym(String type, String id, String name) {
		synonymsFile.println(type + "|" + name + "|" + id + "|" + name);
		synonymsFile.flush();
	}
	
	private Set<OWLClass> getMpsForLastProduct(OWLClass c) {
		return reasoner.subClasses(c, true).filter(x -> classIsMp(x)).collect(Collectors.toSet());
	}
	
//	private void incrementClassMember(OWLClass product) {
//		String id = getId(product);
//		classMemberList.stream().filter(x -> x.getClassId().equals(id)).forEach(y -> {
//			int count = y.getCountDrugMembers();
//			y.setCountDrugMembers(++count);
//		});
//	}
	
	
	
	public HashMap<Long, Set<RxNormIngredient>> getClassTypesAndIns(OWLClass c) {
		// Get the direct MPs, and nothing else
		
		
		HashMap<Long, Set<RxNormIngredient>> ingredients = new HashMap<Long, Set<RxNormIngredient>>();
		
		Set<OWLClass> productDirectSubClasses = reasoner.subClasses(c, true).filter(x -> classIsMp(x)).collect(Collectors.toSet());
//		productDirectSubClasses.remove(c);
		productDirectSubClasses.remove(factory.getOWLNothing());
		productDirectSubClasses.remove(factory.getOWLThing()); //pointless, trying to avoid NPE 

		
		for( OWLClass directMpClass : productDirectSubClasses ) {
			Set<RxNormIngredient> inSet = new HashSet<>();
			Long directMpClassCode = Long.valueOf(getId(directMpClass));
			ArrayList<RxNormIngredient> list = this.sct2RxIN.get(Long.valueOf(getId(directMpClass)));
			
			if( list != null ) {
				for( RxNormIngredient in : list ) {
					for( Long code : in.getSnomedCodes() ) {
						if( directMpClassCode.equals(Long.valueOf(code)) ) {
							
						
							//for debugging
							// String mpCodeString = String.valueOf(getId(directMpClass));							
							//OWLClass mpClass = factory.getOWLClass(namespace, mpCodeString);
							//String mpName = getRDFSLabel(mpClass);
							
							in.setDirect(true);
							
							if( ingredients.containsKey(directMpClassCode) ) {
								inSet = ingredients.get(directMpClassCode);
								if( !inSet.contains(in) ) {
									inSet.add(in);
								}
							}
							else {
								inSet.add(in);
							}
						}
					}
				}				
				
			}
			else {
				//we have no corresponding IN to the snomed code
			}
		
			ingredients.put(directMpClassCode, inSet);

		}
		
		
		return ingredients;
		
	}	
	
	public Set<RxNormIngredient> getIndirects(OWLClass root, Set<OWLClass> productIndirectMps, Set<RxNormIngredient> indirectIngredients) {
		//this is tough.  Instead of going down the tree, OB suggests going up.  So, in addition to
		//the set of all indirect MPs, we also need to provide some sort of stopping point as to not
		//slurp in additional indirects above the Product.  This is also to prevent us from gathering
		//owl:Thing.
		
		//we need these indirect sorted, as the reasoner does not take care of this for us.
		//what this means is we need to form a path upwards, instead of down, so we can gradually
		//collect the indirects, and not to overstep something that is direct (that will be taken care
		//of on the return value from the calling method.
		
		for(OWLClass indirectMpClass : productIndirectMps ) {
			
			//An MP can have an MPo, MPF, or even a CD as its subClass.  So, trying to figure out if we are
			//at the last MP in the subgraph will be difficult.  We need to traverse down until we get to the last MP.
			//We can do this by restricting on all MPs
			Set<OWLClass> indirectMpChildren = reasoner.subClasses(indirectMpClass, false).filter(x -> classIsProductOrMp(x)).collect(Collectors.toSet());
			for( OWLClass indirectMpChild : indirectMpChildren ) {
				//So, how do I know where I am in this set of MPs, and where is the lowest MP amongest.
				//Once we know this, we can recurse upwards.
				if( isaBottomMp(indirectMpChild) ) {
					//work way up, get all indirects
					indirectIngredients = getAllIndirectsFromBottomMp(indirectMpChild, indirectIngredients, root);
					break; //we are done
				}		
			}
			
		}
		
		return indirectIngredients;
		
	}
	
	private Set<RxNormIngredient> getAllIndirectsFromBottomMp(OWLClass c, Set<RxNormIngredient> indirectIngredients, OWLClass root) {
		Set<OWLClass> directSuperClasses = reasoner.superClasses(c, true).filter(x -> classIsMpAndNotRoot(x, root)).collect(Collectors.toSet());
		for( OWLClass dc : directSuperClasses) {
			Set<RxNormIngredient> tmpSet = getIngredients(dc, false);
			indirectIngredients.addAll(tmpSet);
			getAllIndirectsFromBottomMp(dc, indirectIngredients, root);
		}
		
		return indirectIngredients;
	}
	
	private Set<RxNormIngredient> getIngredients(OWLClass c, boolean isDirect) {
		Set<RxNormIngredient> ingredientSet = new HashSet<>();
		ArrayList<RxNormIngredient> list = this.sct2RxIN.get(Long.valueOf(getId(c)));
		if( list != null ) {
			for( RxNormIngredient i : list ) {
				for(Long code : i.getSnomedCodes() ) {
					OWLClass snomedClass = factory.getOWLClass(namespace, String.valueOf(code));
					if( getRDFSLabel(snomedClass) != null && getRDFSLabel(snomedClass).contains(" (substance)")) {
						i.setDirect(isDirect);
						ingredientSet.add(i);
					}
				}
			}
		}
		return ingredientSet;
	}
	
	private boolean isaBottomMp(OWLClass mp) {
		Set<OWLClass> descendingMps = reasoner.subClasses(mp, false).filter(x -> classIsMp(x)).collect(Collectors.toSet());
		descendingMps.remove(mp);
		if( descendingMps.size() == 0 ) {
			return true;
		}
		return false;
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
	
	public void setClassList(RootClass root) {
		//print root
		// MOA||000223|Mechanism of Action (MoA)|N0000000223|821|4309|8||836	
		ClassMember classRootMember = new ClassMember();

		classRootMember.setClassType(root.getClassType());
		classRootMember.setTreeId(String.valueOf(root.getRootCode()));
		classRootMember.setClassId(String.valueOf(root.getRootCode()));
		classRootMember.setClassName(root.getRootName());
		classRootMember.setCountChildren(getProductChildrenCount(root.getRootClass()));
		
	
		classMemberList.add(classRootMember);
		
//		this.classTreeFile.println(root.getClassType() + "|" + "|" + root.getRootCode() + "|" + root.getRootName() + "|" +  root.getRootCode() + "||0||" + "|0"); //post-processing, populate 6 with the number of unique mps
		for(OWLClass c : this.classPathMap.keySet()) {		
			for( Path p : this.classPathMap.get(c) ) {

				//this.classTreeFile.print(getRDFSLabel(c).replace(" (product)", "").replace(" (medicinal product)", "") + "|" + getId(c) + "|" + getDrugMemberCount(c) + "|" + "0" + "|" + getChildrenCount(c) + "|" + parentClassTreeId + 	
				String parentClassTreeId = new String();
				String treeId = new String();				
				for( int i=0; i < p.getPath().size(); i++ ) {
					treeId = treeId + getId(p.getPath().get(i));
					if( i < (p.getPathSize() - 1 ) ) {
						parentClassTreeId = parentClassTreeId + getId(p.getPath().get(i));
					}
					if( i < (p.getPathSize() - 2) ) {
						parentClassTreeId = parentClassTreeId + ".";
					}										
					if( i < (p.getPathSize() - 1) ) {
						treeId = treeId + ".";
					}

				}
				
				ClassMember classMember = new ClassMember();
				classMember.setClassId(getId(c));
				classMember.setTreeId(treeId);
				classMember.setClassName(getRDFSLabel(c));
				classMember.setCountDrugMembers(0); //populate during drug members
				classMember.setClassType(root.getClassType());
				classMember.setCountChildren(getProductChildrenCount(c));
				classMember.setTreeIdOfParent(parentClassTreeId);
				
				classMemberList.add(classMember);
				
			}
		}
	}
	
	private String[] normalizeIngredient(Integer cuiFromPIN) {
		String[] returnArr = new String[2];

		String allRelated = "https://rxnavstage.nlm.nih.gov/REST/rxcui/" + cuiFromPIN + "/related.json?tty=IN";
		JSONObject result = null;
		try {
			result = this.getresult(allRelated);
		} catch (IOException e) {
			e.printStackTrace();
		}

		if( result != null ) {
			JSONObject relatedGroup = null;
			if( !result.isNull("relatedGroup")) {
				relatedGroup = (JSONObject) result.get("relatedGroup");
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
//									RxNormIngredient ingToAdd = null;
//									if( !conceptPropertiesVal.isNull("rxcui") ) {
//										ingCui = conceptPropertiesVal.getString("rxcui");
//									}
									if( !conceptPropertiesVal.isNull("name") ) {
										returnArr[0] = conceptPropertiesVal.getString("name");
									}
									if( !conceptPropertiesVal.isNull("rxcui") ) {
										returnArr[1] = conceptPropertiesVal.getString("rxcui");
									}
//									if( ingCui != null && ingName != null ) {
//										ingToAdd = new RxNormIngredient(Integer.valueOf(ingCui), ingName, tty);
//										if( !this.vIngredient.contains(ingToAdd) ) {
//											this.vIngredient.add(ingToAdd);
//										}
//									}
								}
							}
						}
						
					}
				}
			}
		}
		
		if( returnArr[0] == null || returnArr[1] == null) {
			System.err.println("Could not find the IN for PIN: " + cuiFromPIN);
			return null;
		}
		else {
			return returnArr;
		}
		
	}
	
	private String getProductChildrenCount(OWLClass c) {
		int i = 0;
		
		i = (int) reasoner.subClasses(c, true).filter(x -> getRDFSLabel(x) != null && getRDFSLabel(x).contains("(product)")).count();
		
		return String.valueOf(i);
	}
	
	private int getDrugMemberCount(OWLClass c) {
		int i = 0;
		//TODO: Bring in the drug member map to get this
		
		return i;
	}
		
	
	public void generateClasses() {
		
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

		for( OWLClass root : roots.keySet()  ) {
			RootClass rClass = roots.get(root);
			ArrayList<OWLClass> list = new ArrayList<OWLClass>();
			list.add(root);
//			list.add(rClass.getRootClass());
			setPaths(rClass.getRootClass(), new Path(list));
			setClassList(rClass);
			addMapToMaster(rClass.getRootClass(), this.classPathMap);
			this.classPathMap.clear();
		}
		
	}
	
	public void addMapToMaster(OWLClass root, TreeMap<OWLClass, ArrayList<Path>> map) {
		RootClass rootClass = null;
		if(roots.containsKey(root)) {
			rootClass = roots.get(root);
		}
		else {
			System.out.println("Root Class " + factory.getOWLClass(namespace, getId(root)) + "(" + getRDFSLabel(factory.getOWLClass(namespace, getId(root))) + ")" );
			return;
		}
		TreeMap<OWLClass, ArrayList<Path>> tmpMap = new TreeMap<OWLClass, ArrayList<Path>>();
		for( OWLClass c : map.keySet() ) {
			tmpMap.put(c, map.get(c));
		}
		this.allRootClassPaths.put(rootClass, tmpMap);	
	}
	
	//recursive method to set all possible paths
	private void setPaths(OWLClass c, Path p) {
		Set<OWLClass> directs = reasoner.subClasses(c, true).filter(x -> classIsProduct(x)).collect(Collectors.toSet());
		
		for( OWLClass clz : directs ) {
			Path x = new Path(p);
			x.addToPath(clz);
			addPathToMap(clz, x);
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
			if( getRDFSLabel(c).contains("(product)") || getRDFSLabel(c).contains("(medicinal product)") ) return true;
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
	
	public boolean classIsMp(OWLClass c) {
		if( !c.equals(factory.getOWLNothing()) && getRDFSLabel(c) != null) {
			if( getRDFSLabel(c).contains("(medicinal product)") ) return true;
			else return false;
		}
		else return false;
	}
	
	public boolean classIsProduct(OWLClass c) {
		if( !c.equals(factory.getOWLNothing()) && getRDFSLabel(c) != null) {
			if( getRDFSLabel(c).contains("(product)")) return true;
			else return false;
		}
		else return false;
	}	
	
	
	public boolean classIsProductOrMp(OWLClass c) {
		if( !c.equals(factory.getOWLNothing()) && getRDFSLabel(c) != null) {
			if( getRDFSLabel(c).contains("(medicinal product)") || 
				getRDFSLabel(c).contains("(product)")) return true;
			else return false;
		}
		else return false;
	}
	
	public boolean classIsMpAndNotRoot(OWLClass mp, OWLClass root) {
		if( getRDFSLabel(mp) != null && !mp.equals(root) && getRDFSLabel(mp).contains(" (medicinal product)") ) {
			return true;
		}
		return false;
	}
	
	public boolean classIsMpAndNothing(OWLClass c) {
		if( getRDFSLabel(c) != null) {
			if( getRDFSLabel(c).contains("(medicinal product)") ) return true;
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
	
	public void cleanup(String classFile) throws IOException {
		this.classTreeFile.close();
		this.drugMembersFile.close();
		//post processing currently not necessary	
		//postProcess(classFile);		
		reasoner.dispose();		
	}
	
	//I'm only leaving in this crazy, unused method for any 
	//serializations needing modification in the future.  
	//This can be achieved using system calls, as shown below.
	//This nonsense is resolved with the current model.  Anything
	//requiring model changes could be performed "in post" here.
//	public void postProcess(String classFile) throws IOException {
//
//		ArrayList<OWLClass> uniqueMps = new ArrayList<OWLClass>();
//		for( OWLClass c : this.roots.keySet() ) {
//			ArrayList<OWLClass> allMps = new ArrayList<OWLClass>();
//			if( this.allMps.containsKey(c)) {
//				allMps = this.allMps.get(c);
//			}
//			else {
//				System.out.println("DEBUG: Absent key " + getRDFSLabel(c)); 
//			}
//			ArrayList<OWLClass> allMpsCopy = allMps;
//
//			for( OWLClass mp : allMps ) {
//				if( !allMps.contains(mp) ) {
//					allMpsCopy.add(mp);
//				}
//			}
//			Integer count = 0;			
//			for( OWLClass mpClass : allMpsCopy ) {
//				if( mp2Count.containsKey(mpClass) ) this.mp2Count.put(mpClass, ++count);
//			}
//			
//			PrintWriter perlConfig = null;
//			try {
//				 perlConfig = new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File("configForPost.txt")),StandardCharsets.UTF_8),true);			
//			}
//			catch(Exception e) {
//				System.err.println("Unable to create the classTreeFile.");
//				e.printStackTrace();
//			}
//		
//			perlConfig.println("Unique ID\tCount\tTruncated Name");
//			
//			Runtime.getRuntime().exec("cp classTreeFile.txt classTreeFileTemp.txt");			
//			
//			
//			for( OWLClass uniqueMp : allMpsCopy ) {
//				perlConfig.println(getId(uniqueMp) + "\t" + getId(c) + "\t" + String.valueOf(this.mp2Count.get(uniqueMp)) + "\t" + getRDFSLabel(uniqueMp).replace(" (product)", "").replace(" (medicinal product)", "") );
//				try {
//					System.out.println("grep \""  + getId(uniqueMp) + "\" classTreeFile.txt" + " | grep " + getId(c)  + " | " + "awk -v count=" + String.valueOf(this.mp2Count.get(uniqueMp)) + " -v mp=\"" + getRDFSLabel(uniqueMp).replace(" (product)", "").replace(" (medicinal product)", "")  + "\" -f replace_drugmember_count_in_class_file.awk >> " + "classTreeFileWithCounts.txt");
//					Runtime.getRuntime().exec("grep \""  + getId(uniqueMp) + "\" classTreeFile.txt" + " | grep " + getId(c)  + " | " + "awk -v count=" + String.valueOf(this.mp2Count.get(uniqueMp)) + " -v mp=\"" + getRDFSLabel(uniqueMp).replace(" (product)", "").replace(" (medicinal product)", "")  + "\" -f replace_drugmember_count_in_class_file.awk >> " + "classTreeFileWithCounts.txt");
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					System.err.println("TOTAL PROBS WITH: " + uniqueMp);
//					System.err.println("Is awk and grep installed on this OS?");
//					e.printStackTrace();
//				}
//			}
//
//			uniqueMps.clear();
//		}
//	}

}