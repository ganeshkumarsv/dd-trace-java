package datadog.trace.instrumentation.hibernate.core.v4_0;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.iast.InstrumentationBridge;
import datadog.trace.api.iast.sink.SqlInjectionModule;
import net.bytebuddy.asm.Advice;
import org.hibernate.Query;
import org.hibernate.SharedSessionContract;

@AutoService(Instrumenter.class)
public class IastQueryInstrumentation extends Instrumenter.Iast
    implements Instrumenter.ForSingleType {

  public IastQueryInstrumentation() {
    super("hibernate", "hibernate-core");
  }

  @Override
  public String instrumentedType() {
    return "org.hibernate.internal.AbstractQueryImpl";
  }

  @Override
  public void adviceTransformations(AdviceTransformation transformation) {
    transformation.applyAdvice(
        isMethod().and(named("before").and(takesArguments(0))),
        IastQueryInstrumentation.class.getName() + "$QueryMethodAdvice");
  }

  public static class QueryMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void beforeMethod(@Advice.This final Query query) {
      final SqlInjectionModule module = InstrumentationBridge.SQL_INJECTION;
      if (module != null) {
        module.onJdbcQuery(query.getQueryString());
      }
    }

    /**
     * Some cases of instrumentation will match more broadly than others, so this unused method
     * allows all instrumentation to uniformly match versions of Hibernate starting at 4.0.
     */
    public static void muzzleCheck(final SharedSessionContract contract) {
      contract.createCriteria("");
    }
  }
}
