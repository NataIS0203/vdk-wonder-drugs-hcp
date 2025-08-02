package com.veeva.vault.custom;

import com.veeva.vault.sdk.api.core.*;
import com.veeva.vault.sdk.api.executeas.ExecuteAs;
import com.veeva.vault.sdk.api.executeas.ExecuteAsUser;
import com.veeva.vault.sdk.api.json.JsonObject;
import com.veeva.vault.sdk.api.json.JsonService;
import com.veeva.vault.sdk.api.json.JsonValueType;
import com.veeva.vault.sdk.api.query.*;
import com.veeva.vault.sdk.api.webapi.*;

@ExecuteAs(ExecuteAsUser.REQUEST_OWNER)
@WebApiInfo(endpointName = "hcp_request", minimumVersion = "v24.3", apiGroup = "composite_apis__c")
public class vSdkWonderDrugsMSLWebApi implements WebApi {

    @Override
    public WebApiResponse execute(WebApiContext webApiContext) {
        JsonService jsonService = ServiceLocator.locate(JsonService.class);
        WebApiRequest webApiRequest = webApiContext.getWebApiRequest();
        WebApiResponse.Builder responseBuilder = webApiContext.newWebApiResponseBuilder();

        // Get request body as JsonObject
        JsonObject requestData = webApiRequest.getJsonObject();

        if (!requestData.contains("zip") || !requestData.contains("groupSpecialty")) {
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
            String groupSpecialty = requestData.getValue("groupSpecialty", JsonValueType.STRING);
            String zip = requestData.getValue("zip", JsonValueType.STRING);

            JsonObject response = GetQuery(groupSpecialty, zip);
            if (response == null) {
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
                    .withData(response)
                    .withResponseStatus(WebApiResponseStatus.SUCCESS)
                    .build();
        }
    }

    public JsonObject GetQuery(String groupSpecialty, String zip) {
        QueryService queryService = ServiceLocator.locate(QueryService.class);
        LogService logService = ServiceLocator.locate(LogService.class);
        // Query the Country object to retrieve all active countries
        // Build account query string
        String queryAccountString = "SELECT id FROM account__v WHERE id in " +
                "(SELECT account__v from address__vr where postal_code_cda__v = " + zip + ") " +
                "AND (group_specialty_1__v = '" + groupSpecialty + "' " +
                "OR group_specialty_2__v = '" + groupSpecialty + "')";
        QueryExecutionRequest request = queryService.newQueryExecutionRequestBuilder()
                .withQueryString(queryAccountString).build();

        StringBuilder sb = new StringBuilder();
        final boolean[] isOr = {false};
        queryService.query(request)
                .onSuccess(queryExecutionResponse ->
                        queryExecutionResponse.streamResults().forEach(queryExecutionResult -> {
                            String id = queryExecutionResult.getValue("id", ValueType.STRING);
                            if (isOr[0]) {
                                sb.append(" or ");
                            }
                            sb.append(" account_plan__vr.account__v = '").append(id).append("' ");
                            isOr[0] = true;
                        }))
                .onError(queryOperationError -> logService.error("Failed to query country records: " + queryOperationError.getMessage()))
                .execute();
        return GetMLSJsonBody(sb.toString());
    }

    public JsonObject GetMLSJsonBody(String ids) {
        QueryService queryService = ServiceLocator.locate(QueryService.class);
        LogService logService = ServiceLocator.locate(LogService.class);
        JsonService jsonService = ServiceLocator.locate(JsonService.class);
        final JsonObject[] objectManagerBuilder = {jsonService.newJsonObjectBuilder().build()};
        JsonObject[] objectBuilder = {jsonService.newJsonObjectBuilder().build()};
        final boolean[] isManagerNotFound = {true};
        final boolean[] isMSLNotFound = {true};
        // Query the Country object to retrieve all active countries
        // Build account query string
        String queryString = "SELECT team_member__vr.id, team_member__vr.name__v, team_member__vr.email__sys, " +
                "team_member__vr.mobile_phone__sys, team_member__vr.first_name__sys, team_member__vr.last_name__sys, " +
                "team_member__vr.manager__sys, team_member__vr.company__sys, team_member__vr.title__sys , account_plan__vr.id , account_plan__vr.account__v " +
                "FROM account_team_member__v where " + ids;
        QueryExecutionRequest request = queryService.newQueryExecutionRequestBuilder()
                .withQueryString(queryString).build();
        queryService.query(request)
                .onSuccess(queryExecutionResponse -> queryExecutionResponse.streamResults().forEach(queryResult -> {
                    String id = queryResult.getValue("team_member__vr.id", ValueType.STRING);
                    String title = queryResult.getValue("team_member__vr.title__sys", ValueType.STRING);
                    String manager = queryResult.getValue("team_member__vr.manager__sys", ValueType.STRING);
                    if (((title != null && title.equals("manager")) || manager == null ) && isManagerNotFound[0]) {
                        objectManagerBuilder[0] = FormingJsonObjectBuilder(jsonService, id, title, queryResult);
                        isManagerNotFound[0] = false;
                    } else {
                        if (title != null  && title.equals("MSL") && isMSLNotFound[0]) {
                            objectBuilder[0] = FormingJsonObjectBuilder(jsonService, id, title, queryResult);
                            isMSLNotFound[0] = false;
                        }
                    }
                }))
                .onError(queryOperationError -> logService.error("Failed to query country records: " + queryOperationError.getMessage()))
                .execute();

        return !isMSLNotFound[0] ? objectBuilder[0] : objectManagerBuilder[0];
    }

    private JsonObject FormingJsonObjectBuilder(JsonService jsonService, String id, String title, QueryExecutionResult queryResult) {

       return  jsonService.newJsonObjectBuilder()
               .setValue("id", id)
                .setValue("title", title)
                .setValue("name", queryResult.getValue("team_member__vr.name__v", ValueType.STRING))
                .setValue("email", queryResult.getValue("team_member__vr.email__sys", ValueType.STRING))
                .setValue("phone", queryResult.getValue("team_member__vr.mobile_phone__sys", ValueType.STRING))
                .setValue("firstName", queryResult.getValue("team_member__vr.first_name__sys", ValueType.STRING))
                .setValue("lastName", queryResult.getValue("team_member__vr.last_name__sys", ValueType.STRING))
                .setValue("company", queryResult.getValue("team_member__vr.company__sys", ValueType.STRING))
               .setValue("accountId", queryResult.getValue("account_plan__vr.account__v", ValueType.STRING))
               .build();
    }
}