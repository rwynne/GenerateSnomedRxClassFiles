package gov.nih.nlm.mor;

import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.OWLClass;

import gov.nih.nlm.mor.RxNorm.RxNormIngredient;

public class DrugMember {
	//printDrugMember(_1_source, _2_relationship, productForDirect, in, mp, true, p );
	private String source = "";
	private String relationship = "";
	private OWLClass productForDirect = null;	
	private RxNormIngredient in = null;
	private Path path = null;
	private OWLClass mpProduct = null;
	private boolean direct = false;
	
//	private OWLClass product = null;
//	private OWLClass mp = null;
//	private Set<RxNormIngredient> ingredients = new HashSet<>();
//	private Path path = null;
	
	
	//								DrugMember drugMember = new DrugMember(_1_source, _2_relationship, productForDirect, in, mp, true, p);
	DrugMember(String src, String rel, OWLClass productForDirect, RxNormIngredient in, OWLClass mp, boolean isDirect, Path p) {
		this.source = src;
		this.relationship = rel;
		this.productForDirect = productForDirect;
		this.in = in;
		this.path = p;
		this.mpProduct = mp;
		this.direct = isDirect;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (direct ? 1231 : 1237);
		result = prime * result + ((in == null) ? 0 : in.hashCode());
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		result = prime * result + ((mpProduct == null) ? 0 : mpProduct.hashCode());
		result = prime * result + ((productForDirect == null) ? 0 : productForDirect.hashCode());
		result = prime * result + ((relationship == null) ? 0 : relationship.hashCode());
		result = prime * result + ((source == null) ? 0 : source.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DrugMember other = (DrugMember) obj;
		if (direct != other.direct)
			return false;
		if (in == null) {
			if (other.in != null)
				return false;
		} else if (!in.equals(other.in))
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		if (mpProduct == null) {
			if (other.mpProduct != null)
				return false;
		} else if (!mpProduct.equals(other.mpProduct))
			return false;
		if (productForDirect == null) {
			if (other.productForDirect != null)
				return false;
		} else if (!productForDirect.equals(other.productForDirect))
			return false;
		if (relationship == null) {
			if (other.relationship != null)
				return false;
		} else if (!relationship.equals(other.relationship))
			return false;
		if (source == null) {
			if (other.source != null)
				return false;
		} else if (!source.equals(other.source))
			return false;
		return true;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getRelationship() {
		return relationship;
	}

	public void setRelationship(String relationship) {
		this.relationship = relationship;
	}

	public OWLClass getProductForDirect() {
		return productForDirect;
	}

	public void setProductForDirect(OWLClass productForDirect) {
		this.productForDirect = productForDirect;
	}

	public RxNormIngredient getIn() {
		return in;
	}

	public void setIn(RxNormIngredient in) {
		this.in = in;
	}

	public Path getPath() {
		return path;
	}

	public void setPath(Path path) {
		this.path = path;
	}

	public OWLClass getMpProduct() {
		return mpProduct;
	}

	public void setMpProduct(OWLClass product) {
		this.mpProduct = product;
	}

	public boolean isDirect() {
		return direct;
	}

	public void setDirect(boolean direct) {
		this.direct = direct;
	}

	DrugMember() {
		
	}
	
//	public OWLClass getProduct() {
//		return this.product;
//	}
//	
//	public OWLClass getMP() {
//		return this.mp;
//	}
//	
//	public Set<RxNormIngredient> getIngredients() {
//		return this.ingredients;
//	}
//	
//	public Path getPath() {
//		return this.path;
//	}
//	
//    public synchronized int hashCode() {
//        int _hashCode = 1;
//        if (this.product != null) {
//            _hashCode += this.product.hashCode();
//        }
//        if (this.mp != null) {
//            _hashCode += this.mp.hashCode();
//        }
//        if( this.ingredients != null ) {
//        	_hashCode += this.ingredients.hashCode();
//        }
//        if( this.path != null ) {
//        	_hashCode += this.path.hashCode();
//        }
//        return _hashCode;
//    }
//		  
//	public boolean equals(Object o) {
//		boolean equal = true;
//		if (o instanceof DrugMember) {
//			OWLClass a = this.product;
//			OWLClass b = ((DrugMember) o).getProduct();
//			if( !a.equals(b) ) {
//				equal = false;
//			}
//			OWLClass r = this.mp;
//			OWLClass s = ((DrugMember) o).getMP();
//			if( !r.equals(s)) {
//				equal = false;
//			}
//			Path c = this.path;
//			Path d = ((DrugMember) o).getPath();
//			if( !c.equals(d) ) {
//				equal = false;
//			}
//			Set<RxNormIngredient> e = this.ingredients;
//			Set<RxNormIngredient> f = ((DrugMember) o).getIngredients();
//			if( !e.equals(f) ) {
//				equal = false;
//			}
//		}
//		else {
//			equal = false;
//		}		
//		return equal;
//	}	

}
