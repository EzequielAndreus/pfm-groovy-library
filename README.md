# PFM Groovy Library

A Jenkins shared library providing reusable pipeline functions for deployment and infrastructure operations.

## Overview

This library contains common Jenkins pipeline functions that can be imported and used across multiple Jenkinsfiles to standardize deployment processes, health checks, and infrastructure management operations.

## Functions

The library provides the following core functions:

- **prepareDockerImage()** - Clone repositories, build and push Docker images
- **tagImageForEnvironment()** - Tag Docker images for specific environments  
- **runAnsiblePlaybook()** - Execute Ansible playbooks with parameters
- **updateAwsAsg()** - Manage AWS Auto Scaling Group capacity
- **performHealthCheck()** - Verify application health endpoints
- **validateStageIsUp()** - Check server connectivity and SSH availability

## Usage in Jenkinsfile

```groovy
@Library('pfm-groovy-library') _

pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                script {
                    def commitHash = prepareDockerImage('https://github.com/user/repo.git', 'main', 'myapp')
                    tagImageForEnvironment("myapp:${commitHash}", 'staging')
                }
            }
        }
        stage('Deploy') {
            steps {
                script {
                    runAnsiblePlaybook('/opt/ansible', 'deploy.yml', [environment: 'staging'])
                    updateAwsAsg('staging-asg', 2)
                }
            }
        }
        stage('Verify') {
            steps {
                script {
                    validateStageIsUp('staging-server')
                    performHealthCheck('staging-server')
                }
            }
        }
    }
}
```

## Development

### Testing

All functions are covered by comprehensive unit tests using the Spock framework. Tests validate function behavior, input validation, error handling, and command execution.

### Pull Request Requirements

All changes must pass automated unit tests before merging. Tests run automatically on every pull request via GitHub Actions. The CI pipeline will:

1. Run all unit tests
2. Generate test reports  
3. Block merge if any tests fail
4. Publish test results for review

### Running Tests Locally

```bash
# Run all tests
./gradlew test

# Run with detailed output
./gradlew test --info

# Clean and test
./gradlew clean test
```

## Dependencies

### Runtime Dependencies
- **Groovy 4.0.23** - Core language runtime
- **Jenkins Pipeline** - Execution environment (provided by Jenkins)

### Test Dependencies  
- **Spock Framework 2.3** - Testing framework with BDD-style specifications
- **JUnit 5** - Test execution platform
- **Byte Buddy** - Mocking support for Spock tests

### Build Requirements
- **Java 11+** - Minimum JDK version
- **Gradle 8.5+** - Build automation tool

## Project Structure

```
pfm-groovy-library/
├── src/                     # Main source code
│   └── com/pfm/            # Function implementations
├── vars/                    # Jenkins pipeline step definitions  
├── tests/unitTests/         # Spock unit tests
├── build.gradle            # Build configuration
├── gradle.properties       # Gradle settings
└── .github/workflows/      # CI/CD pipeline definitions
```

## Configuration

The project uses Gradle with configuration cache enabled for faster builds. Key settings:

- **Java compatibility**: Java 11
- **Test framework**: JUnit 5 with Spock
- **Build optimizations**: Configuration cache, parallel execution
- **CI/CD**: GitHub Actions for automated testing

## Contributing

1. Create a feature branch from `main`
2. Implement changes with corresponding unit tests
3. Ensure all tests pass locally: `./gradlew test`
4. Create a pull request
5. Wait for automated tests to pass
6. Request code review

All contributions must maintain 100% test coverage for new functionality and pass the existing test suite.