package com.github.nabsha.plugin;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.github.nabsha.plugin.CommonUtils.esc;
import static com.github.nabsha.plugin.XMLUtils.findNodeByXPath;
import static com.github.nabsha.plugin.XMLUtils.getAttrValue;

public class SequenceDiagramGenerator extends AbstractDiagramGenerator implements DiagramGenerator {

    SortedSet<String> ignoredTags = new TreeSet<String>();
    SequenceDiagramGenerator(org.apache.maven.plugin.logging.Log log, Map<String, String> cliOptions ) {
        super(log, cliOptions);
    }

    @Override
    public void generate(Document muleUberXML, Node item) {
        _generate(muleUberXML, item, "seq", ".puml");
        log.info("Ignored tags: " + ignoredTags);
    }

    @Override

    public String before(Document document, Node node, Map<String, String> props) throws XPathExpressionException {

        switch (node.getNodeName()) {
            case "flow":
                String doc = getAttrValue(node, "doc:description");
                if (doc != null && doc.contains("mule2puml-ignore-router-flow"))
                    return "";
                String name = getAttrValue(node, "name");
                if (name != null)
                    props.put("FlowName", removeCharacters(name));

                String msg = "@startuml\n";
                msg += "title Sequence Diagram for " + getAttrValue(node, "name") + "\n"
                        + "!theme plain\n";

                return msg;

            case "sqs:receive-messages": {

                String configName = getAttrValue(node, "config-ref");

                NodeList nodeByXPath = findNodeByXPath("/*[local-name()='mule']/*[local-name()='config'][@name='" + configName + "']", document);
                NamedNodeMap configAttr = nodeByXPath.item(0).getAttributes();

                String value = "";
                if ((Objects.equals(cliOptions.get("useFlowName"), "true")) && props.get("FlowName") != null) {
                    value = props.get("FlowName");
                } else {
                    value = esc(getAttrValue(node, "queueUrl"));
                }
                props.put("Entry", value);
                return "";
            }
            case "http:listener": {

                String configName = getAttrValue(node, "config-ref");

                NodeList nodeByXPath = findNodeByXPath("/*[local-name()='mule']/*[local-name()='listener-config'][@name='" + configName + "']", document);
                NamedNodeMap configAttr = nodeByXPath.item(0).getAttributes();

                String value = "";
                if ((Objects.equals(cliOptions.get("useFlowName"), "true")) && props.get("FlowName") != null) {
                    value = props.get("FlowName");
                } else {
                    value = esc(getAttrValue(node, "path"));
                }
                props.put("Entry", value);
                return "";
            }

            case "http:request": {
                String configName = getAttrValue(node, "config-ref");
                NodeList nodeByXPath = findNodeByXPath("/*[local-name()='mule']/*[local-name()='request-config'][@name='" + configName + "']", document);
                Node configAttr = nodeByXPath.item(0);
                String url = getAttrValue(configAttr, "protocol") + "://"
                        + getAttrValue(configAttr, "host") + ":"
                        + getAttrValue(configAttr, "port")
                        + getAttrValue(configAttr, "basePath")
                        + getAttrValue(node, "path");

                return props.get("Entry") + "-->" + esc(url) + ":" + getAttrValue(node, "doc:name");
            }

            case "jms:inbound-endpoint": {
                String jmsIn = esc(getAttrValue(node, "queue"));
                props.put("Entry", jmsIn);
                return "";

            }

            case "choice":
            case "transactional":
            case "foreach": {
                return "alt " + getAttrValue(node, "doc:name");
            }

            case "otherwise":
                return "else otherwise";
            case "when":
                return "else " + esc(getAttrValue(node, "expression"));

            case "expression-component":
            case "expression":
            case "invoke":
            case "dw:transform-message":
            case "transformer":
            case "validation:is-not-empty":
                if (detailLevel < 2) return "";
                return props.get("Entry") + " --> " + props.get("Entry") + " : " + getAttrValue(node, "doc:name");


            case "db:update":
            case "db:select":
            case "db:delete":
            case "db:insert":
            case "db:dynamic-query":

            case "objectstore:contains":
            case "objectstore:retrieve":
            case "objectstore:remove":
            case "objectstore:store":

            case "sqs:send-message":
                return (detailLevel < 2) ? "" : getDefaultTransform(node, props);

            default:
                if (detailLevel < 3) ignoredTags.add(node.getNodeName());
                return (detailLevel < 3) ? "" : getDefaultTransform(node, props);
        }
    }


    @Override
    public String after(Document document, Node node, Map<String, String> props) throws XPathExpressionException {
        switch (node.getNodeName()) {
            case "flow": {
                props.remove("Entry");

                // skip this flow
                String doc = getAttrValue(node, "doc:description");
                if (doc != null && doc.contains("mule2puml-ignore-router-flow"))
                    return "";

                return "@enduml";
            }
            case "http:request": {
                String configName = getAttrValue(node, "config-ref");
                NodeList nodeByXPath = findNodeByXPath("/*[local-name()='mule']/*[local-name()='request-config'][@name='" + configName + "']", document);
                Node configAttr = nodeByXPath.item(0);
                String url = getAttrValue(configAttr, "protocol") + "://"
                        + getAttrValue(configAttr, "host") + ":"
                        + getAttrValue(configAttr, "port")
                        + getAttrValue(configAttr, "basePath")
                        + getAttrValue(node, "path");

                return esc(url) + "-->" + props.get("Entry");
            }

            case "choice":
            case "transactional":
            case "foreach":
                return "end alt";
            default:
                return "";
        }
    }

    private String getDefaultTransform(Node node, Map<String, String> props) {
        String entry = props.get("Entry");
        if (entry != null && entry.isEmpty())
            return "";
        return entry + "--> " + esc(node.getNodeName()) + ":" + getAttrValue(node, "doc:name");

    }
}
