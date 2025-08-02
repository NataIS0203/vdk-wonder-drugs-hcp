package com.veeva.vault.custom;

import com.veeva.vault.sdk.api.core.RollbackException;
import com.veeva.vault.sdk.api.core.ServiceLocator;
import com.veeva.vault.sdk.api.core.ValueType;
import com.veeva.vault.sdk.api.core.VaultCollections;
import com.veeva.vault.sdk.api.data.Record;
import com.veeva.vault.sdk.api.data.RecordBatchSaveRequest;
import com.veeva.vault.sdk.api.data.RecordService;
import com.veeva.vault.sdk.api.executeas.ExecuteAs;
import com.veeva.vault.sdk.api.executeas.ExecuteAsUser;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonService;
import com.veeva.vault.sdk.api.json.JsonValueType;
import com.veeva.vault.sdk.api.picklist.PicklistService;
import com.veeva.vault.sdk.api.picklist.PicklistValueMetadataCollectionRequest;
import com.veeva.vault.sdk.api.webapi.*;

import java.time.*;
import java.util.List;

@ExecuteAs(ExecuteAsUser.REQUEST_OWNER)
@WebApiInfo(endpointName = "hcp_meeting_request", minimumVersion = "v24.3", apiGroup = "composite_apis__c")
public class vSdkWonderDrugsMeetingRequest implements WebApi {

    @Override
    public WebApiResponse execute(WebApiContext webApiContext) {
        JsonService jsonService = ServiceLocator.locate(JsonService.class);
        WebApiRequest webApiRequest = webApiContext.getWebApiRequest();
        WebApiResponse.Builder responseBuilder = webApiContext.newWebApiResponseBuilder();

        // Get request body as JsonObject
        JsonObject requestData = webApiRequest.getJsonObject();

        if (!requestData.contains("accountId") || !requestData.contains("assigneeId")) {
            // Send failure response to caller
            JsonObject error = jsonService.newJsonObjectBuilder()
                    .setValue("error", "Expected request data not provided")
                    .build();
            return responseBuilder
                    .withData(error)
                    .withResponseStatus(WebApiResponseStatus.FAILURE)
                    .build();
        } else {
            // Send success response to caller, using provided request data

            boolean response = AddMeetingRequest(requestData);
            if (!response) {
                return webApiContext.newWebApiFailureResponseBuilder()
                        .withErrors(
                                VaultCollections.asList(
                                        webApiContext.newWebApiErrorBuilder()
                                                .withType("CANNOT GET RECORD")
                                                .withMessage("A custom error occurred")
                                                .build()
                                )
                        ).build();
            }
            return responseBuilder
                    .withData(jsonService.newJsonObjectBuilder()
                            .setValue("added", true)
                            .build())
                    .withResponseStatus(WebApiResponseStatus.SUCCESS)
                    .build();
        }
    }

    public boolean AddMeetingRequest(JsonObject requestData) {
        RecordService recordService = ServiceLocator.locate(RecordService.class);
        // Query the Country object to retrieve all active countries
        // Build record query string
        Record record = recordService.newRecord("meeting_request__v");
        record.setValue("assignee__v", requestData.getValue("assigneeId", JsonValueType.STRING));
        record.setValue("account__v", requestData.getValue("accountId", JsonValueType.STRING));
        record.setValue("duration__v", requestData.getValue("duration", JsonValueType.NUMBER));
        record.setValue("external_id__v", requestData.getValue("requestId", JsonValueType.STRING));
        record.setValue("invitee_locale__v", requestData.getValue("NPINumber", JsonValueType.STRING));
        record.setValue("invitee_display_name__v", requestData.getValue("inviteeName", JsonValueType.STRING));
        record.setValue("invitee_email__v", requestData.getValue("email", JsonValueType.STRING));
        record.setValue("phone__v", requestData.getValue("phone", JsonValueType.STRING));
        record.setValue("start_datetime__v", ZonedDateTime.now());

        List<Record> records = VaultCollections.asList(record);

        RecordBatchSaveRequest saveRequest = recordService.newRecordBatchSaveRequestBuilder().withRecords(records).build();

        recordService.batchSaveRecords(saveRequest)
                .onErrors(batchOperationErrors ->{
                    batchOperationErrors.stream().findFirst().ifPresent(error -> {
                        String errMsg = error.getError().getMessage();
                        int errPosition = error.getInputPosition();
                        String name = records.get(errPosition).getValue("name__v", ValueType.STRING);
                        throw new RollbackException("OPERATION_NOT_ALLOWED", "Unable to create: " + name +
                                "because of " + errMsg);
                    });
                })
                .execute();
        return  true;
    }
}