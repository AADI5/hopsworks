/*
 * This file is part of Hopsworks
 * Copyright (C) 2019, Logical Clocks AB. All rights reserved
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

package io.hops.hopsworks.common.jupyter;

import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.hops.hopsworks.common.dao.jupyter.MaterializedJWTFacade;
import io.hops.hopsworks.common.dao.jupyter.JupyterSettingsFacade;
import io.hops.hopsworks.common.dao.jupyter.config.JupyterFacade;
import io.hops.hopsworks.common.dao.project.ProjectFacade;
import io.hops.hopsworks.common.dao.user.UserFacade;
import io.hops.hopsworks.common.user.UsersController;
import io.hops.hopsworks.common.util.DateUtils;
import io.hops.hopsworks.common.util.PayaraClusterManager;
import io.hops.hopsworks.common.util.Settings;
import io.hops.hopsworks.exceptions.ServiceException;
import io.hops.hopsworks.jwt.Constants;
import io.hops.hopsworks.jwt.JWTController;
import io.hops.hopsworks.jwt.SignatureAlgorithm;
import io.hops.hopsworks.jwt.exception.InvalidationException;
import io.hops.hopsworks.jwt.exception.JWTException;
import io.hops.hopsworks.persistence.entity.jupyter.MaterializedJWT;
import io.hops.hopsworks.persistence.entity.jupyter.MaterializedJWTID;
import io.hops.hopsworks.persistence.entity.jupyter.JupyterProject;
import io.hops.hopsworks.persistence.entity.jupyter.JupyterSettings;
import io.hops.hopsworks.persistence.entity.project.Project;
import io.hops.hopsworks.persistence.entity.user.Users;
import io.hops.hopsworks.restutils.RESTCodes;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.AccessTimeout;
import javax.ejb.DependsOn;
import javax.ejb.EJB;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

@Singleton
@Startup
@TransactionAttribute(TransactionAttributeType.NEVER)
@DependsOn("Settings")
public class JupyterJWTManager {
  private static final Logger LOG = Logger.getLogger(JupyterJWTManager.class.getName());
  public static final String TOKEN_FILE_NAME = "token.jwt";

  @EJB
  private Settings settings;
  @EJB
  private MaterializedJWTFacade materializedJWTFacade;
  @EJB
  private JWTController jwtController;
  @EJB
  private UsersController usersController;
  @EJB
  private JupyterFacade jupyterFacade;
  @Inject
  private JupyterManager jupyterManager;
  @EJB
  private JupyterSettingsFacade jupyterSettingsFacade;
  @EJB
  private ProjectFacade projectFacade;
  @EJB
  private UserFacade userFacade;
  @Inject
  private JupyterJWTTokenWriter jwtTokenWriter;
  @Resource
  private TimerService timerService;
  @EJB
  private PayaraClusterManager payaraClusterManager;
  @EJB
  private JupyterJWTCache jupyterJWTCache;
  
  @PostConstruct
  @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
  public void init() {
    try {
      recover();
    } catch (Exception ex) {
      LOG.log(Level.WARNING, "Exception while recovering Jupyter JWTs. Keep going on...", ex);
    }
    long monitorInterval = 5000L;
    timerService.createIntervalTimer(1000L, monitorInterval, new TimerConfig("Jupyter JWT renewal service", false));
  }

  private void addToken(JupyterJWT jupyterJWT) {
    jupyterJWTCache.add(jupyterJWT);
  }

  private void removeToken(CidAndPort pidAndPort) {
    jupyterJWTCache.remove(pidAndPort);
  }
  
  protected void recover() {
    //only one node should do the recovery. If a node is restarted it will not be the primary.
    if (!payaraClusterManager.amIThePrimary()) {
      return;
    }
    LOG.log(INFO, "Starting Jupyter JWT manager recovery");
    List<MaterializedJWT> failed2recover = new ArrayList<>();
    
    // Get state from the database
    for (MaterializedJWT materializedJWT : materializedJWTFacade.findAll4Jupyter()) {
      LOG.log(Level.FINEST, "Recovering Jupyter JWT " + materializedJWT.getIdentifier());
      
      // First lookup project and user in db
      Project project = projectFacade.find(materializedJWT.getIdentifier().getProjectId());
      Users user = userFacade.find(materializedJWT.getIdentifier().getUserId());
      if (project == null || user == null) {
        LOG.log(Level.WARNING, "Tried to recover " + materializedJWT.getIdentifier() + " but could not find " +
          "either Project or User");
        failed2recover.add(materializedJWT);
        continue;
      }
      
      // Get Jupyter configuration from db
      Optional<JupyterProject> jupyterProjectOptional = jupyterFacade.findByProjectUser(project, user);
      if (!jupyterProjectOptional.isPresent()) {
        LOG.log(Level.FINEST, "There is no Jupyter configuration persisted for " + materializedJWT.getIdentifier());
        failed2recover.add(materializedJWT);
        continue;
      }

      JupyterProject jupyterProject = jupyterProjectOptional.get();
      // Check if Jupyter is still running
      if (!jupyterManager.ping(jupyterProject)) {
        LOG.log(Level.FINEST, "Jupyter server is not running for " + materializedJWT.getIdentifier()
          + " Skip recovering...");
        failed2recover.add(materializedJWT);
        continue;
      }
      
      JupyterSettings jupyterSettings = jupyterSettingsFacade.findByProjectUser(project, user.getEmail());
      
      Path tokenFile = constructTokenFilePath(jupyterSettings);
      
      String token = null;
      JupyterJWT jupyterJWT;
      CidAndPort pidAndPort = new CidAndPort(jupyterProject.getCid(), jupyterProject.getPort());
      try {
        token = jwtTokenWriter.readToken(project, user);
        DecodedJWT decodedJWT = jwtController.verifyToken(token, settings.getJWTIssuer());
        jupyterJWT = new JupyterJWT(project, user, DateUtils.date2LocalDateTime(decodedJWT.getExpiresAt()), pidAndPort);
        jupyterJWT.token = token;
        jupyterJWT.tokenFile = tokenFile;
        LOG.log(Level.FINE, "Successfully read existing JWT from local filesystem");
      } catch (IOException | JWTException | JWTDecodeException ex) {
        LOG.log(Level.FINE, "Could not recover Jupyter JWT from local filesystem, generating new!", ex);
        // JWT does not exist, or it is not valid any longer
        // We should create a new one
        String[] audience = new String[]{"api"};
        LocalDateTime expirationDate = LocalDateTime.now().plus(settings.getJWTLifetimeMs(), ChronoUnit.MILLIS);
        String[] userRoles = usersController.getUserRoles(user).toArray(new String[1]);
        try {
          Map<String, Object> claims = new HashMap<>(3);
          claims.put(Constants.RENEWABLE, false);
          claims.put(Constants.EXPIRY_LEEWAY, settings.getJWTExpLeewaySec());
          claims.put(Constants.ROLES, userRoles);
          token = jwtController.createToken(settings.getJWTSigningKeyName(), false, settings.getJWTIssuer(),
              audience, DateUtils.localDateTime2Date(expirationDate), DateUtils.localDateTime2Date(DateUtils.getNow()),
              user.getUsername(), claims, SignatureAlgorithm.valueOf(settings.getJWTSignatureAlg()));
          jupyterJWT = new JupyterJWT(project, user, expirationDate, pidAndPort);
          jupyterJWT.token = token;
          jupyterJWT.tokenFile = tokenFile;
          jwtTokenWriter.writeToken(settings, jupyterJWT);
          LOG.log(Level.FINE, "Generated new Jupyter JWT cause could not recover existing");
        } catch (IOException recIOEx) {
          LOG.log(Level.WARNING, "Failed to recover Jupyter JWT for " + materializedJWT.getIdentifier()
            + ", generated new valid JWT but failed to write to local filesystem. Invalidating new token!" +
            " Continue recovering...");
          if (token != null) {
            try {
              jwtController.invalidate(token);
            } catch (InvalidationException jwtInvEx) {
              // NO-OP
            }
          }
          failed2recover.add(materializedJWT);
          continue;
        } catch (GeneralSecurityException | JWTException jwtEx) {
          LOG.log(Level.WARNING, "Failed to recover Jupyter JWT for " + materializedJWT.getIdentifier()
            + ", tried to generate new token and it failed as well. Could not recover! Continue recovering...");
          // Did our best, it's good to know when you should give up
          failed2recover.add(materializedJWT);
          continue;
        }
      }
      addToken(jupyterJWT);
    }
    
    // Remove from the database entries that we failed to recover
    for (MaterializedJWT failedRecovery : failed2recover) {
      materializedJWTFacade.delete(failedRecovery.getIdentifier());
    }
    LOG.log(INFO, "Finished Jupyter JWT recovery");
  }
  
  private Path constructTokenFilePath(JupyterSettings jupyterSettings) {
    return Paths.get(settings.getStagingDir(), Settings.PRIVATE_DIRS, jupyterSettings.getSecret(), TOKEN_FILE_NAME);
  }
  
  @Lock(LockType.WRITE)
  @AccessTimeout(value = 2000)
  public void materializeJWT(Users user, Project project, JupyterSettings jupyterSettings, String cid,
      Integer port, String[] audience) throws ServiceException {
    MaterializedJWTID materialID = new MaterializedJWTID(project.getId(), user.getUid(),
      MaterializedJWTID.USAGE.JUPYTER);
    if (!materializedJWTFacade.exists(materialID)) {
      LocalDateTime expirationDate = LocalDateTime.now().plus(settings.getJWTLifetimeMs(), ChronoUnit.MILLIS);
      JupyterJWT jupyterJWT = new JupyterJWT(project, user, expirationDate, new CidAndPort(cid, port));
      try {
        String[] roles = usersController.getUserRoles(user).toArray(new String[1]);
        MaterializedJWT materializedJWT = new MaterializedJWT(materialID);
        materializedJWTFacade.persist(materializedJWT);
        
        Map<String, Object> claims = new HashMap<>(3);
        claims.put(Constants.RENEWABLE, false);
        claims.put(Constants.EXPIRY_LEEWAY, settings.getJWTExpLeewaySec());
        claims.put(Constants.ROLES, roles);
        String token = jwtController.createToken(settings.getJWTSigningKeyName(), false, settings.getJWTIssuer(),
            audience, DateUtils.localDateTime2Date(expirationDate), DateUtils.localDateTime2Date(DateUtils.getNow()),
            user.getUsername(), claims, SignatureAlgorithm.valueOf(settings.getJWTSignatureAlg()));
        
        jupyterJWT.tokenFile = constructTokenFilePath(jupyterSettings);
        
        jupyterJWT.token = token;
        jwtTokenWriter.writeToken(settings, jupyterJWT);
        
        addToken(jupyterJWT);
      } catch (GeneralSecurityException | JWTException ex) {
        LOG.log(Level.SEVERE, "Error generating Jupyter JWT for " + jupyterJWT, ex);
        materializedJWTFacade.delete(materialID);
        throw new ServiceException(RESTCodes.ServiceErrorCode.JUPYTER_START_ERROR, Level.SEVERE,
          "Could not generate Jupyter JWT", ex.getMessage(), ex);
      } catch (IOException ex) {
        LOG.log(Level.SEVERE, "Error writing Jupyter JWT to file for " + jupyterJWT, ex);
        materializedJWTFacade.delete(materialID);
        try {
          jwtController.invalidate(jupyterJWT.token);
        } catch (InvalidationException invEx) {
          LOG.log(Level.FINE, "Could not invalidate Jupyter JWT after failure to write to file", ex);
        }
        throw new ServiceException(RESTCodes.ServiceErrorCode.JUPYTER_START_ERROR, Level.SEVERE,
          "Could not write Jupyter JWT to file", ex.getMessage(), ex);
      }
    }
  }
  
  @Lock(LockType.WRITE)
  @AccessTimeout(value = 500)
  @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
  @Timeout
  public void monitorJWT() {
    if (!payaraClusterManager.amIThePrimary()) {
      return;
    }
    // Renew the rest of them
    Set<JupyterJWT> renewedJWTs = new HashSet<>(jupyterJWTCache.getSize());
    Iterator<JupyterJWTDTO> jupyterJWTs = jupyterJWTCache.getMaybeExpired();
    LocalDateTime now = DateUtils.getNow();
    try {

      while (jupyterJWTs.hasNext()) {
        JupyterJWTDTO element = jupyterJWTs.next();
        // Elements are sorted by their expiration date.
        // If element N does not need to be renewed neither does N+1
        if (element.maybeRenew(now)) {
          LocalDateTime newExpirationDate = now.plus(settings.getJWTLifetimeMs(), ChronoUnit.MILLIS);
          String newToken = null;
          try {
            newToken = jwtController.renewToken(element.getToken(), DateUtils.localDateTime2Date(newExpirationDate),
                DateUtils.localDateTime2Date(now), false, new HashMap<>(3));
            Project project = projectFacade.find(element.getProjectId());
            Users user = userFacade.find(element.getUserId());
            JupyterJWT renewedJWT = new JupyterJWT(project, user, newExpirationDate, element.getPidAndPort());
            renewedJWT.tokenFile = Paths.get(element.getTokenFile());
            renewedJWT.token = newToken;
            jwtTokenWriter.writeToken(settings, renewedJWT);
            renewedJWTs.add(renewedJWT);
          } catch (JWTException ex) {
            LOG.log(Level.WARNING, "Could not renew Jupyter JWT for " + element, ex);
          } catch (IOException ex) {
            LOG.log(Level.WARNING, "Could not write renewed Jupyter JWT to file for " + element, ex);
            if (newToken != null) {
              try {
                jwtController.invalidate(newToken);
              } catch (InvalidationException invEx) {
                LOG.log(Level.FINE, "Could not invalidate failed token", invEx);
              }
            }
          } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Generic error renewing Jupyter JWT for " + element, ex);
          }
        } else {
          break;
        }
      }
      renewedJWTs.forEach(t -> {
        removeToken(t.pidAndPort);
        addToken(t);
      });
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "Got an exception while renewing jupyter jwt token" , e);
    }
  }

  @Lock(LockType.WRITE)
  @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
  public void cleanJWT(String cid, Integer port) {
    Optional<JupyterJWT> optional = jupyterJWTCache.get(new CidAndPort(cid, port));

    if (!optional.isPresent()) {
      LOG.log(WARNING, "JupyterJWT not found for cid " + cid + " and port " + port);
      return;
    }

    JupyterJWT element = optional.get();
    try {
      MaterializedJWTID materializedJWTID = new MaterializedJWTID(element.project.getId(), element.user.getUid(),
        MaterializedJWTID.USAGE.JUPYTER);
      MaterializedJWT material = materializedJWTFacade.findById(materializedJWTID);
      jwtTokenWriter.deleteToken(element);
      if (material != null) {
        materializedJWTFacade.delete(materializedJWTID);
      }
      removeToken(element.pidAndPort);
      jwtController.invalidate(element.token);
    } catch (Exception ex) {
      // Catch everything and do not fail. If we failed to determine the status of Jupyter, we renew the token
      // to be safe
      LOG.log(Level.FINE, "Could not determine if Jupyter JWT for " + element + " is still valid. Renewing it...");
    }
  }
}
