package ru.demo.servicemock;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;


public class MockRestServlet extends HttpServlet {
    public static List<Queue<CustomMock>> newCustomMocks = Collections.synchronizedList(new LinkedList<>());
    public static List<IncomingRequest> incomingRequests = Collections.synchronizedList(new LinkedList<>());
    private static final Logger logger = LogManager.getLogger(MockRestServlet.class);
    private static final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logger.info("Пришел запрос в часть, конфигурирующую поведение моков");

        String requestString = request.getReader().lines().collect(Collectors.joining());
        logger.info("Входящий реквест: " + requestString);

        ConfigureRequest configureRequest = gson.fromJson(requestString, ConfigureRequest.class);

        switch (configureRequest.getActionType()) {
            case ADD_MOCK:
                addMock(response, configureRequest.getCustomMocks());
                break;
            case GET_SYSTEM_INFO:
                getSystemInfo(response, configureRequest.getRequestParameters());
                break;
            case CHECK_MOCK_READY:
                checkMockReady(response, configureRequest.getCheckMockField(), configureRequest.getCheckMockValue());
                break;
            case REMOVE_ALL_MOCKS:
                newCustomMocks.clear();
                response.setStatus(HttpServletResponse.SC_OK);
                break;
            case REMOVE_ALL_REQUESTS:
                incomingRequests.clear();
                response.setStatus(HttpServletResponse.SC_OK);
                break;
            default:
                logger.error("Данный actionType не поддерживается");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    public static List<IncomingRequest> findRequests(RequestParameters parameters) {
        if (parameters.parameters != null) {
            if (parameters.dateFrom != -1 && parameters.dateTo != -1) {
                return findRequests(parameters.parameters, parameters.dateFrom, parameters.dateTo);
            } else {
                return findRequests(parameters.parameters);
            }
        } else {
            return findRequests(parameters.dateFrom, parameters.dateTo);
        }
    }

    private static List<IncomingRequest> findRequests(Map<String, String> fields, long dateFrom, long dateTo) {
        List<IncomingRequest> result = new ArrayList<>();

        for (IncomingRequest request : incomingRequests) {
            if (request.getDate() < dateFrom)
                continue;
            if (request.getDate() > dateTo)
                break;

            boolean checkFields = true;
            NodeList nl = request.getDocument().getElementsByTagName("*");
            for (String fieldName : fields.keySet()) {
                checkFields &= MockHttpServlet.checkAllNodes(nl, fieldName, fields.get(fieldName));
            }

            if (!checkFields) continue;
            result.add(request);
        }

        return result;
    }

    private static List<IncomingRequest> findRequests(long dateFrom, long dateTo) {
        List<IncomingRequest> result = new ArrayList<>();

        for (IncomingRequest request : incomingRequests) {
            if (request.getDate() < dateFrom)
                continue;
            if (request.getDate() > dateTo)
                break;
            result.add(request);
        }

        return result;
    }

    private static List<IncomingRequest> findRequests(Map<String, String> fields) {
        List<IncomingRequest> result = new ArrayList<>();

        for (IncomingRequest request : incomingRequests) {
            boolean checkFields = true;
            NodeList nl = request.getDocument().getElementsByTagName("*");
            for (String fieldName : fields.keySet()) {
                checkFields &= MockHttpServlet.checkAllNodes(nl, fieldName, fields.get(fieldName));
            }

            if (!checkFields) continue;
            result.add(request);
        }

        return result;
    }

    static String isMockReady(String field, String value) {
        for (Queue<CustomMock> queueMock : MockRestServlet.newCustomMocks) {
            CustomMock firstMock = queueMock.peek();
            if (firstMock == null)
                continue;

            if (firstMock.getField().equals(field) && firstMock.getValue().equals(value)) {
                return firstMock.getMock();
            }
        }
        return "";
    }

    private void checkMockReady(HttpServletResponse response, String mockField, String mockValue) throws IOException {
        logger.info("Поиск готового мока по параметрам: " + mockField + " - " + mockValue);
        String mock = isMockReady(mockField, mockValue);
        logger.info(mock.equals("") ? "Нет готового мока" : ("Найденный мок: " + mock));
        response.setContentType("application/json");
        response.getWriter().append(gson.toJson(mock));
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void getSystemInfo(HttpServletResponse response, RequestParameters parameters) throws IOException {
        logger.info("Поиск системной информации по параметрам: " + parameters);
        List<IncomingRequest> requests = findRequests(parameters);
        List<String> result = requests.stream().map(IncomingRequest::getStringDocument).collect(Collectors.toList());
        response.setContentType("application/json");
        response.getWriter().append(gson.toJson(result));
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void addMock(HttpServletResponse response, CustomMock[] customMocks) throws ServletException {
        logger.info("Добавление нового набора моков из запроса");

        DocumentBuilder documentBuilder;
        try {
            documentBuilder = DocumentBuilderFactory
                    .newInstance()
                    .newDocumentBuilder();
        } catch (ParserConfigurationException ex) {
            throw new ServletException("Unable to create document builder", ex);
        }

        Queue<CustomMock> queueMock = new ArrayDeque<>();
        for (CustomMock mock : customMocks) {
            InputSource inputSource = new InputSource(new StringReader(mock.getMock()));

            Document actualRequestDocument;
            try {
                actualRequestDocument = documentBuilder.parse(inputSource);
                mock.setDocument(actualRequestDocument);
                queueMock.add(mock);
            } catch (SAXException | IOException ex) {
                logger.error(ex.getMessage());
                throw new ServletException("Unable to parse xml document", ex);
            }
        }
        newCustomMocks.add(queueMock);
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
