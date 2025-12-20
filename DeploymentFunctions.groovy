/**
 * Builds and tags a new Docker image from a specific branch.
 * Clones the repository, builds the image, tags it with the commit hash, and publishes it.
 *
 * @param repositoryUrl The URL of the repository to clone (required)
 * @param branch The branch name to build from (required)
 * @return The commit hash (shortened)
 * @throws IllegalArgumentException if parameters are null or empty
 */
String prepareDockerImage(String repositoryUrl, String branch, String imageName) {
    // Input validation
    if (!repositoryUrl?.trim()) {
        throw new IllegalArgumentException('Repository URL cannot be null or empty')
    }
    if (!branch?.trim()) {
        throw new IllegalArgumentException('Branch cannot be null or empty')
    }

    String workDir = "/tmp/build-${System.currentTimeMillis()}"

    try {
        // Clone the repository
        sh "git clone ${repositoryUrl} ${workDir}"

        // Checkout the specified branch
        sh "cd ${workDir} && git checkout ${branch}"

        // Get the short commit hash
        String commitHash = sh(
            script: "cd ${workDir} && git rev-parse --short HEAD",
            returnStdout: true
        ).trim()

        // Build the Docker image
        sh "cd ${workDir} && docker build -t ${imageName}:${commitHash} ."

        // publish image in Docker Hub
        sh "docker push ${imageName}:${commitHash}"

        echo "Successfully built, tagged, and published image as ${imageName}:${commitHash}"
        return commitHash
    } finally {
        // Clean up the working directory
        sh "rm -rf ${workDir}"
    }
}

/**
 * Updates an AWS Auto Scaling Group by increasing its desired capacity.
 * Increases the current desired capacity by the specified number of instances
 * without modifying the group's minimum or maximum capacity settings.
 *
 * Note: Requires AWS CLI to be configured with appropriate credentials (IAM role or profile).
 *
 * @param asgName The name of the Auto Scaling Group (required)
 * @param instanceCount The number of instances to add to the current desired capacity (must be > 0)
 * @return true if update is successful, false otherwise
 * @throws IllegalArgumentException if parameters are invalid
 */
boolean updateAwsAsg(String asgName, Integer instanceCount) {
    // Input validation
    if (!asgName?.trim()) {
        throw new IllegalArgumentException('ASG name cannot be null or empty')
    }
    if (instanceCount == null || instanceCount <= 0) {
        throw new IllegalArgumentException('Instance count must be greater than 0')
    }

    try {
        // Get current desired capacity
        Integer currentCapacity = sh(
            script: 'aws autoscaling describe-auto-scaling-groups ' +
                    "--auto-scaling-group-names ${asgName} " +
                    "--query 'AutoScalingGroups[0].DesiredCapacity' " +
                    '--output text',
            returnStdout: true
        ).trim().toInteger()

        // Calculate new desired capacity
        Integer newCapacity = currentCapacity + instanceCount

        // Get the maximum capacity of the ASG
        Integer maxCapacity = sh(
            script: "aws autoscaling describe-auto-scaling-groups --auto-scaling-group-names ${asgName} " +
                    "--query 'AutoScalingGroups[0].MaxSize' --output text",
            returnStdout: true
        ).trim().toInteger()

        if (newCapacity > maxCapacity) {
            throw new IllegalArgumentException("New capacity ${newCapacity} exceeds maximum ${maxCapacity}")
        }

        echo "Updating ASG '${asgName}' from ${currentCapacity} to ${newCapacity} instances"

        // Update the Auto Scaling Group
        sh "aws autoscaling set-desired-capacity --auto-scaling-group-name ${asgName} --desired-capacity ${newCapacity}"

        echo "Successfully updated ASG '${asgName}' desired capacity to ${newCapacity}"
        return true
    } catch (IOException | InterruptedException e) {
        error "Failed to update ASG '${asgName}': ${e.message}"
    }
}

/**
 * Validates that a stage is accessible and ready to receive traffic.
 * Performs ping checks and verifies SSH port accessibility.
 *
 * @param instanceAddress IP address or hostname of the instance to check (required)
 * @return true if stage is accessible, false otherwise
 * @throws IllegalArgumentException if instanceAddress is null or empty
 */
boolean validateStageIsUp(String instanceAddress) {
    // Input validation
    if (!instanceAddress?.trim()) {
        throw new IllegalArgumentException('Instance address cannot be null or empty')
    }

    try {
        echo "Validating stage at ${instanceAddress}"

        // Perform ping check (send 3 pings)
        Integer pingResult = sh(
            script: "ping -c 3 ${instanceAddress}",
            returnStatus: true
        )

        if (pingResult) {
            echo "Ping check failed for ${instanceAddress}"
            return false
        }

        echo "Ping check passed for ${instanceAddress}"

        // Check SSH port (22) accessibility
        Integer sshResult = sh(
            script: "nc -zv -w 5 ${instanceAddress} 22",
            returnStatus: true
        )

        if (sshResult) {
            echo "SSH port check failed for ${instanceAddress}"
            return false
        }

        echo "SSH port check passed for ${instanceAddress}"
        echo "Stage ${instanceAddress} is up and accessible"
        return true
    } catch (IOException | InterruptedException e) {
        echo "Error validating stage: ${e.message}"
        return false
    }
}

