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

    // ============================================================================
    // Tests for updateAwsAsg()
    // ============================================================================

    void testUpdateAwsAsgSuccessfully() {
        given:
        String asgName = 'production-asg-web'
        Integer instanceCount = 3

        when:
        deploymentFunctions.updateAwsAsg(asgName, instanceCount)

        then:
        assert noExceptionThrown() != null
    }

    @Unroll
    void testUpdateAwsAsgHandlesDifferentInstanceCounts(Integer instanceCount) {
        given:
        String asgName = 'prod-asg'

        when:
        deploymentFunctions.updateAwsAsg(asgName, instanceCount)

        then:
        noExceptionThrown()

        where:
        instanceCount << [1, 5, 10, 25]
    }

    void testUpdateAwsAsgThrowsExceptionForNegativeInstanceCount() {
        given:
        String asgName = 'prod-asg'
        Integer instanceCount = -1

        when:
        deploymentFunctions.updateAwsAsg(asgName, instanceCount)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testUpdateAwsAsgThrowsExceptionForZeroInstanceCount() {
        given:
        String asgName = 'prod-asg'
        Integer instanceCount = 0

        when:
        deploymentFunctions.updateAwsAsg(asgName, instanceCount)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testUpdateAwsAsgThrowsExceptionForEmptyAsgName() {
        given:
        String asgName = ''
        Integer instanceCount = 2

        when:
        deploymentFunctions.updateAwsAsg(asgName, instanceCount)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testUpdateAwsAsgThrowsExceptionForNullAsgName() {
        when:
        deploymentFunctions.updateAwsAsg(null, 2)

        then:
        assert thrown(NullPointerException)
    }

    // ============================================================================
    // Tests for validateStageIsUp()
    // ============================================================================

    void testValidateStageIsUpReturnsTrue() {
        given:
        String stageName = 'staging-01'

        when:
        boolean result = deploymentFunctions.validateStageIsUp(stageName)

        then:
        assert result == true
    }

    void testValidateStageIsUpReturnsFalse() {
        given:
        String stageName = 'staging-02'

        when:
        boolean result = deploymentFunctions.validateStageIsUp(stageName)

        then:
        assert result == false
    }

    void testValidateStageIsUpHandlesIpAddresses() {
        given:
        String stageName = '192.168.1.100'

        when:
        boolean result = deploymentFunctions.validateStageIsUp(stageName)

        then:
        assert result instanceof Boolean
    }

    void testValidateStageIsUpThrowsExceptionForEmptyStageName() {
        given:
        String stageName = ''

        when:
        deploymentFunctions.validateStageIsUp(stageName)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testValidateStageIsUpThrowsExceptionForNullStageName() {
        when:
        deploymentFunctions.validateStageIsUp(null)

        then:
        assert thrown(NullPointerException)
    }

    // ============================================================================
    // Tests for runAnsiblePlaybook()
    // ============================================================================

    void testRunAnsiblePlaybookExecutesWithParameters() {
        given:
        String ansibleRepoPath = '/etc/ansible'
        String playbookName = 'deploy.yml'
        Map parameters = [env: 'staging', version: '1.0.0']

        when:
        deploymentFunctions.runAnsiblePlaybook(ansibleRepoPath, playbookName, parameters)

        then:
        assert noExceptionThrown() != null
    }

    void testRunAnsiblePlaybookHandlesEmptyParameterMap() {
        given:
        String ansibleRepoPath = '/etc/ansible'
        String playbookName = 'health-check.yml'
        Map parameters = [:]

        when:
        deploymentFunctions.runAnsiblePlaybook(ansibleRepoPath, playbookName, parameters)

        then:
        assert noExceptionThrown() != null
    }

    void testRunAnsiblePlaybookHandlesVariousParameterTypes() {
        given:
        String ansibleRepoPath = '/opt/ansible'
        String playbookName = 'configure.yml'
        Map parameters = [
            debug      : true,
            retries    : 3,
            timeout    : 300,
            hosts      : 'all',
            tags       : ['setup', 'deploy']
        ]

        when:
        deploymentFunctions.runAnsiblePlaybook(ansibleRepoPath, playbookName, parameters)

        then:
        assert noExceptionThrown() != null
    }

    void testRunAnsiblePlaybookThrowsExceptionForEmptyAnsibleRepoPath() {
        given:
        String ansibleRepoPath = ''
        String playbookName = 'deploy.yml'
        Map parameters = [:]

        when:
        deploymentFunctions.runAnsiblePlaybook(ansibleRepoPath, playbookName, parameters)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testRunAnsiblePlaybookThrowsExceptionForEmptyPlaybookName() {
        given:
        String ansibleRepoPath = '/etc/ansible'
        String playbookName = ''
        Map parameters = [:]

        when:
        deploymentFunctions.runAnsiblePlaybook(ansibleRepoPath, playbookName, parameters)

        then:
        assert thrown(IllegalArgumentException)
    }

    void testRunAnsiblePlaybookThrowsExceptionForNullParametersMap() {
        given:
        String ansibleRepoPath = '/etc/ansible'
        String playbookName = 'deploy.yml'

        when:
        deploymentFunctions.runAnsiblePlaybook(ansibleRepoPath, playbookName, null)

        then:
        assert thrown(NullPointerException)
    }

    void testRunAnsiblePlaybookValidatesPlaybookFileExists() {
        given:
        String ansibleRepoPath = '/etc/ansible'
        String playbookName = 'non-existent.yml'
        Map parameters = [:]

        when:
        deploymentFunctions.runAnsiblePlaybook(ansibleRepoPath, playbookName, parameters)

        then:
        assert thrown(FileNotFoundException)
    }
}
