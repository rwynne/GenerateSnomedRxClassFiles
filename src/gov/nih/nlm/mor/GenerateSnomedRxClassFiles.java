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
	HashMap<String, String> roots = new HashMap<String, String>();
	HashMap<OWLClass, ArrayList<Path>> productPaths = new HashMap<OWLClass, ArrayList<Path>>();
	HashMap<OWLClass, ArrayList<OWLClass>>  product2Mp = new HashMap<OWLClass, ArrayList<OWLClass>>();
	HashMap<OWLClass, Integer> productCountOfDescendants = new HashMap<OWLClass, Integer>();
	TreeMap<OWLClass, ArrayList<Path>> classPathMap = new TreeMap<OWLClass, ArrayList<Path>>();
	TreeMap<OWLClass, TreeMap<OWLClass, ArrayList<Path>>> allRootClassPaths = new TreeMap<OWLClass, TreeMap<OWLClass, ArrayList<Path>>>();
	TreeMap<OWLClass, ArrayList<RxNormIngredient>> classToIngredients = new TreeMap<OWLClass, ArrayList<RxNormIngredient>>();
	HashMap<Long, ArrayList<RxNormIngredient>> sct2RxIN = new HashMap<Long, ArrayList<RxNormIngredient>>();
	HashMap<String, HashMap<String, ArrayList<RxNormIngredient>>> p2mp2INs = new HashMap<String, HashMap<String, ArrayList<RxNormIngredient>>>();
	HashMap<OWLClass, HashMap<Long, Set<RxNormIngredient>>> root2mp2RxInMap = new HashMap<OWLClass, HashMap<Long, Set<RxNormIngredient>>>();
	HashMap<OWLClass, ArrayList<DrugMember>> drugMemberMap = new HashMap<OWLClass, ArrayList<DrugMember>>();

	final String namespace = "http://snomed.info/id/";

	String url = "https://rxnavstage.nlm.nih.gov/REST/allconcepts.json?tty=IN+PIN";



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
			9: Source Name - the SNCT MP FSN without parentheticals
			10: Class relation (DIRECT = direct member of class; INDIRECT = member of descendant class)
			11: Class Tree ID - Path to root (reuse previous paths?)
			12: RxCUI of IN/MIN - CUI of the IN.  Will worry about MINs in the future.
			13: Name of IN/MIN
			14: Significant flag (N= not significant; Y= significant – is prescribable and has SCDs)
			15: TTY of Field 12 (IN or MIN)

		 */

		populateIngredientMap();

		for(OWLClass root : this.allRootClassPaths.keySet()) {

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
					Integer integer = new Integer(0);
					ArrayList<OWLClass> path = p.getPath(); 
					HashMap<Long, Set<RxNormIngredient>> mp2RxInMap = getMpsAndIns(c);

					integer = mp2RxInMap.keySet().size();
					//					this.productCountOfDescendants.put(factory.getOWLClass(namespace, String.valueOf(getId(c))), integer);

					for( Long snctCodeOfMp : mp2RxInMap.keySet() ) {

						Set<RxNormIngredient> ins = mp2RxInMap.get(snctCodeOfMp);
						Set<RxNormIngredient> newIns = new HashSet<>();
						OWLClass snctClassOfMp = factory.getOWLClass(namespace, String.valueOf(snctCodeOfMp));
						setDrugMemberMap(c, snctClassOfMp, newIns, p);
						
						for( RxNormIngredient in : ins ) {					
							RxNormIngredient modIn = in;
							for( int j=path.size()-1; j != 0; j--) {
								setProduct2MpMap(path.get(j), snctClassOfMp);
								if( j == p.getPathSize()-1 ) {
									OWLClass direct = path.get(j);
									modIn.setDirect(true);
									if(!newIns.contains(modIn)) {
										newIns.add(modIn);
									}
//									if( classToIngredients.containsKey(direct)) {
//										ArrayList<RxNormIngredient> list = classToIngredients.get(direct);
//										list.add(modIn);
//										this.classToIngredients.put(direct, list);
//									}
//									else {
//										ArrayList<RxNormIngredient> list = new ArrayList<RxNormIngredient>();
//										list.add(modIn);
//										this.classToIngredients.put(direct, list);
//									}
								}
								else {
									OWLClass inDirect = path.get(j);
									modIn.setDirect(false);
									if(!newIns.contains(modIn)) {
										newIns.add(modIn);
									}									
//									if( classToIngredients.containsKey(inDirect) ) {
//										ArrayList<RxNormIngredient> list = classToIngredients.get(inDirect);
//										if( !in.getDirect() ) {
//											list.add(modIn);											
//										}
//										else {
//											//do nothing, a direct IN trumps an indirect IN
//										}
//
//									}
//									else {
//										ArrayList<RxNormIngredient> list = new ArrayList<RxNormIngredient>();
//										list.add(modIn);
//										this.classToIngredients.put(inDirect, list);
//									}
								}
							}
							setDrugMemberMap(c, snctClassOfMp, newIns, p);

						}
//						for( Long a : mp2RxInMap.keySet() ) {
//							Set<RxNormIngredient> copySet = mp2RxInMap.get(a);
//							Set<RxNormIngredient> newSet = new HashSet<>();
//							HashMap<Long, Set<RxNormIngredient>> map = new HashMap<Long, Set<RxNormIngredient>>();
//							for(RxNormIngredient r : copySet ) {
//								newSet.add(r);
//							}
//							map.put(a, newSet);
//							this.root2mp2RxInMap.put(c, map);
//						}						
					}
				}
			}
		}
		
	
		String _1_source = "SNOMED";
		String _2_relationship = "is_a";

			for( OWLClass c : drugMemberMap.keySet() ) {
				for( DrugMember dm : drugMemberMap.get(c) ) {
					Set<RxNormIngredient> ins = dm.getIngredients();
					
					String _3_classId = null;
					String _4_className = null;
					String _5_rxCui = null;
					String _6_rxName = null;
					String _7_rxTty = null;
					String _8_sourceId = null;      //MP id
					String _9_sourceName = null;    //MP name
					String _10_classRelation = null;
					String _11_classTreeId = null;  //Path
					String _12_rxCui = null;        //normalized PIN cui
					String _13_inName = null;       //normalized PIN name
					String _14_significant = "Y";  //always Y per Lee
					String _15_ttyAgain = "IN";		//always IN
					
					if( !ins.isEmpty() ) {
						
					for(RxNormIngredient in : ins ) {
						_3_classId = null;
						_4_className = null;
						_5_rxCui = null;
						_6_rxName = null;
						_7_rxTty = null;
						_8_sourceId = null;      //MP id
						_9_sourceName = null;    //MP name
						_10_classRelation = null;
						_11_classTreeId = null;  //Path
						_12_rxCui = null;        //normalized PIN cui
						_13_inName = null;       //normalized PIN name
						_14_significant = "Y";  //always Y per Lee
						_15_ttyAgain = "IN";		//always IN	
						
						String[] normalizedIn = normalizeIngredient(in.getRxcui());
						_3_classId = getId(dm.getProduct());
						_4_className = getRDFSLabel(dm.getProduct()).replace(" (product)", "");
						_5_rxCui = String.valueOf(in.getRxcui());
						_6_rxName = in.getName();
						_7_rxTty = in.getTty();
						_8_sourceId = String.valueOf(getId(dm.getMP()));      //MP id
						_9_sourceName = getRDFSLabel(dm.getMP()).replace(" (medicinal product)", "");    //MP name
						_10_classRelation = in.getDirect() ? "DIRECT" : "INDIRECT";
						_11_classTreeId = getClassTreeId(dm.getPath());  //Path
						_12_rxCui = normalizedIn[1];        //normalized PIN cui
						_13_inName = normalizedIn[0];       //normalized PIN name
						_14_significant = "Y";  //always Y per Lee
						_15_ttyAgain = "IN";		//always IN
					}
					}
					else {  //we don't have the asserted official mapping
						_3_classId = null;
						_4_className = null;
						_5_rxCui = null;
						_6_rxName = null;
						_7_rxTty = null;
						_8_sourceId = null;      //MP id
						_9_sourceName = null;    //MP name
						_10_classRelation = null;
						_11_classTreeId = null;  //Path
						_12_rxCui = null;        //normalized PIN cui
						_13_inName = null;       //normalized PIN name
						_14_significant = "Y";  //always Y per Lee
						_15_ttyAgain = "IN";		//always IN	
						
//						String[] normalizedIn = normalizeIngredient(in.getRxcui());
						_3_classId = getId(dm.getProduct());
						_4_className = getRDFSLabel(dm.getProduct()).replace(" (product)", "");
						_5_rxCui = "";
						_6_rxName = "";
						_7_rxTty = "";
						_8_sourceId = String.valueOf(getId(dm.getMP()));      //MP id
						_9_sourceName = getRDFSLabel(dm.getMP()).replace(" (medicinal product)", "");    //MP name
						_10_classRelation = "";
						_11_classTreeId = getClassTreeId(dm.getPath());  //Path
						_12_rxCui = "";        //normalized PIN cui
						_13_inName = "";       //normalized PIN name
						_14_significant = "";  //always Y per Lee
						_15_ttyAgain = "";		//always IN						
						
					}
						
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
						
					}
										
				}
			}
		
	
	private void setDrugMemberMap(OWLClass c, OWLClass snctClassOfMp, Set<RxNormIngredient> newIns, Path p) {
		DrugMember dm = new DrugMember(c, snctClassOfMp, newIns, p);
		if( drugMemberMap.containsKey(c) ) {
			ArrayList<DrugMember> dms = drugMemberMap.get(c);
			dms.add(dm);
			drugMemberMap.put(c, dms);
		}
		else {
			ArrayList<DrugMember> dms = new ArrayList<DrugMember>();
			dms.add(dm);
			drugMemberMap.put(c, dms);
		}		
	}
	

	private void setProduct2MpMap(OWLClass product, OWLClass mp) {
		
		//we need to add the product as a key if it doesn't exist
		if( !product2Mp.containsKey(product) ) {
			ArrayList<OWLClass> mpList = new ArrayList<OWLClass>();
			mpList.add(mp);
			product2Mp.put(product, mpList);
		}
		
		//then we need to loop through the mps found within the product
		//if the above case occurs this portion is skipped
		for( OWLClass p : product2Mp.keySet() ) {
			if( product2Mp.containsKey(p) ) {
				if( product2Mp.containsKey(p) ) {
					ArrayList<OWLClass> mpList = product2Mp.get(p);
					if( !mpList.contains(mp) ) {
						mpList.add(mp);
					}
					product2Mp.put(p, mpList);
				}
				else {
					ArrayList<OWLClass> mpList = new ArrayList<OWLClass>();
					mpList.add(mp);
					product2Mp.put(p, mpList);
					
				}			
			}
		}
		
	}
	
	
	public HashMap<Long, Set<RxNormIngredient>> getMpsAndIns(OWLClass c) {
		// Get the direct MPs, and nothing else
		
		
		HashMap<Long, Set<RxNormIngredient>> ingredients = new HashMap<Long, Set<RxNormIngredient>>();
		
		Set<OWLClass> productDirectSubClasses = reasoner.subClasses(c, true).filter(x -> classIsMp(x)).collect(Collectors.toSet());
//		productDirectSubClasses.remove(c);
		productDirectSubClasses.remove(factory.getOWLNothing());
		productDirectSubClasses.remove(factory.getOWLThing()); //pointless, trying to avoid NPE 

		
		for( OWLClass directMpClass : productDirectSubClasses ) {
			Set<RxNormIngredient> inSet = new HashSet<>();
			String directMpClassName = getRDFSLabel(directMpClass);
			Long directMpClassCode = Long.valueOf(getId(directMpClass));
			ArrayList<RxNormIngredient> list = this.sct2RxIN.get(Long.valueOf(getId(directMpClass)));
			
			if( list != null ) {
				for( RxNormIngredient in : list ) {
					for( Long code : in.getSnomedCodes() ) {
						if( directMpClassCode.equals(Long.valueOf(code)) ) {
							
							String mpCodeString = String.valueOf(getId(directMpClass));
							OWLClass mpClass = factory.getOWLClass(namespace, mpCodeString);
							String mpName = getRDFSLabel(mpClass);  //unused, for debugging
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
			Set<OWLClass> indirectMpChildren = reasoner.subClasses(indirectMpClass, false).filter(x -> classIsMp(x)).collect(Collectors.toSet());
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
	
	public void printClassMap(String root) {
		//print root
		// MOA||000223|Mechanism of Action (MoA)|N0000000223|821|4309|8||836
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
	
	private String[] normalizeIngredient(Integer cuiFromPIN) {
		String[] returnArr = new String[2];
		//Am I really doing this again?  For a possible IN?  get clarification for performance
		String inString = "";
		Integer inCode = null;
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
//									String ingCui = null;
									String ingName = null;
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
	
	public boolean classIsMp(OWLClass c) {
		if( !c.equals(factory.getOWLNothing()) && getRDFSLabel(c) != null) {
			if( getRDFSLabel(c).contains("(medicinal product)") ) return true;
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
	
	public void cleanup() {
		reasoner.dispose();		
		this.classTreeFile.close();
		this.drugMembersFile.close();
	}

}