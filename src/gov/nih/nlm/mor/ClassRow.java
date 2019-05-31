package gov.nih.nlm.mor;

public class ClassRow {
	
	private String _1_ClassType = null;
	private String _2_NotUsed = "";
	private String _3_TreeId = null;
	private String _4_ClassName = null;
	private String _5_ClassId = null;
	private int _6_CountMembersAll = 0;
	private int _7_CountMembersSource = 0;
	private int _8_DirectDescendentCount = 0;
	private String _9_ParentClassTreeId = null;
	private int _10_CountOfDrugMembersFromFDA = 0;
	
	
	public ClassRow() {
				
	}
	
	public void setClassType(String s) {
		this._1_ClassType = s;
	}
	
	public void setNotUsed(String s) {
		this._2_NotUsed = s;
	}
	
	public void setTreeId(String s) {
		this._3_TreeId = s;
	}
	
	public void setClassName(String s) {
		this._4_ClassName = s;
	}
	
	public void setClassId(String s) {
		this._5_ClassId = s;
	}
	
	public void setCountMemebersAll(int i) {
		this._6_CountMembersAll = i;
	}
	
	public void setCountMembersSource(int i) {
		this._7_CountMembersSource = i;
	}
	
	public void setDirectDescendentCount(int i) {
		this._8_DirectDescendentCount = i;
	}
	
	public void setParentClassTreeId(String s) {
		this._9_ParentClassTreeId = s;
	}
	
	public void setCountOfDrugMembersFromFDA(int i) {
		this._10_CountOfDrugMembersFromFDA = i;
	}

}
