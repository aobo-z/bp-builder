package com.bpbuilder.bp_builder.config;

import com.bpbuilder.bp_builder.service.interpret.GeminiClient;
import com.bpbuilder.bp_builder.workflow.planning.ChainPlanner;
import com.bpbuilder.bp_builder.workflow.planning.CompletenessChecker;
import com.bpbuilder.bp_builder.workflow.runtime.HttpTransport;
import com.bpbuilder.bp_builder.workflow.runtime.JdkHttpTransport;
import com.bpbuilder.bp_builder.workflow.runtime.WorkflowExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the framework-free workflow classes into Spring. Running locally they
 * read endpoint URLs from this process's environment; a Lambda handler would
 * construct the same WorkflowExecutor with its own environment.
 */
@Configuration
public class WorkflowConfiguration {

    @Bean
    public HttpTransport workflowHttpTransport() {
        return new JdkHttpTransport();
    }

    @Bean
    public WorkflowExecutor workflowExecutor(HttpTransport workflowHttpTransport) {
        return new WorkflowExecutor(workflowHttpTransport, System::getenv);
    }

    @Bean
    public GeminiClient geminiClient(HttpTransport workflowHttpTransport) {
        return new GeminiClient(workflowHttpTransport, System::getenv);
    }

    @Bean
    public ChainPlanner chainPlanner() {
        return new ChainPlanner();
    }

    @Bean
    public CompletenessChecker completenessChecker(ChainPlanner chainPlanner) {
        return new CompletenessChecker(chainPlanner);
    }
}
