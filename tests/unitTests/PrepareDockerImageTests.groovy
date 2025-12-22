/* groovylint-disable CompileStatic, JUnitPublicNonTestMethod, MethodName */
package com.pfm.tests

import spock.lang.Specification
import com.pfm.DeploymentFunctions
import spock.lang.Unroll

/**
 * Unit tests for the {@link DeploymentFunctions#prepareDockerImage(String, String, String)} method.
 *
 * <p>This test class validates the Docker image preparation functionality which includes
 * cloning repositories, checking out branches, building Docker images, and pushing them
 * to registries. The tests cover various scenarios including successful builds, input validation,
 * and error handling with proper cleanup.</p>
 *
 * <p>The tests use Spock framework with mocked Jenkins script context to simulate
 * Git operations, Docker commands, and file system interactions.</p>
 *
 * @author PFM Team
 * @since 1.0
 * @see DeploymentFunctions
 */
class PrepareDockerImageTests extends Specification {

    // Git/Repository Constants
    private static final String HTTPS_REPO_URL = 'https://github.com/user/repo.git'
    private static final String SSH_REPO_URL = 'git@github.com:user/repo.git'
    private static final String MAIN_BRANCH = 'main'
    private static final String DEVELOP_BRANCH = 'develop'

    // Instance Fields
    private DeploymentFunctions deploymentFunctions
    /* groovylint-disable-next-line FieldTypeRequired, NoDef */
    private scriptMock

    /**
     * Sets up test fixtures before each test method execution.
     *
     * <p>Initializes the mock Jenkins script object and creates a new
     * {@link DeploymentFunctions} instance with the mocked script context.</p>
     */
    void setup() {
        scriptMock = GroovyMock(Object)
        deploymentFunctions = new DeploymentFunctions(scriptMock)
    }

    /**
     * Tests the {@link DeploymentFunctions#prepareDockerImage(String, String, String)} method
     * with various repository URLs, branches, and image names.
     *
     * <p>This parameterized test verifies that the Docker image preparation process correctly
     * executes all required steps in the proper order:</p>
     * <ul>
     *   <li>Clones the repository from the specified URL</li>
     *   <li>Checks out the specified branch</li>
     *   <li>Gets the short commit hash</li>
     *   <li>Builds the Docker image with the commit hash as tag</li>
     *   <li>Pushes the image to the registry</li>
     *   <li>Cleans up temporary files and directories</li>
     * </ul>
     *
     * <p>The test mocks all shell commands and file operations to simulate successful
     * execution and verifies that commands are executed in the correct sequence.</p>
     *
     * @param repositoryUrl the Git repository URL to clone from
     * @param branch the Git branch to checkout
     * @param imageName the Docker image name to build and push
     */
    @Unroll('#repositoryUrl with branch #branch should return valid commit hash')
    void testPrepareDockerImage(String repositoryUrl, String branch, String imageName) {
        given: 'captured commands'
        /* groovylint-disable-next-line VariableTypeRequired */
        List<String> capturedCommands = []

        // Handle both Map-based and String-based sh calls
        scriptMock.sh(_) >> { args ->
            String command = (args instanceof Map) ? args.script : args[0]
            capturedCommands << command

            return command.contains('git rev-parse') ? 'abc123def\n' : 0
        }
        // Mock readFile() to return the commit hash (simulating what was written to file)
        scriptMock.readFile('/tmp/commit-hash.txt') >> 'abc123def'

        // Mock echo for logging
        scriptMock.echo(_) >> null

        when: 'calling prepareDockerImage'
        String result = deploymentFunctions.prepareDockerImage(repositoryUrl, branch, imageName)

        then: 'returns the commit hash'
        result == 'abc123def'

        and: 'commands executed in correct order'
        capturedCommands[0].contains("git clone ${repositoryUrl}")
        capturedCommands[1].contains("git checkout ${branch}")
        capturedCommands[2].contains('git rev-parse --short HEAD')
        capturedCommands.any {
            command -> command.contains('docker build') && command.contains("-t ${imageName}:abc123def")
        }
        capturedCommands.any {
            command -> command.contains("docker push ${imageName}:abc123def")
        }
        capturedCommands[-2].contains('rm -rf /tmp/build-')
        capturedCommands.last().contains('m -f /tmp/commit-hash.txt')

        where:
        repositoryUrl   | branch          | imageName
        HTTPS_REPO_URL  | MAIN_BRANCH     | 'myapp'
        SSH_REPO_URL    | DEVELOP_BRANCH  | 'myapp'
    }

