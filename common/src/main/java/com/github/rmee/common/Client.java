package com.github.rmee.common;

import com.github.rmee.common.internal.IOUtils;
import com.github.rmee.common.internal.UnixUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.ExecSpec;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class Client {


	private ClientExtensionBase extension;

	private String version;

	private Map<String, String> environment = new HashMap();

	/**
	 * Environment for docker engine
	 */
	private Map<String, String> dockerEnvironment = new HashMap();

	private boolean download = true;

	private String binPath;

	private boolean dockerized = true;

	private String imageName;

	private String repository;

	private String downloadUrl;

	private String downloadFileName;

	private String binName;

	private File downloadDir;

	private File installDir;

	private OperatingSystem operatingSystem;

	private Map<String, File> volumeMappings = new HashMap<>();

	private Map<Integer, Integer> portMappings = new HashMap<>();

	private boolean useWrapper = true;

	private String runAs;

	private boolean runAsEnabled = true;

	public Client(ClientExtensionBase extension, String binName) {
		this.binName = binName;
		this.extension = extension;

		environment.put("HOME", "/build/home");

		dockerEnvironment.putAll(System.getenv());
	}

	private String getEnvValue(String name) {
		String value = System.getenv(name);
		if (value == null) {
			value = System.getenv(name.toLowerCase());
		}
		return value;
	}

	public File getHomeDir() {
		if (dockerized) {
			File buildDir = extension.project.getBuildDir();
			return new File(buildDir, "home");
		}

		String home = environment.get("HOME");
		if (home == null) {
			home = System.getenv("HOME");
		}
		if (home == null) {
			throw new IllegalStateException("HOME not specified in environment");
		}
		return new File(home);
	}


	public String getRunAs() {
		if (runAs == null && runAsEnabled && getOperatingSystem() != OperatingSystem.WINDOWS) {
			runAs = UnixUtils.getUid();
		}
		return runAs;
	}

	public void setRunAs(String runAs) {
		this.runAs = runAs;
	}

	public boolean isRunAsEnabled() {
		return runAsEnabled;
	}

	public void setRunAsEnabled(boolean runAsEnabled) {
		this.runAsEnabled = runAsEnabled;
	}

	/**
	 * Mapping from docker path to host path (the other way around compared to docker to ease configuration)
	 */
	public Map<String, File> getVolumeMappings() {
		return volumeMappings;
	}

	public void setVolumeMappings(Map<String, File> volumeMappings) {
		this.volumeMappings = volumeMappings;
	}

	public Map<Integer, Integer> getPortMappings() {
		return portMappings;
	}

	public void setPortMappings(Map<Integer, Integer> portMappings) {
		this.portMappings = portMappings;
	}

	public Map<String, String> getEnvironment() {
		return environment;
	}

	public void setEnvironment(Map<String, String> environment) {
		this.environment = environment;
	}

	public Map<String, String> getDockerEnvironment() {
		return dockerEnvironment;
	}

	public void setDockerEnvironment(Map<String, String> dockerEnvironment) {
		this.dockerEnvironment = dockerEnvironment;
	}

	public String getImageName() {
		return imageName;
	}

	public void setImageName(String imageName) {
		this.imageName = imageName;
	}

	public String getVersion() {
		extension.init();
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public boolean isDockerized() {
		return dockerized;
	}

	public void setDockerized(boolean dockerized) {
		if (dockerized) {
			environment.put("HOME", "/build/home");
		} else {
			environment.remove("HOME");
		}
		this.dockerized = dockerized;
	}

	public OperatingSystem getOperatingSystem() {
		if (operatingSystem == null) {
			operatingSystem = org.gradle.internal.os.OperatingSystem.current();
		}
		return operatingSystem;
	}

	public void setOperationSystem(OperatingSystem operatingSystem) {
		this.operatingSystem = operatingSystem;
	}

	public void init(Project project) {
		if (!dockerized) {
			// in non-Docker version use current environment by default
			if (environment.isEmpty()) {
				environment.putAll(System.getenv());
			}

			if (downloadDir == null && download) {
				downloadDir = new File(project.getBuildDir(), "tmp/" + binName + "/v" + version);
				downloadDir.mkdirs();
			}
			if (installDir == null) {
				installDir = new File(project.getBuildDir(), "/kubernetes/");
			}
			if (operatingSystem == null) {
				operatingSystem = org.gradle.internal.os.OperatingSystem.current();
			}

			if (binPath == null && download) {
				// use downloaded binary
				String binSuffix = getBinSuffix();
				binPath = new File(installDir, binName + binSuffix).getAbsolutePath();
				if (downloadUrl == null && download) {
					downloadFileName = computeDownloadFileName();
					downloadUrl = computeDownloadUrl(repository, downloadFileName);
				}
			} else if (binPath == null) {
				// assume binary available from PATH
				binPath = binName;
			}
		}
	}

	protected abstract String computeDownloadFileName();

	protected String getBinSuffix() {
		checkNotDockerized();
		return operatingSystem.isWindows() ? ".exe" : "";
	}

	protected abstract String computeDownloadUrl(String repository, String downloadFileName);

	public File getInstallDir() {
		checkNotDockerized();
		extension.init();
		return installDir;
	}

	public void setInstallDir(File installDir) {
		this.installDir = installDir;
	}

	public File getDownloadDir() {
		extension.init();
		return downloadDir;
	}

	public void setDownloadDir(File downloadDir) {
		checkNotDockerized();
		this.downloadDir = downloadDir;
	}

	public String getRepository() {
		checkNotDockerized();
		extension.init();
		return repository;
	}

	public void setRepository(String repository) {
		this.repository = repository;
	}

	/**
	 * @deprecated move to dockerized version
	 */
	@Deprecated
	public String getDownloadUrl() {
		extension.init();
		return downloadUrl;
	}

	protected String getDownloadFileName() {
		checkNotDockerized();
		extension.init();
		return downloadFileName;
	}

	/**
	 * @deprecated move to dockerized version
	 */
	public boolean getDownload() {
		return download;
	}

	/**
	 * @deprecated move to dockerized version
	 */
	public void setDownload(boolean download) {
		if (download) {
			checkNotDockerized();
		}
		this.download = download;
	}

	/**
	 * @deprecated move to dockerized version
	 */
	public String getBinPath() {
		extension.init();
		return binPath;
	}

	public void setBinPath(String binPath) {
		checkNotDockerized();
		this.binPath = binPath;
	}

	/**
	 * @deprecated move to dockerized version
	 */
	public File getDownloadedFile() {
		extension.init();
		return new File(downloadDir, getDownloadFileName());
	}

	public String getBinName() {
		return binName;
	}


	private void checkNotDockerized() {
		if (dockerized) {
			throw new IllegalStateException("not necssary when in dockerized mode");
		}
	}

	public void configureExec(ExecSpec execSpec, ClientExecSpec clientExecSpec) {
		execSpec.setIgnoreExitValue(clientExecSpec.isIgnoreExitValue());

		List<String> args = clientExecSpec.getCommandLine();
		if (dockerized) {
			execSpec.setEnvironment(dockerEnvironment);

			List<String> commandLine = new ArrayList<>();
			commandLine.addAll(buildBaseCommandLine(false));

			String httpProxy = getEnvValue("HTTP_PROXY");
			String httpsProxy = getEnvValue("HTTPS_PROXY");
			String noProxy = getEnvValue("NO_PROXY");
			if (httpProxy != null) {
				commandLine.add("-e");
				commandLine.add("HTTP_PROXY=" + httpProxy);
			}
			if (httpsProxy != null) {
				commandLine.add("-e");
				commandLine.add("HTTPS_PROXY=" + httpsProxy);
			}
			if (noProxy != null) {
				commandLine.add("-e");
				commandLine.add("NO_PROXY=" + noProxy);
			}

			String containerName = clientExecSpec.getContainerName();
			if (containerName != null) {
				commandLine.add("--name");
				commandLine.add(containerName);
			}

			String volumesFrom = clientExecSpec.getVolumesFrom();
			if (volumesFrom != null) {
				commandLine.add("--volumes-from");
				commandLine.add(volumesFrom);
			}

			commandLine.add(imageName + ":" + version);

			for (String arg : args) {
				commandLine.add(mapArg(arg));
			}
			if(extension.isLogCommandLine()) {
				System.out.println("Executing: " + commandLine.stream().collect(Collectors.joining(" ")));
			}
			execSpec.setCommandLine(commandLine);

		} else {
			args.set(0, getBinPath());

			execSpec.setEnvironment(environment);
			execSpec.setCommandLine(args);
		}

		File stdoutFile = clientExecSpec.getStdoutFile();
		if (stdoutFile != null) {
			try {
				if (stdoutFile.exists() && !stdoutFile.delete()) {
					throw new IllegalStateException("failed to delete " + stdoutFile);
				}
				execSpec.setStandardOutput(new FileOutputStream(stdoutFile));
			} catch (FileNotFoundException e) {
				throw new IllegalStateException("failed to redirect helm stdout: " + e.getMessage(), e);
			}
		}

	}

	/**
	 * Apply volume mappings to arguments
	 */
	private String mapArg(String arg) {
		for (Map.Entry<String, File> entry : volumeMappings.entrySet()) {
			String path = entry.getValue().getAbsolutePath();
			if (arg.startsWith(path)) {
				arg = entry.getKey() + arg.substring(path.length());
				break;
			} else if (arg.contains("=" + path)) {
				int sep = arg.indexOf("=" + path);
				arg = arg.substring(0, sep) + "=" + entry.getKey() + arg.substring(sep + 1 + path.length());
				break;
			}
		}
		arg = arg.replace('\\', '/');
		return arg;
	}

	private Collection<String> buildBaseCommandLine(boolean wrapper, String... additionalParams) {
		List<String> commandLine = new ArrayList<>();
		commandLine.add("docker");
		commandLine.add("run");
		commandLine.add("-i");
		commandLine.add("--rm");
		if (wrapper) {
			commandLine.add("--tty");
		}

		// directly running docker images as non-root is causing permission issues with many images
		// we fix the permissions of the output files instead
		String runAs = getRunAs();
		if (runAs != null) {
			commandLine.add("-u");
			commandLine.add(runAs);
		}

		commandLine.addAll(Arrays.asList(additionalParams));

		for (Map.Entry<String, String> entry : environment.entrySet()) {
			commandLine.add("-e");
			commandLine.add(entry.getKey() + "=" + entry.getValue());
		}

		for (Map.Entry<String, File> entry : volumeMappings.entrySet()) {
			addVolumeMapping(commandLine, entry);
		}

		for (Map.Entry<Integer, Integer> entry : portMappings.entrySet()) {
			commandLine.add("-p");
			commandLine.add(entry.getValue() + ":" + entry.getKey());
		}

		if (version == null) {
			throw new IllegalStateException("no version specified");
		}

		return commandLine;
	}

	private void addVolumeMapping(List<String> commandLine, Map.Entry<String, File> entry) {
		addVolumeMapping(commandLine, entry.getKey(), entry.getValue());
	}

	private void addVolumeMapping(List<String> commandLine, String guestPath, File hostDir) {
		String hostPath = hostDir.getAbsolutePath();

		// fix hostPath issues with windows
		hostPath = hostPath.replace('\\', '/');

		commandLine.add("-v");
		commandLine.add(hostPath + ":" + guestPath);

		hostDir.mkdirs();
	}

	public boolean useWrapper() {
		return useWrapper;
	}

	public void setWrapper(boolean useWrapper) {
		this.useWrapper = useWrapper;
	}

	public void setupWrapper(Project project) {
		setupWrapper(project, true);
	}

	public void setupWrapper(Project project, boolean mustIncludeBinary) {
		if (useWrapper()) {
			Project rootProject = project.getRootProject();
			Task wrapper = rootProject.getTasks().getByName("wrapper");
			wrapper.doLast(task -> {
				StringBuilder builder = new StringBuilder();

				// consider additional volume mappings in the future by calling
				// buildBaseCommandLine(true, ...);
				try {
					String bootstrapSnipped = IOUtils.toString(getClass().getClassLoader().getResourceAsStream("wrapper.sh.template"));

					bootstrapSnipped = bootstrapSnipped.replace("${DOCKER_IMAGE}", imageName + ":" + version);
					bootstrapSnipped = bootstrapSnipped.replace("${DOCKER_COMMAND}", mustIncludeBinary ? binName : "");
					bootstrapSnipped = bootstrapSnipped.replace("\r", "");

					builder.append(bootstrapSnipped);
				} catch (IOException e) {
					throw new IllegalStateException("failed to find wrapper template", e);
				}

				File file = new File(project.getProjectDir(), binName);
				try (FileWriter writer = new FileWriter(file)) {
					writer.write(builder.toString());
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			});
		}
	}


	public void exec(ClientExecSpec spec) {
		Project project = extension.project;
		project.exec(execSpec -> {
			configureExec(execSpec, spec);
		});
	}


	public File getHome(String file) {
		File homeDir = getHomeDir();
		return new File(homeDir, file);
	}
}
