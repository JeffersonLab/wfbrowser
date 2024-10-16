package org.jlab.wfbrowser.presentation.controller;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.jlab.wfbrowser.business.filter.EventFilter;
import org.jlab.wfbrowser.business.service.EventService;
import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Series;
import org.jlab.wfbrowser.model.SeriesSet;
import org.jlab.wfbrowser.presentation.util.GraphConfig;
import org.jlab.wfbrowser.presentation.util.SessionUtils;

/**
 * @author adamc
 */
@WebServlet(name = "Graph", urlPatterns = {"/graph"})
public class Graph extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(Graph.class.getName());

    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String beginString = request.getParameter("begin");
        String endString = request.getParameter("end");
        String[] locSel = request.getParameterValues("location");
        String[] serSel = request.getParameterValues("series");
        String[] serSetSel = request.getParameterValues("seriesSet");
        String[] classSel = request.getParameterValues("classification");

        String eventId = request.getParameter("eventId");
        String system = request.getParameter("system");
        String minCF = request.getParameter("minCF");

        Integer minCaptureFiles = null;
        if (minCF != null && !minCF.isEmpty()) {
            minCaptureFiles = Integer.parseInt(minCF);
        }

        // All arguments must be specified (even if only as an empty string).  Series handling is a little more complex.
        // Check that all the options are specified.  If not they will be set via a redirect.
        boolean redirectNeeded = false;
        // Check for simple options
        if (system == null || system.isEmpty() || beginString == null || beginString.isEmpty()
                || endString == null || endString.isEmpty() || locSel == null || locSel.length == 0
                || classSel == null || minCF == null || serSel == null || serSetSel == null
                || eventId == null) {
            redirectNeeded = true;
        }

        // You need either series sets or series to be given.
        if ((serSel == null || serSel.length == 0) && (serSetSel == null || serSetSel.length == 0)) {
            redirectNeeded = true;
        }

        /* Basic strategy with these session attributes - if we get explicit request parameters, use them and update the session
         * copies.  If we don't get request params, but we have the needed session attributes, use them and redirect.  If we don't
         * have request or session values, then use defaults, update the session, and redirect.
         */
        HttpSession session = request.getSession();

        // Get the default config for this system.  The config we use will start with defaults, then be overridden by
        // the session config and finally, the config based on the actual request.
        GraphConfig graphConfig = GraphConfig.getDefaultConfig(system);
        if (system == null) {
            system = graphConfig.getSystem();
        }

        // Set up the session
        setupSession(system, request);

        // Process selections compared with their viable options.  Still contains default values at this point.
        List<Series> seriesOptions = graphConfig.getSeriesOptions();
        Set<Series> seriesSelections = initializeSeriesSelections(serSel, seriesOptions);

        List<SeriesSet> seriesSetOptions = graphConfig.getSeriesSetOptions();
        Set<SeriesSet> seriesSetSelections = initializeSeriesSetSelections(serSetSel, seriesSetOptions);

        List<String> locationOptions = graphConfig.getLocationOptions();
        Set<String> locationSelections = GraphConfig.keepOnlyMatches(locSel, locationOptions);
        if (locSel == null || (locationSelections.size() != locSel.length)) {
            redirectNeeded = true;
        }

        List<String> classificationOptions = graphConfig.getClassificationOptions();
        Set<String> classificationSelections = GraphConfig.keepOnlyMatches(classSel, classificationOptions);
        if (classSel == null || (classificationSelections.size() != classSel.length)) {
            redirectNeeded = true;
        }

        Long eId = (eventId == null || eventId.isEmpty()) ? null : Long.parseLong(eventId);


        GraphConfig requestGraphConfig = new GraphConfig(system, locationSelections, classificationSelections, minCaptureFiles,
                eId, beginString, endString, classificationOptions, locationOptions, seriesSelections, seriesOptions,
                seriesSetSelections, seriesSetOptions);

        // Used in UI
        Map<String, Boolean> locationMap = new TreeMap<>();
        Map<String, Boolean> classificationMap = new TreeMap<>();
        Map<String, Boolean> seriesMap = new TreeMap<>();
        Map<String, Boolean> seriesSetMap = new TreeMap<>();
        Set<String> seriesMasterSet = new HashSet<>();
        List<Event> eventList;
        Event currentEvent = null;

        synchronized (SessionUtils.getSessionLock(request, null)) {
            @SuppressWarnings("unchecked")
            Map<String, GraphConfig> gcMap = (Map<String, GraphConfig>) session.getAttribute("graphConfigMap");
            GraphConfig sessionGraphConfig = gcMap.get(system);

            // graphConfig starts with defaults, overwritten by saved session state, overwritten by recently requested
            // options.  The final config then gets put back to the session state.  Work with the session after this
            // since it is now the right config and it will persist.  Some changes may be made to the event that is
            // displayed for example.
            graphConfig.overwriteWith(sessionGraphConfig);
            graphConfig.overwriteWith(requestGraphConfig);
            sessionGraphConfig.overwriteWith((graphConfig));

            // Overwrite the graph configuration variables with the finals values after merging session and request
            // versions.
            eId = sessionGraphConfig.getEventId();
            system = sessionGraphConfig.getSystem();
            beginString = sessionGraphConfig.getBeginString();
            endString = sessionGraphConfig.getEndString();
            seriesSelections = sessionGraphConfig.getSeries();
            seriesSetSelections = sessionGraphConfig.getSeriesSets();
            classificationSelections = sessionGraphConfig.getClassifications();
            locationSelections = sessionGraphConfig.getLocations();
            Instant begin = sessionGraphConfig.getBegin();
            Instant end = sessionGraphConfig.getEnd();

            List<String> locationSelectionsList = new ArrayList<>(locationSelections);
            List<String> classificationSelectionsList = new ArrayList<>(classificationSelections);

            // Check that the current event is within the requested time.  If it's not, select a new current event
            // from within that time range
            try {
                EventService es = new EventService();
                // We have a requested ID.  Grab it, but enforce the requested time range and other filters.  If we get
                // nothing back, it's because either the ID is invalid or it's out of the time range.
                if (eId != null) {
                    EventFilter filter = new EventFilter(Collections.singletonList(eId), begin, end, system,
                            locationSelectionsList, classificationSelectionsList, null, null, minCaptureFiles);
                    currentEvent = es.getMostRecentEvent(filter, true);
                    eId = (currentEvent == null) ? null : currentEvent.getEventId();
                    sessionGraphConfig.setEventId(eId);

                    // If this is null, we'll need to redirect to switch to an empty eventId param.  Means we had an
                    // event requested that does not match the other parameters.
                    if (eId == null) {
                        redirectNeeded = true;
                    }
                }

                // We do not have a requested event ID, let's see if we can find one to use with our time range.  This
                // could be because the requested event did not match the other filters and was dropped.
                if (eId == null) {
                    EventFilter filter = new EventFilter(null, begin, end, system,
                            locationSelectionsList, classificationSelectionsList, null, null, minCaptureFiles);
                    currentEvent = es.getMostRecentEvent(filter, true);
                    sessionGraphConfig.setEventId((currentEvent == null) ? null : currentEvent.getEventId());
                    if (currentEvent != null) {
                        // We got a new event to display, we'll need to redirect to update the url.
                        redirectNeeded = true;
                        eId = currentEvent.getEventId();
                    }
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying database for event information.", ex);
                throw new ServletException("Error querying database for event information.");
            } catch (FileNotFoundException ex) {
                LOGGER.log(Level.SEVERE, "File not found:  Error locating event data on disk.", ex);
                throw new ServletException("File not found:  Error locating event data on disk.", ex);
            }

            // If a redirect was found to be needed, build the URL based on variables set above and redirect to it.
            if (redirectNeeded) {
                doRedirect(request, response, eId, system, beginString, endString, locationSelections,
                        classificationSelections, seriesSelections, seriesSetSelections, minCaptureFiles);
            }

            // Process more configuration info since we know we aren't redirecting and will use it.
            for (Series s : sessionGraphConfig.getSeriesMasterSet()) {
                seriesMasterSet.add(s.getName());
            }

            // Process the option maps for convenient use in the UI
            if (locationOptions != null) {
                for (String location : locationOptions) {
                    locationMap.put(location, false);
                }
            }
            if (locationSelections != null) {
                for (String location : locationSelections) {
                    locationMap.put(location, true);
                }
            }
            if (classificationOptions != null) {
                for (String classification : classificationOptions) {
                    if (classification == null || classification.isEmpty()) {
                        continue;
                    }
                    classificationMap.put(classification, false);
                }
            }
            if (classificationSelections != null) {
                for (String classification : classificationSelections) {
                    if (classification == null || classification.isEmpty()) {
                        continue;
                    }
                    classificationMap.put(classification, true);
                }
            }
            if (seriesOptions != null) {
                for (Series s : seriesOptions) {
                    seriesMap.put(s.getName(), false);
                }
            }
            if (seriesSelections != null) {
                for (Series s : seriesSelections) {
                    seriesMap.put(s.getName(), true);
                }
            }
            if (seriesSetOptions != null) {
                for (SeriesSet s : seriesSetOptions) {
                    seriesSetMap.put(s.getName(), false);
                }
            }
            if (seriesSetSelections != null) {
                for (SeriesSet s : seriesSetSelections) {
                    seriesSetMap.put(s.getName(), true);
                }
            }

            // Get a list of events that are to be displayed in the timeline - should not be in session since this might change
            EventService es = new EventService();
            try {
                EventFilter eFilter = new EventFilter(null, begin, end, system,
                        new ArrayList<>(locationSelections), new ArrayList<>(classificationSelections),
                        null, null, minCaptureFiles);
                eventList = es.getEventListWithoutCaptureFiles(eFilter);
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying database for event information.", ex);
                throw new ServletException("Error querying database for event information.");
            }
        }

        JsonArrayBuilder jab = Json.createArrayBuilder();
        JsonObjectBuilder job = Json.createObjectBuilder();
        for (Event event : eventList) {
            jab.add(event.toJsonObject());
        }
        JsonObject eventListJson = job.add("events", jab.build()).build();

        // Create a system name meant for user consumption
        String systemDisplay;
        switch (system) {
            case "rf":
                systemDisplay = "RF";
                break;
            case "acclrm":
                systemDisplay = "Accelerometer";
                break;
            case "bpm":
                systemDisplay = "BPM";
                break;
            default:
                throw new IllegalArgumentException("No display name defined for system -" + system);
        }

        // It's easier to work with a list of names in the JSP
        List<String> classificationSelectionStrings = new ArrayList<>(classificationSelections);
        Collections.sort(classificationSelectionStrings);
        List<String> locationSelectionStrings = new ArrayList<>(locationSelections);
        Collections.sort(locationSelectionStrings);
        List<String> seriesSelectionStrings = new ArrayList<>();
        if (seriesSelections != null) {
            for (Series s : seriesSelections) {
                seriesSelectionStrings.add(s.getName());
            }
        }
        List<String> seriesSetSelectionStrings = new ArrayList<>();
        if (seriesSetSelections != null) {
            for (SeriesSet s : seriesSetSelections) {
                seriesSetSelectionStrings.add(s.getName());
            }
        }

        request.setAttribute("begin", beginString);
        request.setAttribute("end", endString);
        request.setAttribute("locationSelections", locationSelectionStrings);
        request.setAttribute("locationMap", locationMap);
        request.setAttribute("classificationSelections", classificationSelectionStrings);
        request.setAttribute("classificationMap", classificationMap);
        request.setAttribute("seriesSelections", seriesSelectionStrings);
        request.setAttribute("seriesSetSelections", seriesSetSelectionStrings);
        request.setAttribute("seriesMasterSet", seriesMasterSet);
        request.setAttribute("seriesMap", seriesMap);
        request.setAttribute("seriesSetMap", seriesSetMap);
        request.setAttribute("eventId", eId);
        request.setAttribute("minCF", minCaptureFiles);
        request.setAttribute("system", system);
        request.setAttribute("systemDisplay", systemDisplay);
        request.setAttribute("eventListJson", eventListJson.toString());
        request.setAttribute("currentEvent", currentEvent == null ? "null" : currentEvent.toDyGraphJsonObject(seriesMasterSet).toString());

        request.getRequestDispatcher("/WEB-INF/views/graph.jsp").forward(request, response);
    }


    /**
     * Initialize session data or update any parts that may have changed since last use.
     *
     * @param system  The name of the system the session is currently interested in
     * @param request The current HTTP request being processed
     * @throws IOException If I/O problem
     */
    private void setupSession(String system, HttpServletRequest request) throws IOException {
        HttpSession session = request.getSession();
        GraphConfig defaultGraphConfig = GraphConfig.getDefaultConfig(system);

        synchronized (SessionUtils.getSessionLock(request, null)) {

            // Make sure we have a default system to query against
            if (system == null) {
                if (session.getAttribute("graphSystem") == null) {
                    system = defaultGraphConfig.getSystem();
                } else {
                    system = (String) session.getAttribute("graphSystem");
                }
            }
            session.setAttribute("graphSystem", system);
            @SuppressWarnings("unchecked")
            Map<String, GraphConfig> gcMap = (Map<String, GraphConfig>) session.getAttribute("graphConfigMap");
            if (gcMap == null) {
                gcMap = new HashMap<>();
                session.setAttribute("graphConfigMap", gcMap);
            }
            if (gcMap.get(system) == null) {
                // Use the default as the starting point for the session config if none exists.
                gcMap.put(system, defaultGraphConfig);
            } else {
                // We only want to work with the sessionGraphConfig object within this lock.
                GraphConfig sessionGraphConfig = gcMap.get(system);

                // Update the different options in case they may have changed since last use.
                sessionGraphConfig.setLocationOptions(defaultGraphConfig.getLocationOptions());
                sessionGraphConfig.setClassificationOptions(defaultGraphConfig.getClassificationOptions());
                sessionGraphConfig.setSeriesOptions(defaultGraphConfig.getSeriesOptions());
                sessionGraphConfig.setSeriesSetOptions(defaultGraphConfig.getSeriesSetOptions());
            }
        }
    }


    private Set<Series> initializeSeriesSelections(String[] serSel, List<Series> seriesOptions) {
        // Get the series selections if any were made.  Keep only the valid ones.
        Set<Series> seriesSelections = null;
        if (serSel != null && serSel.length > 0) {
            for (String seriesName : serSel) {
                for (Series s : seriesOptions) {
                    if (seriesName.equals(s.getName())) {
                        if (seriesSelections == null) {
                            seriesSelections = new HashSet<>();
                        }
                        seriesSelections.add(s);
                    }
                }
            }
        }
        return seriesSelections;
    }

    private Set<SeriesSet> initializeSeriesSetSelections(String[] serSetSel, List<SeriesSet> seriesSetOptions) {
        Set<SeriesSet> seriesSetSelections = null;
        if (serSetSel != null && serSetSel.length > 0) {
            for (String setName : serSetSel) {
                for (SeriesSet s : seriesSetOptions) {
                    if (setName.equals(s.getName())) {
                        if (seriesSetSelections == null) {
                            seriesSetSelections = new HashSet<>();
                        }
                        seriesSetSelections.add(s);
                    }
                }
            }
        }
        return seriesSetSelections;
    }

    private void doRedirect(HttpServletRequest request, HttpServletResponse response, Long eId, String system,
                            String beginString, String endString, Set<String> locationSelections,
                            Set<String> classificationSelections, Set<Series> seriesSelections,
                            Set<SeriesSet> seriesSetSelections, Integer minCaptureFiles) throws IOException {
        StringBuilder redirectUrl = new StringBuilder(request.getContextPath() + "/graph?"
                + "eventId=" + URLEncoder.encode((eId == null ? "" : "" + eId), StandardCharsets.UTF_8)
                + "&system=" + URLEncoder.encode(system, StandardCharsets.UTF_8)
                + "&begin=" + URLEncoder.encode(beginString, StandardCharsets.UTF_8)
                + "&end=" + URLEncoder.encode(endString, StandardCharsets.UTF_8));
        if (locationSelections.isEmpty()) {
            redirectUrl.append("&location=");
        } else {
            for (String location : locationSelections) {
                redirectUrl.append("&location=").append(URLEncoder.encode(location, StandardCharsets.UTF_8));
            }
        }
        if (classificationSelections.isEmpty()) {
            redirectUrl.append("&classification=");
        } else {
            for (String classification : classificationSelections) {
                redirectUrl.append("&classification=").append(URLEncoder.encode(classification, StandardCharsets.UTF_8));
            }
        }
        if (seriesSelections != null && !seriesSelections.isEmpty()) {
            for (Series series : seriesSelections) {
                redirectUrl.append("&series=").append(URLEncoder.encode(series.getName(), StandardCharsets.UTF_8));
            }
        } else {
            redirectUrl.append("&series=");
        }
        if (seriesSetSelections != null && !seriesSetSelections.isEmpty()) {
            for (SeriesSet seriesSet : seriesSetSelections) {
                redirectUrl.append("&seriesSet=").append(URLEncoder.encode(seriesSet.getName(), StandardCharsets.UTF_8));
            }
        } else {
            redirectUrl.append("&seriesSet=");
        }
        if (minCaptureFiles != null) {
            redirectUrl.append("&minCF=").append(URLEncoder.encode(minCaptureFiles.toString(), StandardCharsets.UTF_8));
        } else {
            redirectUrl.append("&minCF=");
        }
        response.sendRedirect(response.encodeRedirectURL(redirectUrl.toString()));
    }
}