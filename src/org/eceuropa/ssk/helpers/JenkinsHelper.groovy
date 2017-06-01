#!/usr/bin/groovy

package org.eceuropa.ssk.helpers

import com.cloudbees.groovy.cps.NonCPS
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.jenkinsci.plugins.workflow.support.steps.input.Rejection

class JenkinsHelper implements Serializable {

    def script

    JenkinsHelper(script) {
        this.script = script
    }

    /**
     * Generates a path to a temporary file location, ending with {@code path} parameter.
     *
     * @param path path suffix
     * @return path to file inside a temp directory
     */
    @NonCPS
    String createTempLocation(String path) {
        String tmpDir = script.pwd tmp: true
        return tmpDir + File.separator + new File(path).getName()
    }

    /**
     * Returns the path to a temp location of a script from the global library (resources/ subdirectory)
     *
     * @param srcPath path within the resources/ subdirectory of this repo
     * @param destPath destination path (optional)
     * @return path to local file
     */
    String copyGlobalLibraryScript(String srcPath, String destPath = null) {

        destPath = destPath ?: createTempLocation(srcPath)
        script.writeFile file: destPath, text: script.libraryResource(srcPath)
        script.echo "copyGlobalLibraryScript: copied ${srcPath} to ${destPath}"
        return destPath
    }

    /**
     * Annotates the build namme (#i by default) with some text, e.g., the version built.
     *
     * @param text Text to add to {@code currentBuild.displayName}
     */
    def annotateBuildName(String text) {
        script.currentBuild.displayName = "#${script.currentBuild.getNumber()} ${text}"
    }

    /**
     * Generates a pipeline {@code input} step that times out after a specified amount of time.
     *
     * The options for the timeout are supplied via {@code timeoutOptions}.
     * The options for the input dialog are supplied via {@code inputOptions}.
     *
     * The returned Map contains the following keys:
     *
     * - proceed: true, if the Proceed button was clicked, false if aborted manually aborted or timed out
     * - reason: 'user', if user hit Proceed or Abort; 'timeout' if input dialog timed out
     * - submitter: name of the user that submitted or canceled the dialog
     * - additional keys for every parameter submitted via {@code inputOptions.parameters}
     *
     * @param args Map containing inputOptions and timoutOptions, both passed to respective script
     * @return Map containing above specified keys response/reason/submitter and those for parameters
     */
    Map inputWithTimeout(Map args) {
        def returnData = [:]

        // see https://go.cloudbees.com/docs/support-kb-articles/CloudBees-Jenkins-Enterprise/Pipeline---How-to-add-an-input-step,-with-timeout,-that-continues-if-timeout-is-reached,-using-a-default-value.html
        try {
            script.timeout(args.timeoutOptions) {
                def inputOptions = args.inputOptions
                inputOptions.submitterParameter = "submitter"

                // as we ask for the submitter, we get a Map back instead of a string
                // besides the parameter supplied using args.inputOptions, this will include "submitter"
                def responseValues = script.input inputOptions
                script.echo "Response values: ${responseValues}"

                // BlueOcean currently drops the submitterParameter
                // https://issues.jenkins-ci.org/browse/JENKINS-41421
                if (responseValues instanceof String) {
                    script.echo "Response is a String. BlueOcean? Mimicking the correct behavior."
                    String choiceValue = responseValues
                    String choiceKey = args.inputOptions.parameters.first().getName()
                    responseValues = [(choiceKey): choiceValue, submitter: null]
                }
                script.echo "Submitted by ${responseValues.submitter}"

                returnData = [proceed: true, reason: 'user'] + responseValues
            }
        } catch (FlowInterruptedException err) { // error means we reached timeout
            // err.getCauses() returns [org.jenkinsci.plugins.workflow.support.script.input.Rejection]
            Rejection rejection = err.getCauses().first()

            if ('SYSTEM' == rejection.getUser().toString()) { // user == SYSTEM means timeout.
                returnData = [proceed: false, reason: 'timeout']
            } else { // explicitly aborted
                script.echo rejection.getShortDescription()
                returnData = [proceed: false, reason: 'user', submitter: rejection.getUser().toString()]
            }
        } catch (err) {
            // try to figure out, what's wrong when we manually abort the pipeline
            returnData = [proceed: false, reason: err.getMessage()]
        }

        returnData
    }

    /**
     * Returns a list of the short descriptions the build causes that triggered this build.
     * @return List of build causes
     */
    @NonCPS
    List<String> getBuildCauses() {
        script.currentBuild.rawBuild.getCauses().collect{ it.getShortDescription() }
    }
}

