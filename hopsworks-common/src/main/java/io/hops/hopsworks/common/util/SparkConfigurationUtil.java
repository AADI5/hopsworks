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

package io.hops.hopsworks.common.util;

import com.google.common.base.Strings;
import com.logicalclocks.servicediscoverclient.exceptions.ServiceDiscoveryException;
import io.hops.hopsworks.common.hosts.ServiceDiscoveryController;
import io.hops.hopsworks.common.serving.ServingConfig;
import io.hops.hopsworks.common.util.templates.ConfigProperty;
import io.hops.hopsworks.common.util.templates.ConfigReplacementPolicy;
import io.hops.hopsworks.exceptions.ApiKeyException;
import io.hops.hopsworks.exceptions.JobException;
import io.hops.hopsworks.persistence.entity.jobs.configuration.DistributionStrategy;
import io.hops.hopsworks.persistence.entity.jobs.configuration.ExperimentType;
import io.hops.hopsworks.persistence.entity.jobs.configuration.JobConfiguration;
import io.hops.hopsworks.persistence.entity.jobs.configuration.JobType;
import io.hops.hopsworks.persistence.entity.jobs.configuration.spark.SparkJobConfiguration;
import io.hops.hopsworks.persistence.entity.project.Project;
import io.hops.hopsworks.persistence.entity.user.Users;
import io.hops.hopsworks.restutils.RESTCodes;
import io.hops.hopsworks.servicediscovery.HopsworksService;
import io.hops.hopsworks.servicediscovery.tags.OpenSearchTags;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class SparkConfigurationUtil extends ConfigurationUtil {
  
  public Map<String, String> setFrameworkProperties(Project project, JobConfiguration jobConfiguration,
                                                    Settings settings, String hdfsUser, Users hopsworksUser,
                                                    Map<String, String> extraJavaOptions, String kafkaBrokersString,
                                                    String hopsworksRestEndpoint, ServingConfig servingConfig,
                                                    ServiceDiscoveryController serviceDiscoveryController)
      throws IOException, ServiceDiscoveryException, JobException, ApiKeyException {
    SparkJobConfiguration sparkJobConfiguration = (SparkJobConfiguration) jobConfiguration;

    validateExecutorMemory(sparkJobConfiguration.getExecutorMemory(), settings);

    ExperimentType experimentType = sparkJobConfiguration.getExperimentType();
    DistributionStrategy distributionStrategy = sparkJobConfiguration.getDistributionStrategy();
    String userSparkProperties = sparkJobConfiguration.getProperties();

    Map<String, ConfigProperty> sparkProps = new HashMap<>();

    if(jobConfiguration.getAppName() != null) {
      sparkProps.put(Settings.SPARK_APP_NAME_ENV,
        new ConfigProperty(
          Settings.SPARK_APP_NAME_ENV,
          HopsUtils.OVERWRITE,
          sparkJobConfiguration.getAppName()));
    }

    if(sparkJobConfiguration.getJobType() != null && sparkJobConfiguration.getJobType() == JobType.PYSPARK) {
      sparkProps.put(Settings.SPARK_YARN_IS_PYTHON_ENV,
        new ConfigProperty(
          Settings.SPARK_YARN_IS_PYTHON_ENV,
          HopsUtils.OVERWRITE,
          "true"));
    }
  
    sparkProps.put(Settings.SPARK_YARN_APPMASTER_CONTAINER_RUNTIME, new ConfigProperty(
      Settings.SPARK_YARN_APPMASTER_CONTAINER_RUNTIME, HopsUtils.OVERWRITE, settings.getYarnRuntime()));
    sparkProps.put(Settings.SPARK_YARN_APPMASTER_DOCKER_IMAGE, new ConfigProperty(
      Settings.SPARK_YARN_APPMASTER_DOCKER_IMAGE, HopsUtils.OVERWRITE, ProjectUtils.getFullDockerImageName(project,
      settings, serviceDiscoveryController, false)));
    sparkProps.put(Settings.SPARK_YARN_APPMASTER_DOCKER_MOUNTS, new ConfigProperty(
      Settings.SPARK_YARN_APPMASTER_DOCKER_MOUNTS, HopsUtils.OVERWRITE, settings.getDockerMounts()));
  
    sparkProps.put(Settings.SPARK_EXECUTOR_CONTAINER_RUNTIME, new ConfigProperty(
      Settings.SPARK_EXECUTOR_CONTAINER_RUNTIME, HopsUtils.OVERWRITE, settings.getYarnRuntime()));
    sparkProps.put(Settings.SPARK_EXECUTOR_DOCKER_IMAGE, new ConfigProperty(
      Settings.SPARK_EXECUTOR_DOCKER_IMAGE, HopsUtils.OVERWRITE, ProjectUtils.getFullDockerImageName(project,
      settings, serviceDiscoveryController, false)));
    sparkProps.put(Settings.SPARK_EXECUTOR_DOCKER_MOUNTS, new ConfigProperty(
      Settings.SPARK_EXECUTOR_DOCKER_MOUNTS, HopsUtils.OVERWRITE, settings.getDockerMounts()));
  
    sparkProps.put(Settings.SPARK_HADOOP_FS_PERMISSIONS_UMASK, new ConfigProperty(
      Settings.SPARK_HADOOP_FS_PERMISSIONS_UMASK, HopsUtils.OVERWRITE,
      Settings.SPARK_HADOOP_FS_PERMISSIONS_UMASK_DEFAULT));
  
    sparkProps.put(Settings.SPARK_YARN_APPMASTER_IS_DRIVER, new ConfigProperty(Settings.SPARK_YARN_APPMASTER_IS_DRIVER,
      HopsUtils.IGNORE, "true"));
  
    sparkProps.put(Settings.SPARK_PYSPARK_PYTHON_OPTION, new ConfigProperty(
      Settings.SPARK_PYSPARK_PYTHON_OPTION, HopsUtils.IGNORE,
      settings.getAnacondaProjectDir() + "/bin/python"));
  
    //https://docs.nvidia.com/cuda/cuda-c-programming-guide/index.html
    //Needs to be set for CUDA libraries to not initialize GPU context
    sparkProps.put(Settings.SPARK_YARN_APPMASTER_CUDA_DEVICES,
      new ConfigProperty(Settings.SPARK_YARN_APPMASTER_CUDA_DEVICES,
        HopsUtils.IGNORE, ""));
  
    //https://rocm-documentation.readthedocs.io/en/latest/Other_Solutions/Other-Solutions.html
    //Needs to be set for ROCm libraries to not initialize GPU context
    sparkProps.put(Settings.SPARK_YARN_APPMASTER_HIP_DEVICES,
      new ConfigProperty(Settings.SPARK_YARN_APPMASTER_HIP_DEVICES,
        HopsUtils.IGNORE, "-1"));
  
    sparkProps.put(Settings.SPARK_YARN_APPMASTER_ENV_EXECUTOR_GPUS,
        new ConfigProperty(Settings.SPARK_YARN_APPMASTER_ENV_EXECUTOR_GPUS,
            HopsUtils.IGNORE, "0"));

    sparkProps.put(Settings.SPARK_EXECUTOR_ENV_EXECUTOR_GPUS,
        new ConfigProperty(Settings.SPARK_EXECUTOR_ENV_EXECUTOR_GPUS,
            HopsUtils.IGNORE, Integer.toString(sparkJobConfiguration.getExecutorGpus())));

    sparkProps.put(Settings.SPARK_SUBMIT_DEPLOYMODE, new ConfigProperty(Settings.SPARK_SUBMIT_DEPLOYMODE,
      HopsUtils.OVERWRITE,"cluster"));
    
    if(sparkJobConfiguration.getExecutorGpus() == 0) {
      addToSparkEnvironment(sparkProps, "HIP_VISIBLE_DEVICES", "-1", HopsUtils.IGNORE);
      addToSparkEnvironment(sparkProps, "CUDA_VISIBLE_DEVICES", "", HopsUtils.IGNORE);
      sparkProps.put(Settings.SPARK_EXECUTOR_GPU_AMOUNT,
        new ConfigProperty(
          Settings.SPARK_EXECUTOR_GPU_AMOUNT,
          HopsUtils.IGNORE,
          Integer.toString(0)));
    } else if (experimentType != null && sparkJobConfiguration.getExecutorGpus() > 0) {
      //Number of GPU allocated for each executor
      sparkProps.put(Settings.SPARK_EXECUTOR_GPU_AMOUNT,
        new ConfigProperty(
          Settings.SPARK_EXECUTOR_GPU_AMOUNT,
          HopsUtils.IGNORE,
          Integer.toString(sparkJobConfiguration.getExecutorGpus())));
      //Spark tasks should not share GPUs so we set it to the number of GPUs allocated for each executor
      sparkProps.put(Settings.SPARK_TASK_RESOURCE_GPU_AMOUNT,
        new ConfigProperty(
          Settings.SPARK_TASK_RESOURCE_GPU_AMOUNT,
          HopsUtils.OVERWRITE,
          Integer.toString(sparkJobConfiguration.getExecutorGpus())));
      //Script needed to find all the GPUs that the Executor has access to
      sparkProps.put(Settings.SPARK_EXECUTOR_RESOURCE_GPU_DISCOVERY_SCRIPT,
        new ConfigProperty(
          Settings.SPARK_EXECUTOR_RESOURCE_GPU_DISCOVERY_SCRIPT,
          HopsUtils.IGNORE,
          settings.getSparkDir() + "/bin/getGpusResources.sh"));
    }


    addToSparkEnvironment(sparkProps, "SPARK_HOME", settings.getSparkDir(), HopsUtils.IGNORE);
    addToSparkEnvironment(sparkProps, "SPARK_CONF_DIR", settings.getSparkConfDir(), HopsUtils.IGNORE);

    String elasticEndpoint = (settings.isOpenSearchHTTPSEnabled() ? "https://" : "http://") +
        serviceDiscoveryController.constructServiceAddressWithPort(
            HopsworksService.OPENSEARCH.getNameWithTag(OpenSearchTags.rest));
    addToSparkEnvironment(sparkProps, "ELASTIC_ENDPOINT", elasticEndpoint, HopsUtils.IGNORE);

    addToSparkEnvironment(sparkProps, "HADOOP_VERSION", settings.getHadoopVersion(), HopsUtils.IGNORE);
    addToSparkEnvironment(sparkProps, "HOPSWORKS_VERSION", settings.getHopsworksVersion(), HopsUtils.IGNORE);
    addToSparkEnvironment(sparkProps, "TENSORFLOW_VERSION", settings.getTensorflowVersion(), HopsUtils.IGNORE);
    addToSparkEnvironment(sparkProps, "KAFKA_VERSION", settings.getKafkaVersion(),HopsUtils.IGNORE);
    addToSparkEnvironment(sparkProps, "SPARK_VERSION", settings.getSparkVersion(), HopsUtils.IGNORE);
    addToSparkEnvironment(sparkProps, "LIVY_VERSION", settings.getLivyVersion(), HopsUtils.IGNORE);
    addToSparkEnvironment(sparkProps, "HADOOP_HOME", settings.getHadoopSymbolicLinkDir(), HopsUtils.IGNORE);
    addToSparkEnvironment(sparkProps, "HADOOP_HDFS_HOME", settings.getHadoopSymbolicLinkDir(),
            HopsUtils.IGNORE);
    addToSparkEnvironment(sparkProps, "HADOOP_USER_NAME", hdfsUser, HopsUtils.IGNORE);

    if(!Strings.isNullOrEmpty(sparkJobConfiguration.getAppName())) {
      addToSparkEnvironment(sparkProps, "HOPSWORKS_JOB_NAME", sparkJobConfiguration.getAppName(),
              HopsUtils.IGNORE);
    }
    if(!Strings.isNullOrEmpty(kafkaBrokersString)) {
      addToSparkEnvironment(sparkProps, "KAFKA_BROKERS", kafkaBrokersString, HopsUtils.IGNORE);
    }
    addToSparkEnvironment(sparkProps, "REST_ENDPOINT", hopsworksRestEndpoint, HopsUtils.IGNORE);
    addToSparkEnvironment(sparkProps,
      Settings.SPARK_PYSPARK_PYTHON, settings.getAnacondaProjectDir() + "/bin/python",
            HopsUtils.IGNORE);
    addToSparkEnvironment(sparkProps, "HOPSWORKS_PROJECT_ID", Integer.toString(project.getId()),
            HopsUtils.IGNORE);
    addToSparkEnvironment(sparkProps, "REQUESTS_VERIFY", String.valueOf(settings.getRequestsVerify()),
      HopsUtils.IGNORE);
    addToSparkEnvironment(sparkProps, "DOMAIN_CA_TRUSTSTORE", Settings.DOMAIN_CA_TRUSTSTORE, HopsUtils.IGNORE);
    addToSparkEnvironment(sparkProps, "SERVICE_DISCOVERY_DOMAIN", settings.getServiceDiscoveryDomain(),
        HopsUtils.IGNORE);
    // HOPSWORKS-3158
    addToSparkEnvironment(sparkProps, "HOPSWORKS_PUBLIC_HOST", settings.getHopsworksPublicHost(),
                          HopsUtils.IGNORE);

    // add extra env vars
    if (servingConfig != null) {
      Map<String, String> servingEnvVars = servingConfig.getEnvVars(hopsworksUser, true);
      if (servingEnvVars != null) {
        servingEnvVars.forEach((key, value) -> addToSparkEnvironment(sparkProps, key, value, HopsUtils.IGNORE));
      }
    }
    
    addLibHdfsOpts(userSparkProperties, settings, sparkProps, sparkJobConfiguration);
  
    //If DynamicExecutors are not enabled, set the user defined number
    //of executors

    //Force dynamic allocation if we are running a DL experiment (we never want users to lock up GPUs)
    sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_ENV,
                  new ConfigProperty(
                          Settings.SPARK_DYNAMIC_ALLOC_ENV,
                          HopsUtils.OVERWRITE,
                          String.valueOf(sparkJobConfiguration.isDynamicAllocationEnabled() ||
                            experimentType != null)));

    if(experimentType != null) {
      //Dynamic executors requires the shuffle service to be enabled
      sparkProps.put(Settings.SPARK_SHUFFLE_SERVICE,
        new ConfigProperty(
          Settings.SPARK_SHUFFLE_SERVICE,
          HopsUtils.OVERWRITE,
          "true"));
      //To avoid deadlock in resource allocation this configuration is needed
      if(experimentType == ExperimentType.DISTRIBUTED_TRAINING) {
        if(distributionStrategy == DistributionStrategy.MULTI_WORKER_MIRRORED) {
          sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_MIN_EXECS_ENV,
            new ConfigProperty(
              Settings.SPARK_DYNAMIC_ALLOC_MIN_EXECS_ENV,
              HopsUtils.OVERWRITE,
              "0"));
          sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_MAX_EXECS_ENV,
            new ConfigProperty(
              Settings.SPARK_DYNAMIC_ALLOC_MAX_EXECS_ENV,
              HopsUtils.OVERWRITE,
              String.valueOf(sparkJobConfiguration.getDynamicAllocationMaxExecutors())));
          sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_INIT_EXECS_ENV,
            new ConfigProperty(
              Settings.SPARK_DYNAMIC_ALLOC_INIT_EXECS_ENV,
              HopsUtils.OVERWRITE,
              String.valueOf(sparkJobConfiguration.getDynamicAllocationMaxExecutors())));
        } else if(distributionStrategy == DistributionStrategy.PARAMETER_SERVER) {
          sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_MIN_EXECS_ENV,
            new ConfigProperty(
              Settings.SPARK_DYNAMIC_ALLOC_MIN_EXECS_ENV,
              HopsUtils.OVERWRITE,
              "0"));
          sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_MAX_EXECS_ENV,
            new ConfigProperty(
              Settings.SPARK_DYNAMIC_ALLOC_MAX_EXECS_ENV,
              HopsUtils.OVERWRITE,
              String.valueOf(sparkJobConfiguration.getDynamicAllocationMaxExecutors())
              + sparkJobConfiguration.getNumPs()));
          sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_INIT_EXECS_ENV,
            new ConfigProperty(
              Settings.SPARK_DYNAMIC_ALLOC_INIT_EXECS_ENV,
              HopsUtils.OVERWRITE,
              String.valueOf(sparkJobConfiguration.getDynamicAllocationMaxExecutors())
              + sparkJobConfiguration.getNumPs()));
          addToSparkEnvironment(sparkProps, "NUM_TF_PS", Integer.toString(sparkJobConfiguration.getNumPs()),
            HopsUtils.IGNORE);
        }
        //These values were set based on:
        //https://docs.nvidia.com/deeplearning/nccl/archives/nccl_256/nccl-developer-guide/docs/env.html
        addToSparkEnvironment(sparkProps, Settings.NCCL_SOCKET_NTHREADS, "2", HopsUtils.OVERWRITE);
        addToSparkEnvironment(sparkProps, Settings.NCCL_NSOCKS_PERTHREAD, "8", HopsUtils.OVERWRITE);

      } else if(experimentType == ExperimentType.PARALLEL_EXPERIMENTS) {
        sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_MIN_EXECS_ENV,
          new ConfigProperty(
            Settings.SPARK_DYNAMIC_ALLOC_MIN_EXECS_ENV,
            HopsUtils.OVERWRITE,
            "0"));
        sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_MAX_EXECS_ENV,
          new ConfigProperty(
            Settings.SPARK_DYNAMIC_ALLOC_MAX_EXECS_ENV,
            HopsUtils.OVERWRITE,
            String.valueOf(sparkJobConfiguration.getDynamicAllocationMaxExecutors())));
        sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_INIT_EXECS_ENV,
          new ConfigProperty(
            Settings.SPARK_DYNAMIC_ALLOC_INIT_EXECS_ENV,
            HopsUtils.OVERWRITE,
            "0"));
      } else { //EXPERIMENT
        sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_MIN_EXECS_ENV,
          new ConfigProperty(
            Settings.SPARK_DYNAMIC_ALLOC_MIN_EXECS_ENV,
            HopsUtils.OVERWRITE,
            "0"));
        sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_MAX_EXECS_ENV,
          new ConfigProperty(
            Settings.SPARK_DYNAMIC_ALLOC_MAX_EXECS_ENV,
            HopsUtils.OVERWRITE,
            "1"));
        sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_INIT_EXECS_ENV,
          new ConfigProperty(
            Settings.SPARK_DYNAMIC_ALLOC_INIT_EXECS_ENV,
            HopsUtils.OVERWRITE,
            "0"));
      }
    } else if(sparkJobConfiguration.isDynamicAllocationEnabled()) {
      //Spark dynamic
      sparkProps.put(Settings.SPARK_SHUFFLE_SERVICE,
        new ConfigProperty(
          Settings.SPARK_SHUFFLE_SERVICE,
          HopsUtils.OVERWRITE,
          "true"));

      // To avoid users creating erroneous configurations for the initialExecutors field
      // Initial executors should not be greater than MaxExecutors
      if(sparkJobConfiguration.getDynamicAllocationInitialExecutors() >
              sparkJobConfiguration.getDynamicAllocationMaxExecutors()) {
        sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_INIT_EXECS_ENV,
          new ConfigProperty(
          Settings.SPARK_DYNAMIC_ALLOC_INIT_EXECS_ENV,
          HopsUtils.OVERWRITE,
          String.valueOf(sparkJobConfiguration.getDynamicAllocationMaxExecutors())));
      // Initial executors should not be less than MinExecutors
      } else if(sparkJobConfiguration.getDynamicAllocationInitialExecutors() <
              sparkJobConfiguration.getDynamicAllocationMinExecutors()) {
        sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_INIT_EXECS_ENV,
          new ConfigProperty(
          Settings.SPARK_DYNAMIC_ALLOC_INIT_EXECS_ENV,
          HopsUtils.OVERWRITE,
          String.valueOf(sparkJobConfiguration.getDynamicAllocationMinExecutors())));
      } else {
      // User set it to a valid value
        sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_INIT_EXECS_ENV,
          new ConfigProperty(
          Settings.SPARK_DYNAMIC_ALLOC_INIT_EXECS_ENV,
          HopsUtils.OVERWRITE,
          String.valueOf(sparkJobConfiguration.getDynamicAllocationInitialExecutors())));
      }
      sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_MIN_EXECS_ENV,
        new ConfigProperty(
          Settings.SPARK_DYNAMIC_ALLOC_MIN_EXECS_ENV,
          HopsUtils.OVERWRITE,
          String.valueOf(sparkJobConfiguration.getDynamicAllocationMinExecutors())));
      sparkProps.put(Settings.SPARK_DYNAMIC_ALLOC_MAX_EXECS_ENV,
        new ConfigProperty(
          Settings.SPARK_DYNAMIC_ALLOC_MAX_EXECS_ENV,
          HopsUtils.OVERWRITE,
          String.valueOf(sparkJobConfiguration.getDynamicAllocationMaxExecutors())));
      sparkProps.put(Settings.SPARK_NUMBER_EXECUTORS_ENV,
        new ConfigProperty(
          Settings.SPARK_NUMBER_EXECUTORS_ENV,
          HopsUtils.OVERWRITE,
          Integer.toString(sparkJobConfiguration.getDynamicAllocationMinExecutors())));
    } else {
      //Spark Static
      sparkProps.put(Settings.SPARK_NUMBER_EXECUTORS_ENV,
          new ConfigProperty(
            Settings.SPARK_NUMBER_EXECUTORS_ENV,
            HopsUtils.OVERWRITE,
            Integer.toString(sparkJobConfiguration.getExecutorInstances())));
    }
    sparkProps.put(Settings.SPARK_DRIVER_MEMORY_ENV,
      new ConfigProperty(
        Settings.SPARK_DRIVER_MEMORY_ENV,
        HopsUtils.OVERWRITE,
        sparkJobConfiguration.getAmMemory() + "m"));
    sparkProps.put(Settings.SPARK_DRIVER_CORES_ENV,
      new ConfigProperty(
        Settings.SPARK_DRIVER_CORES_ENV,
        HopsUtils.OVERWRITE,
        Integer.toString(experimentType != null ? 1 : sparkJobConfiguration.getAmVCores())));
    sparkProps.put(Settings.SPARK_EXECUTOR_MEMORY_ENV,
      new ConfigProperty(
        Settings.SPARK_EXECUTOR_MEMORY_ENV,
        HopsUtils.OVERWRITE,
        sparkJobConfiguration.getExecutorMemory() + "m"));
    sparkProps.put(Settings.SPARK_EXECUTOR_CORES_ENV,
      new ConfigProperty(
        Settings.SPARK_EXECUTOR_CORES_ENV,
        HopsUtils.OVERWRITE,
        Integer.toString(experimentType != null ? 1 : sparkJobConfiguration.getExecutorCores())));

    StringBuilder extraClassPath = new StringBuilder();
    extraClassPath
      .append("{{PWD}}")
      .append(File.pathSeparator)
      .append(settings.getSparkDir())
      .append("/jars/*")
      .append(File.pathSeparator)
      .append(settings.getSparkDir())
      .append("/hopsworks-jars/*");

    StringBuilder sparkFiles = new StringBuilder(settings.getSparkLog4JPath());
    String applicationsJars = sparkJobConfiguration.getJars();
    if(!Strings.isNullOrEmpty(applicationsJars)) {
      applicationsJars = formatResources(applicationsJars);
      for(String jar: applicationsJars.split(",")) {
        String name = jar.substring(jar.lastIndexOf("/") + 1);
        extraClassPath.append(File.pathSeparator).append(name);
      }
      applicationsJars = formatResources(applicationsJars);
      sparkFiles.append(",").append(applicationsJars);
    }

    String applicationArchives = sparkJobConfiguration.getArchives();
    if(!Strings.isNullOrEmpty(applicationArchives)) {
      applicationArchives = formatResources(applicationArchives);
      sparkProps.put(Settings.SPARK_YARN_DIST_ARCHIVES, new ConfigProperty(
          Settings.SPARK_YARN_DIST_ARCHIVES, HopsUtils.APPEND_COMMA,
          applicationArchives));
    }

    // If Hops RPC TLS is enabled, password file would be injected by the
    // NodeManagers. We don't need to add it as LocalResource
    if (!settings.getHopsRpcTls()) {
      sparkFiles
              // Keystore
              .append(",hdfs://").append(settings.getHdfsTmpCertDir()).append(File.separator)
              .append(hdfsUser).append(File.separator).append(hdfsUser)
              .append("__kstore.jks#").append(Settings.K_CERTIFICATE)
              .append(",")
              // TrustStore
              .append("hdfs://").append(settings.getHdfsTmpCertDir()).append(File.separator)
              .append(hdfsUser).append(File.separator).append(hdfsUser)
              .append("__tstore.jks#").append(Settings.T_CERTIFICATE)
              .append(",")
              // File with crypto material password
              .append("hdfs://").append(settings.getHdfsTmpCertDir()).append(File.separator)
              .append(hdfsUser).append(File.separator).append(hdfsUser)
              .append("__cert.key#").append(Settings.CRYPTO_MATERIAL_PASSWORD);
    }

    String applicationFiles = sparkJobConfiguration.getFiles();
    if(!Strings.isNullOrEmpty(applicationFiles)) {
      applicationFiles = formatResources(applicationFiles);
      sparkFiles.append(",").append(applicationFiles);
    }

    String applicationPyFiles = sparkJobConfiguration.getPyFiles();
    if(!Strings.isNullOrEmpty(applicationPyFiles)) {
      StringBuilder pythonPath = new StringBuilder();
      applicationPyFiles = formatResources(applicationPyFiles);
      for(String pythonDep: applicationPyFiles.split(",")) {
        String name = pythonDep.substring(pythonDep.lastIndexOf("/") + 1);
        pythonPath.append("{{PWD}}/" + name + File.pathSeparator);
      }
      addToSparkEnvironment(sparkProps,"PYTHONPATH", pythonPath.toString(), HopsUtils.APPEND_PATH);
      sparkFiles.append(",").append(applicationPyFiles);
    }

    applicationFiles = formatResources(sparkFiles.toString());
    sparkProps.put(Settings.SPARK_YARN_DIST_FILES, new ConfigProperty(
            Settings.SPARK_YARN_DIST_FILES, HopsUtils.APPEND_COMMA,
            applicationFiles));

    sparkProps.put(Settings.SPARK_DRIVER_EXTRACLASSPATH, new ConfigProperty(
            Settings.SPARK_DRIVER_EXTRACLASSPATH, HopsUtils.APPEND_PATH, extraClassPath.toString()));

    sparkProps.put(Settings.SPARK_EXECUTOR_EXTRACLASSPATH, new ConfigProperty(
            Settings.SPARK_EXECUTOR_EXTRACLASSPATH, HopsUtils.APPEND_PATH, extraClassPath.toString()));

    //We do not support fault-tolerance for distributed training
    if(experimentType == ExperimentType.DISTRIBUTED_TRAINING)
    {
      sparkProps.put(Settings.SPARK_BLACKLIST_ENABLED, new ConfigProperty(
        Settings.SPARK_BLACKLIST_ENABLED, HopsUtils.OVERWRITE,
        "false"));
    } else if(sparkJobConfiguration.isBlacklistingEnabled()) {
      sparkProps.put(Settings.SPARK_BLACKLIST_ENABLED, new ConfigProperty(
        Settings.SPARK_BLACKLIST_ENABLED, HopsUtils.OVERWRITE,
        Boolean.toString(sparkJobConfiguration.isBlacklistingEnabled())));

      // If any task fails on an executor - kill it instantly (need fresh working directory for each task)
      sparkProps.put(Settings.SPARK_BLACKLIST_MAX_TASK_ATTEMPTS_PER_EXECUTOR, new ConfigProperty(
        Settings.SPARK_BLACKLIST_MAX_TASK_ATTEMPTS_PER_EXECUTOR, HopsUtils.OVERWRITE, "1"));

      // Blacklist node after 2 tasks fails on it
      sparkProps.put(Settings.SPARK_BLACKLIST_MAX_TASK_ATTEMPTS_PER_NODE, new ConfigProperty(
        Settings.SPARK_BLACKLIST_MAX_TASK_ATTEMPTS_PER_NODE, HopsUtils.OVERWRITE, "2"));

      // If any task fails on an executor within a stage - blacklist it
      sparkProps.put(Settings.SPARK_BLACKLIST_STAGE_MAX_FAILED_TASKS_PER_EXECUTOR, new ConfigProperty(
        Settings.SPARK_BLACKLIST_STAGE_MAX_FAILED_TASKS_PER_EXECUTOR, HopsUtils.OVERWRITE, "1"));

      // Blacklist node after 2 tasks within a stage fails on it
      sparkProps.put(Settings.SPARK_BLACKLIST_STAGE_MAX_FAILED_TASKS_PER_NODE, new ConfigProperty(
        Settings.SPARK_BLACKLIST_STAGE_MAX_FAILED_TASKS_PER_NODE, HopsUtils.OVERWRITE, "2"));

      // If any task fails on an executor within an application - blacklist it
      sparkProps.put(Settings.SPARK_BLACKLIST_APPLICATION_MAX_FAILED_TASKS_PER_EXECUTOR, new ConfigProperty(
        Settings.SPARK_BLACKLIST_APPLICATION_MAX_FAILED_TASKS_PER_EXECUTOR, HopsUtils.OVERWRITE, "1"));

      // If 2 task fails on a node within an application - blacklist it
      sparkProps.put(Settings.SPARK_BLACKLIST_APPLICATION_MAX_FAILED_TASKS_PER_NODE, new ConfigProperty(
        Settings.SPARK_BLACKLIST_APPLICATION_MAX_FAILED_TASKS_PER_NODE, HopsUtils.OVERWRITE, "2"));

      // Always kill the blacklisted executors (further failures could be results of local files from the failed task)
      sparkProps.put(Settings.SPARK_BLACKLIST_KILL_BLACKLISTED_EXECUTORS, new ConfigProperty(
        Settings.SPARK_BLACKLIST_KILL_BLACKLISTED_EXECUTORS, HopsUtils.OVERWRITE, "true"));
    }

    //These settings are very important, a DL experiment should not be retried unless fault tolerance is enabled
    //If fault tolerance is enabled then TASK_MAX_FAILURES needs to be set to 3 to match the blacklisting
    // settings above
    if(experimentType != null) {
      // Blacklisting is enabled and we are dealing with an Experiment/Parallel Experiment
      if (sparkJobConfiguration.isBlacklistingEnabled() &&
              (experimentType == ExperimentType.EXPERIMENT || experimentType == ExperimentType.PARALLEL_EXPERIMENTS)) {
        sparkProps.put(Settings.SPARK_TASK_MAX_FAILURES, new ConfigProperty(
                Settings.SPARK_TASK_MAX_FAILURES, HopsUtils.OVERWRITE, "3"));
      // All other configurations should not retry to avoid wasting time during development (syntax errors etc)
      } else {
        sparkProps.put(Settings.SPARK_TASK_MAX_FAILURES, new ConfigProperty(
                Settings.SPARK_TASK_MAX_FAILURES, HopsUtils.OVERWRITE, "1"));
      }
    }

    extraJavaOptions.put(Settings.JOB_LOG4J_CONFIG, settings.getSparkLog4j2FilePath());
    extraJavaOptions.put(Settings.HOPSWORKS_REST_ENDPOINT_PROPERTY, hopsworksRestEndpoint);
    extraJavaOptions.put(Settings.HOPSUTIL_INSECURE_PROPERTY, String.valueOf(settings.isHopsUtilInsecure()));
    extraJavaOptions.put(Settings.SERVER_TRUSTSTORE_PROPERTY, Settings.SERVER_TRUSTSTORE_PROPERTY);
    extraJavaOptions.put(Settings.HOPSWORKS_PROJECTID_PROPERTY, Integer.toString(project.getId()));
    extraJavaOptions.put(Settings.HOPSWORKS_PROJECTNAME_PROPERTY, project.getName());
    extraJavaOptions.put(Settings.SPARK_JAVA_LIBRARY_PROP, settings.getHadoopSymbolicLinkDir() + "/lib/native/");
    extraJavaOptions.put(Settings.HOPSWORKS_PROJECTUSER_PROPERTY, hdfsUser);
    extraJavaOptions.put(Settings.KAFKA_BROKERADDR_PROPERTY, kafkaBrokersString);
    extraJavaOptions.put(Settings.HOPSWORKS_JOBTYPE_PROPERTY, JobType.SPARK.name());
    extraJavaOptions.put(Settings.HOPSWORKS_DOMAIN_CA_TRUSTSTORE_PROPERTY, Settings.DOMAIN_CA_TRUSTSTORE);
    if(jobConfiguration.getAppName() != null) {
      extraJavaOptions.put(Settings.HOPSWORKS_JOBNAME_PROPERTY, jobConfiguration.getAppName());
    }

    StringBuilder extraJavaOptionsSb = new StringBuilder();
    for (String key : extraJavaOptions.keySet()) {
      extraJavaOptionsSb.append(" -D").append(key).append("=").append(extraJavaOptions.get(key));
    }

    sparkProps.put(Settings.SPARK_EXECUTOR_EXTRA_JAVA_OPTS, new ConfigProperty(
            Settings.SPARK_EXECUTOR_EXTRA_JAVA_OPTS, HopsUtils.APPEND_SPACE, extraJavaOptionsSb.toString()));

    sparkProps.put(Settings.SPARK_DRIVER_EXTRA_JAVA_OPTIONS, new ConfigProperty(
            Settings.SPARK_DRIVER_EXTRA_JAVA_OPTIONS, HopsUtils.APPEND_SPACE, extraJavaOptionsSb.toString()));

    Map<String, String> validatedSparkProperties = HopsUtils.validateUserProperties(userSparkProperties,
      settings.getSparkDir());
    // Merge system and user defined properties
    return HopsUtils.mergeHopsworksAndUserParams(sparkProps,
        validatedSparkProperties);
  }

  /**
   * Checks provided executor memory if sufficient
   * @param executorMemory
   * @param settings
   * @throws JobException
   */
  public void validateExecutorMemory(int executorMemory, Settings settings) throws JobException {
    if(executorMemory < settings.getSparkExecutorMinMemory()) {
      throw new JobException(RESTCodes.JobErrorCode.INSUFFICIENT_EXECUTOR_MEMORY, Level.SEVERE,
              ". Executor memory should not be less than " +  settings.getSparkExecutorMinMemory());
    }
  }

  private void addToSparkEnvironment(Map<String, ConfigProperty> sparkProps, String envName,
                                     String value, ConfigReplacementPolicy replacementPolicy) {
    sparkProps.put(Settings.SPARK_EXECUTOR_ENV + envName,
            new ConfigProperty(Settings.SPARK_EXECUTOR_ENV + envName,
                    replacementPolicy, value));
    sparkProps.put(Settings.SPARK_YARN_APPMASTER_ENV + envName,
            new ConfigProperty(Settings.SPARK_YARN_APPMASTER_ENV + envName,
                    replacementPolicy, value));
  }

  private void addLibHdfsOpts(String userSparkProperties, Settings settings, Map<String, ConfigProperty> sparkProps,
                              SparkJobConfiguration sparkJobConfiguration) {

    String defaultLibHdfsOpts = "-Dlog4j.configurationFile=" +
        settings.getHadoopSymbolicLinkDir() +"/etc/hadoop/log4j2.properties " +
        "-Dhadoop.log.dir=/tmp " +
        "-Dhadoop.root.logger=ERROR,console";
    Map<String, String> userProperties = HopsUtils.parseUserProperties(userSparkProperties);

    if(userProperties.containsKey(Settings.SPARK_YARN_APPMASTER_LIBHDFS_OPTS)) {
      //if user supplied xmx then append what they provided
      sparkProps.put(Settings.SPARK_YARN_APPMASTER_LIBHDFS_OPTS,
          new ConfigProperty(Settings.SPARK_YARN_APPMASTER_LIBHDFS_OPTS,
              HopsUtils.APPEND_SPACE, defaultLibHdfsOpts));
    } else {
      addDefaultXmx(sparkProps, Settings.SPARK_YARN_APPMASTER_ENV, (int)(sparkJobConfiguration.getAmMemory()*0.2),
          defaultLibHdfsOpts);
    }

    if(userProperties.containsKey(Settings.SPARK_EXECUTOR_ENV + "LIBHDFS_OPTS")) {
      //if user supplied xmx then append what they provided
      sparkProps.put(Settings.SPARK_EXECUTOR_ENV + "LIBHDFS_OPTS",
          new ConfigProperty(Settings.SPARK_EXECUTOR_ENV + "LIBHDFS_OPTS",
              HopsUtils.APPEND_SPACE, defaultLibHdfsOpts));
    } else {
      addDefaultXmx(sparkProps, Settings.SPARK_EXECUTOR_ENV, (int)(sparkJobConfiguration.getExecutorMemory()*0.2),
          defaultLibHdfsOpts);
    }
  }

  private void addDefaultXmx(Map<String, ConfigProperty> sparkProps, String property, int xmxValue,
                             String defaultLibHdfsOpts) {
    sparkProps.put(property + "LIBHDFS_OPTS",
        new ConfigProperty(property + "LIBHDFS_OPTS", HopsUtils.IGNORE,
            defaultLibHdfsOpts + " -Xmx" + xmxValue + "m"));
  }

  //Clean comma-separated resource string
  private String formatResources(String commaSeparatedResources) {
    String[] resourceArr = commaSeparatedResources.split(",");
    StringBuilder resourceBuilder = new StringBuilder();
    for(String resource: resourceArr) {
      if(!resource.equals(",") && !resource.equals("")) {
        resourceBuilder.append(resource.trim()).append(",");
      }
    }
    if(resourceBuilder.charAt(resourceBuilder.length()-1) == ',') {
      resourceBuilder.deleteCharAt(resourceBuilder.length()-1);
    }
    return resourceBuilder.toString();
  }
}
