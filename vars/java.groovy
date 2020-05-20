def call(body) {
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

def label = "worker-${UUID.randomUUID().toString()}"

podTemplate(label: label,

containers: [

  containerTemplate(name: 'maven', image: '${env.REGISTRY_ADDRESS}/iyzico/misc/maven-cache', command: 'cat', ttyEnabled: true),

  containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true),

  containerTemplate(name: 'kubectl', image: 'lachlanevenson/k8s-kubectl:v1.8.8', command: 'cat', ttyEnabled: true),

  containerTemplate(name: 'helm', image: 'lachlanevenson/k8s-helm:latest', command: 'cat', ttyEnabled: true),

  containerTemplate(name: 'jnlp', image: 'jenkinsci/jnlp-slave:latest', args: '${computer.jnlpmac} ${computer.name}')

],
            imagePullSecrets: ['${env.REGISTRY_ADDRESS}'],
volumes: [

  //hostPathVolume(mountPath: '/home/gradle/.gradle', hostPath: '/tmp/jenkins/.gradle'),

  hostPathVolume(mountPath: '/var/run/docker.sock', hostPath: '/var/run/docker.sock')

]) {

  node(label) {

    def myRepo = checkout scm

    def gitCommit = myRepo.GIT_COMMIT

    def gitBranch = myRepo.GIT_BRANCH

    def shortGitCommit = "${gitCommit[0..10]}"

    def previousGitCommit = sh(script: "git rev-parse ${gitCommit}~", returnStdout: true)

    stage('Build') {

      container('maven') {

        sh 'mvn -s /data/settings.xml clean package -DskipTests=true'

      }

    }

    stage('Create Docker images') {

      container('docker') {
          
          withCredentials([usernamePassword( credentialsId: 'halit.soydemir.registry', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {

          sh """

            docker login ${env.REGISTRY_ADDRESS} -u ${USERNAME} -p ${PASSWORD}
            
            docker build -t r${env.REGISTRY_ADDRESS}/iyzico/iyzipay/${pipelineParams.registeryName}:${env.BUILD_NUMBER} .

            docker images
            
            docker push ${env.REGISTRY_ADDRESS}/iyzico/iyzipay/${pipelineParams.registeryName}:${env.BUILD_NUMBER}

            """

        }

      }

    }

    stage('Run kubectl') {

      container('kubectl') {

        sh "kubectl get pods"

      }

    }

    stage('Run helm') {

      container('helm') {

        sh "helm list"

      }

    }

  }

}
}
