/**
 * 
 */
package rs.mojo.uploadfiles;

import java.lang.reflect.Field;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.metadata.ArtifactMetadata;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.repository.metadata.ArtifactRepositoryMetadata;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.AbstractLogEnabled;

/**
 * @author ralph
 *
 */
@SuppressWarnings("deprecation")
@Component(role=ArtifactRepositoryLayout.class, instantiationStrategy="per-lookup")
public class P2RepositoryLayout extends AbstractLogEnabled implements ArtifactRepositoryLayout {

	private static final char PATH_SEPARATOR = '/';
	private static final char GROUP_SEPARATOR = '.';
	private static final char ARTIFACT_SEPARATOR = '-';

	public String pathOf(Artifact artifact) {
		ArtifactHandler artifactHandler = artifact.getArtifactHandler();

		StringBuffer path = new StringBuffer();

		path.append(artifact.getArtifactId()).append(ARTIFACT_SEPARATOR).append(artifact.getVersion());

		if (artifact.hasClassifier()) {
			path.append(ARTIFACT_SEPARATOR).append(artifact.getClassifier());
		}

		if (artifactHandler.getExtension() != null && artifactHandler.getExtension().length() > 0) {
			path.append(GROUP_SEPARATOR).append(artifactHandler.getExtension());
		}

		return path.toString();
	}

	public String pathOfLocalRepositoryMetadata(ArtifactMetadata metadata, ArtifactRepository repository) {
		return pathOfRepositoryMetadata(metadata, metadata .getLocalFilename(repository));
	}

	private String pathOfRepositoryMetadata(ArtifactMetadata metadata, String filename) {
		StringBuffer path = new StringBuffer();

		path.append(formatAsDirectory(metadata.getGroupId())).append(PATH_SEPARATOR);
		if (!metadata.storedInGroupDirectory()) {
			String version = getBaseVersion(metadata);
			// always store in version directory; default implementation does
			// not
			path.append(version).append(PATH_SEPARATOR);

			path.append(metadata.getArtifactId()).append(PATH_SEPARATOR);
		}

		path.append(filename);

		return path.toString();
	}

	/**
	 * Extract base version from metadata
	 * @param metadata
	 * @return base version from the artifact
	 */
	private String getBaseVersion(ArtifactMetadata metadata) {
		String version = null;
		if (metadata.getBaseVersion() != null) {
			version = metadata.getBaseVersion();
		} else {
			// artifact base version is not accessible from ArtifactRepositoryMetadata
			if (metadata instanceof ArtifactRepositoryMetadata) {
				try {
					Field f = metadata.getClass().getDeclaredField("artifact");
					boolean accessible = f.isAccessible();
					try {
						f.setAccessible(true);
						Artifact artifact = (Artifact) f.get(metadata);
						version = artifact.getBaseVersion();
					} finally {
						f.setAccessible(accessible);
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}
		return version;
	}

	public String pathOfRemoteRepositoryMetadata(ArtifactMetadata metadata) {
		return pathOfRepositoryMetadata(metadata, metadata.getRemoteFilename());
	}

	private String formatAsDirectory(String directory) {
		return directory.replace(GROUP_SEPARATOR, PATH_SEPARATOR);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getId() {
		return "p2";
	}
	
	
}
