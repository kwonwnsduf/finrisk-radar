package com.finrisk.radar.financial;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

@Component
public class DartCorpCodeParser {
	public List<DartCorpCodeEntry> parse(String xml) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
			NodeList nodes = document.getElementsByTagName("list");
			List<DartCorpCodeEntry> entries = new ArrayList<>();
			for (int i = 0; i < nodes.getLength(); i++) {
				Element element = (Element) nodes.item(i);
				entries.add(new DartCorpCodeEntry(text(element, "corp_code"), text(element, "corp_name"),
						text(element, "stock_code"), text(element, "modify_date")));
			}
			return entries;
		} catch (Exception exception) {
			throw new DartClientException("DART corp code XML could not be parsed.", exception);
		}
	}

	private String text(Element element, String name) {
		NodeList nodes = element.getElementsByTagName(name);
		return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
	}
}
