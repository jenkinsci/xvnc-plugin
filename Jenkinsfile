#!/usr/bin/env groovy

node('docker') {
    docker.image('jenkins/ath:acceptance-test-harness-1.73').inside {
        stage('test') {
            checkout scm
            // Skipping enforcer inside ATH container as the maven there is too old
            // https://github.com/jenkinsci/plugin-pom/commit/826a0f18a91fe1bf3ca2c0e0f2ebd69af1224ebb
            sh 'mvn -B --no-transfer-progress clean package -Dmaven.test.failure.ignore -Denforcer.skip'
            junit '**/target/surefire-reports/TEST-*.xml'
        }
    }
}
