/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.presentation.controller.ajax;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jlab.wfbrowser.business.service.SeriesService;

/**
 *
 * @author adamc
 */
@WebServlet(name = "SeriesAddAjax", urlPatterns = {"/ajax/series-update"})
public class SeriesUpdateAjax extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(SeriesUpdateAjax.class.getName());

        @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html");
    }
    
    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String id = request.getParameter("id");
        String system = request.getParameter("system");
        String name = request.getParameter("name");
        String pattern = request.getParameter("pattern");
        String description = request.getParameter("description");
        String units = request.getParameter("units");
        Double yMin, yMax;

        try {
            yMin = request.getParameter("ymin") == "" ? null : Double.valueOf(request.getParameter("ymin"));
            yMax = request.getParameter("ymax") == "" ? null : Double.valueOf(request.getParameter("ymax"));
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Parameter 'yMin' or 'yMax' not empty or a valid double\"}");
            }
            return;
        }

        if (id == null || system == null || system.isEmpty() || name == null || name.isEmpty() || pattern == null || pattern.isEmpty()
                || description == null || description.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Missing required parameters - id, system, name, pattern, or description\"}");
            }
            return;
        }

        int seriesId;
        try {
            seriesId = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Parameter 'id' not a valid integer\"}");
            }
            return;
        }

        SeriesService ss = new SeriesService();
        try {
            ss.updateSeries(seriesId, name, pattern, description, system, units, yMin, yMax);
        } catch (SQLException ex) {
            String msg = "Error updating database - " + ex.getMessage();
            LOGGER.log(Level.WARNING, "Error updating database", ex);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                pw.print("{\"error\":\"" + msg + "\"}");
            }
            return;
        }

        response.setContentType("application/json");
        try (PrintWriter pw = response.getWriter()) {
            pw.print("{\"message\":\"Successful update\"}");
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
