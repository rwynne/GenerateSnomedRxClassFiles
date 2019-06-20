package gov.nih.nlm.mor;

import org.semanticweb.owlapi.model.OWLClass;

public class RootClass implements Comparable<Object>  {
	public OWLClass rootClass = null;
	public Long rootCode = null;
	public String rootName = null;
	public String rela = null;
	public String classType = null;
	public String source = null;
	
	RootClass(OWLClass c, String n, Long code, String rel, String classType, String source) {
		this.rootClass = c;
		this.rootName = n;
		this.rootCode = code;
		this.rela = rel;
		this.classType = classType.substring(0, 6);  //varchar 8 in rxnorm_currect db, we must pare down
		this.source = source;
			
	}
	
	RootClass() {
		
	}
	
	public void setRootClass(OWLClass c) {
		this.rootClass = c;
	}
	
	public void setRootCode(Long code) {
		this.rootCode = code;
	}
	
	public void setRootName(String name) {
		this.rootName = name;
	}
	
	public void setRela(String rel) {
		this.rela = rel;
	}
	
	public void setClassType(String type) {
		this.classType = type;
	}
	
	public void setSource(String s) {
		this.source = s;
	}
	
	public OWLClass getRootClass() {
		return this.rootClass;
	}
	
	public Long getRootCode() {
		return this.rootCode;
	}
	
	public String getClassType() {
		return this.classType;
	}
	
	public String getRootName() {
		return this.rootName;
	}
	
	public String getRela() {
		return this.rela;
	}
	
	public String getSource() {
		return this.source;
	}
	
    public synchronized int hashCode() {
        int _hashCode = 1;
        if (this.rootClass != null) {
            _hashCode += this.rootClass.hashCode();
        }
        if (this.rootClass != null) {
            _hashCode += this.rootCode.hashCode();
        }        
        if (this.rootName != null) {
            _hashCode += this.rootName.hashCode();
        }
        if( this.classType != null ) {
        	_hashCode += this.classType.hashCode();
        }
        if (this.rela != null) {
            _hashCode += this.rela.hashCode();
        }
        if (this.source != null) {
        	_hashCode += this.source.hashCode();
        }
        return _hashCode;
    }
		  
	public boolean equals(Object o) {
		boolean equal = true;
		if (o instanceof RootClass) {
			OWLClass a = this.getRootClass();
			OWLClass b = ((RootClass) o).getRootClass();
			if( !a.equals(b) ) {
				equal = false;
			}
			String r = this.getRootName();
			String s = ((RootClass) o).getRootName();
			if( !r.equals(s)) {
				equal = false;
			}
			Long e = this.getRootCode();
			Long f = ((RootClass) o).getRootCode();
			if( !e.equals(f) ) {
				equal = false;
			}
			String c = this.getSource();
			String d = ((RootClass) o).getSource();
			if( !c.equals(d) ) {
				equal = false;
			}
			String g = this.getClassType();
			String h = ((RootClass) o).getClassType();
			if( !g.equals(h)) {
				equal = false;
			}
		}
		else {
			equal = false;
		}		
		return equal;
	}

	@Override
	public int compareTo(Object o) {
		int equal = 0;
		if (o instanceof RootClass) {
			OWLClass a = this.getRootClass();
			OWLClass b = ((RootClass) o).getRootClass();
			if( !a.equals(b) ) {
				equal = -1;
			}
			String r = this.getRootName();
			String s = ((RootClass) o).getRootName();
			if( !r.equals(s)) {
				equal = -1;
			}
			Long e = this.getRootCode();
			Long f = ((RootClass) o).getRootCode();
			if( !e.equals(f) ) {
				equal = -1;
			}
			String c = this.getSource();
			String d = ((RootClass) o).getSource();
			if( !c.equals(d) ) {
				equal = -1;
			}
			String g = this.getClassType();
			String h = ((RootClass) o).getClassType();
			if( !g.equals(h)) {
				equal = -1;
			}			
		}
		else {
			equal = -1;
		}		
		return equal;
	}	

}
