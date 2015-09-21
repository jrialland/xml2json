package net.jr.xml2json;

public interface GroupingOption {
	
	public Xml2Json usingId();
	
	public Xml2Json usingAttribute(String attrName);
	
	public Xml2Json asArray();
	
}
