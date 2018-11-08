package com.github.nabsha.plugin;


import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 */
@Mojo ( name = "mule2puml", defaultPhase = LifecyclePhase.GENERATE_RESOURCES )
public class MyMojo extends AbstractMojo {
  /**
   * Location of the file.
   */
  @Parameter ( defaultValue = "${project.basedir}", property = "muleSourceDir", required = true )
  private File muleSourceDir;

  @Parameter ( defaultValue = "/.*/src/main/app/.*\\.xml", property = "muleFilesPathRegex", required = false )
  private String muleFilesPathRegex;

  @Parameter ( defaultValue = "/.*/.*\\.properties", property = "propertiesFilesPathRegex", required = false )
  private String propertiesFilesPathRegex;

  @Parameter ( defaultValue = "true", property = "generateUberMuleXML", required = false )
  private boolean generateUberMuleXML;

  @Parameter ( defaultValue = "${project.build.directory}/docs/mule.xml", property = "outputUberMuleXMLPath", required = false )
  private File outputUberMuleXMLPath;

  @Parameter ( defaultValue = "${project.build.directory}/docs/puml", property = "pumlOutputDir", required = false )
  private File pumlOutputDir;


  @Parameter ( property = "entryXPathFilters", required = false )
  private List<String> entryXPathFilters;

  public void execute ()
    throws MojoExecutionException {

    if ( entryXPathFilters == null || entryXPathFilters.isEmpty () ) {
      entryXPathFilters = getResourceFileAsList ( "entryXPath.cfg" );
    }

    List<Path> files = null;
    try {
      getLog ().info ( "Searching for mule files in " + muleSourceDir.getAbsolutePath () + " with regex " + muleFilesPathRegex );
      files = searchFiles ( muleSourceDir.getAbsolutePath (), muleFilesPathRegex );
      files.forEach ( file -> {
        getLog ().info ( "Found : " + file.toString () );
      } );

    } catch ( IOException e ) {
      throw new MojoExecutionException ( "Failed to search mule source files in " + muleSourceDir.getAbsolutePath () );
    }
    if ( files.isEmpty () ) {
      getLog ().info ( "No files found in " + muleSourceDir.getAbsolutePath () + " with " + muleFilesPathRegex + " pattern" );
      return;
    }


    Stream<File> fileStream = files.stream ().map ( Path::toFile );

    Properties namespaces = new Properties ();


    try {
      namespaces.load ( this.getClass ().getClassLoader ().getResourceAsStream ( "default-namespaces.properties" ) );
      Document merge = merge ( "/mule", namespaces, fileStream.toArray ( File[]::new ) );
      String mergedDoc = toString ( merge );
      DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance ();
      docBuilderFactory.setNamespaceAware ( true );
      DocumentBuilder docBuilder = null;
      docBuilder = docBuilderFactory.newDocumentBuilder ();
      Document base = docBuilder.parse ( new InputSource ( new StringReader ( mergedDoc ) ) );
      Document transformed = transform ( base, this.getClass ().getClassLoader ().getResourceAsStream ( "flatten-stage1.xsl" ) );
      Document reduced = transform ( transformed, this.getClass ().getClassLoader ().getResourceAsStream ( "reduce-stage2.xsl" ) );

      // replace properties with values
      ////*[contains(@address,'Downing')]
      getLog ().info ("Searching for " + propertiesFilesPathRegex + " in " + muleSourceDir.getAbsolutePath ());
      List<Path> propertiesFiles = searchFiles ( muleSourceDir.getAbsolutePath (), propertiesFilesPathRegex );

      getLog ().info ( "Properties files found : " + propertiesFiles );

      propertiesFiles.forEach ( path -> {
        Properties properties = new Properties (  );
        try {
          properties.load ( new FileInputStream ( path.toFile ()));
          properties.forEach ( ( k, v ) -> {
            try {

              NodeList nodeByXPath = findNodeByXPath ( "//attribute::*[contains(., '${"  + k + "}')]", reduced );
              for (int i = 0; i < nodeByXPath.getLength (); i++) {
                Node item = nodeByXPath.item ( i );
                item.setNodeValue ( v.toString () );
              }
            } catch ( XPathExpressionException e ) {
              e.printStackTrace ();
            }
          } );
        } catch ( IOException e ) {
          e.printStackTrace ();
        }
      });

      if ( generateUberMuleXML ) {
        getLog ().info ( "Writing mule uber xml to " + outputUberMuleXMLPath.getAbsolutePath () );
        outputUberMuleXMLPath.getParentFile ().mkdirs ();
        try ( PrintWriter out = new PrintWriter ( outputUberMuleXMLPath ) ) {
          out.println ( toString ( reduced ) );
        }
      }

      String joined = String.join ( " | ", entryXPathFilters );
      getLog ().info ( "Filtering flow using " + joined );
      NodeList results = findNodeByXPath ( joined, reduced );

      for ( int i = 0; i < results.getLength (); i++ ) {
        Map<String, String> props = new HashMap<> ();
        Node item = results.item ( i );
        String name = getAttrValue ( item, "name" ).replace ( ":", "_" ).replace ( "/", "-" );
        File file = new File ( pumlOutputDir.getAbsolutePath () + "/" + name + ".puml" );
        getLog ().info ( "Creating file " + file.getAbsolutePath () );
        file.getParentFile ().mkdirs ();

        PrintWriter out = new PrintWriter ( file );
        walk ( reduced, item, props, out );
        out.close ();
      }

    } catch ( Exception e ) {

      StringWriter sw = new StringWriter ();
      PrintWriter pw = new PrintWriter ( sw );
      e.printStackTrace ( pw );
      String sStackTrace = sw.toString (); // stack trace as a string

      throw new MojoExecutionException ( "Failed to merge documents " + sStackTrace );
    }

  }


