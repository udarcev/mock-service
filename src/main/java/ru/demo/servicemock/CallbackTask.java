package ru.demo.servicemock;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.soap.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;


class CallbackTask implements Runnable {

    private static final Logger logger = LogManager.getLogger(CallbackTask.class);

    private final String endpoint;
    private final File callbackBodyFile;
    private final Map<String, String> variableValues;
    private final Long delayMillis;

    public CallbackTask(String endpoint, File callbackBodyFile, Map<String, String> variableValues) {
        this(endpoint, callbackBodyFile, variableValues, null);
    }

    public CallbackTask(String endpoint, File callbackBodyFile, Map<String, String> variableValues, Long delayMillis) {
        this.endpoint = endpoint;
        this.callbackBodyFile = callbackBodyFile;
        this.variableValues = variableValues;
        this.delayMillis = delayMillis;
    }

    @Override
    public void run() {
        if (delayMillis != null) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ex) {
                logger.error("Выполнение коллбека было прервано", ex);
                return;
            }
        }

        SOAPConnection soapConnection = null;
        try {
            SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
            soapConnection = soapConnectionFactory.createConnection();
            MessageFactory messageFactory = MessageFactory.newInstance();

            String requestString;
            try (InputStream inputStream = new FileInputStream(callbackBodyFile)) {
                requestString = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            }

            for (Entry<String, String> variableValue : variableValues.entrySet()) {
                requestString = requestString.replace(variableValue.getKey(), variableValue.getValue());
            }

            SOAPMessage requestSoapMessage;
            try (InputStream inputStream = new ByteArrayInputStream(requestString.getBytes(StandardCharsets.UTF_8))) {
                requestSoapMessage = messageFactory.createMessage(new MimeHeaders(), inputStream);
            }

            soapConnection.call(requestSoapMessage, endpoint);
        } catch (SOAPException | IOException ex) {
            logger.error("Ошибка при выполнении коллбека", ex);
        } finally {
            if (soapConnection != null) {
                try {
                    soapConnection.close();
                } catch (SOAPException ex) {
                    logger.error("Не получилось закрыть соединение SOAP", ex);
                }
            }
        }

    }

}
