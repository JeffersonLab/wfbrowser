<%@tag description="The Site Page Template" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<%@taglib prefix="s" uri="http://jlab.org/jsp/smoothness"%>
<%@attribute name="title"%>
<%@attribute name="pageStart"%>
<%@attribute name="pageEnd" %>
<%@attribute name="stylesheets" fragment="true"%>
<%@attribute name="scripts" fragment="true"%>
<%@attribute name="secondaryNavigation" fragment="true" %>
<c:url var="domainRelativeReturnUrl" scope="request" context="/" value="${requestScope['javax.servlet.forward.request_uri']}${requestScope['javax.servlet.forward.query_string'] ne null ? '?'.concat(requestScope['javax.servlet.forward.query_string']) : ''}"/>
<c:set var="currentPath" scope="request" value="${requestScope['javax.servlet.forward.servlet_path']}"/>
<s:tabbed-page title="${title}" category="">
    <jsp:attribute name="stylesheets">
        <link rel="shortcut icon" href="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/img/favicon.ico"/>
        <link rel="stylesheet" type="text/css" href="${initParam.cdnContextPath}/jquery-ui/1.10.3/theme/smoothness/jquery-ui.min.css"/>
        <link rel="stylesheet" type="text/css" href="${initParam.cdnContextPath}/jquery-plugins/timepicker/jquery-ui-timepicker-1.3.1.css"/>
        <link rel="stylesheet" type="text/css" href="${initParam.cdnContextPath}/jquery-plugins/select2/3.5.2/select2.css"/>
        <link rel="stylesheet" type="text/css" href="${initParam.cdnContextPath}/jlab-theme/smoothness/1.6/css/smoothness.min.css"/>
        <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/css/wfbrowser.css"/>
        <jsp:invoke fragment="stylesheets"/>
    </jsp:attribute>
    <jsp:attribute name="scripts">
        <script type="text/javascript" src="${initParam.cdnContextPath}/jquery/1.10.2.min.js"></script>
        <script type="text/javascript" src="${initParam.cdnContextPath}/jquery-ui/1.10.3/jquery-ui.min.js"></script>
        <script type="text/javascript" src="${initParam.cdnContextPath}/uri/uri-1.14.1.min.js"></script>
        <script type="text/javascript" src="${initParam.cdnContextPath}/jquery-plugins/timepicker/jquery-ui-timepicker-1.3.1.js"></script>
        <script type="text/javascript" src="${initParam.cdnContextPath}/jquery-plugins/select2/3.5.2/select2.min.js"></script>
        <script type="text/javascript" src="${initParam.cdnContextPath}/jlab-theme/smoothness/1.6/js/smoothness.min.js"></script>
        <script type="text/javascript">
            jlab.contextPath = "${pageContext.request.contextPath}";
            // THIS VARIABLE SHOULD ONLY BE USED FOR UI PURPOSES AND NOT FOR ANY REAL SECURITY
            jlab.isUserAdmin = false;
            <c:if test='${pageContext.request.isUserInRole("wfb_admin")}'>
            jlab.isUserAdmin = true;
            </c:if>
        </script>
        <script type="text/javascript" src="${pageContext.request.contextPath}/resources/js/wfbrowser.js"></script>
        <jsp:invoke fragment="scripts"/>
    </jsp:attribute>
    <jsp:attribute name="primaryNavigation">
        <ul>
            <li${'/graph' eq currentPath ? ' class="current-primary"' : ''}>
                <a href="${pageContext.request.contextPath}/graph">Graph</a>
            </li>
            <li ${fn:startsWith(currentPath, '/reports') ? ' class="current-primary"' : ''}>
                <a href="${pageContext.request.contextPath}/reports/rf-label-summary">Reports</a>
            </li>
            <c:if test='${pageContext.request.isUserInRole("wfb_admin")}'>
                <li${'/admin' eq currentPath ? ' class="current-primary"' : ''}>
                    <a href="${pageContext.request.contextPath}/admin/series">Admin</a>
                </li>
            </c:if>
            <li${'/help' eq currentPath ? ' class="current-primary"' : ''}>
                <a href="${pageContext.request.contextPath}/help">Help</a>
            </li>
        </ul>
</jsp:attribute>
    <jsp:attribute name="secondaryNavigation">
        <jsp:invoke fragment="secondaryNavigation"/>
    </jsp:attribute>
    <jsp:attribute name="footnote">
        <div id="version-info">Version: ${initParam.releaseNumber}, Released: ${initParam.releaseDate}</div>
    </jsp:attribute>
    <jsp:body>
        <jsp:doBody/>
    </jsp:body>
</s:tabbed-page>
