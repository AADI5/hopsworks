/*
 * This file is part of Hopsworks
 * Copyright (C) 2021, Logical Clocks AB. All rights reserved
 *
 * Hopsworks is free software: you can redistribute it and/or modify it under the terms of
 * the GNU Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * Hopsworks is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package io.hops.hopsworks.api.featurestore.preparestatement;

import io.hops.hopsworks.common.featurestore.featureview.FeatureViewController;
import io.hops.hopsworks.api.featurestore.trainingdataset.PreparedStatementBuilder;
import io.hops.hopsworks.api.filter.AllowedProjectRoles;
import io.hops.hopsworks.api.filter.Audience;
import io.hops.hopsworks.api.filter.apiKey.ApiKeyRequired;
import io.hops.hopsworks.api.jwt.JWTHelper;
import io.hops.hopsworks.common.api.ResourceRequest;
import io.hops.hopsworks.common.featurestore.query.ServingPreparedStatementDTO;
import io.hops.hopsworks.exceptions.FeaturestoreException;
import io.hops.hopsworks.jwt.annotation.JWTRequired;
import io.hops.hopsworks.persistence.entity.featurestore.Featurestore;
import io.hops.hopsworks.persistence.entity.featurestore.featureview.FeatureView;
import io.hops.hopsworks.persistence.entity.project.Project;
import io.hops.hopsworks.persistence.entity.user.Users;
import io.hops.hopsworks.persistence.entity.user.security.apiKey.ApiScope;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.ejb.EJB;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.context.RequestScoped;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

@RequestScoped
@TransactionAttribute(TransactionAttributeType.NEVER)
public class PreparedStatementResource {

  @EJB
  private JWTHelper jWTHelper;
  @EJB
  private FeatureViewController featureViewController;
  @EJB
  private PreparedStatementBuilder preparedStatementBuilder;

  private Project project;
  private Featurestore featurestore;
  private FeatureView featureView;

  public void setProject(Project project) {
    this.project = project;
  }

  public void setFeatureStore(Featurestore featurestore) {
    this.featurestore = featurestore;
  }

  public void setFeatureView(String name, Integer version) throws FeaturestoreException {
    featureView = featureViewController.getByNameVersionAndFeatureStore(name, version, featurestore);
  }

  @ApiOperation(value = "Get prepared statements used to generate model serving vector from feature view query",
      response = ServingPreparedStatementDTO.class)
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_OWNER, AllowedProjectRoles.DATA_SCIENTIST})
  @JWTRequired(acceptedTokens = {Audience.API, Audience.JOB},
    allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER", "HOPS_SERVICE_USER"})
  @ApiKeyRequired(acceptedScopes = {ApiScope.FEATURESTORE},
    allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER", "HOPS_SERVICE_USER"})
  public Response getPreparedStatements(
      @Context
          SecurityContext sc,
      @Context
          UriInfo uriInfo,
      @Context
          HttpServletRequest req,
      @ApiParam(value = "get batch serving vectors", example = "false")
      @QueryParam("batch")
      @DefaultValue("false")
          boolean batch,
      @ApiParam(value = "get inference helper columns", example = "false")
      @QueryParam("inference_helper_columns")
      @DefaultValue("false")
      boolean inference_helper_columns)
      throws FeaturestoreException {
    Users user = jWTHelper.getUserPrincipal(sc);
    ServingPreparedStatementDTO servingPreparedStatementDTO = preparedStatementBuilder.build(uriInfo,
       new ResourceRequest(ResourceRequest.Name.PREPAREDSTATEMENTS), project, user, featurestore, featureView, batch,
      inference_helper_columns);
    return Response.ok().entity(servingPreparedStatementDTO).build();
  }
}
