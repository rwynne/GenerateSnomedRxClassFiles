package gov.nih.nlm.mor;

import java.util.ArrayList;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

public class Product {
	
	private Long id = null;
	private OWLReasoner reasoner = null;	
	private ArrayList<OWLClass> allSubClasses = new ArrayList<OWLClass>();
	final String namespace = "http://snomed.info/id/";	
	
	public Product() {
		
	}
	
	public Product(OWLClass c, OWLClass root, OWLReasoner reasoner) {
		this.id = Long.valueOf(getId(c));
		this.reasoner = reasoner;
		getDescendents(c);
	}
	
	private void getDescendents(OWLClass c) {
		reasoner.subClasses(c, false).forEachOrdered(x -> {
			allSubClasses.add(x);			
		});
	}
	
	public String getId(OWLClass c) {
		String id = null;
		if( c != null ) {
			id = c.getIRI().getIRIString().replace(namespace, "");
		}
		return id;		
	}
	
	public ArrayList<OWLClass> getProductSubClasses() {
		return this.allSubClasses;
	}

}
