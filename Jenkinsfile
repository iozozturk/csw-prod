pipeline {
    agent {
        label 'master'
    }

    options {
        // using the Timestamper plugin we can add timestamps to the console log
        timestamps()
    }

    stages {
        stage('Checkout') {
            steps {
                git 'https://github.com/tmtsoftware/csw-prod.git'
            }
        }

        stage('Build') {
            steps {
                sh "sbt scalafmtCheck"
                sh "sbt -Dcheck.cycles=true -Dformat.on.compile=false clean scalastyle compile"
            }
        }

        stage('Unit and Component Tests') { // Component tests cover the scenario of multiple components in single container
            steps {
                sh "sbt -DenableCoverage=true test:test"
            }
            post {
                always {
                    sh "sbt -DenableCoverage=true coverageReport"
                    sh "sbt coverageAggregate"
                    junit '**/target/test-reports/*.xml'
                    publishHTML(target: [
                            allowMissing         : true,
                            alwaysLinkToLastBuild: false,
                            keepAll              : true,
                            reportDir            : './target/scala-2.12/scoverage-report',
                            reportFiles          : 'index.html',
                            reportName           : "Scoverage Report"
                    ])
                }
            }
        }

        stage('Multi-Jvm Test') { // These tests cover the scenario of multiple components in multiple containers on same machine.
            steps {
                sh "sbt csw-location/multi-jvm:test"
                sh "sbt csw-config-client/multi-jvm:test"
                sh "sbt csw-config-client-cli/multi-jvm:test"
                sh "sbt csw-framework/multi-jvm:test"
            }
        }
    }
    post {
        always {
            script {
                sendNotification(currentBuild.result)
            }
        }
    }
}

def sendNotification(String buildStatus = 'STARTED') {
    buildStatus = buildStatus ?: 'SUCCESSFUL'

    def colorName = 'RED'
    def colorCode = '#FF0000'
    def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
    def summary = "${subject} (${env.BUILD_URL})"
    def details = '${JELLY_SCRIPT,template="html"}'

    // Override default values based on build status
    if (buildStatus == 'STARTED') {
        color = 'YELLOW'
        colorCode = '#FFFF00'
    } else if (buildStatus == 'SUCCESSFUL') {
        color = 'GREEN'
        colorCode = '#00FF00'
    } else {
        color = 'RED'
        colorCode = '#FF0000'
        emailext(
                    subject: subject,
                    body: details,
                    to: "tmt-csw@thoughtworks.com"
            )
    }

    slackSend(channel: "#csw-build", color: colorCode, message: summary)
}