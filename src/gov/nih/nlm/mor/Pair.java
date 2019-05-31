package gov.nih.nlm.mor;

import org.semanticweb.owlapi.model.OWLClass;

public class Pair {
	OWLClass parent = null;
	OWLClass child = null;
	
	Pair(OWLClass parent, OWLClass child) {
		this.parent = parent;
		this.child = child;
	}
	
	Pair() {
		
	}
	
	public Pair getPair() {
		return this;
	}
	
	public OWLClass getChild() {
		return this.child;
	}
	
	public OWLClass getParent() {
		return this.parent;
	}	

}