    /**
     * Tests input validation for the {@link DeploymentFunctions#prepareDockerImage(String, String, String)} method.
     *
     * <p>This parameterized test ensures that the Docker image preparation method properly validates
     * input parameters and throws {@link IllegalArgumentException} for invalid inputs such as null
     * or empty values for repository URL, branch, or image name.</p>
     *
     * @param repositoryUrl the repository URL to validate (may be invalid)
     * @param branch the branch name to validate (may be invalid)
     * @param imageName the image name to validate (may be invalid)
     * @param expectedException the expected exception type to be thrown
     * @param scenario descriptive text explaining the validation scenario being tested
     * @throws IllegalArgumentException when any input parameter is invalid
     */
    @Unroll('prepareDockerImage throws exception when #scenario')
    void testPrepareDockerImageValidation(String repositoryUrl, String branch, String imageName,
                                        Class<? extends Throwable> expectedException, String scenario) {
        when:
        deploymentFunctions.prepareDockerImage(repositoryUrl, branch, imageName)

        then:
        thrown(expectedException)

        where:
        repositoryUrl   | branch        | imageName | expectedException           | scenario
        null            | MAIN_BRANCH   | 'myapp'   | IllegalArgumentException    | 'repository URL is null'
        ''              | MAIN_BRANCH   | 'myapp'   | IllegalArgumentException    | 'repository URL is empty'
        HTTPS_REPO_URL  | null          | 'myapp'   | IllegalArgumentException    | 'branch is null'
        HTTPS_REPO_URL  | ''            | 'myapp'   | IllegalArgumentException    | 'branch is empty'
        HTTPS_REPO_URL  | MAIN_BRANCH   | null      | IllegalArgumentException    | 'image name is null'
        HTTPS_REPO_URL  | MAIN_BRANCH   | ''        | IllegalArgumentException    | 'image name is empty'
    }

    /**
     * Tests error handling and cleanup behavior when Docker build operations fail.
     *
     * <p>This test verifies that when a Docker build fails during the image preparation process,
     * the method properly handles the exception and ensures that cleanup operations are still
     * executed to remove temporary files and directories.</p>
     *
     * <p>The test simulates successful Git operations followed by a Docker build failure,
     * and verifies that:</p>
     * <ul>
     *   <li>The original exception is propagated</li>
     *   <li>Temporary build directories are cleaned up</li>
     *   <li>Commit hash files are cleaned up</li>
     * </ul>
     *
     * @throws Exception when Docker build operations fail
     */
    void 'prepareDockerImage cleans up on docker build failure'() {
        given: 'git succeeds but docker fails'
        List<String> capturedCommands = []

        scriptMock.sh(_) >> { args ->
            String command = (args instanceof Map) ? args.script : args[0]
            capturedCommands << command

            if (command.contains('git clone')) { return 0 }
            if (command.contains('git checkout')) { return 0 }
            if (command.contains('git rev-parse')) { return 'abc123def\n' }
            if (command.contains('docker build')) {
                /* groovylint-disable-next-line ThrowException */
                throw new Exception('Docker build failed')
            }
            return 0
        }
        scriptMock.readFile(_) >> 'abc123def'
        scriptMock.echo(_) >> null

        when:
        deploymentFunctions.prepareDockerImage(HTTPS_REPO_URL, MAIN_BRANCH, 'myapp')

        then:
        Exception exception = thrown(Exception)
        exception.message == 'Docker build failed'

        and: 'cleanup was called with the actual build directory'
        capturedCommands.any { command -> command.contains('rm -rf /tmp/build-') }

        and: 'commit hash file cleanup was also called'
        capturedCommands.any { command -> command.contains('rm -f /tmp/commit-hash.txt') }
    }

}
