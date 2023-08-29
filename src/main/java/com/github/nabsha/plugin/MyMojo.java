package com.github.nabsha.plugin;


import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.*;
import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.github.nabsha.plugin.CommonUtils.printStackTrace;
import static com.github.nabsha.plugin.FileUtils.*;
import static com.github.nabsha.plugin.XMLUtils.*;


/**
 *
 */
@Mojo(name = "mule2puml", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class MyMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.basedir}", property = "muleSourceDir", required = true)
    private File muleSourceDir;

    @Parameter(defaultValue = "false", property = "isMultiMuleProject", required = false)
    private boolean isMultiMuleProject;

    @Parameter(defaultValue = "/.*/src/main/app/.*\\.xml", property = "muleFilesPathRegex", required = false)
    private String muleFilesPathRegex;

    @Parameter(defaultValue = "(^(?!.*(target|test)).*).*.properties", property = "propertiesFilesPathRegex", required = false)
    private String propertiesFilesPathRegex;

    @Parameter(defaultValue = "true", property = "generateUberMuleXML", required = false)
    private boolean generateUberMuleXML;

    @Parameter(defaultValue = "${project.build.directory}/docs/mule", property = "outputUberMuleXMLPath", required = false)
    private File outputUberMuleXMLPath;

    @Parameter(defaultValue = "${project.build.directory}/docs/puml", property = "pumlOutputDir", required = false)
    private File pumlOutputDir;

    @Parameter(defaultValue = "false", property = "useFlowName", required = false)
    private boolean useFlowName;

    @Parameter(defaultValue = "1", property = "detailLevel", required = false)
    private int detailLevel;

    @Parameter(property = "entryXPathFilters", required = false)
    private List<String> entryXPathFilters;

    @Parameter(defaultValue = "true", property = "sequenceDiagram", required = false)
    private boolean sequenceDiagram;

    @Parameter(defaultValue = "false", property = "interactionDiagram", required = false)
    private boolean interactionDiagram;

    public void execute() throws MojoExecutionException {

        if (entryXPathFilters == null || entryXPathFilters.isEmpty()) {
            entryXPathFilters = getResourceFileAsList("entryXPath.cfg");
        }

        Map<String, List<Path>> filesGroup = getMuleProjectFiles(muleSourceDir.getAbsolutePath(), muleFilesPathRegex, isMultiMuleProject);

        Map<String, String> cliOptions = new HashMap<>();
        cliOptions.put("detailLevel", detailLevel + "");
        cliOptions.put("useFlowName", useFlowName + "");
        cliOptions.put("pumlOutputDir", pumlOutputDir.getAbsolutePath());


        SequenceDiagramGenerator sequenceDiagramGenerator = new SequenceDiagramGenerator(getLog(), cliOptions);
        PlantUMLInteractionDiagramGenerator interactionDiagramGenerator = new PlantUMLInteractionDiagramGenerator(getLog(), cliOptions);

        if (filesGroup == null) return;

        for (Map.Entry<String, List<Path>> entry : filesGroup.entrySet()) {
            String folderBasePath = entry.getKey();

            String propertiesSearchBasePath = muleSourceDir.getAbsolutePath() + "/";
            String muleUberXMLFilePath = outputUberMuleXMLPath.toString();
            String pumlOutputPath = pumlOutputDir.getAbsolutePath().toString();

            if (isMultiMuleProject) {
                propertiesSearchBasePath = muleSourceDir.getAbsolutePath() + "/" + folderBasePath;
                muleUberXMLFilePath = outputUberMuleXMLPath.toString() + "/" + folderBasePath;
                pumlOutputPath = pumlOutputDir.getAbsolutePath().toString() + "/" + folderBasePath;
            }

            List<Path> files = entry.getValue();

            Document muleUberXML = null;
            try {
                muleUberXML = createMuleUberXML(files);
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to merge documents " + printStackTrace(e));
            }

            try {
                replaceProperties(muleUberXML, propertiesSearchBasePath, propertiesFilesPathRegex);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to replace properties" + printStackTrace(e));
            }

            try {
                injectHTTPListenerForAPIKitFlows(muleUberXML);
            } catch (XPathExpressionException e) {
                throw new MojoExecutionException("Failed to inject HTTP Listener for APIKit generated flows " + printStackTrace(e));
            }

            try {
                writeMuleUberXML(muleUberXML, new File(muleUberXMLFilePath));
            } catch (FileNotFoundException e) {
                throw new MojoExecutionException("Write Uber XML Failed : " + printStackTrace(e));
            }

            String joined = String.join(" | ", entryXPathFilters);
            getLog().info("Filtering Entry flows using " + joined);

            try {
                NodeList results = findNodeByXPath(joined, muleUberXML);

                for (int i = 0; i < results.getLength(); i++) {
                    if (sequenceDiagram) {
                        getLog().info("Generating Sequence Diagram for " + getAttrValue(results.item(i), "name"));
                        sequenceDiagramGenerator.generate(muleUberXML, results.item(i));
                    }
                    if (interactionDiagram) {
                        getLog().info("Generating Interaction Diagram for " + getAttrValue(results.item(i), "name"));
                        interactionDiagramGenerator.generate(muleUberXML, results.item(i));
                    }

                }
            } catch (XPathExpressionException e) {
                throw new MojoExecutionException("Generate Puml failed: Failed to find node using xpath " + printStackTrace(e));
            }
        }

    }


    private void writeMuleUberXML(Document muleUberXML, File outputPath) throws FileNotFoundException {
        if (generateUberMuleXML) {
            getLog().info("Writing mule uber xml to " + outputPath.getAbsolutePath());
            File file = new File(outputPath.getAbsolutePath() + "/mule.xml");
            file.getParentFile().mkdirs();
            try (PrintWriter out = new PrintWriter(file)) {
                out.println(docToString(muleUberXML));
            }
        }
    }

    private void injectHTTPListenerForAPIKitFlows(Document reduced) throws XPathExpressionException {
        NodeList apikitFlows = findNodeByXPath("/*[local-name()='mule']/*[local-name()='flow'][contains(@name, 'get:') or contains(@name, 'put:') or contains(@name, 'post:') or contains(@name, 'patch:') or contains(@name, 'delete:') or contains (@name, 'head:')]", reduced);
        for (int i = 0; i < apikitFlows.getLength(); i++) {
            Node item = apikitFlows.item(i);
            String name = getAttrValue(item, "name");
            Pattern pattern = Pattern.compile("[^:]*:([^:]*):.*");
            Matcher matcher = pattern.matcher(name);

            if (matcher.find()) {
                String group = matcher.group(1);
                NodeList pathAttr = findNodeByXPath(".//*[local-name()='listener']/@path", item.getOwnerDocument());

                Node path = pathAttr.item(0);
                String pathValue = path.getNodeValue().replace("*", "");
                String finalPath = pathValue + group;
                path.setNodeValue(finalPath.replace("//", "/"));
            }
        }
    }

    private void replaceProperties(Document muleUberXML, String absolutePath, String propertiesFilesPathRegex) throws IOException {
        // replace properties with values
        getLog().info("Searching for " + propertiesFilesPathRegex + " in " + absolutePath);

        List<Path> propertiesFiles = searchFiles(absolutePath, propertiesFilesPathRegex);
        getLog().info("Properties files found : " + propertiesFiles);

        propertiesFiles.forEach(path -> {
            getLog().info("Find and replace properties from " + path.toAbsolutePath().toString());
            Properties properties = new Properties();
            try {
                properties.load(new FileInputStream(path.toFile()));
                properties.forEach((k, v) -> {
                    try {

                        NodeList nodeByXPath = findNodeByXPath("//attribute::*[contains(., '${" + k + "}')]", muleUberXML);
                        for (int i = 0; i < nodeByXPath.getLength(); i++) {
                            Node item = nodeByXPath.item(i);
                            item.setNodeValue(v.toString());
                        }
                    } catch (XPathExpressionException e) {
                        e.printStackTrace();
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private Document createMuleUberXML(List<Path> files) throws Exception {
        Stream<File> fileStream = files.stream().map(Path::toFile);
        Properties namespaces = new Properties();
        namespaces.load(this.getClass().getClassLoader().getResourceAsStream("default-namespaces.properties"));
        Document merge = merge("/mule", namespaces, fileStream.toArray(File[]::new));
        String mergedDoc = docToString(merge);
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setNamespaceAware(true);
        DocumentBuilder docBuilder = null;
        docBuilder = docBuilderFactory.newDocumentBuilder();
        getLog().info("Parsing mule uber xml");
        Document base = docBuilder.parse(new InputSource(new StringReader(mergedDoc)));
        getLog().info("Transforming mule uber xml");
        Document transformed = transform(base, this.getClass().getClassLoader().getResourceAsStream("flatten-stage1.xsl"));
        getLog().info("Reducing mule uber xml");
        return transform(transformed, this.getClass().getClassLoader().getResourceAsStream("reduce-stage2.xsl"));
    }

    private Map<String, List<Path>> getMuleProjectFiles(String absolutePath, String muleFilesPathRegex, boolean isMultiMuleProject) throws MojoExecutionException {
        Map<String, List<Path>> filesMap = null;

        try {
            getLog().info("Searching for mule files in " + absolutePath + " with regex " + muleFilesPathRegex);
            filesMap = searchAndGroupFiles(absolutePath, muleFilesPathRegex, isMultiMuleProject);

            for (Map.Entry<String, List<Path>> entry : filesMap.entrySet()) {
                List<Path> files = entry.getValue();
                files.forEach(file -> {
                    getLog().info("Found : " + file.toString());
                });
            }

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to search mule source files in " + absolutePath);
        }
        if (filesMap.isEmpty()) {
            getLog().info("No files found in " + absolutePath + " with " + muleFilesPathRegex + " pattern");
            return null;
        }
        return filesMap;
    }


    private Document merge(String expression, Properties defaultNamespaces, File... files) throws Exception {
        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xpath = xPathFactory.newXPath();
        XPathExpression compiledExpression = xpath.compile(expression);
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        docBuilderFactory.setIgnoringElementContentWhitespace(true);
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();

        getLog().info("Merging files...");
        Document base = docBuilder.parse(files[0]);

        defaultNamespaces.stringPropertyNames().forEach(key -> {
            base.getDocumentElement().setAttribute(key, defaultNamespaces.getProperty(key));
        });

        Node results = (Node) compiledExpression.evaluate(base, XPathConstants.NODE);
        if (results == null) {
            throw new IOException(files[0]
                    + ": expression does not evaluate to node");
        }

        for (int i = 1; i < files.length; i++) {
            getLog().info("Merging " + files[i].getName());
            Document merge = docBuilder.parse(files[i]);
            Node nextResults = (Node) compiledExpression.evaluate(merge,
                    XPathConstants.NODE);
            while (nextResults.hasChildNodes()) {
                Node kid = nextResults.getFirstChild();
                nextResults.removeChild(kid);
                kid = base.importNode(kid, true);
                results.appendChild(kid);
            }
        }

        return base;
    }




}
