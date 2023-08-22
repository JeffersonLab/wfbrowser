package org.jlab.wfbrowser.presentation.controller.ajax;

import java.io.*;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.jlab.wfbrowser.business.filter.EventFilter;
import org.jlab.wfbrowser.business.filter.SeriesSetFilter;
import org.jlab.wfbrowser.business.service.EventService;
import org.jlab.wfbrowser.business.service.SeriesService;
import org.jlab.wfbrowser.business.util.TimeUtil;
import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Label;
import org.jlab.wfbrowser.model.Series;
import org.jlab.wfbrowser.model.SeriesSet;
import org.jlab.wfbrowser.presentation.util.GraphConfig;
import org.jlab.wfbrowser.presentation.util.SessionUtils;

/**
 * @author adamc
 */
@WebServlet(name = "event", urlPatterns = {"/ajax/event"})
public class EventAjax extends HttpServlet {

    private final static Logger LOGGER = Logger.getLogger(EventAjax.class.getName());

    /**
     * Allows users to query for event data
     *
     * @param request  servlet request
     * @param response servlet response
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String[] eArray = request.getParameterValues("id");
        List<Long> eventIdList = null;
        if (eArray != null) {
            eventIdList = new ArrayList<>();
            for (String eventId : eArray) {
                if (eventId != null && (!eventId.isEmpty())) {
                    eventIdList.add(Long.valueOf(eventId));
                }
            }
        }

        String beginString = request.getParameter("begin");
        Instant begin = beginString == null ? null : TimeUtil.getInstantFromDateTimeString(beginString);
        String endString = request.getParameter("end");
        Instant end = endString == null ? null : TimeUtil.getInstantFromDateTimeString(endString);
        String system = request.getParameter("system");
        String[] locArray = request.getParameterValues("location");
        List<String> locationList = locArray == null ? null : Arrays.asList(locArray);
        String[] clsArray = request.getParameterValues("classification");
        List<String> classificationList = clsArray == null ? null : Arrays.asList(clsArray);
        String[] serArray = request.getParameterValues("series");
        List<String> seriesList = serArray == null ? null : Arrays.asList(serArray);
        String[] serSetArray = request.getParameterValues("seriesSet");
        List<String> seriesSetList = serSetArray == null ? null : Arrays.asList(serSetArray);
        String requester = request.getParameter("requester");

        String arch = request.getParameter("archive");
        Boolean archive = (arch == null) ? null : arch.equals("true");
        String del = request.getParameter("toDelete");
        Boolean delete = (del == null) ? null : del.equals("true");
        boolean includeData = Boolean.parseBoolean(request.getParameter("includeData"));  // false if not supplied or not "true"

        String out = request.getParameter("out");
        if (out == null) {
            out = "json";
        }

        String minCF = request.getParameter("minCF");
        Integer minCaptureFiles = null;
        if (minCF != null && !minCF.isEmpty()) {
            minCaptureFiles = Integer.parseInt(minCF);
        }

        if (eventIdList != null && eventIdList.isEmpty()) {
            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                pw.write("{\"error\": \"No event IDs specified\"}");
            }
            return;
        }

        // The Event objects can filter the data the return based on a Set of series names.  Since we take Series and SeriesSet
        // names as parameters to this endpoint, we need to combine them into a single set of Series names, i.e., a master set
        Set<String> seriesMasterSet = null;

        // Add any series from the seriesSets.
        if (seriesSetList != null) {
            seriesMasterSet = new TreeSet<>();

            // Setup the query by the SeriesSets by names
            SeriesService ss = new SeriesService();
            SeriesSetFilter sFilter = new SeriesSetFilter(null, null, seriesSetList);
            try {
                // For each SeriesSet return, add the names of it's contained series to the master set
                List<SeriesSet> seriesSets = ss.getSeriesSets(sFilter);
                for (SeriesSet set : seriesSets) {
                    for (Series series : set.getSet()) {
                        seriesMasterSet.add(series.getName()); // Not null
                    }
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Error querying database for series sets information");
                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                try (PrintWriter pw = response.getWriter()) {
                    pw.print("{\"error\": \"error querying database - " + ex.getMessage() + "\"}");
                }
            }
        }
        if (seriesList != null) {
            if (seriesMasterSet == null) {
                seriesMasterSet = new TreeSet<>();
            }
            seriesMasterSet.addAll(seriesList);
        }

        // Enforce an rf system filter since this is likely to be an interface for only RF systems for some time
        EventFilter filter = new EventFilter(eventIdList, begin, end, system, locationList, classificationList, archive, delete, minCaptureFiles);

        // Output data in the request format.  CSV probably only makes sense if you wanted the data, but not reason to not support
        // the no data case.
        List<Event> eventList;
        try {
            EventService es = new EventService();
            if (includeData) {
                // Since we're asking for data, we need to include capture files too.
                eventList = es.getEventList(filter, null, true, true);
            } else {
                // Don't get capture files or data.  This query is much faster and is useful if only interested in when
                // and where, etc. events happened, not details about them.
                eventList = es.getEventListWithoutCaptureFiles(filter);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error querying database", ex);
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter pw = response.getWriter()) {
                pw.print("{\"error\": \"error querying database - " + ex.getMessage() + "\"}");
            }
            return;
        } catch (FileNotFoundException ex) {
            LOGGER.log(Level.SEVERE, "Error querying data - {0}", ex.getMessage());
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter pw = response.getWriter()) {
                pw.print("{\"error\": \"error querying data - " + ex.getMessage() + "\"}");
            }
            return;
        }

        // Update the session's graphEventId if the request came from graph page
        if (requester != null && requester.equals("graph")) {
            // The session-based graph configuration is keyed off of system.  We only update the graph configuration
            // if we're sure that the request is relevant to the graph display.
            if (system != null) {
                // Make sure that only one request is updating the session variable at a time.
                synchronized (SessionUtils.getSessionLock(request, null)) {
                    HttpSession session = request.getSession();
                    // This should almost certainly have a graph config already if the requester is truly the graph page.
                    @SuppressWarnings("unchecked")
                    Map<String, GraphConfig> gcMap = (Map<String, GraphConfig>) session.getAttribute("graphConfigMap");
                    if (gcMap == null) {
                        gcMap = new HashMap<>();
                        session.setAttribute("graphConfigMap", gcMap);
                    }
                    gcMap.putIfAbsent(system, GraphConfig.getDefaultConfig(system));
                    GraphConfig sessionGraphConfig = gcMap.get(system);

                    if (!eventList.isEmpty() && eventList.get(0) != null) {
                        if (sessionGraphConfig.getEventId() == null) {
                            if (!eventList.isEmpty()) {
                                // We haven't selected an event to graph yet.  Pick the first one.
                                sessionGraphConfig.setEventId(eventList.get(0).getEventId());
                            }
                        } else if (eventIdList.size() == 1) {
                            // The user requested information on a single event from the graph page.  Update the eventId.
                            sessionGraphConfig.setEventId(eventList.get(0).getEventId());
                        }
                    }
                }
            }
        }

        JsonObjectBuilder job;
        JsonArrayBuilder jab;
        switch (out) {
            case "json":
                try (PrintWriter pw = response.getWriter()) {
                    response.setContentType("application/json");
                    // TODO: Make this faster.  Probably need to make the same modifications as to the dygraph to side
                    // step the JsonObjectBuilder speed limitations.
                    try {
                        pw.print(EventService.convertEventListToJson(eventList, seriesMasterSet).toString());
                    } catch (Exception exc) {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        pw.print("{\"error\": " + exc.getMessage() + "}");
                    }
                }
                break;
            case "dygraph":
                try (PrintWriter pw = response.getWriter()) {
                    response.setContentType("application/json");
                    try {
                        job = Json.createObjectBuilder();
                        jab = Json.createArrayBuilder();
                        for (Event e : eventList) {
                            jab.add(e.toDyGraphJsonObject(seriesMasterSet));
                        }
                        job.add("events", jab.build());

                        pw.print(job.build().toString());
                    } catch (Exception exc) {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        pw.print("{\"error\": " + exc.getMessage() + "}");
                    }
                }
                break;
            case "csv":
                response.setContentType("text/csv");
                try (PrintWriter pw = response.getWriter()) {
                    // This only returns the first event in a csv.  Update so that multiple CSVs are tar.gz'ed and sent, but not needed
                    // for now.  Only used to send over a single event to a dygraph chart widget.
                    if (!eventList.isEmpty()) {
                        Event e = eventList.get(0);
                        if (e.getWaveforms() != null && (!e.getWaveforms().isEmpty())) {
                            pw.write(e.toCsv(seriesMasterSet));
                        } else {
                            pw.write("No data requested");
                        }
                        break;
                    }
                } catch (Exception exc) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.setContentType("text/plain");
                    try (PrintWriter pw = response.getWriter()) {
                        pw.write("Error: " + exc.getMessage());
                    }
                }
                break;
            case "orig":
                try {
                    if (eventList.size() != 1) {
                        response.setContentType("application/json");
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        try (PrintWriter pw = response.getWriter()) {
                            pw.print("{'error': 'out=orig only defined for single event");
                        }
                        return;
                    }

                    Event e = eventList.get(0);
                    String filename = e.getSystem() + "_" + e.getLocation();
                    if (e.getClassification() != null && !e.getClassification().isEmpty()) {
                        filename += "_" + e.getClassification();
                    }
                    filename += "_" + TimeUtil.getDateTimeString(e.getEventTime(), ZoneId.systemDefault()).replace(":", "").replace(" ", "_") + ".tar.gz";
                    response.setContentType("application/gzip");
                    response.setHeader("Content-Disposition", "attachment; filename=" + filename);
                    try (OutputStream os = response.getOutputStream()) {
                        e.streamCaptureFiles(os);
                    }
                } catch (Exception exc) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    try (PrintWriter pw = response.getWriter()) {
                        pw.print("{\"error\": " + exc.getMessage() + "}");
                    }
                }
                break;
            default:
                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                try (PrintWriter pw = response.getWriter()) {
                    pw.print("{'error':'unrecognized output format - " + out + "'}");
                }
        }
    }

    /**
     * Handle logic for events to be added to waveform database.
     *
     * @param request  Standard HttpServletRequest object
     * @param response Standard HttpServletResponse object
     * @throws IOException If problems arise while accessing data from disk
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String datetime = request.getParameter("datetime");
        String location = request.getParameter("location");
        String system = request.getParameter("system");
        String classification = request.getParameter("classification");
        String archive = request.getParameter("archive");
        String delete = request.getParameter("delete");
        String grouped = request.getParameter("grouped");
        String captureFile = request.getParameter("captureFile");
        String[] labelParams = request.getParameterValues("label");

        response.setContentType("application/json");

        if (datetime == null || location == null || system == null || classification == null || grouped == null) {
            try (PrintWriter pw = response.getWriter()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                pw.write("{\"error\": \"Missing required argument.  Requires datetime, location, system, classification, grouped\"}");
            }
            return;
        }

        String userName = request.getUserPrincipal().getName();

        Instant t = TimeUtil.getInstantFromDateTimeString(datetime);
        EventService wfs = new EventService();
        String kvp;
        try {

            // An event can have multiple labels associated with it.  Process these into a list of Label objects.
            List<Label> labelList = null;
            if (labelParams != null) {
                labelList = new ArrayList<>();
                JsonObject json;
                for (String labelParam : labelParams) {
                    // The label parameter should be a JSON representing the cavity label (with the label name, value,
                    // confidence, and model info).
                    if (labelParam != null) {
                        json = Json.createReader(new StringReader(labelParam)).readObject();
                        labelList.add(new Label(json));
                    }
                }
            }

            // Process other parameters needed to create an event
            boolean arch = Boolean.parseBoolean(archive);
            boolean del = Boolean.parseBoolean(delete);
            boolean grp = Boolean.parseBoolean(grouped);
            kvp = "sys=" + system + " loc=" + location + " cls=" + classification + " timestamp=" +
                    (t == null ? "null" : t.toString()) + " grp=" + grp + " arc=" + arch + " del=" + del + " cFile=" +
                    captureFile;
            LOGGER.log(Level.INFO, "User ''{0}'' attempting to add event {1}", new Object[]{userName, kvp});
            Event event = new Event(t, location, system, arch, del, grp, classification, captureFile, labelList);
            long id = wfs.addEvent(event);
            LOGGER.log(Level.INFO, "Event addition succeeded");
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"id\": \"" + id + "\", \"message\": \"Waveform event successfully added to database\"}");
            }
        } catch (SQLException | IOException | IllegalArgumentException e) {
            try (PrintWriter pw = response.getWriter()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                pw.write("{\"error\": \"Problem updating database - " + e.toString() + "\"}");
                LOGGER.log(Level.INFO, "Event addition failed - {0}", new Object[]{e.toString()});
            }
        }
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
