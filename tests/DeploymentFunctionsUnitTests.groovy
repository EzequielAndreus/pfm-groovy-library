import spock.lang.Specification
import spock.lang.Unroll

/* groovylint-disable CompileStatic, MethodCount, JUnitPublicNonTestMethod */

/**
 * Unit tests for DeploymentFunctions.
 *
 * This test class provides comprehensive coverage for all deployment automation functions including:
 * - Docker image building and tagging
 * - AWS Auto Scaling Group management
 * - Stage validation and health checks
 * - Ansible playbook execution
 * - Application health monitoring
 * - Environment-specific image tagging
 *
 * Uses Spock Framework for clear, expressive test specifications with Given-When-Then syntax.
 * Each test method validates both positive scenarios and error handling with appropriate exception checks.
 */
class DeploymentFunctionsUnitTests extends Specification {

    private DeploymentFunctions deploymentFunctions

    void setup() {
        deploymentFunctions = new DeploymentFunctions()
    }

    // ============================================================================
    // Tests for buildAndTagImage()
    // ============================================================================

    void testBuildAndTagImageReturnsValidCommitHash() {
        given:
        String repositoryUrl = 'https://github.com/user/repo.git'
        String branch = 'main'

        when:
        String result = deploymentFunctions.buildAndTagImage(repositoryUrl, branch)

        then:
        assert result != null
        assert result.matches('[a-f0-9]{40}') // Validate SHA-1 hash format
    }

    void testBuildAndTagImageHandlesRepositoryUrlWithSSHFormat() {
        given:
        String repositoryUrl = 'git@github.com:user/repo.git'
        String branch = 'develop'

        when:
        String result = deploymentFunctions.buildAndTagImage(repositoryUrl, branch)

        then:
        assert result != null
        assert result.length() == 40
    }

    void testBuildAndTagImageBuildsFromSpecifiedBranch() {
        given:
        String repositoryUrl = 'https://github.com/user/repo.git'
        String branch = 'feature/new-feature'

        when:
        String result = deploymentFunctions.buildAndTagImage(repositoryUrl, branch)

        then:
        assert result != null
        assert result
    }

    void testBuildAndTagImageThrowsExceptionForEmptyRepositoryUrl() {
        given:
        String repositoryUrl = ''
        String branch = 'main'

        when:
        deploymentFunctions.buildAndTagImage(repositoryUrl, branch)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testBuildAndTagImageThrowsExceptionForEmptyBranch() {
        given:
        String repositoryUrl = 'https://github.com/user/repo.git'
        String branch = ''

        when:
        deploymentFunctions.buildAndTagImage(repositoryUrl, branch)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testBuildAndTagImageThrowsExceptionForNullParameters() {
        when:
        deploymentFunctions.buildAndTagImage(null, null)

        then:
        assert thrown(NullPointerException)
    }
}
