package com.bpbuilder.bp_builder.service.deploy;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.AddPermissionRequest;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.FunctionUrlAuthType;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.Runtime;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StateMachineDoesNotExistException;
import software.amazon.awssdk.services.sfn.model.StateMachineType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Deploys workflows with the AWS SDK. Credentials come from the standard AWS
 * chain (AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY); AWS_REGION defaults to
 * ap-southeast-2. LAMBDA_EXECUTION_ROLE_ARN names the role every function runs
 * as and STEP_FUNCTIONS_ROLE_ARN the role state machines run as.
 */
@Component
public class AwsLambdaGateway implements LambdaGateway {

    static final String HANDLER = "com.bpbuilder.lambda_runner.WorkflowLambdaHandler::handleRequest";
    private static final int MEMORY_MB = 1024;

    private final Function<String, String> environment;

    public AwsLambdaGateway() {
        this(System::getenv);
    }

    AwsLambdaGateway(Function<String, String> environment) {
        this.environment = environment;
    }

    @Override
    public DeployedFunction deployFunction(String name, byte[] code, Map<String, String> variables,
                                           int timeoutSeconds, boolean publicUrl) {
        String role = required("LAMBDA_EXECUTION_ROLE_ARN");
        return lambda(lambda -> {
            SdkBytes zip = SdkBytes.fromByteArray(code);
            FunctionConfiguration current = existing(lambda, name);
            if (current == null) {
                lambda.createFunction(request -> request.functionName(name).role(role).runtime(Runtime.JAVA17)
                        .handler(HANDLER).timeout(timeoutSeconds).memorySize(MEMORY_MB)
                        .description("BP Builder").code(source -> source.zipFile(zip))
                        .environment(env -> env.variables(variables)));
                lambda.waiter().waitUntilFunctionActiveV2(request -> request.functionName(name));
            } else {
                if (!sha256Base64(code).equals(current.codeSha256())) {
                    lambda.updateFunctionCode(request -> request.functionName(name).zipFile(zip));
                    lambda.waiter().waitUntilFunctionUpdatedV2(request -> request.functionName(name));
                }
                Map<String, String> currentVariables = current.environment() == null ? Map.of()
                        : current.environment().variables();
                if (!variables.equals(currentVariables) || current.timeout() != timeoutSeconds
                        || !role.equals(current.role()) || !HANDLER.equals(current.handler())) {
                    lambda.updateFunctionConfiguration(request -> request.functionName(name).role(role)
                            .runtime(Runtime.JAVA17).handler(HANDLER).timeout(timeoutSeconds).memorySize(MEMORY_MB)
                            .environment(env -> env.variables(variables)));
                    lambda.waiter().waitUntilFunctionUpdatedV2(request -> request.functionName(name));
                }
            }
            String arn = lambda.getFunction(request -> request.functionName(name)).configuration().functionArn();
            return new DeployedFunction(arn, publicUrl ? functionUrl(lambda, name) : null);
        }, "deploy " + name);
    }

    @Override
    public String deployStateMachine(String name, String definition) {
        String role = required("STEP_FUNCTIONS_ROLE_ARN");
        String arn = "arn:aws:states:" + region() + ":" + account(role) + ":stateMachine:" + name;
        return stepFunctions(sfn -> {
            try {
                sfn.describeStateMachine(request -> request.stateMachineArn(arn));
                sfn.updateStateMachine(request -> request.stateMachineArn(arn).definition(definition).roleArn(role));
                return arn;
            } catch (StateMachineDoesNotExistException missing) {
                return sfn.createStateMachine(request -> request.name(name).definition(definition).roleArn(role)
                        .type(StateMachineType.STANDARD)).stateMachineArn();
            }
        }, "deploy the state machine " + name);
    }

    @Override
    public void deleteFunction(String name) {
        lambda(lambda -> {
            try {
                lambda.deleteFunction(request -> request.functionName(name));
            } catch (ResourceNotFoundException alreadyGone) {
                // Nothing to delete.
            }
            return null;
        }, "delete " + name);
    }

    @Override
    public void deleteStateMachine(String name) {
        String role = required("STEP_FUNCTIONS_ROLE_ARN");
        String arn = "arn:aws:states:" + region() + ":" + account(role) + ":stateMachine:" + name;
        stepFunctions(sfn -> {
            try {
                sfn.deleteStateMachine(request -> request.stateMachineArn(arn));
            } catch (StateMachineDoesNotExistException alreadyGone) {
                // Nothing to delete.
            }
            return null;
        }, "delete the state machine " + name);
    }

    @Override
    public String region() {
        String region = environment.apply("AWS_REGION");
        return region == null || region.isBlank() ? "ap-southeast-2" : region.trim();
    }

    /** The function's public URL, created with the permissions an unauthenticated URL needs. */
    private static String functionUrl(LambdaClient lambda, String name) {
        try {
            return lambda.getFunctionUrlConfig(request -> request.functionName(name)).functionUrl();
        } catch (ResourceNotFoundException missing) {
            String url = lambda.createFunctionUrlConfig(request -> request.functionName(name)
                    .authType(FunctionUrlAuthType.NONE)).functionUrl();
            permit(lambda, request -> request.functionName(name).statementId("FunctionURLAllowPublicAccess")
                    .action("lambda:InvokeFunctionUrl").principal("*").functionUrlAuthType(FunctionUrlAuthType.NONE));
            permit(lambda, request -> request.functionName(name).statementId("FunctionURLInvokeAllowPublicAccess")
                    .action("lambda:InvokeFunction").principal("*").invokedViaFunctionUrl(true));
            return url;
        }
    }

    private static void permit(LambdaClient lambda, Consumer<AddPermissionRequest.Builder> request) {
        try {
            lambda.addPermission(request);
        } catch (ResourceConflictException alreadyGranted) {
            // The statement exists from an earlier deployment.
        }
    }

    private static FunctionConfiguration existing(LambdaClient lambda, String name) {
        try {
            return lambda.getFunction(request -> request.functionName(name)).configuration();
        } catch (ResourceNotFoundException missing) {
            return null;
        }
    }

    private String required(String name) {
        String value = environment.apply(name);
        if (value == null || value.isBlank()) {
            throw DeploymentException.unavailable(name + " is not configured for the backend.");
        }
        return value.trim();
    }

    /** The account of an ARN such as arn:aws:iam::123456789012:role/name. */
    private static String account(String arn) {
        String[] parts = arn.split(":");
        if (parts.length < 5 || parts[4].isBlank()) {
            throw DeploymentException.unavailable("STEP_FUNCTIONS_ROLE_ARN is not a role ARN.");
        }
        return parts[4];
    }

    /** Lambda reports CodeSha256 as the Base64 SHA-256 of the deployment package. */
    private static String sha256Base64(byte[] code) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(code));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private <T> T lambda(Function<LambdaClient, T> action, String what) {
        try (LambdaClient lambda = LambdaClient.builder().region(Region.of(region())).build()) {
            return action.apply(lambda);
        } catch (SdkException error) {
            throw DeploymentException.failed("AWS could not " + what + ": " + error.getMessage(), error);
        }
    }

    private <T> T stepFunctions(Function<SfnClient, T> action, String what) {
        try (SfnClient sfn = SfnClient.builder().region(Region.of(region())).build()) {
            return action.apply(sfn);
        } catch (SdkException error) {
            throw DeploymentException.failed("AWS could not " + what + ": " + error.getMessage(), error);
        }
    }
}
