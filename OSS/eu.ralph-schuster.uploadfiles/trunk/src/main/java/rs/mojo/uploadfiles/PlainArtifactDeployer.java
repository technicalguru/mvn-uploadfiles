/**
 * 
 */
package rs.mojo.uploadfiles;

import java.io.File;
import java.io.IOException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.deployer.ArtifactDeployer;
import org.apache.maven.artifact.deployer.ArtifactDeploymentException;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.repository.legacy.resolver.transform.ArtifactTransformationManager;
import org.apache.maven.wagon.TransferFailedException;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.FileUtils;

/**
 * @author ralph
 *
 */
@SuppressWarnings("deprecation")
@Component(role=ArtifactDeployer.class, instantiationStrategy="per-lookup")
public class PlainArtifactDeployer extends AbstractLogEnabled implements ArtifactDeployer {

	private WagonManager wagonManager;

	private ArtifactTransformationManager transformationManager;

	/**
	 * Constructor.
	 */
	public PlainArtifactDeployer() {
	}

	/**
	 * @deprecated we want to use the artifact method only, and ensure artifact.file is set correctly.
	 */
	public void deploy( String basedir, String finalName, Artifact artifact, ArtifactRepository deploymentRepository, ArtifactRepository localRepository ) throws ArtifactDeploymentException {
		String extension = artifact.getArtifactHandler().getExtension();
		File source = new File( basedir, finalName + "." + extension );
		deploy( source, artifact, deploymentRepository, localRepository );
	}

	public void deploy( File source, Artifact artifact, ArtifactRepository deploymentRepository, ArtifactRepository localRepository ) throws ArtifactDeploymentException {
		if ( !wagonManager.isOnline() ) {
			// deployment shouldn't silently fail when offline
			throw new ArtifactDeploymentException( "System is offline. Cannot deploy artifact: " + artifact + "." );
		}
		getLogger().info("Would install now from "+source.getName()+" to "+deploymentRepository.getBasedir());
		/*
		try {
			transformationManager.transformForDeployment( artifact, deploymentRepository, localRepository );
			// Copy the original file to the new one if it was transformed
			File artifactFile = new File( localRepository.getBasedir(), localRepository.pathOf( artifact ) );
			if ( !artifactFile.equals( source ) ) {
				FileUtils.copyFile( source, artifactFile );
			}
			wagonManager.putArtifact( source, artifact, deploymentRepository );
		} catch ( TransferFailedException e ) {
			throw new ArtifactDeploymentException( "Error deploying artifact: " + e.getMessage(), e );
		} catch ( IOException e ) {
			throw new ArtifactDeploymentException( "Error deploying artifact: " + e.getMessage(), e );
		}
		*/
	}
}
