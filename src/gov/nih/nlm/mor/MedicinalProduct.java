package gov.nih.nlm.mor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Vector;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import gov.nih.nlm.mor.RxNorm.RxNormIngredient;

public class MedicinalProduct {
	
	private ArrayList<Path> paths = new ArrayList<Path>();
	private String rxCui = null;
	private String snctCui = null;
	final String namespace = "http://snomed.info/id/";
	private Vector<RxNormIngredient> ingredients = null;
	
	public MedicinalProduct() {
		
	}
	
	public MedicinalProduct(OWLClass c, OWLClass root, OWLReasoner reasoner) {
		String snctId = getId(c);
//		ingredients = getIngredientsForMedicinalProduct(c);
		
	}
	
	private String getId(OWLClass c) {
		String id = null;
		if( c != null ) {
			id = c.getIRI().getIRIString().replace(namespace, "");
		}
		return id;		
	}		

}
