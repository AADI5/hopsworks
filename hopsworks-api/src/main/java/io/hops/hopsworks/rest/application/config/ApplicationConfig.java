/*
 * Changes to this file committed after and not including commit-id: ccc0d2c5f9a5ac661e60e6eaf138de7889928b8b
 * are released under the following license:
 *
 * This file is part of Hopsworks
 * Copyright (C) 2018, Logical Clocks AB. All rights reserved
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
 *
 * Changes to this file committed before and including commit-id: ccc0d2c5f9a5ac661e60e6eaf138de7889928b8b
 * are released under the following license:
 *
 * Copyright (C) 2013 - 2018, Logical Clocks AB and RISE SICS AB. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS  OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL  THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.hops.hopsworks.rest.application.config;

import io.hops.hopsworks.api.admin.UsersAdminResource;
import io.hops.hopsworks.api.admin.alert.AdminAlertResource;
import io.hops.hopsworks.api.admin.alert.silence.AdminSilenceResource;
import io.hops.hopsworks.api.util.PrometheusQueryResource;
import io.swagger.annotations.Api;
import org.glassfish.jersey.server.ResourceConfig;

@Api
@javax.ws.rs.ApplicationPath("api")
public class ApplicationConfig extends ResourceConfig {

  /**
   * adding manually all the restful services of the application.
   */
  public ApplicationConfig() {
    register(io.hops.hopsworks.api.agent.AgentResource.class);
    register(io.hops.hopsworks.api.opensearch.OpenSearchService.class);
    register(io.hops.hopsworks.api.exception.mapper.RESTApiThrowableMapper.class);
    register(io.hops.hopsworks.api.filter.ProjectAuthFilter.class);
    register(io.hops.hopsworks.api.filter.AuthFilter.class);
    register(io.hops.hopsworks.api.filter.apiKey.ApiKeyFilter.class);
    register(io.hops.hopsworks.api.filter.JWTAutoRenewFilter.class);
    register(io.hops.hopsworks.api.filter.featureFlags.FeatureFlagFilter.class);
    register(io.hops.hopsworks.api.jwt.JWTResource.class);
    register(io.hops.hopsworks.api.jobs.executions.ExecutionsResource.class);
    register(io.hops.hopsworks.api.jobs.JobsResource.class);
    register(io.hops.hopsworks.api.jupyter.JupyterService.class);
    register(io.hops.hopsworks.api.serving.ServingService.class);
    register(io.hops.hopsworks.api.serving.inference.InferenceResource.class);
    register(io.hops.hopsworks.api.kafka.KafkaResource.class);
    register(io.hops.hopsworks.api.project.MessageService.class);
    register(io.hops.hopsworks.api.project.ProjectMembersService.class);
    register(io.hops.hopsworks.api.project.ProjectService.class);
    register(io.hops.hopsworks.api.project.RequestService.class);
    register(io.hops.hopsworks.api.activities.ProjectActivitiesResource.class);
    register(io.hops.hopsworks.api.alert.AlertResource.class);
    register(io.hops.hopsworks.api.alert.silence.SilenceResource.class);
    register(AdminAlertResource.class);
    register(io.hops.hopsworks.api.admin.alert.management.ManagementResource.class);
    register(AdminSilenceResource.class);
    register(io.hops.hopsworks.api.python.environment.command.EnvironmentCommandsResource.class);
    register(io.hops.hopsworks.api.python.environment.EnvironmentResource.class);
    register(io.hops.hopsworks.api.python.environment.history.EnvironmentHistoryResource.class);
    register(io.hops.hopsworks.api.python.library.command.LibraryCommandsResource.class);
    register(io.hops.hopsworks.api.python.library.LibraryResource.class);
    register(io.hops.hopsworks.api.python.PythonResource.class);
    register(io.hops.hopsworks.api.user.AuthService.class);
    register(io.hops.hopsworks.api.airflow.AirflowService.class);
    register(io.hops.hopsworks.api.user.UsersResource.class);
    register(io.hops.hopsworks.api.user.apiKey.ApiKeyResource.class);
    register(io.hops.hopsworks.api.metadata.XAttrsResource.class);
    
    register(io.hops.hopsworks.api.util.BannerService.class);
    register(io.hops.hopsworks.api.util.ClusterUtilisationService.class);
    register(io.hops.hopsworks.api.util.DownloadService.class);
    register(io.hops.hopsworks.api.util.EndpointService.class);
    register(io.hops.hopsworks.api.util.UploadService.class);
    register(io.hops.hopsworks.api.util.VariablesService.class);
    register(io.hops.hopsworks.api.cluster.Monitor.class);
    register(io.hops.hopsworks.api.serving.ServingConfResource.class);
    register(io.hops.hopsworks.api.featurestore.FeaturestoreService.class);
    register(io.hops.hopsworks.api.tags.TagSchemasResource.class);
    register(io.hops.hopsworks.api.featurestore.datavalidationv2.greatexpectations.GreatExpectationResource.class);
    register(io.hops.hopsworks.api.tags.TagSchemasResource.class);

    // admin
    register(UsersAdminResource.class);
    register(io.hops.hopsworks.api.admin.SystemAdminService.class);
    register(io.hops.hopsworks.api.admin.projects.ProjectsAdminResource.class);
    register(io.hops.hopsworks.api.admin.hosts.HostsAdminResource.class);
    register(io.hops.hopsworks.api.admin.security.CertificateMaterializerAdmin.class);
    register(io.hops.hopsworks.api.admin.security.CredentialsResource.class);
    register(io.hops.hopsworks.api.admin.security.X509Resource.class);
    register(io.hops.hopsworks.api.admin.services.ServicesResource.class);
    register(io.hops.hopsworks.api.admin.conf.ConfigurationResource.class);

    register(org.glassfish.jersey.media.multipart.MultiPartFeature.class);

    //maggy
    register(io.hops.hopsworks.api.maggy.MaggyService.class);

    //provenance
    register(io.hops.hopsworks.api.provenance.ProjectProvenanceResource.class);
    
    //search
    register(io.hops.hopsworks.api.opensearch.OpenSearchResource.class);
    
    //uncomment to allow Cross-Origin Resource Sharing
    //register(io.hops.hopsworks.filters.AllowCORSFilter.class);

    //swagger
    register(io.swagger.jaxrs.listing.ApiListingResource.class);
    register(io.swagger.jaxrs.listing.SwaggerSerializers.class);

    //git
    register(io.hops.hopsworks.api.git.execution.GitExecutionResource.class);
    register(io.hops.hopsworks.api.git.GitResource.class);

    //prometheus
    register(PrometheusQueryResource.class);

    register(org.glassfish.jersey.jackson.JacksonFeature.class);
    register(io.hops.hopsworks.filters.CustomJsonProvider.class);
  }
}
