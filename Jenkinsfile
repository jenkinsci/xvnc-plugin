#!/usr/bin/env groovy

node('docker') {
    docker.image('jenkins/ath:acceptance-test-harness-1.73').inside {
        stage('test') {
            checkout scm
            // Skipping enforcer inside ATH container as the maven there is too old
            // TODO: remove after https://github.com/jenkinsci/acceptance-test-harness/pull/690
            sh 'mvn -B --no-transfer-progress clean package -Dmaven.test.failure.ignore -Denforcer.skip'
            junit '**/target/surefire-reports/TEST-*.xml'
        }
    }
}
