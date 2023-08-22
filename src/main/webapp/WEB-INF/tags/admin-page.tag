<%@tag description="The Setup Page Template" pageEncoding="UTF-8"%>
<%@taglib prefix="t" tagdir="/WEB-INF/tags"%> 
<%@attribute name="title"%>
<%@attribute name="stylesheets" fragment="true"%>
<%@attribute name="scripts" fragment="true"%>
<t:page title="Admin - ${title}"> 
    <jsp:attribute name="stylesheets">       
        <jsp:invoke fragment="stylesheets"/>
    </jsp:attribute>
    <jsp:attribute name="scripts">
        <jsp:invoke fragment="scripts"/>
    </jsp:attribute>    
    <jsp:body>
        <div id="two-columns">
            <div id="left-column">
                <section>
                    <h2 id="left-column-header">Admin</h2>
                    <nav id="secondary-nav">
                        <ul>
                            <li${'/admin/series' eq currentPath ? ' class="current-secondary"' : ''}><a href="${pageContext.request.contextPath}/admin/series">Series</a></li>
                            <li${'/admin/series-sets' eq currentPath ? ' class="current-secondary"' : ''}><a href="${pageContext.request.contextPath}/admin/series-sets">Series Sets</a></li>
                        </ul>
                    </nav>
                </section>
            </div>
            <div id="right-column">
                <jsp:doBody/>
            </div>
        </div>        
    </jsp:body>         
</t:page>
