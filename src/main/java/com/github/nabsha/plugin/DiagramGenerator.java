package com.github.nabsha.plugin;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.xpath.XPathExpressionException;
import java.util.Map;

public interface DiagramGenerator {


    /**
     * Generates the diagram for the given item
     *
     * @param muleUberXML
     * @param item
     * @param outputDir
     */
    void generate(Document muleUberXML, Node item);

    public String before(Document document, Node node, Map<String, String> props) throws XPathExpressionException;

    public String after(Document document, Node node, Map<String, String> props) throws XPathExpressionException;
}
