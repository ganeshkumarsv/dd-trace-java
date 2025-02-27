package foo.bar.smoketest;

import jakarta.servlet.RequestDispatcher;
import java.util.Enumeration;

public interface ServletRequestTestSuite<E> {

  void init(final E request);

  java.util.Map<String, String[]> getParameterMap();

  String getParameter(String paramName);

  String[] getParameterValues(String paramName);

  Enumeration getParameterNames();

  RequestDispatcher getRequestDispatcher(String path);
}
