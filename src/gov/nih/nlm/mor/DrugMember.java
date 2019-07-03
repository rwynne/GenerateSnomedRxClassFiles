package gov.nih.nlm.mor;

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
	
    public synchronized int hashCode() {
        int _hashCode = 1;
        if (this.product != null) {
            _hashCode += this.product.hashCode();
        }
        if (this.mp != null) {
            _hashCode += this.mp.hashCode();
        }
        if( this.ingredients != null ) {
        	_hashCode += this.ingredients.hashCode();
        }
        if( this.path != null ) {
        	_hashCode += this.path.hashCode();
        }
        return _hashCode;
    }
		  
	public boolean equals(Object o) {
		boolean equal = true;
		if (o instanceof DrugMember) {
			OWLClass a = this.product;
			OWLClass b = ((DrugMember) o).getProduct();
			if( !a.equals(b) ) {
				equal = false;
			}
			OWLClass r = this.mp;
			OWLClass s = ((DrugMember) o).getMP();
			if( !r.equals(s)) {
				equal = false;
			}
			Path c = this.path;
			Path d = ((DrugMember) o).getPath();
			if( !c.equals(d) ) {
				equal = false;
			}
			Set<RxNormIngredient> e = this.ingredients;
			Set<RxNormIngredient> f = ((DrugMember) o).getIngredients();
			if( !e.equals(f) ) {
				equal = false;
			}
		}
		else {
			equal = false;
		}		
		return equal;
	}	

}