/**
 * Executes an Ansible playbook with specified parameters.
 *
 * Note: Requires Ansible to be installed and properly configured on the executing system.
 *
 * @param ansibleRepoPath The path to the Ansible repository (required)
 * @param playbookName The name of the playbook to run (required)
 * @param parameters A map of parameters to pass to the playbook (required, can be empty)
 * @throws IllegalArgumentException if parameters are invalid
 * @throws FileNotFoundException if playbook file does not exist
 */
void runAnsiblePlaybook(String ansibleRepoPath, String playbookName, Map parameters) {
    // Input validation
    if (!ansibleRepoPath?.trim()) {
        throw new IllegalArgumentException('Ansible repository path cannot be null or empty')
    }
    if (!playbookName?.trim()) {
        throw new IllegalArgumentException('Playbook name cannot be null or empty')
    }
    if (parameters == null) {
        throw new IllegalArgumentException('Parameters map cannot be null (use empty map instead)')
    }

    try {
        echo "Running Ansible playbook: ${playbookName}"

        // Check if playbook file exists
        String playbookPath = "${ansibleRepoPath}/${playbookName}"
        Integer fileCheckResult = sh(
            script: "test -f ${playbookPath}",
            returnStatus: true
        )

        if (fileCheckResult) {
            throw new FileNotFoundException("Playbook file not found: ${playbookPath}")
        }

        // Build extra vars from parameters map using JSON format for better handling
        String extraVars = ''
        if (parameters) {
            // Convert map to JSON string for safer passing
            String jsonParams = groovy.json.JsonOutput.toJson(parameters)
            extraVars = "--extra-vars '${jsonParams}'"
        }

        // Build and execute the ansible-playbook command
        String command = "ansible-playbook ${playbookPath} ${extraVars}"

        echo "Executing: ${command}"
        sh command

        echo "Successfully executed playbook ${playbookName}"
    } catch (IOException | InterruptedException e) {
        error "Failed to run Ansible playbook '${playbookName}': ${e.message}"
    }
}

/**
 * Performs a health check on the application running in a specified instance.
 * Tries multiple common health endpoints until one responds with HTTP 200.
 *
 * @param instanceAddress IP address or hostname of the instance to check (required)
 * @param healthEndpoints List of endpoint paths to check (optional, defaults to ['/health', '/healthz', '/'])
 * @param protocol Protocol to use for the health check (optional, defaults to 'http')
 * @param connectTimeout Connection timeout in seconds (optional, defaults to 5)
 * @param maxTimeout Maximum request timeout in seconds (optional, defaults to 10)
 * @return true if health check passes, false otherwise
 * @throws IllegalArgumentException if instanceAddress is null or empty
 */
boolean performHealthCheck(String instanceAddress,
                          List<String> healthEndpoints = ['/health', '/healthz', '/'],
                          String protocol = 'http',
                          int connectTimeout = 5,
                          int maxTimeout = 10) {
    // Input validation
    if (!instanceAddress?.trim()) {
        throw new IllegalArgumentException('Instance address cannot be null or empty')
    }

    try {
        echo "Performing health check on ${instanceAddress}"

        // Try to reach the health endpoint

        for (endpoint in healthEndpoints) {
            String url = "${protocol}://${instanceAddress}${endpoint}"
            String result = sh(
                script: "curl -f -s -o /dev/null -w '%{http_code}' " +
                        "--connect-timeout ${connectTimeout} " +
                        "--max-time ${maxTimeout} ${url}",
                returnStdout: true
            ).trim()

            if (result == '200') {
                echo "Health check passed for ${instanceAddress}${endpoint} - HTTP ${result}"
                return true
            }
        }

        echo "Health check failed for ${instanceAddress} - no healthy endpoints found"
        return false
    } catch (IOException | InterruptedException e) {
        echo "Error performing health check: ${e.message}"
        return false
    }
}

/**
 * Tags a Docker image for a specific environment.
 *
 * Note: Requires Docker to be installed and running on the executing system.
 *
 * @param imageId The ID or name of the image to tag (required)
 * @param environment The target environment: 'development', 'staging', 'production', or 'test' (required)
 * @param imageName The base image name for tagging (required)
 * @return true if tagging is successful, false otherwise
 * @throws IllegalArgumentException if parameters are invalid
 */
boolean tagImageForEnvironment(String imageId, String environment, String imageName) {
    // Input validation
    if (!imageId?.trim()) {
        throw new IllegalArgumentException('Image ID cannot be null or empty')
    }
    if (!environment?.trim()) {
        throw new IllegalArgumentException('Environment cannot be null or empty')
    }

    // Validate environment parameter
    List<String> validEnvironments = ['development', 'staging', 'production', 'test']
    if (!validEnvironments.contains(environment)) {
        String validEnvList = validEnvironments.join(', ')
        throw new IllegalArgumentException("Invalid environment: ${environment}. Must be one of: ${validEnvList}")
    }

    try {
        echo "Tagging image ${imageId} for ${environment} environment"

        // Tag the image for the specified environment
        sh "docker tag ${imageId} ${imageName}:${environment}"
        sh "docker tag ${imageId} ${imageName}:${environment}-latest"

        echo "Successfully tagged image ${imageId} as ${imageName}:${environment} " +
             "and ${imageName}:${environment}-latest"
        return true
    } catch (IOException | InterruptedException e) {
        echo "Failed to tag image: ${e.message}"
        return false
    }
}
