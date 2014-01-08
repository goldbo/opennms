/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2011-2012 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2012 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.core.test.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;
import org.custommonkey.xmlunit.DetailedDiff;
import org.custommonkey.xmlunit.Difference;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.xml.CastorUtils;
import org.opennms.core.xml.JaxbUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;

@RunWith(Parameterized.class)
abstract public class XmlTest<T> {
    private static final Logger LOG = LoggerFactory.getLogger(XmlTest.class);

    private T m_sampleObject;
    private Object m_sampleXml;
    private String m_schemaFile;
    private static List<String> m_ignoredNamespaces = new ArrayList<String>();
    private static List<String> m_ignoredPrefixes   = new ArrayList<String>();

    public XmlTest(final T sampleObject, final Object sampleXml, final String schemaFile) {
        this(sampleObject, sampleXml, schemaFile, null, null);
    }

    public XmlTest(final T sampleObject, final Object sampleXml, final String schemaFile, final String[] ignoredNamespaces, final String[] ignoredPrefixes) {
        m_sampleObject = sampleObject;
        m_sampleXml = sampleXml;
        m_schemaFile = schemaFile;
        if (ignoredNamespaces != null) {
            for (final String ignore : ignoredNamespaces) {
                m_ignoredNamespaces.add(ignore);
            }
        }
        if (ignoredPrefixes != null) {
            for (final String ignore : ignoredPrefixes) {
                m_ignoredPrefixes.add(ignore);
            }
        }
    }

    protected T getSampleObject() {
        return m_sampleObject;
    }

    protected String getSampleXml() throws IOException {
        if (m_sampleXml instanceof File) {
            return IOUtils.toString(((File)m_sampleXml).toURI());
        } else if (m_sampleXml instanceof URI) {
            return IOUtils.toString((URI)m_sampleXml);
        } else if (m_sampleXml instanceof URL) {
            return IOUtils.toString((URL)m_sampleXml);
        } else {
            return m_sampleXml.toString();
        }
    }

    protected ByteArrayInputStream getSampleXmlInputStream() throws IOException {
        return new ByteArrayInputStream(getSampleXml().getBytes());
    }

    @SuppressWarnings("unchecked")
    private Class<T> getSampleClass() {
        return (Class<T>) getSampleObject().getClass();
    }

