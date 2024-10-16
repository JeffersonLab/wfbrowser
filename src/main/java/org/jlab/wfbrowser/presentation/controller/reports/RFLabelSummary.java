package org.jlab.wfbrowser.presentation.controller.reports;

import org.jlab.wfbrowser.business.filter.EventFilter;
import org.jlab.wfbrowser.business.filter.LabelFilter;
import org.jlab.wfbrowser.business.service.EventService;
import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.presentation.util.GraphConfig;
import org.jlab.wfbrowser.presentation.util.SessionUtils;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "Servlet", urlPatterns = "/reports/rf-label-summary")
public class RFLabelSummary extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(RFLabelSummary.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String beginString = request.getParameter("begin");
        String endString = request.getParameter("end");
        String confString = request.getParameter("conf");
        String confOpString = request.getParameter("confOp");
        String[] locationStrings = request.getParameterValues("location");
        String isLabeledString = request.getParameter("isLabeled");
        String heatmap = request.getParameter("heatmap");
        String timeline = request.getParameter("timeline");
        boolean isLabeled = Boolean.parseBoolean(isLabeledString);

        boolean redirectNeeded = false;
        if ( beginString == null || beginString.isEmpty() || endString == null || endString.isEmpty()
                || locationStrings == null || locationStrings.length == 0 || confString == null || confString.isEmpty()
                || confOpString == null || confOpString.isEmpty() || isLabeledString == null
                || isLabeledString.isEmpty() || heatmap == null || heatmap.isEmpty() || timeline == null
                || timeline.isEmpty()) {
            redirectNeeded = true;
        }

        Double confidence;
        // If someone doesn't supply a confidence value, set it to 0.0.  This works permissively with the default confOp
        // of ">"
        if (confString == null || confString.isEmpty()) {
            confString = "0.0";
            confidence = 0.0;
        } else {
            confidence = Double.valueOf(confString);
        }
        // If someone supplied a confidence, but not an operator, assume a default operator of ">"
        if (confOpString == null) {
            confOpString = ">";
        }

        if (heatmap == null ||
                (!heatmap.equals("linac") && !heatmap.equals("zone") && !heatmap.equals("all"))) {
            redirectNeeded = true;
            heatmap = "linac";
        }

        if (timeline == null ||
                (!timeline.equals("single") && !timeline.equals("separate"))) {
            redirectNeeded = true;
            timeline = "separate";
        }

        // Start with the defaults and filter out any invalid locations.
        String system = "rf";
        GraphConfig graphConfig = GraphConfig.getDefaultConfig(system);
        Set<String> locations = GraphConfig.keepOnlyMatches(locationStrings, graphConfig.getLocationOptions());
        // Must have been some invalid locations or no location given in request
        if (locationStrings != null && locations.size() != locationStrings.length) {
            redirectNeeded = true;
        }

        // Here is a graph config object with only the requested parameters.
        GraphConfig requestGraphConfig = new GraphConfig(system, locations, null,
                null, null, beginString, endString, null, null,
                null, null, null, null);

        Instant begin, end;
        List<String> locationSelections;
        Map<String, Boolean> locationSelectionMap;
        synchronized (SessionUtils.getSessionLock(request, null)) {
            HttpSession session = request.getSession();

            @SuppressWarnings("unchecked")
            Map<String, GraphConfig> gcMap = (Map<String, GraphConfig>) session.getAttribute("graphConfigMap");
            if (gcMap == null) {
                gcMap = new HashMap<>();
                session.setAttribute("graphConfigMap", gcMap);
            }

            // We want defaults, overwritten by session if it exists, overwritten by request params
            if (gcMap.containsKey(system) && gcMap.get(system) != null) {
                graphConfig.overwriteWith(gcMap.get(system));
            }
            graphConfig.overwriteWith(requestGraphConfig);
            gcMap.put(system, graphConfig);

            // From here we work with the session graph config since it is the current master settings and is what will
            // be used in future requests.
            GraphConfig sessionGraphConfig = gcMap.get(system);
            boolean updated = sessionGraphConfig.overwriteWith(requestGraphConfig);
            redirectNeeded = redirectNeeded || updated;

            begin = sessionGraphConfig.getBegin();
            end = sessionGraphConfig.getEnd();
            beginString = sessionGraphConfig.getBeginString();
            endString = sessionGraphConfig.getEndString();
            locationSelections = new ArrayList<>(sessionGraphConfig.getLocations());
            locationSelectionMap = sessionGraphConfig.getSelectionMap("location");

            // Redirect if needed.  Make sure we grab all of our user selections to make this bookmark-able
            if (redirectNeeded) {
                StringBuilder redirectUrl = new StringBuilder(request.getContextPath() + "/reports/rf-label-summary?" +
                        "begin=" + URLEncoder.encode(beginString, "UTF-8") +
                        "&end=" + URLEncoder.encode(endString, "UTF-8") +
                        "&heatmap=" + URLEncoder.encode(heatmap, "UTF-8") +
                        "&timeline=" + URLEncoder.encode(timeline, "UTF-8") +
                        "&isLabeled=" + URLEncoder.encode(String.valueOf(isLabeled), "UTF-8"));
                redirectUrl.append("&conf=");
                redirectUrl.append(URLEncoder.encode(confString, "UTF-8"));
                redirectUrl.append("&confOp=");
                redirectUrl.append(URLEncoder.encode(confOpString, "UTF-8"));
                if (locationSelections == null || locationSelections.isEmpty()) {
                    redirectUrl.append("&location=");
                } else {
                    for (String location : locationSelections) {
                        redirectUrl.append("&location=");
                        redirectUrl.append(URLEncoder.encode(location, "UTF-8"));
                    }
                }
                response.sendRedirect(response.encodeRedirectURL(redirectUrl.toString()));
                return;
            }
        }

        EventService es = new EventService();
        List<Event> events;
        try {
            // Get the tally of labeled events
            EventFilter ef = new EventFilter(null, begin, end, "rf", locationSelections, null, null, null, null);
            List<LabelFilter> lfList = new ArrayList<>();
            lfList.add(new LabelFilter(null, null, null, confidence, confOpString));

            events = es.getEventListWithoutCaptureFiles(ef);
            events = EventService.applyLabelFilters(events, lfList, !isLabeled);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error querying database for label tally");
            throw new ServletException(ex);
        }


        request.setAttribute("events", EventService.convertEventListToJson(events, null).toString());
        request.setAttribute("locationSelectionMap", locationSelectionMap);
        request.setAttribute("locationSelections", locationSelections);
        request.setAttribute("confString", confString);
        request.setAttribute("confOpString", confOpString);
        request.setAttribute("beginString", beginString);
        request.setAttribute("endString", endString);
        request.setAttribute("isLabeled", isLabeled);
        request.setAttribute("heatmap", heatmap);
        request.setAttribute("timeline", timeline);
        request.getRequestDispatcher("/WEB-INF/views/reports/rf-label-summary.jsp").forward(request, response);
    }
}
