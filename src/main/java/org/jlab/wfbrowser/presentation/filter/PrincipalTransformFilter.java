package org.jlab.wfbrowser.presentation.filter;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * This filter stores a transformed version of the remoteUser so that JSPs have a nicer version to display.
 * @author adamc
 */
@WebFilter(filterName = "PrincipalTransformFilter", urlPatterns = {"/*"}, dispatcherTypes = {DispatcherType.REQUEST})
public class PrincipalTransformFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest req = (HttpServletRequest) request;
            String rawName = req.getRemoteUser();
            if (rawName != null) {
                String[] tokens = rawName.split(":");
                // Service account names appear to have only the name service-account-<keycloak_client_name>
                // Others have realm, etc. identifiers separated by colons
                String transformedName = tokens[0];
                if (tokens.length > 1) {
                    transformedName = tokens[2];
                }
                request.setAttribute("simpleRemoteUser", transformedName);
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void destroy() {
    }
}
