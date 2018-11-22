package com.github.nabsha.plugin;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class XMLUtils {

  /**
   * Utility to apply xsl transformation
   * @param input
   * @param xslPath
   * @return
   * @throws TransformerException
   */
  public static Document transform ( Document input, InputStream xslPath ) throws TransformerException {
    DOMSource in = new DOMSource ( input );
    Source xslt = new StreamSource ( xslPath );
    TransformerFactory factory = TransformerFactory.newInstance ();
    Transformer transformer = factory.newTransformer ( xslt );

    DOMResult xmlResult = new DOMResult ();
    transformer.transform ( in, xmlResult );

    return (Document) xmlResult.getNode ();

  }


  /**
   * A utility function to print Document
   * @param doc
   * @return
   */
  public static String docToString ( Document doc ) {
    try {
      StringWriter sw = new StringWriter ();
      TransformerFactory tf = TransformerFactory.newInstance ();
      Transformer transformer = tf.newTransformer ();
      transformer.setOutputProperty ( OutputKeys.OMIT_XML_DECLARATION, "no" );
      transformer.setOutputProperty ( OutputKeys.METHOD, "xml" );
      transformer.setOutputProperty ( OutputKeys.INDENT, "yes" );
      transformer.setOutputProperty ( OutputKeys.ENCODING, "UTF-8" );

      transformer.transform ( new DOMSource ( doc ), new StreamResult ( sw ) );
      return sw.toString ();
    } catch ( Exception ex ) {
      throw new RuntimeException ( "Error converting to String", ex );
    }
  }

  /**
   * return value of attribute
   * @param node
   * @param attributeName
   * @return
   */
  public static String getAttrValue ( Node node, String attributeName ) {
    if ( node.getAttributes () != null && node.getAttributes ().getNamedItem ( attributeName ) != null )
      return node.getAttributes ().getNamedItem ( attributeName ).getNodeValue ();
    else
      return "";
  }



  public static NodeList findNodeByXPath ( String xpathString, Document base ) throws XPathExpressionException {
    XPathFactory xPathFactory = XPathFactory.newInstance ();
    XPath xpath = xPathFactory.newXPath ();
    XPathExpression compiledExpression = xpath.compile ( xpathString );
    return (NodeList) compiledExpression.evaluate ( base, XPathConstants.NODESET );

  }


}
