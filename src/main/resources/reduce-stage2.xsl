<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:ns0="http://www.mulesoft.org/schema/mule/core"
                xmlns:apikit="http://www.mulesoft.org/schema/mule/apikit"
                xmlns:http="http://www.mulesoft.org/schema/mule/http"
                xmlns:jms="http://www.mulesoft.org/schema/mule/jms"
                xmlns:sqs="http://www.mulesoft.org/schema/mule/sqs">

    <xsl:output omit-xml-declaration="yes" indent="yes"/>
    <xsl:strip-space elements="*"/>

    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>


    <xsl:template match="/ns0:mule/ns0:sub-flow"/>


    <xsl:template match="/ns0:mule/ns0:flow">
        <xsl:if test="current()/http:listener or current()/jms:inbound-endpoint or current()/sqs:receive-messages">
            <xsl:copy>
                <xsl:apply-templates select="@*|node()"/>
            </xsl:copy>
        </xsl:if>
    </xsl:template>

</xsl:stylesheet>