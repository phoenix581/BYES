package com.byes.paap.rest.uploaddocument;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.Base64;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.byes.paap.rest.uploaddocument.dto.UploadedFile;
import com.planonsoftware.jaxrs.api.v9.context.IJaxRsResourceContext;
import com.planonsoftware.platform.data.v1.ActionNotFoundException;
import com.planonsoftware.platform.data.v1.BusinessException;
import com.planonsoftware.platform.data.v1.FieldNotFoundException;
import com.planonsoftware.platform.data.v1.IAction;
import com.planonsoftware.platform.data.v1.IActionListManager;
import com.planonsoftware.platform.data.v1.IBusinessObject;
import com.planonsoftware.platform.data.v1.IDatabaseQuery;
import com.planonsoftware.platform.data.v1.IMessageHandler;
import com.planonsoftware.platform.data.v1.IResultSet;
import com.planonsoftware.platform.data.v1.Operator;

@Path("/document")
public class UploadDocument
{

    @Context 
    IJaxRsResourceContext jaxrsContext;

    @POST
    @Path("/upload/{orderNumber}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response uploadDocument(@PathParam("orderNumber") String orderNumber, UploadedFile uploadedFile) throws BusinessException, ActionNotFoundException, FieldNotFoundException, IOException {
      
        IDatabaseQuery query = jaxrsContext.getDataService().getPVDatabaseQuery("BaseOrderQuery");
        query.getStringSearchExpression("orderNumber", Operator.EQUAL).addValue(orderNumber); 
        IResultSet resultset = query.execute();
        resultset.next();
        jaxrsContext.getLogService().debug(resultset.getString("Description"));
        jaxrsContext.getLogService().debug(orderNumber);
        jaxrsContext.getLogService().debug(uploadedFile.getFileName());
        String base64File = uploadedFile.getFile().split("base64.")[1];
        byte[] decodedString = Base64.getDecoder().decode(new String(base64File).getBytes("UTF-8"));
        
        //Check if ComLog exists
        IBusinessObject existingComLog = null;
        String[] fileNameArray = uploadedFile.getFileName().split("_");
        String comLogPK = "";
        int comLogPKInt = 0;
        if (fileNameArray.length > 0) {
            comLogPK = fileNameArray[0];
            if (isNumeric(comLogPK)) {
                comLogPKInt = Integer.parseInt(comLogPK);
            }
        }

        IDatabaseQuery queryComLog = jaxrsContext.getDataService().getPVDatabaseQuery("ComLogQuery");
        queryComLog.getStringSearchExpression("syscode", Operator.EQUAL).addValue(comLogPKInt);
        IResultSet resultsetComLog = queryComLog.execute();
        if (resultsetComLog.next()) {
            IActionListManager actionListManagerComLog = jaxrsContext.getDataService().getActionListManager("UsrCommunicationLog");
            IAction actionComLog = actionListManagerComLog.getReadAction(resultsetComLog.getPrimaryKey());
            existingComLog = actionComLog.execute();
        }

        if (existingComLog == null) {
            IActionListManager actionListManager = jaxrsContext.getDataService().getActionListManager("UsrCommunicationLog");
            IAction action = actionListManager.getAction("BomAdd");
            action.getIntegerArgument("Syscode").setValue(resultset.getPrimaryKey());
            action.getStringArgument("BOType").setValue("BaseOrder");
            IBusinessObject comLog = action.execute();

            long timestamp = Instant.now().getEpochSecond();
            String fileName = comLog.getPrimaryKeyAsString() + "_" + timestamp + "_" + uploadedFile.getFileName();
            comLog.getStringField("Name").setValue(fileName);
            comLog.getDateTimeField("BeginDate").setValue(new Date());
            InputStream targetStream = new ByteArrayInputStream(decodedString);
            comLog.getSecureDocumentField("SecureDocumentReferral").uploadContent(fileName, targetStream);
            jaxrsContext.getLogService().debug(comLog.getPrimaryKeyAsString());
            comLog.executeSave();
        } else {
            existingComLog.getDateTimeField("BeginDate").setValue(new Date());
            InputStream targetStream = new ByteArrayInputStream(decodedString);
            existingComLog.getSecureDocumentField("SecureDocumentReferral").uploadContent(uploadedFile.getFileName(), targetStream);
            try {
                existingComLog.executeSave();
            } catch (BusinessException e) {
                IMessageHandler messageHandler = prepareMessageHandler(e);
                existingComLog.executeSave(messageHandler);
            }
        }
        
        return null;
    }

    private static IMessageHandler prepareMessageHandler(BusinessException e) throws BusinessException {
        IMessageHandler messageHandler = e.getMessageHandler();
        // ignore warnings
        messageHandler.setIgnoreWarnings();
        // confirm confirmations
        if (messageHandler.getNumberOfConfirmations() > 0) {
            for (int i = 0; i < messageHandler.getNumberOfConfirmations(); i++) {
                messageHandler.getConfirmation(i).setAnswer(true);
            }
        }
        // see if there is anything we cannot handle
        if (!(messageHandler.canReply())) {
            // nothing to answer. can't handle
            throw e;
        }
        
        return messageHandler;
    }

    public boolean isNumeric(String strNum) {
        Pattern pattern = Pattern.compile("-?\\d+(\\.\\d+)?");
        if (strNum == null) {
            return false; 
        }
        return pattern.matcher(strNum).matches();
    }
}