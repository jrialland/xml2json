package net.jr.xml2json;

import java.io.InputStream;

import org.junit.Assert;
import org.junit.Test;

public class Xml2JsonTest {

	protected InputStream load(String resource) {
		InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
		Assert.assertNotNull(is);
		return is;
	}
	
	@Test
	public void doTest() throws Exception {

		InputStream xml = load("test-web.xml");
		
		new Xml2Json()
			.group("servlet").asArray()
			.group("test").usingAttribute("id")
		.jsonify(xml, System.out);

	}
	
	@Test
	public void doTestBooksXml() throws Exception {		
		new Xml2Json().group("book").usingId().jsonify(load("books.xml"), System.out);
	}
}
