#!/usr/bin/groovy

package org.eceuropa.ssk.v1;

def prepare() {
    node {
        checkout(scm)
        // we e.g. have a .kitchen.docker.yml left from the last run. Remove that.
        sh("git clean -fdx")
    }
}

def failTheBuild(String message) {

    currentBuild.result = "FAILURE"

    (new SlackPostBuild()).execute()
    
    def messageColor = "\u001B[32m" 
    def messageColorReset = "\u001B[0m"
    echo messageColor + message + messageColorReset
    error(message)
}

def run(Object step){
    try {
        step.execute()
    } catch (err) {
        // unfortunately, err.message is not whitelisted by script security
        //failTheBuild(err.message)
        failTheBuild("Build failed")
    }
}

def execute() {
    (new SlackPreBuild()).execute()

    (new SlackPostBuild()).execute()
}

return this;