    @Before
    public void setUp() {
        MockLogAppender.setupLogging(true);
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);
        XMLUnit.setIgnoreComments(true);
        XMLUnit.setIgnoreDiffBetweenTextAndCDATA(true);
        XMLUnit.setNormalize(true);
    }

    public static boolean ignoreAllNamespaceDifferences() {
        return true;
    }

    public static boolean ignoreAllPrefixDifferences() {
        return false;
    }

    @Test
    public void marshalCastorAndCompareToXml() throws Exception {
        final String xml = marshalToXmlWithCastor();
        assertXmlEquals(getSampleXml(), xml);
    }

    @Test
    public void marshalJaxbAndCompareToXml() throws Exception {
        final String xml = marshalToXmlWithJaxb();
        assertXmlEquals(getSampleXml(), xml);
    }

    @Test
    public void unmarshalXmlAndCompareToCastor() throws Exception {
        final T obj = CastorUtils.unmarshal(getSampleClass(), getSampleXmlInputStream());
        LOG.debug("Sample object: {}\n\nCastor object: {}", getSampleObject(), obj);
        assertTrue("objects should match", getSampleObject().equals(obj));
    }

    @Test
    public void unmarshalXmlAndCompareToJaxb() throws Exception {
        LOG.debug("xml: {}", getSampleXml());
        final T obj = JaxbUtils.unmarshal(getSampleClass(), new InputSource(getSampleXmlInputStream()), null);
        LOG.debug("Sample object: {}\n\nJAXB object: {}", getSampleObject(), obj);
        assertTrue("objects should match", getSampleObject().equals(obj));
    }

    @Test
    public void marshalCastorUnmarshalJaxb() throws Exception {
        final String xml = marshalToXmlWithCastor();

        final T config = JaxbUtils.unmarshal(getSampleClass(), xml);

        LOG.debug("Generated Object: {}", config);

        assertTrue("objects should match", config.equals(getSampleObject()));
    }

    @Test
    public void marshalJaxbUnmarshalCastor() throws Exception {
        final String xml = marshalToXmlWithJaxb();

        final T config = CastorUtils.unmarshal(getSampleClass(), new ByteArrayInputStream(xml.getBytes()));
        LOG.debug("Generated Object: {}", config);

        assertTrue("objects should match", config.equals(getSampleObject()));
    }

    @Test
    public void validateCastorObjectAgainstSchema() throws Exception {
        org.exolab.castor.xml.Unmarshaller unmarshaller = CastorUtils.getUnmarshaller(getSampleClass());
        unmarshaller.setValidation(true);
        @SuppressWarnings("unchecked")
        T obj = (T) unmarshaller.unmarshal(new InputSource(getSampleXmlInputStream()));
        assertNotNull(obj);
    }

    @Test
    public void validateJaxbXmlAgainstSchema() throws Exception {
        LOG.debug("Validating against XSD: {}", m_schemaFile);
        javax.xml.bind.Unmarshaller unmarshaller = JaxbUtils.getUnmarshallerFor(getSampleClass(), null, true);
        final SchemaFactory factory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
        final Schema schema = factory.newSchema(new StreamSource(m_schemaFile));
        unmarshaller.setSchema(schema);
        unmarshaller.setEventHandler(new ValidationEventHandler() {
            @Override
            public boolean handleEvent(final ValidationEvent event) {
                LOG.warn("Received validation event: {}", event, event.getLinkedException());
                return false;
            }
        });
        try {
            final InputSource inputSource = new InputSource(getSampleXmlInputStream());
            final XMLFilter filter = JaxbUtils.getXMLFilterForClass(getSampleClass());
            final SAXSource source = new SAXSource(filter, inputSource);
            @SuppressWarnings("unchecked")
            T obj = (T) unmarshaller.unmarshal(source);
            assertNotNull(obj);
        } finally {
            unmarshaller.setSchema(null);
        }
    }

    protected void validateXmlString(final String xml) throws Exception {
        if (m_schemaFile == null) {
            LOG.warn("skipping validation, schema file not set");
            return;
        }

        final SchemaFactory schemaFactory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
        final File schemaFile = new File(m_schemaFile);
        LOG.debug("Validating using schema file: {}", schemaFile);
        final Schema schema = schemaFactory.newSchema(schemaFile);

        final SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        saxParserFactory.setValidating(true);
        saxParserFactory.setNamespaceAware(true);
        saxParserFactory.setSchema(schema);

        assertTrue("make sure our SAX implementation can validate", saxParserFactory.isValidating());

        final Validator validator = schema.newValidator();
        final ByteArrayInputStream inputStream = new ByteArrayInputStream(xml.getBytes());
        final Source source = new StreamSource(inputStream);

        validator.validate(source);
    }

    protected String marshalToXmlWithCastor() {
        LOG.debug("Reference Object: {}", getSampleObject());

        final StringWriter writer = new StringWriter();
        CastorUtils.marshalWithTranslatedExceptions(getSampleObject(), writer);
        final String xml = writer.toString();
        LOG.debug("Castor XML: {}", xml);
        return xml;
    }

    protected String marshalToXmlWithJaxb() {
        LOG.debug("Reference Object: {}", getSampleObject());

        final StringWriter writer = new StringWriter();
        JaxbUtils.marshal(getSampleObject(), writer);
        final String xml = writer.toString();
        LOG.debug("JAXB XML: {}", xml);
        return xml;
    }

    public static void assertXmlEquals(final String expectedXml, final String actualXml) throws Exception {
        final List<Difference> differences = getDifferences(expectedXml, actualXml);
        if (differences.size() > 0) {
            LOG.debug("XML:\n\n{}\n\n...does not match XML:\n\n{}", expectedXml, actualXml);
        }
        assertEquals("number of XMLUnit differences between the expected xml and the actual xml should be 0", 0, differences.size());
    }

    protected static List<Difference> getDifferences(final String xmlA, final String xmlB) throws SAXException, IOException {
        final DetailedDiff myDiff = new DetailedDiff(XMLUnit.compareXML(xmlA, xmlB));
        final List<Difference> retDifferences = new ArrayList<Difference>();
        @SuppressWarnings("unchecked")
        final List<Difference> allDifferences = myDiff.getAllDifferences();
        if (allDifferences.size() > 0) {
            DIFFERENCES: for (final Difference d : allDifferences) {
                final String control = d.getControlNodeDetail().getValue();
                final String test = d.getTestNodeDetail().getValue();

                if (d.getDescription().equals("namespace URI")) {
                    for (final String namespace : m_ignoredNamespaces) {
                        if (control != null && !"null".equals(control)) {
                            if (control.equalsIgnoreCase(namespace)) {
                                LOG.trace("Ignoring {}: {}", d.getDescription(), d);
                                continue DIFFERENCES;
                            }
                        }
                        if (test != null && !"null".equals(test)) {
                            if (test.equalsIgnoreCase(namespace)) {
                                LOG.trace("Ignoring {}: {}", d.getDescription(), d);
                                continue DIFFERENCES;
                            }
                        }
                    }
                    if (ignoreAllNamespaceDifferences()) {
                        LOG.trace("Found difference: {}: {} (IGNORED, ignoreAllNamespaceDifferences=true)", d.getDescription(), d);
                        continue DIFFERENCES;
                    }
                } else if (d.getDescription().equals("namespace prefix")) {
                    for (final String namespace : m_ignoredPrefixes) {
                        if (control != null && !"null".equals(control)) {
                            if (control.equalsIgnoreCase(namespace)) {
                                LOG.trace("Ignoring {}: {}", d.getDescription(), d);
                                continue DIFFERENCES;
                            }
                        }
                        if (test != null && !"null".equals(test)) {
                            if (test.equalsIgnoreCase(namespace)) {
                                LOG.trace("Ignoring {}: {}", d.getDescription(), d);
                                continue DIFFERENCES;
                            }
                        }
                    }
                    if (ignoreAllPrefixDifferences()) {
                        LOG.trace("Found difference: {}: {} (IGNORED, ignoreAllPrefixDifferences=true)", d.getDescription(), d);
                        continue DIFFERENCES;
                    }
                }

                LOG.warn("Found difference: {}: {}", d.getDescription(), d);
                retDifferences.add(d);
            }
        }
        return retDifferences;
    }

    public static void assertXpathDoesNotMatch(final String xml, final String expression) throws XPathExpressionException {
        assertXpathDoesNotMatch(null, xml, expression);
    }

    public static void assertXpathDoesNotMatch(final String description, final String xml, final String expression) throws XPathExpressionException {
        final NodeList nodes = xpathGetNodesMatching(xml, expression);
        assertTrue(description == null? ("Must get at least one node back from the query '" + expression + "'") : description, nodes == null || nodes.getLength() == 0);
    }

    public static void assertXpathMatches(final String xml, final String expression) throws XPathExpressionException {
        assertXpathMatches(null, xml, expression);
    }

    public static void assertXpathMatches(final String description, final String xml, final String expression) throws XPathExpressionException {
        final NodeList nodes = xpathGetNodesMatching(xml, expression);
        assertTrue(description == null? ("Must get at least one node back from the query '" + expression + "'") : description, nodes != null && nodes.getLength() != 0);
    }

    protected static NodeList xpathGetNodesMatching(final String xml, final String expression) throws XPathExpressionException {
        final XPath query = XPathFactory.newInstance().newXPath();
        StringReader sr = null;
        InputSource is = null;
        NodeList nodes = null;
        try {
            sr = new StringReader(xml);
            is = new InputSource(sr);
            nodes = (NodeList)query.evaluate(expression, is, XPathConstants.NODESET);
        } finally {
            sr.close();
            IOUtils.closeQuietly(sr);
        }
        return nodes;
    }

}
