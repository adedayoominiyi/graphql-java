package benchmark;

import graphql.execution.CoercedVariables;
import graphql.language.Document;
import graphql.normalized.ExecutableNormalizedOperation;
import graphql.normalized.ExecutableNormalizedOperationFactory;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaGenerator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2)
@Measurement(iterations = 2, timeUnit = TimeUnit.NANOSECONDS)
public class NQBenchmark1 {

    @State(Scope.Benchmark)
    public static class MyState {

        GraphQLSchema schema;
        Document document;

        @Setup
        public void setup() {
            try {
                String schemaString = BenchmarkUtils.loadResource("large-schema-1.graphqls");
                schema = SchemaGenerator.createdMockedSchema(schemaString);

                String query = BenchmarkUtils.loadResource("large-schema-1-query.graphql");
                document = Parser.parse(query);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Benchmark
    @Warmup(iterations = 2)
    @Measurement(iterations = 3, time = 10)
    @Threads(1)
    @Fork(3)
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchMarkAvgTime(MyState myState, Blackhole blackhole )  {
        runImpl(myState, blackhole);
    }

    @Benchmark
    @Warmup(iterations = 2)
    @Measurement(iterations = 3, time = 10)
    @Threads(1)
    @Fork(3)
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchMarkThroughput(MyState myState, Blackhole blackhole )  {
        runImpl(myState, blackhole);
    }

    private void runImpl(MyState myState, Blackhole blackhole) {
        ExecutableNormalizedOperation executableNormalizedOperation = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperation(myState.schema, myState.document, null, CoercedVariables.emptyVariables());
        blackhole.consume(executableNormalizedOperation);
    }


}
