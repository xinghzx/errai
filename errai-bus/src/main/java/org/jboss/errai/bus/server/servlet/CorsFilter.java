package org.jboss.errai.bus.server.servlet;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet filter that inclues the HTTP headers to allow <a
 * href="http://www.html5rocks.com/en/tutorials/cors/">cross-origin resource
 * sharing</a> from browsers that support CORS.
 * <p>
 * This code was adapted from the sample code found at <a href=
 * "http://padcom13.blogspot.com/2011/09/cors-filter-for-java-applications.html"
 * >Matthias Hryniszak's blog</a>. Thanks, Matthias!
 * <p>
 * To use this filter to make a "remote" ErraiBus accessible from a webapp, add
 * this to your web.xml:
 * <p>
 * 
 * <pre>
 *   &lt;filter>
 *     &lt;filter-name>CorsFilter&lt;/filter-name>
 *     &lt;filter-class>org.jboss.errai.bus.server.servlet.CorsFilter&lt;/filter-class>
 *   &lt;/filter>
 *   &lt;filter-mapping>
 *     &lt;filter-name>CorsFilter&lt;/filter-name>
 *     &lt;url-pattern>*.erraiBus&lt;/url-pattern>
 *   &lt;/filter-mapping>
 * </pre>
 * 
 * @author Matthias Hryniszak - original version
 * @author Jonathan Fuerth - updates for Errai
 */
public class CorsFilter implements Filter {

  public CorsFilter() {
  }

  public void init(FilterConfig fc) throws ServletException {
  }

  public void destroy() {
  }

  public void doFilter(ServletRequest request, ServletResponse response,
          FilterChain chain) throws IOException, ServletException {

    HttpServletResponse r = (HttpServletResponse) response;
    r.addHeader("Access-Control-Allow-Origin", "*");
    r.addHeader("Access-Control-Allow-Headers",
            "Accept,Accept-Encoding,Accept-Language,Cache-Control,Connection,Content-Length,Content-Type," +
            "Cookie,Host,Pragma,Referer,RemoteQueueID,User-Agent");
    r.addHeader("Access-Control-Allow-Credentials", "true");
    chain.doFilter(request, response);
  }
}