  private static Document transform ( Document input, InputStream xslPath ) throws TransformerException {
    DOMSource in = new DOMSource ( input );
    Source xslt = new StreamSource ( xslPath );
    TransformerFactory factory = TransformerFactory.newInstance ();
    Transformer transformer = factory.newTransformer ( xslt );

    DOMResult xmlResult = new DOMResult ();
    transformer.transform ( in, xmlResult );

    return (Document) xmlResult.getNode ();

  }


  public String toString ( Document doc ) {
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

  public String readFile ( Path file ) throws IOException {
    return new String ( Files.readAllBytes ( file ) );

  }

  public List<Path> searchFiles ( String path, String pattern ) throws IOException {

    return Files.walk ( Paths.get ( path ) )
      .filter ( p -> p.toString ().matches ( path + pattern ) )
      .collect ( Collectors.toList () );

  }

  private Document merge ( String expression, Properties defaultNamespaces, File... files ) throws Exception {
    XPathFactory xPathFactory = XPathFactory.newInstance ();
    XPath xpath = xPathFactory.newXPath ();
    XPathExpression compiledExpression = xpath.compile ( expression );
    return merge ( compiledExpression, defaultNamespaces, files );
  }

  private Document merge ( XPathExpression expression, Properties defaultNamespaces, File... files ) throws Exception {
    DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance ();
    docBuilderFactory.setIgnoringElementContentWhitespace ( true );
    DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder ();

    Document base = docBuilder.parse ( files[ 0 ] );


    defaultNamespaces.stringPropertyNames ().forEach ( key -> {
      base.getDocumentElement ().setAttribute ( key, defaultNamespaces.getProperty ( key ) );
    } );

    Node results = (Node) expression.evaluate ( base, XPathConstants.NODE );
    if ( results == null ) {
      throw new IOException ( files[ 0 ]
        + ": expression does not evaluate to node" );
    }

    for ( int i = 1; i < files.length; i++ ) {
      Document merge = docBuilder.parse ( files[ i ] );
      Node nextResults = (Node) expression.evaluate ( merge,
        XPathConstants.NODE );
      while ( nextResults.hasChildNodes () ) {
        Node kid = nextResults.getFirstChild ();
        nextResults.removeChild ( kid );
        kid = base.importNode ( kid, true );
        results.appendChild ( kid );
      }
    }

    return base;
  }

  /**
   * Reads given resource file as a string.
   *
   * @param fileName the path to the resource file
   * @return the file's contents or null if the file could not be opened
   */
  public List<String> getResourceFileAsList ( String fileName ) {
    InputStream is = getClass ().getClassLoader ().getResourceAsStream ( fileName );
    if ( is != null ) {
      BufferedReader reader = new BufferedReader ( new InputStreamReader ( is ) );
      return reader.lines ().collect ( Collectors.toList () );
    }
    return null;
  }

  public NodeList findNodeByXPath ( String xpathString, Document base ) throws XPathExpressionException {
    XPathFactory xPathFactory = XPathFactory.newInstance ();
    XPath xpath = xPathFactory.newXPath ();
    XPathExpression compiledExpression = xpath.compile ( xpathString );
    return (NodeList) compiledExpression.evaluate ( base, XPathConstants.NODESET );

  }


  private String esc ( String str ) {
    return "\"" + str + "\"";
  }

  public String before ( Document document, Node node, Map<String, String> props ) throws XPathExpressionException {

    switch ( node.getNodeName () ) {
      case "flow":
        return "@startuml";

      case "http:listener": {

        NamedNodeMap attributes = node.getAttributes ();
        Node namedItem = attributes.getNamedItem ( "config-ref" );
        String configName = namedItem.getNodeValue ();

        NodeList nodeByXPath = findNodeByXPath ( "/*[local-name()='mule']/*[local-name()='listener-config'][@name='" + configName + "']", document );
        NamedNodeMap configAttr = nodeByXPath.item ( 0 ).getAttributes ();
//        for ( int i = 0; i < configAttr.getLength (); i++ ) {
//          step.transportConfig.put ( configAttr.item ( i ).getNodeName (), configAttr.item ( i ).getNodeValue () );
//        }
        String value = esc ( getAttrValue ( node, "path" ) );
        props.put ( "Entry", value );
        return "";
      }

      case "jms:inbound-endpoint": {
        String jmsIn = esc ( getAttrValue ( node, "queue" ) );
        props.put ( "Entry", jmsIn );
        return "";

      }

      case "choice":
      case "foreach": {
        return "alt " + getAttrValue ( node, "doc:name" );
      }

      case "otherwise":
        return "else otherwise";
      case "when":
        return "else " + esc ( getAttrValue ( node, "expression" ) );

      case "expression-component":
      case "expression":
      case "invoke":
      case "dw:transform-message": {
        return props.get ( "Entry" ) + " --> " + props.get ( "Entry" ) + " : " + getAttrValue ( node, "doc:name" );
      }
      case "db:update":
      case "db:select":
      case "db:delete":

        String entry = props.get ( "Entry" );
        if ( entry != null && entry.isEmpty () )
          return "";
        return entry + "--> " + esc ( node.getNodeName () );

      default:
        return "";
    }
//    return null;

  }

  public String getAttrValue ( Node node, String attributeName ) {
    if ( node.getAttributes () != null && node.getAttributes ().getNamedItem ( attributeName ) != null )
      return node.getAttributes ().getNamedItem ( attributeName ).getNodeValue ();
    else
      return "";
  }

  public String after ( Document document, Node node, Map<String, String> props ) {
    switch ( node.getNodeName () ) {
      case "flow": {
        props.remove ( "Entry" );
        return "@enduml";
      }
      case "choice":
      case "foreach":
        return "end alt";
      default:
        return "";
    }
  }

  public void print ( String str, PrintWriter out ) {
    if ( str.isEmpty () )
      return;

    out.println ( str );
  }

  public void walk ( Document base, Node node, Map<String, String> props, PrintWriter out ) throws XPathExpressionException {

    print ( before ( base, node, props ), out );

    NodeList nodeList = node.getChildNodes ();

    for ( int i = 0; i < nodeList.getLength (); i++ ) {

      Node currentNode = nodeList.item ( i );

      if ( currentNode.getNodeType () == Node.ELEMENT_NODE ) {

        walk ( base, currentNode, props, out );

      }

    }

    print ( after ( base, node, props ), out );
  }

}
