package foo.bar.smoketest;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletRequest;
import java.util.Enumeration;

public class JakartaServletRequestTestSuite implements ServletRequestTestSuite<ServletRequest> {
  ServletRequest request;

  @Override
  public void init(ServletRequest request) {
    this.request = request;
  }

  @Override
  public java.util.Map<String, String[]> getParameterMap() {
    return request.getParameterMap();
  }

  @Override
  public String getParameter(String paramName) {
    return request.getParameter(paramName);
  }

  @Override
  public String[] getParameterValues(String paramName) {
    return request.getParameterValues(paramName);
  }

  @Override
  public Enumeration getParameterNames() {
    return request.getParameterNames();
  }

  @Override
  public RequestDispatcher getRequestDispatcher(String path) {
    return request.getRequestDispatcher(path);
  }
}
