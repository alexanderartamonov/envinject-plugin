package org.jenkinsci.plugins.envinject.service;

import hudson.matrix.MatrixProject;
import hudson.matrix.MatrixRun;
import hudson.model.*;
import hudson.tasks.BuildWrapper;
import hudson.util.DescribableList;
import org.jenkinsci.lib.envinject.EnvInjectException;
import org.jenkinsci.lib.envinject.EnvInjectLogger;
import org.jenkinsci.plugins.envinject.EnvInjectGlobalPasswordEntry;
import org.jenkinsci.plugins.envinject.EnvInjectPasswordEntry;
import org.jenkinsci.plugins.envinject.EnvInjectPasswordWrapper;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * @author Gregory Boissinot
 */
public class EnvInjectPasswordsMasker implements Serializable {


    public void maskPasswordsIfAny(@Nonnull AbstractBuild build, @Nonnull EnvInjectLogger logger, @Nonnull Map<String, String> envVars) {
        maskPasswordsJobParameterIfAny(build, logger, envVars);
        maskPasswordsEnvInjectIfAny(build, logger, envVars);
    }

    private void maskPasswordsJobParameterIfAny(@Nonnull AbstractBuild build, 
            @Nonnull EnvInjectLogger logger, @Nonnull Map<String, String> envVarsTarget) {
        ParametersAction parametersAction = build.getAction(ParametersAction.class);
        if (parametersAction != null) {
            List<ParameterValue> parameters = parametersAction.getParameters();
            if (parameters != null) {
                for (ParameterValue parameter : parameters) {
                    if (parameter instanceof PasswordParameterValue) {
                        PasswordParameterValue passwordParameterValue = ((PasswordParameterValue) parameter);
                        envVarsTarget.put(passwordParameterValue.getName(), passwordParameterValue.getValue().getEncryptedValue());
                    }
                }
            }
        }
    }

    private void maskPasswordsEnvInjectIfAny(@Nonnull AbstractBuild build, 
            @Nonnull EnvInjectLogger logger, @Nonnull Map<String, String> envVars) {
        try {

            EnvInjectPasswordWrapper envInjectPasswordWrapper = getEnvInjectPasswordWrapper(build);
            if (envInjectPasswordWrapper == null) {
                //nothing to mask
                return;
            }

            if (envInjectPasswordWrapper.isInjectGlobalPasswords()) {
                //Global passwords
                maskGlobalPasswords(envVars);
            }

            //Job passwords
            maskJobPasswords(envVars, envInjectPasswordWrapper.getPasswordEntryList());

        } catch (EnvInjectException ee) {
            logger.error("Can't mask global password :" + ee.getMessage());
        }
    }

    @CheckForNull
    private EnvInjectPasswordWrapper getEnvInjectPasswordWrapper(@Nonnull AbstractBuild build) throws EnvInjectException {

        DescribableList<BuildWrapper, Descriptor<BuildWrapper>> wrappersProject;
        if (build instanceof MatrixRun) {
            MatrixProject project = ((MatrixRun) build).getParentBuild().getProject();
            wrappersProject = project.getBuildWrappersList();
        } else {
            AbstractProject abstractProject = build.getProject();
            if (abstractProject instanceof BuildableItemWithBuildWrappers) {
                BuildableItemWithBuildWrappers project = (BuildableItemWithBuildWrappers) abstractProject;
                wrappersProject = project.getBuildWrappersList();
            } else {
                throw new EnvInjectException(String.format("Job type %s is not supported", abstractProject));
            }
        }

        for (BuildWrapper buildWrapper : wrappersProject) {
            if (EnvInjectPasswordWrapper.class.equals(buildWrapper.getClass())) {
                return (EnvInjectPasswordWrapper) buildWrapper;
            }
        }

        return null;
    }

    private void maskGlobalPasswords(Map<String, String> envVarsTarget) throws EnvInjectException {
        EnvInjectGlobalPasswordRetriever globalPasswordRetriever = new EnvInjectGlobalPasswordRetriever();
        EnvInjectGlobalPasswordEntry[] globalPasswordEntries = globalPasswordRetriever.getGlobalPasswords();
        if (globalPasswordEntries != null) {
            for (EnvInjectGlobalPasswordEntry globalPasswordEntry : globalPasswordEntries) {
                envVarsTarget.put(globalPasswordEntry.getName(),
                        globalPasswordEntry.getValue().getEncryptedValue());
            }
        }
    }

    private void maskJobPasswords(@Nonnull Map<String, String> envVarsTarget, @Nonnull List<EnvInjectPasswordEntry> passwordEntries) {
        for (EnvInjectPasswordEntry passwordEntry : passwordEntries) {
            envVarsTarget.put(passwordEntry.getName(), passwordEntry.getValue().getEncryptedValue());
        }
    }


}
