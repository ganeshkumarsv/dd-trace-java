package datadog.trace.instrumentation.akkahttp102.iast;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import akka.http.scaladsl.server.Directive;
import akka.http.scaladsl.server.util.Tupler$;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.agent.tooling.muzzle.Reference;
import datadog.trace.instrumentation.akkahttp102.iast.helpers.TaintParametersFunction;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public class ParameterDirectivesImplInstrumentation extends Instrumenter.Iast
    implements Instrumenter.ForSingleType {
  public ParameterDirectivesImplInstrumentation() {
    super("akka-http");
  }

  @Override
  public String instrumentedType() {
    return "akka.http.scaladsl.server.directives.ParameterDirectives$Impl$";
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".helpers.TaintParametersFunction",
    };
  }

  @Override
  public Reference[] additionalMuzzleReferences() {
    // just so we can use assertInverse in the muzzle directive
    return new Reference[] {new Reference.Builder(instrumentedType()).build()};
  }

  @Override
  public void adviceTransformations(AdviceTransformation transformation) {
    transformation.applyAdvice(
        isMethod()
            .and(not(isStatic()))
            .and(named("filter"))
            .and(takesArguments(2))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, named("akka.http.scaladsl.unmarshalling.Unmarshaller")))
            .and(returns(named("akka.http.scaladsl.server.Directive"))),
        ParameterDirectivesImplInstrumentation.class.getName() + "$FilterAdvice");

    // requiredFilter not relevant
    transformation.applyAdvice(
        isMethod()
            .and(not(isStatic()))
            .and(named("repeatedFilter"))
            .and(takesArguments(2))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, named("akka.http.scaladsl.unmarshalling.Unmarshaller")))
            .and(returns(named("akka.http.scaladsl.server.Directive"))),
        ParameterDirectivesImplInstrumentation.class.getName() + "$RepeatedFilterAdvice");
  }

  static class FilterAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    static void after(
        @Advice.Argument(0) String paramName,
        @Advice.Return(readOnly = false) Directive /*<Tuple1<?>>*/ retval) {
      try {
        retval =
            retval.tmap(new TaintParametersFunction(paramName), Tupler$.MODULE$.forTuple(null));
      } catch (Exception e) {
        throw new RuntimeException(e); // propagate so it's logged
      }
    }
  }

  static class RepeatedFilterAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    static void after(
        @Advice.Argument(0) String paramName,
        @Advice.Return(readOnly = false) Directive /*<Tuple1<Iterable<?>>>*/ retval) {
      try {
        retval =
            retval.tmap(new TaintParametersFunction(paramName), Tupler$.MODULE$.forTuple(null));
      } catch (Exception e) {
        throw new RuntimeException(e); // propagate so it's logged
      }
    }
  }
}
