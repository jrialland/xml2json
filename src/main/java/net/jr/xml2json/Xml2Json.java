package net.jr.xml2json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.TreeMap;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.helpers.XMLReaderFactory;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

public class Xml2Json {

	private static final Logger LOGGER = LoggerFactory.getLogger(Xml2Json.class);

	public void jsonify(InputStream xml, OutputStream json) throws SAXException, IOException {
		JsonFactory jsonFactory = new JsonFactory();
		final JsonGenerator gen = jsonFactory.createGenerator(json, JsonEncoding.UTF8);
		gen.useDefaultPrettyPrinter();
		jsonify(new InputSource(xml), gen);
	}

	private List<String> groupedAsArray = new ArrayList<String>();

	private Map<String, String> groupedByAttr = new TreeMap<>();

	public void jsonify(InputSource xml, final JsonGenerator gen) throws SAXException, IOException {

		XMLReader reader = XMLReaderFactory.createXMLReader();
		reader.setContentHandler(new DefaultHandler2() {

			class TagAttr {

				public TagAttr(String tag, String attr) {
					this.tag = tag;
					this.attr = attr;
				}

				private String tag;

				private String attr;

				@Override
				public int hashCode() {
					return new HashCodeBuilder().append(tag).append(attr).toHashCode();
				}

			}

			class Node {
				Node parent;
				List<Node> children = new ArrayList<>();
				Map<String, List<Node>> arrayChildren = new TreeMap<>();
				Map<TagAttr, List<Node>> groupedChildren = new HashMap<>();
				Map<String, String> attrs = new TreeMap<String, String>();
				StringWriter data = new StringWriter();
				String name;
			}

			private Stack<Node> stack = new Stack<>();

			@Override
			public void startDocument() throws SAXException {
				stack.push(new Node());
			}

			@Override
			public void startElement(String uri, String localName, String qName, Attributes attributes)
					throws SAXException {
				super.startElement(uri, localName, qName, attributes);
				Node pf = stack.peek();

				Node sf = new Node();
				sf.parent = pf;
				sf.name = localName;

				if (pf != null) {
					pf.children.add(sf);
				}
				stack.push(sf);

				int attrs = attributes.getLength();
				if (attrs > 0) {
					for (int i = 0; i < attrs; i++) {
						String attrName = attributes.getLocalName(i);
						String value = attributes.getValue(i);
						sf.attrs.put(attrName, value);
					}
				}

			}

			@Override
			public void endElement(String uri, String localName, String qName) throws SAXException {
				stack.pop();
			}

			@Override
			public void characters(char[] ch, int start, int length) throws SAXException {
				stack.peek().data.write(ch, start, length);
			}

			@Override
			public void endDocument() throws SAXException {
				Node root = stack.pop();
				arrangeArrayChildren(root);
				generate(root);
			}

			private void arrangeArrayChildren(Node sf) {
				Map<String, List<Node>> nodes = new TreeMap<>();

				for (Node child : sf.children) {
					arrangeArrayChildren(child);
					List<Node> list = nodes.get(child.name);
					if (list == null) {
						list = new ArrayList<>();
						list.add(child);
						nodes.put(child.name, list);
					} else {
						list.add(child);
					}
				}

				for (Entry<String, List<Node>> entry : nodes.entrySet()) {
					String name = entry.getKey();
					List<Node> list = entry.getValue();
					if (groupedAsArray.contains(name)) {
						for (Node n : list) {
							sf.children.remove(n);
						}
						sf.arrayChildren.put(name, list);
					}

					if (groupedByAttr.containsKey(name)) {
						TagAttr key = new TagAttr(name, groupedByAttr.get(name));
						sf.groupedChildren.put(key, list);
						for (Node n : list) {
							sf.children.remove(n);
						}
					}

				}
			}

			private String toPath(Node sf) {
				Node current = sf;
				List<Node> list = new ArrayList<>();
				while (current != null) {
					list.add(0, current);
					current = current.parent;
				}
				StringWriter sw = new StringWriter();
				for (Node n : list) {
					sw.append("/");
					sw.append(n.name);
				}
				return sw.toString();
			}

			private void generate(Node sf) throws SAXException {
				try {

					if (sf.attrs.isEmpty() && sf.children.isEmpty() && sf.arrayChildren.isEmpty()
							&& sf.groupedChildren.isEmpty()) {
						String val = sf.data.toString().trim();
						if (val.matches("^[0-9]|([1-9][0-9]+)$")) {
							gen.writeNumber(Integer.parseInt(val));
						} else {
							gen.writeString(StringEscapeUtils.escapeJson(val));
						}
					} else {

						gen.writeStartObject();

						// attrs
						for (Entry<String, String> entry : sf.attrs.entrySet()) {
							String key = entry.getKey();
							String val = entry.getValue();
							if (val.matches("^[0-9]|([1-9][0-9]+)$")) {
								gen.writeNumberField(key, Integer.parseInt(val));
							} else {
								gen.writeStringField(key, StringEscapeUtils.escapeJson(val));
							}
						}

						// children
						for (Node child : sf.children) {
							gen.writeFieldName(child.name);
							generate(child);
						}

						// array-style children
						for (Entry<String, List<Node>> arrayChildren : sf.arrayChildren.entrySet()) {
							String name = arrayChildren.getKey();
							gen.writeFieldName(name);
							gen.writeStartArray();
							for (Node node : arrayChildren.getValue()) {
								generate(node);
							}
							gen.writeEndArray();
						}

						// dict-style children
						for (Entry<TagAttr, List<Node>> grouped : sf.groupedChildren.entrySet()) {
							String name = grouped.getKey().tag;
							String attribute = grouped.getKey().attr;
							gen.writeFieldName(name);
							gen.writeStartObject();

							for (Node node : grouped.getValue()) {
								String key = node.attrs.remove(attribute);
								gen.writeFieldName(key);
								generate(node);
							}

							gen.writeEndObject();
						}

						gen.writeEndObject();

					}

				} catch (IOException e) {
					LOGGER.error("while handling '" + toPath(sf) + "'", e);
					throw new SAXException(e);
				}
			}
		});
		reader.parse(xml);
		gen.flush();
	}

	public GroupingOption group(final String tagName) {
		final Xml2Json that = this;
		return new GroupingOption() {
			@Override
			public Xml2Json usingAttribute(String attrName) {
				groupedByAttr.put(tagName, attrName);
				return that;
			}

			@Override
			public Xml2Json usingId() {
				return usingAttribute("id");
			}

			@Override
			public Xml2Json asArray() {
				groupedAsArray.add(tagName);
				return that;
			}
		};
	}

}
