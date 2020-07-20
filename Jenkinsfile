#!/usr/bin/env groovy

node('docker') {
    docker.image('jenkins/ath:acceptance-test-harness-1.73').inside {
        stage('test') {
            checkout scm
            sh 'mvn -B --no-transfer-progress clean package -Dmaven.test.failure.ignore'
            junit '**/target/surefire-reports/TEST-*.xml'
        }
    }
}
