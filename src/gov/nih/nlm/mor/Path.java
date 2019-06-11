package gov.nih.nlm.mor;

import java.util.ArrayList;

import org.semanticweb.owlapi.model.OWLClass;

public class Path {
	ArrayList<OWLClass> path = new ArrayList<OWLClass>();
	final String namespace = "http://snomed.info/id/";	
	
	Path(OWLClass c) {
		path.add(c);
	}
	
	Path(ArrayList<OWLClass> list) {
		path = list;
	}
	
	Path(Path p) {
		for( OWLClass c : p.getPath()) {
			this.path.add(c);
		}
	}
	
	Path() {
		
	}
	
	public Path addToPath(OWLClass c) {
		path.add(c);
		return this;
	}
	
	public Path removeHead() {
		path.remove(0);
		return this;
	}
	
	public int getPathSize() {
		if( path != null ) {
			return path.size();
		}
		else {
			return 0;
		}
	}
	
	public ArrayList<OWLClass> getPath() {
		return path;
	}
	
	public String getPathAsString() {
		String s = "";
		for( OWLClass c : path ) {
			if( !s.isEmpty() ) {
				s = s.concat("." + getId(c));
			}
			else {
				s = getId(c);
			}
		}
		return s;
	}
	
	private String getId(OWLClass c) {
		String id = null;
		if( c != null ) {
			id = c.getIRI().getIRIString().replace(namespace, "");
		}
		return id;		
	}
	
	

}