package rs.mojo.uploadfiles;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.Authentication;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.Proxy;
import org.apache.maven.repository.legacy.WagonManager;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.wagon.CommandExecutionException;
import org.apache.maven.wagon.CommandExecutor;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.ResourceDoesNotExistException;
import org.apache.maven.wagon.TransferFailedException;
import org.apache.maven.wagon.UnsupportedProtocolException;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.authentication.AuthenticationException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.authorization.AuthorizationException;
import org.apache.maven.wagon.observers.Debug;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.apache.maven.wagon.repository.Repository;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ComponentConfigurator;
import org.codehaus.plexus.component.repository.exception.ComponentLifecycleException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * Goal that uploads a file or directory to a distribution repository.
 * <p>The target "upload" is used mainly to copy simple files or directories up to
 * a repository. It was created due to a tycho lack of publishing P2 repositories.
 * This plugin is able to copy such directories.</p>
 * Example:
 * <pre>
	&lt;build&gt;
		&lt;plugins&gt;
			&lt;plugin&gt;
				&lt;groupId&gt;eu.ralph-schuster&lt;/groupId&gt;
				&lt;artifactId&gt;uploadfiles-maven-plugin&lt;/artifactId&gt;
				&lt;version&gt;1.0.0&lt;/version&gt;
				&lt;executions&gt;
					&lt;execution&gt;
						&lt;goals&gt;&lt;goal&gt;upload&lt;/goal&gt;&lt;/goals&gt;
						&lt;phase&gt;deploy&lt;/phase&gt;
					&lt;/execution&gt;
				&lt;/executions&gt;
				&lt;configuration&gt;
					&lt;path&gt;target/repository&lt;/path&gt;
					&lt;targetPath&gt;.&lt;/targetPath&gt;
				&lt;/configuration&gt;
			&lt;/plugin&gt;
		&lt;/plugins&gt;
	&lt;/build&gt;
 * </pre>
 * The example above uploads the target/repository folder to the root of the repository (specified
 * by &lt;distributionManagement&gt; &lt;repository&gt; or &lt;snapshotRepository&gt;.
 * 
 * @author Ralph Schuster
 * @version 1.0.0
 */
@Mojo(name="upload", defaultPhase=LifecyclePhase.DEPLOY)
public class Upload extends AbstractMojo {

	/** Project */
	@Parameter(defaultValue="${project}")
	private MavenProject project;
	/** repository to upload to */
	@Parameter(defaultValue="${project.distributionManagementArtifactRepository}")
	private ArtifactRepository repository;
	@Component(role = WagonManager.class)
	private WagonManager wagonManager;

	/**
	 * File or directory to be uploaded, defaults to main artifact file.
	 * @since 1.0
	 */
	@Parameter( alias = "path", required = false )
	private File path;

	/**
	 * Target directory and/or target name, relative to repository root. Defaults #{@link #path}.
	 * @since 1.0
	 */
	@Parameter( alias = "targetPath", required = false )
	private String targetPath;

	/**
	 * Whether to run the "chmod" command on the remote site after the deploy.
	 * Defaults to "true".
	 *
	 * @since 1.0
	 */
	@Parameter( property = "rs.upload.chmod", defaultValue = "true" )
	private boolean chmod;

	/**
	 * The mode used by the "chmod" command. Only used if chmod = true.
	 * Defaults to "g+w,a+rX".
	 *
	 * @since 1.0
	 */
	@Parameter( property = "rs.upload.chmod.mode", defaultValue = "g+w,a+rX" )
	private String chmodMode;

	/**
	 * The options used by the "chmod" command. Only used if chmod = true.
	 * Defaults to "-Rf".
	 *
	 * @since 1.0
	 */
	@Parameter( property = "rs.upload.chmod.options", defaultValue = "-Rf" )
	private String chmodOptions;

	/**
	 * Set this to 'true' to skip upload.
	 *
	 * @since 1.0
	 */
	@Parameter( property = "rs.upload.skip", defaultValue = "false" )
	private boolean skipDeploy;

	/**
	 * Execute these remote commands before uploading.
	 * You can prefix a single command with '@' so its errors will always be ignored.
	 * Commands are executed from the user's home directory.
	 *
	 * @since 1.1
	 */
	@Parameter
	private List<String> preCommands;

	/**
	 * Set this to 'true' to fail in case any pre command fails.
	 * You can prefix a single command with '@' so its errors will always be ignored.
	 *
	 * @since 1.1
	 */
	@Parameter( property = "rs.upload.failOnPreCommandErrors", defaultValue = "false" )
	private boolean failOnPreCommandErrors;

	/**
	 * Execute these remote commands after uploading.
	 * You can prefix a single command with '@' so its errors will always be ignored.
	 * Commands are executed from the user's home directory.
	 *
	 * @since 1.1
	 */
	@Parameter
	private List<String> postCommands;

	/**
	 * Set this to 'true' to fail in case any post command fails.
	 * You can prefix a single command with '@' so its errors will always be ignored.
	 *
	 * @since 1.1
	 */
	@Parameter( property = "rs.upload.failOnPostCommandErrors", defaultValue = "false" )
	private boolean failOnPostCommandErrors;

	/**
	 * The current user system settings for use in Maven.
	 */
	@Component
	private Settings settings;

	/**
	 * @since 1.0
	 */
	@Component
	protected MavenSession mavenSession;

	@Component
	private PlexusContainer container;

	/**
	 * {@inheritDoc}
	 */
	public void execute() throws MojoExecutionException {
		//		getLog().info("Deploying '"+getPath().getPath()+"' to '"+getTargetPath()+"' @ '"+repository.getUrl());
		deploy(getPath(), new Repository( repository.getId(), appendSlash( repository.getUrl() ) ));
	}

	private File getPath() {
		if (path != null) return path;
		return project.getArtifact().getFile();
	}

	/**
	 * Make sure the given url ends with a slash.
	 *
	 * @param url a String.
	 * @return if url already ends with '/' it is returned unchanged,
	 *         otherwise a '/' character is appended.
	 */
	protected static String appendSlash( final String url ) {
		if ( url.endsWith( "/" ) ) {
			return url;
		} else {
			return url + "/";
		}
	}

	protected String getTargetPath() {
		String rc = null;
		File path = getPath();
		if (targetPath != null) {
			rc = targetPath;
		} else {
			rc = "";
		}

		if (rc.endsWith("/") || (rc.length() == 0)) {
			rc += path.getName();
		}

		return rc;
	}

	private void deploy( final File file, final Repository repository ) throws MojoExecutionException {
		// TODO: work on moving this into the deployer like the other deploy methods
		final Wagon wagon = getWagon( repository, wagonManager );

		try {
			configureWagon( wagon, repository.getId(), settings, container, getLog() );
		} catch ( TransferFailedException e ) {
			throw new MojoExecutionException( "Unable to configure Wagon: '" + repository.getProtocol() + "'", e );
		}

		// Authentication Info
		AuthenticationInfo authenticationInfo = authenticationInfo( this.repository );
		getLog().debug( "authenticationInfo with id '" + repository.getId() + "': " + 
				( ( authenticationInfo == null ) ? "-" : authenticationInfo.getUserName() ) );

		// Proxy Info
		ProxyInfo proxyInfo = proxyInfo(this.repository);
		getLog().debug( "proxyInfo with id '" + repository.getId() + "': " + 
				( ( proxyInfo == null ) ? "-" : proxyInfo.getUserName() ) );

		try {

			push( file, repository, wagon, authenticationInfo, proxyInfo, getTargetPath() );

			if ( chmod ) {
				chmod( wagon, repository, chmodOptions, chmodMode );
			}
		} finally {
			try {
				wagon.disconnect();
			} catch ( ConnectionException e ) {
				getLog().error( "Error disconnecting wagon - ignored", e );
			}
		}
	}

	private AuthenticationInfo authenticationInfo(ArtifactRepository repository) {
		Authentication auth = repository.getAuthentication();
		AuthenticationInfo ai = null;
		if (auth != null) {
			ai = new AuthenticationInfo();
			ai.setUserName( auth.getUsername() );
			ai.setPassword( auth.getPassword() );
			ai.setPassphrase(auth.getPassphrase());
			ai.setPrivateKey(auth.getPrivateKey());
		}
		return ai;
	}

	private ProxyInfo proxyInfo(ArtifactRepository repository) {
		Proxy proxy = repository.getProxy();
		ProxyInfo pi = null;
		if (proxy != null) {
			pi = new ProxyInfo();
			pi.setHost(proxy.getHost());
			pi.setPort(proxy.getPort());
			pi.setType(proxy.getProtocol());
			pi.setNonProxyHosts(proxy.getNonProxyHosts());
			pi.setNtlmDomain(proxy.getNtlmDomain());
			pi.setNtlmHost(proxy.getNtlmHost());
			pi.setUserName(proxy.getUserName());
			pi.setPassword(proxy.getPassword());
		}
		return pi;
	}

	@SuppressWarnings("deprecation")
	private Wagon getWagon( final Repository repository, final WagonManager manager ) throws MojoExecutionException {
		final Wagon wagon;

		try {
			wagon = manager.getWagon( repository );
		} catch ( UnsupportedProtocolException e ) {
			String shortMessage = "Unsupported protocol: '" + repository.getProtocol() + "' for site deployment to "
					+ "distributionManagement.site.url=" + repository.getUrl() + ".";
			String longMessage =
					"\n" + shortMessage + "\n" + "Currently supported protocols are: " + getSupportedProtocols() + ".\n"
							+ "    Protocols may be added through wagon providers.\n" + "    For more information, see "
							+ "http://maven.apache.org/plugins/maven-site-plugin/examples/adding-deploy-protocol.html";

			getLog().error( longMessage );

			throw new MojoExecutionException( shortMessage );
		} catch ( TransferFailedException e ) {
			throw new MojoExecutionException( "Unable to configure Wagon: '" + repository.getProtocol() + "'", e );
		}

		if ( !wagon.supportsDirectoryCopy() ) {
			throw new MojoExecutionException("Wagon protocol '" + repository.getProtocol() + "' doesn't support directory copying" );
		}

		return wagon;
	}

	private String getSupportedProtocols() {
		try {
			@SuppressWarnings("unchecked")
			Set<String> protocols = (Set<String>)container.lookupMap( Wagon.class.getName() ).keySet();

			return StringUtils.join( protocols.iterator(), ", " );
		} catch ( ComponentLookupException e ) {
			// in the unexpected case there is a problem when instantiating a wagon provider
			getLog().error( e );
		}
		return "";
	}

	private void push( final File f, final Repository repository, final Wagon wagon, final AuthenticationInfo authenticationInfo, final ProxyInfo proxyInfo, final String targetPath)	throws MojoExecutionException {
		try {
			Debug debug = new Debug();

			wagon.addSessionListener( debug );
			wagon.addTransferListener( debug );
			wagon.connect( repository, authenticationInfo, proxyInfo );

			if (!executePreCommands(wagon, repository) && failOnPreCommandErrors) {
				getLog().error("Aborting due to previous errors");
				throw new MojoExecutionException("Aborting due to previous errors");
			};

			if (f.isDirectory()) {
				wagon.putDirectory( f, targetPath );
			} else {
				wagon.put( f, targetPath);
			}

			if (!executePostCommands(wagon, repository) && failOnPostCommandErrors) {
				getLog().error("Aborting due to previous errors");
				throw new MojoExecutionException("Aborting due to previous errors");
			};

		} catch ( ResourceDoesNotExistException e ) {
			throw new MojoExecutionException( "Error uploading site", e );
		} catch ( TransferFailedException e ) {
			throw new MojoExecutionException( "Error uploading site", e );
		} catch ( AuthorizationException e ) {
			throw new MojoExecutionException( "Error uploading site", e );
		} catch ( ConnectionException e ) {
			throw new MojoExecutionException( "Error uploading site", e );
		} catch ( AuthenticationException e ) {
			throw new MojoExecutionException( "Error uploading site", e );
		}
	}

	private static void chmod( final Wagon wagon, final Repository repository, final String chmodOptions, final String chmodMode ) throws MojoExecutionException {
		try {
			if ( wagon instanceof CommandExecutor ) {
				CommandExecutor exec = (CommandExecutor) wagon;
				exec.executeCommand( "chmod " + chmodOptions + " " + chmodMode + " " + repository.getBasedir() );
			}
			// else ? silently ignore, FileWagon is not a CommandExecutor!
		} catch ( CommandExecutionException e ) {
			throw new MojoExecutionException( "Error uploading site", e );
		}
	}

	private boolean executePreCommands(final Wagon wagon, final Repository repository) throws MojoExecutionException {
		if ( wagon instanceof CommandExecutor ) {
			return executeCommands((CommandExecutor)wagon, repository, preCommands, failOnPreCommandErrors);
		}
		return false;
	}

	private boolean executePostCommands(final Wagon wagon, final Repository repository) throws MojoExecutionException {
		if ( wagon instanceof CommandExecutor ) {
			return executeCommands((CommandExecutor)wagon, repository, postCommands, failOnPostCommandErrors);
		}
		return false;
	}

	private boolean executeCommands(final CommandExecutor exec, final Repository repository, final List<String> commands, boolean failOnError) throws MojoExecutionException {
		if ((commands == null) || (commands.size() == 0)) return true;
		boolean rc = true;
		for (String cmd : commands) {
			rc &= executeCommand(exec, repository, cmd, failOnError);
			if (failOnError && !rc) return rc;
		}
		return rc;
	}

	private boolean executeCommand(CommandExecutor exec, Repository repository, String command, boolean doError) throws MojoExecutionException {
		boolean doFail = !command.startsWith("@");
		if (!doFail) command = command.substring(1);
		try {
			exec.executeCommand( command );
		} catch ( CommandExecutionException e ) {
			boolean messageOnly = e.getMessage().indexOf("Exit code:") >= 0;
			if (doFail) {
				if (doError) {
					if (messageOnly) {
						getLog().error("Command failed: "+command);
						getLog().error("  "+e.getLocalizedMessage());
					} else {
						getLog().error("Command failed: "+command, e);
					}
				} else {
					if (messageOnly) {
						getLog().warn("Command failed: "+command);
						getLog().warn("  "+e.getLocalizedMessage());
					} else {
						getLog().warn("Command failed: "+command, e);
					}
				}
				return false;
			} else {
				if (messageOnly) {
					getLog().warn("Command failed: "+command);
					getLog().warn("  "+e.getLocalizedMessage());
				} else {
					getLog().warn("Command failed: "+command, e);
				}
			}
		}
		return true;
	}

	/**
	 * Configure the Wagon with the information from serverConfigurationMap ( which comes from settings.xml )
	 *
	 * @param wagon
	 * @param repositoryId
	 * @param settings
	 * @param container
	 * @param log
	 * @throws TransferFailedException
	 * @todo Remove when {@link WagonManager#getWagon(Repository) is available}. It's available in Maven 2.0.5.
	 */
	private static void configureWagon( Wagon wagon, String repositoryId, Settings settings, PlexusContainer container, Log log ) throws TransferFailedException {
		log.debug( " configureWagon " );

		// MSITE-25: Make sure that the server settings are inserted
		for ( Server server : settings.getServers() ) {
			String id = server.getId();

			log.debug( "configureWagon server " + id );

			if ( id != null && id.equals( repositoryId ) && ( server.getConfiguration() != null ) ) {
				final PlexusConfiguration plexusConf = new XmlPlexusConfiguration( (Xpp3Dom) server.getConfiguration() );

				ComponentConfigurator componentConfigurator = null;
				try {
					componentConfigurator = (ComponentConfigurator) container.lookup( ComponentConfigurator.ROLE, "basic" );
					componentConfigurator.configureComponent( wagon, plexusConf, container.getContainerRealm() );
				} catch ( final ComponentLookupException e ) {
					throw new TransferFailedException( "While configuring wagon for \'" + repositoryId + "\': Unable to lookup wagon configurator."
							+ " Wagon configuration cannot be applied.", e );
				} catch ( ComponentConfigurationException e ) {
					throw new TransferFailedException( "While configuring wagon for \'" + repositoryId + "\': Unable to apply wagon configuration.", e );
				} finally {
					if ( componentConfigurator != null ) {
						try {
							container.release( componentConfigurator );
						} catch ( ComponentLifecycleException e ) {
							log.error( "Problem releasing configurator - ignoring: " + e.getMessage() );
						}
					}
				}
			}
		}
	}


}
