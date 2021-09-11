package ru.demo.servicemock;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.custommonkey.xmlunit.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.yaml.snakeyaml.Yaml;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MockHttpServlet extends HttpServlet {

    private static final Logger logger = LogManager.getLogger(MockHttpServlet.class);

    private final List<ServiceMock> serviceMocks;

    private final Executor callbackExecutor = Executors.newWorkStealingPool();

    public MockHttpServlet() throws IOException, ParserConfigurationException, SAXException {
        XMLUnit.setIgnoreComments(true);
        XMLUnit.setIgnoreWhitespace(true);

        // в yaml файле лежат дефолтные пары request -> response, которые грузятся и используются при отсутствии CustomMock
        Yaml yaml = new Yaml();
        File applicationYamlFile = new File(System.getProperty("catalina.base") + "/conf/service-mock/application.yml");
        if (!applicationYamlFile.exists()) {
            throw new FileNotFoundException("File not found: " + applicationYamlFile);
        }

        Map applicationYamlFileMap;
        try (InputStream inputStream = new FileInputStream(applicationYamlFile)) {
            applicationYamlFileMap = yaml.load(inputStream);
        }

        List<Map> serviceMocksYaml = (List<Map>) applicationYamlFileMap.get("general");
        serviceMocks = new ArrayList<>(serviceMocksYaml.size());

        DocumentBuilder documentBuilder = DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder();

        for (Map requestResponseMap : serviceMocksYaml) {
            File requestFile = new File((String) requestResponseMap.get("request"));
            if (!requestFile.exists()) {
                logger.warn("Файл не найден: {}", requestFile);
                continue;
            }

            File responseFile = new File((String) requestResponseMap.get("response"));
            if (!responseFile.exists()) {
                logger.warn("Файл не найден: {}", responseFile);
                continue;
            }

            Document requestDocument = documentBuilder.parse(requestFile),
                    responseDocument = documentBuilder.parse(responseFile);

            ServiceMock serviceMock = new ServiceMock(requestDocument, responseDocument);

            Set<String> variables = fetchVariables(requestFile);
            serviceMock.setVariables(variables);

            Integer statusCode = (Integer) requestResponseMap.get("status");
            if (statusCode != null && statusCode != 0) {
                serviceMock.setResponseStatus(statusCode);
            }

            Map callbackYamlMap = (Map) requestResponseMap.get("callback");
            if (callbackYamlMap != null) {
                String callbackEndpoint = (String) callbackYamlMap.get("endpoint");
                serviceMock.setCallbackEndpoint(callbackEndpoint);

                File callbackBodyFile = new File((String) callbackYamlMap.get("body"));
                if (!callbackBodyFile.exists()) {
                    logger.warn("Файл не найден: {}", callbackBodyFile);
                    continue;
                }

                serviceMock.setCallbackBodyFile(callbackBodyFile);

                Integer callbackDelayMillis = (Integer) callbackYamlMap.get("delay_millis");
                if (callbackDelayMillis != null) {
                    serviceMock.setCallbackDelayMillis(callbackDelayMillis.longValue());
                }
            }

            serviceMocks.add(serviceMock);
        }
    }

    private Set<String> fetchVariables(File requestFile) throws IOException {
        Set<String> result = new HashSet<>();
        Pattern pattern = Pattern.compile("#\\{.+?}");
        String fileContent = FileUtils.readFileToString(requestFile, "UTF-8");
        Matcher matcher = pattern.matcher(fileContent);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logger.info("Пришел входящий реквест в часть, отвечающую за поиск моков");
        DocumentBuilder documentBuilder;
        try {
            documentBuilder = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder();
        } catch (ParserConfigurationException ex) {
            logger.error(ex.getMessage());
            throw new ServletException("Unable to create document builder", ex);
        }

        InputSource inputSource = new InputSource(request.getReader());

        Document actualRequestDocument;
        try {
            actualRequestDocument = documentBuilder.parse(inputSource);
        } catch (SAXException ex) {
            logger.error(ex.getMessage());
            throw new ServletException("Unable to parse xml document", ex);
        }
        logger.info("Входящий запрос: " + toString(actualRequestDocument));

        MockRestServlet.incomingRequests.add(new IncomingRequest(actualRequestDocument, System.currentTimeMillis()));

        NodeList nodeList = actualRequestDocument.getElementsByTagName("*");
        CustomMock goodMock = null;

        logger.info("Поиск динамического респонса");
        for (Queue<CustomMock> queueMock : MockRestServlet.newCustomMocks) {
            CustomMock firstMock = queueMock.peek();
            if (firstMock == null)
                continue;

            if (checkAllNodes(nodeList, firstMock.getField(), firstMock.getValue())) {
                goodMock = queueMock.poll();
                break;
            }
        }

        if (goodMock != null) {
            logger.info("Был найден подходящий динамический ответ");
            Document responseDocument = goodMock.getDocument();
            logger.info("Динамический ответ: " + toString(responseDocument));
            // TODO может передавать и статус код тип?
            writeXmlResponse(response, responseDocument, Collections.emptyMap(), 200);
            return;
        }

        logger.info("Не был найден динамический мок, fallback к старой схеме моков");
        for (ServiceMock serviceMock : serviceMocks) {
            Document expectedRequestDocument = serviceMock.getRequest();
            Set<String> variables = serviceMock.getVariables();
            Map<String, String> variableValues = new HashMap<>();
            Diff diff = new Diff(expectedRequestDocument, actualRequestDocument);
            diff.overrideDifferenceListener(new DifferenceListener() {
                @Override
                public int differenceFound(Difference difference) {
                    NodeDetail controlNodeDetail = difference.getControlNodeDetail();
                    String controlNodeDetailValue = controlNodeDetail.getValue();
                    if (variables.contains(controlNodeDetailValue)) {
                        NodeDetail testNodeDetail = difference.getTestNodeDetail();
                        String testNodeDetailValue = testNodeDetail.getValue();
                        variableValues.put(controlNodeDetailValue, testNodeDetailValue);
                        return DifferenceListener.RETURN_IGNORE_DIFFERENCE_NODES_IDENTICAL;
                    } else {
                        return DifferenceListener.RETURN_ACCEPT_DIFFERENCE;
                    }
                }

                @Override
                public void skippedComparison(Node control, Node test) {
                    // no operation
                }
            });

            if (diff.identical()) {
                Document responseDocument = serviceMock.getResponse();
                logger.info("Найденный ответ по старой схеме: " + toString(responseDocument));
                writeXmlResponse(response, responseDocument, variableValues, serviceMock.getResponseStatus());
                if (serviceMock.hasCallback()) {
                    CallbackTask callbackTask = new CallbackTask(serviceMock.getCallbackEndpoint(),
                            serviceMock.getCallbackBodyFile(), variableValues,
                            serviceMock.getCallbackDelayMillis());
                    callbackExecutor.execute(callbackTask);
                }
                return;
            }

            logger.info("Ответ по старой схеме не был найден");
        }

        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    private void writeXmlResponse(HttpServletResponse response, Document document, Map<String, String> variableValues, int status) throws IOException, ServletException {
        response.setContentType("text/xml;charset=UTF-8");

        String xmlString = toString(document);

        for (Map.Entry<String, String> variableValue : variableValues.entrySet()) {
            xmlString = xmlString.replace(variableValue.getKey(), variableValue.getValue());
        }

        response.getWriter().append(xmlString);
        response.setStatus(status);
    }

    public static String toString(Document document) throws ServletException {
        StringWriter stringWriter = new StringWriter();
        StreamResult result = new StreamResult(stringWriter);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();

        Transformer transformer;
        try {
            transformer = transformerFactory.newTransformer();
        } catch (TransformerConfigurationException ex) {
            throw new ServletException("Unable to create transformer", ex);
        }

        DOMSource domSource = new DOMSource(document);

        try {
            transformer.transform(domSource, result);
        } catch (TransformerException ex) {
            throw new ServletException("Unable to transform xml document", ex);
        }

        return stringWriter.toString();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PrintWriter printWriter = response.getWriter();
        printWriter.append("Mock service started");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    static boolean checkAllNodes(NodeList nodeList, String field, String value) {
        for (int i = 0; i < nodeList.getLength(); i++) {     // TODO мб сначала equals затем contains
            String nodeName = nodeList.item(i).getNodeName().toLowerCase();
            if (nodeName.contains(field.toLowerCase())) {
                String nodeValue;
                try {
                    nodeValue = nodeList.item(i).getChildNodes().item(0).getNodeValue();
                } catch (Exception e) {
                    nodeValue = null;
                }
                if (nodeValue != null && nodeValue.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

}
