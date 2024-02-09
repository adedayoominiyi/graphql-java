package graphql.execution;

import graphql.ExecutionResult;
import graphql.GraphQLContext;
import graphql.PublicApi;
import graphql.execution.incremental.DeferredExecutionSupport;
import graphql.execution.instrumentation.ExecutionStrategyInstrumentationContext;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionStrategyParameters;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * The standard graphql execution strategy that runs fields asynchronously non-blocking.
 */
@PublicApi
public class AsyncExecutionStrategy extends AbstractAsyncExecutionStrategy {

    /**
     * The standard graphql execution strategy that runs fields asynchronously
     */
    public AsyncExecutionStrategy() {
        super(new SimpleDataFetcherExceptionHandler());
    }

    /**
     * Creates a execution strategy that uses the provided exception handler
     *
     * @param exceptionHandler the exception handler to use
     */
    public AsyncExecutionStrategy(DataFetcherExceptionHandler exceptionHandler) {
        super(exceptionHandler);
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
        Instrumentation instrumentation = executionContext.getInstrumentation();
        InstrumentationExecutionStrategyParameters instrumentationParameters = new InstrumentationExecutionStrategyParameters(executionContext, parameters);

        ExecutionStrategyInstrumentationContext executionStrategyCtx = ExecutionStrategyInstrumentationContext.nonNullCtx(instrumentation.beginExecutionStrategy(instrumentationParameters, executionContext.getInstrumentationState()));

        MergedSelectionSet fields = parameters.getFields();
        List<String> fieldNames = fields.getKeys();

        DeferredExecutionSupport deferredExecutionSupport =
                Optional.ofNullable(executionContext.getGraphQLContext())
                        .map(graphqlContext -> (Boolean) graphqlContext.get(GraphQLContext.ENABLE_INCREMENTAL_SUPPORT))
                        .orElse(false) ?
                        new DeferredExecutionSupport.DeferredExecutionSupportImpl(
                                fields,
                                parameters,
                                executionContext,
                                this::resolveFieldWithInfo
                        ) : DeferredExecutionSupport.NOOP;

        executionContext.getIncrementalCallState().enqueue(deferredExecutionSupport.createCalls());

        Async.CombinedBuilder<FieldValueInfo> futures = getAsyncFieldValueInfo(executionContext, parameters);
        CompletableFuture<ExecutionResult> overallResult = new CompletableFuture<>();
        executionStrategyCtx.onDispatched(overallResult);

        futures.await().whenComplete((completeValueInfos, throwable) -> {
            List<String> fieldsExecutedOnInitialResult = deferredExecutionSupport.getNonDeferredFieldNames(fieldNames);

            BiConsumer<List<Object>, Throwable> handleResultsConsumer = handleResults(executionContext, fieldsExecutedOnInitialResult, overallResult);
            if (throwable != null) {
                handleResultsConsumer.accept(null, throwable.getCause());
                return;
            }

            Async.CombinedBuilder<Object> fieldValuesFutures = Async.ofExpectedSize(completeValueInfos.size());
            for (FieldValueInfo completeValueInfo : completeValueInfos) {
                fieldValuesFutures.add(completeValueInfo.getFieldValueFuture());
            }
            executionStrategyCtx.onFieldValuesInfo(completeValueInfos);
            fieldValuesFutures.await().whenComplete(handleResultsConsumer);
        }).exceptionally((ex) -> {
            // if there are any issues with combining/handling the field results,
            // complete the future at all costs and bubble up any thrown exception so
            // the execution does not hang.
            executionStrategyCtx.onFieldValuesException();
            overallResult.completeExceptionally(ex);
            return null;
        });

        overallResult.whenComplete(executionStrategyCtx::onCompleted);
        return overallResult;
    }
}
