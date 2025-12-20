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
