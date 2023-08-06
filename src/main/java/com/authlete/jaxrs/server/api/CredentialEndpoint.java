/*
 * Copyright (C) 2023 Authlete, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 */
package com.authlete.jaxrs.server.api;


import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import com.authlete.common.api.AuthleteApi;
import com.authlete.common.api.AuthleteApiFactory;
import com.authlete.common.dto.CredentialIssuanceOrder;
import com.authlete.common.dto.CredentialOfferInfoRequest;
import com.authlete.common.dto.CredentialRequestInfo;
import com.authlete.common.dto.CredentialSingleIssueRequest;
import com.authlete.common.dto.CredentialSingleIssueResponse;
import com.authlete.common.dto.CredentialSingleParseRequest;
import com.authlete.common.dto.CredentialSingleParseResponse;
import com.authlete.common.dto.IntrospectionRequest;
import com.authlete.common.dto.IntrospectionResponse;
import com.authlete.jaxrs.BaseEndpoint;
import com.authlete.jaxrs.server.util.CredentialUtil;
import com.authlete.jaxrs.server.util.ExceptionUtil;
import com.authlete.jaxrs.server.util.ResponseUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minidev.json.JSONObject;


@Path("/api/credential")
public class CredentialEndpoint extends BaseEndpoint
{
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response post(@Context HttpServletRequest request,
                         final String requestContent)
    {
        if (requestContent == null)
        {
            return ResponseUtil.badRequest("Missing request content.");
        }

        final String accessToken = processAccessToken(request);
        if (accessToken == null)
        {
            return ResponseUtil.badRequest("Missing access token.");
        }

        final AuthleteApi api = AuthleteApiFactory.getDefaultApi();

        // Validate access token
        introspect(api, accessToken);

        // Parse credential and make it an order
        final CredentialRequestInfo credential = credentialSingleParse(api,
                                                                       requestContent,
                                                                       accessToken);
        final CredentialIssuanceOrder order = CredentialUtil.toOrder(credential);

        // Issue
        final String issuance = credentialIssue(api, order, accessToken);
        return ResponseUtil.ok(issuance);
    }


    private String processAccessToken(final HttpServletRequest request)
    {
        String accessToken = request.getHeader("Authorization");
        if (accessToken == null || !accessToken.startsWith("Bearer "))
        {
            return null;
        }
        accessToken = accessToken.replaceFirst("Bearer ", "");
        return accessToken;
    }


    private String introspect(final AuthleteApi api,
                                final String accessToken)
            throws WebApplicationException
    {
        final IntrospectionRequest introspectionRequest = new IntrospectionRequest()
                .setToken(accessToken);

        final IntrospectionResponse response = api.introspection(introspectionRequest);
        String resultMessage = response.getResultMessage();

        switch (response.getAction())
        {
            case BAD_REQUEST:
                throw ExceptionUtil.badRequestException(resultMessage);

            case UNAUTHORIZED:
                throw ExceptionUtil.unauthorizedException(accessToken, resultMessage);

            case FORBIDDEN:
                throw ExceptionUtil.forbiddenException(resultMessage);

            case OK:
                return resultMessage;

            case INTERNAL_SERVER_ERROR:
            default:
                throw ExceptionUtil.internalServerErrorException(resultMessage);
        }
    }


    private CredentialRequestInfo credentialSingleParse(final AuthleteApi api,
                                     final String requestContent,
                                     final String accessToken)
            throws WebApplicationException
    {
        final CredentialSingleParseRequest parseRequest = new CredentialSingleParseRequest()
                .setRequestContent(requestContent)
                .setAccessToken(accessToken);

        final CredentialSingleParseResponse response = api.credentialSingleParse(parseRequest);
        final String resultMessage = response.getResultMessage();

        switch (response.getAction())
        {
            case BAD_REQUEST:
                throw ExceptionUtil.badRequestException(resultMessage);

            case UNAUTHORIZED:
                throw ExceptionUtil.unauthorizedException(accessToken, resultMessage);

            case FORBIDDEN:
                throw ExceptionUtil.forbiddenException(resultMessage);

            case OK:
                return response.getInfo();

            case INTERNAL_SERVER_ERROR:
            default:
                throw ExceptionUtil.internalServerErrorException(resultMessage);
        }
    }


    private String credentialIssue(final AuthleteApi api,
                            final CredentialIssuanceOrder order,
                            final String accessToken)
    {
        final CredentialSingleIssueRequest credentialSingleIssueRequest = new CredentialSingleIssueRequest()
                .setAccessToken(accessToken)
                .setOrder(order);

        final CredentialSingleIssueResponse response = api.credentialSingleIssue(credentialSingleIssueRequest);
        final String resultMessage = response.getResultMessage();

        switch (response.getAction())
        {
            case CALLER_ERROR:
                throw ExceptionUtil.badRequestException(resultMessage);

            case UNAUTHORIZED:
                throw ExceptionUtil.unauthorizedException(accessToken, resultMessage);

            case FORBIDDEN:
                throw ExceptionUtil.forbiddenException(resultMessage);

            case OK:
            case ACCEPTED:
                return resultMessage;

            case INTERNAL_SERVER_ERROR:
            default:
                throw ExceptionUtil.internalServerErrorException(resultMessage);
        }
    }
}
