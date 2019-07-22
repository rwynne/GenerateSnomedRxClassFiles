package gov.nih.nlm.mor;

import org.semanticweb.owlapi.model.OWLClass;

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
public class ClassMember {
	String classType = "";
	String field2 = "";
	String treeId = "";
	String className = "";
	String classId = "";
	String countFromDaily = "";
	String countFromMedrt = "0";
	String countChildren = "";
	String treeIdOfParent = "";
	OWLClass product = null;
	int countDrugMembers = 0;
	String delimiter = "|";
	
	public ClassMember() {
		
	}
	
	public String getClassType() {
		return classType;
	}



	public void setClassType(String classType) {
		this.classType = classType;
	}



	public String getField2() {
		return field2;
	}



	public void setField2(String field2) {
		this.field2 = field2;
	}



	public String getTreeId() {
		return treeId;
	}



	public void setTreeId(String treeId) {
		this.treeId = treeId;
	}



	public String getClassName() {
		return className;
	}



	public void setClassName(String className) {
		this.className = className;
	}



	public String getClassId() {
		return classId;
	}



	public void setClassId(String classId) {
		this.classId = classId;
	}



	public String getCountFromDaily() {
		return countFromDaily;
	}



	public void setCountFromDaily(String countFromDaily) {
		this.countFromDaily = countFromDaily;
	}



	public String getCountFromMedrt() {
		return countFromMedrt;
	}



	public void setCountFromMedrt(String countFromMedrt) {
		this.countFromMedrt = countFromMedrt;
	}



	public String getCountChildren() {
		return countChildren;
	}



	public void setCountChildren(String countChildren) {
		this.countChildren = countChildren;
	}



	public String getTreeIdOfParent() {
		return treeIdOfParent;
	}



	public void setTreeIdOfParent(String treeIdOfParent) {
		this.treeIdOfParent = treeIdOfParent;
	}



	public int getCountDrugMembers() {
		return countDrugMembers;
	}



	public void setCountDrugMembers(int countDrugMembers) {
		this.countDrugMembers = countDrugMembers;
	}



	public String getDelimiter() {
		return delimiter;
	}



	public void setDelimiter(String delimiter) {
		this.delimiter = delimiter;
	}
	
	public void setProductClass(OWLClass c) {
		this.product = c;
	}



	public String getMemberRow() {
		String row = "";
		row += classType + delimiter;
		row += field2 + delimiter;
		row += treeId + delimiter;
		row += className + delimiter;
		row += classId + delimiter;
		row += countFromDaily + delimiter;
		row += countFromMedrt + delimiter;
		row += countChildren + delimiter;
		row += treeIdOfParent + delimiter;
		row += countDrugMembers;
		return row;
	}
	
	
	public int compareTo(final ClassMember m1, final ClassMember m2) {
	    int c;
	    c = m1.getClassType().compareTo(m2.getClassType());
	    if (c == 0)
	       c = m1.getClassId().compareTo(m2.getClassId());
	    return c;
	}
	
	
}
