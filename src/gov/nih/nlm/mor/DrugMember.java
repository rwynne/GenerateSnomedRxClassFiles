package gov.nih.nlm.mor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLClass;

import gov.nih.nlm.mor.RxNorm.RxNormIngredient;

public class DrugMember {
	public OWLClass product = null;
	public OWLClass mp = null;
	public Set<RxNormIngredient> ingredients = new HashSet<>();
	public Path path = null;
	
	DrugMember(OWLClass p, OWLClass m, Set<RxNormIngredient> ins, Path path) {
		this.product = p;
		this.mp = m;
		this.ingredients = ins;
		this.path = path;
	}
	
	DrugMember() {
		
	}
	
	public OWLClass getProduct() {
		return this.product;
	}
	
	public OWLClass getMP() {
		return this.mp;
	}
	
	public Set<RxNormIngredient> getIngredients() {
		return this.ingredients;
	}
	
	public Path getPath() {
		return this.path;
	}

}
