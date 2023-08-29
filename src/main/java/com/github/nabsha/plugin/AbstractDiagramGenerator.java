package com.github.nabsha.plugin;

import org.apache.maven.plugin.logging.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static com.github.nabsha.plugin.CommonUtils.esc;
import static com.github.nabsha.plugin.XMLUtils.getAttrValue;

public abstract class AbstractDiagramGenerator implements DiagramGenerator {
    protected Map<String, String> cliOptions;
    protected final Log log;

    protected int detailLevel;

    public void _generate(Document muleUberXML, Node item, String filePrefix, String fileExtension) {
        Map<String, String> props = new HashMap<>();
        String name = removeCharacters(getAttrValue(item, "name"));
        File file = new File(cliOptions.get("pumlOutputDir") + "/" + filePrefix + "_" + name + fileExtension);
        log.info("Creating file " + file.getAbsolutePath());
        file.getParentFile().mkdirs();

        try {
            PrintWriter out = new PrintWriter(file);
            walk(muleUberXML, item, props, out);
            out.close();
            Path path = file.toPath();

            if (Files.size(path) < 1) {
                log.info("Deleting empty file " + file.getAbsolutePath());
                Files.delete(path);
            }
        } catch (Exception e) {
            log.error("Error creating file " + file.getAbsolutePath(), e);
        }

    }
    protected AbstractDiagramGenerator(Log log, Map<String, String> cliOptions) {
        this.log = log;
        this.cliOptions = cliOptions;
        this.detailLevel = Integer.parseInt(cliOptions.getOrDefault("detailLevel", "1"));
    }

    public void print(String str, PrintWriter out) {
        if (str.isEmpty())
            return;
        out.println(str);
    }

    protected String removeCharacters(String str) {
        return str.replace(":", "_")
                .replace("/", "_")
                .replace("{", "_")
                .replace("}", "_")
                .replace("__", "_");
    }
    public void walk(Document base, Node node, Map<String, String> props, PrintWriter out) throws XPathExpressionException {
        print(before(base, node, props), out);
        NodeList nodeList = node.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node currentNode = nodeList.item(i);
            if (currentNode.getNodeType() == Node.ELEMENT_NODE) {
                walk(base, currentNode, props, out);
            }
        }
        print(after(base, node, props), out);
    }



}
