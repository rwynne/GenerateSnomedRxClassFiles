package gov.nih.nlm.mor;


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
	String countDrugMembers = "0";
	String delimiter = "|";
	
	public ClassMember() {
		
	}
	
	public void setClassType(String s) {
		this.classType = s;
	}
	
	public void setTreeId(String s) {
		this.treeId = s;
	}
	
	public void setClassName(String s) {
		this.className = s;
	}
	
	public void setClassId(String s) {
		this.classId = s;
	}
	
	public void setCountFromDaily(String s) {
		this.countFromDaily = s;
	}
	
	public void setCountFromMedrt(String s) {
		this.countFromMedrt = s;
	}
	
	public void setCountChildren(String s) {
		this.countChildren = s;
	}
	
	public void setTreeIdParent(String s) {
		this.treeIdOfParent = s;
	}
	
	public void setCountDrugMember(String s) {
		this.countDrugMembers = s;
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
}
