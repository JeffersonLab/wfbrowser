/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.model;

import java.util.List;
import java.util.Objects;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

/**
 * Simple object for representing a named set of waveform data series lookup
 * information.
 *
 * @author adamc
 */
public class SeriesSet {

    private final List<Series> list;
    private final String name;
    private final int id;
    private final String systemName;
    private final String description;

    public SeriesSet(List<Series> list, String name, int id, String systemName, String description) {
        if (list == null) {
            throw new RuntimeException("list cannot be null");
        }
        this.list = list;
        this.name = name;
        this.id = id;
        this.systemName = systemName;
        this.description = description;
    }

    public List<Series> getList() {
        return list;
    }

    public String getName() {
        return name;
    }

    public String getSystemName() {
        return systemName;
    }

    public int getId() {
        return id;
    }

    public void addSeries(Series series) {
        list.add(series);
    }

    public String getDescription() {
        return description;
    }

    public JsonObject toJsonObject() {
        JsonArrayBuilder jab = Json.createArrayBuilder();
        for (Series s : list) {
            jab.add(s.toJsonObject());
        }
        return Json.createObjectBuilder()
                .add("name", name)
                .add("setId", id)
                .add("description", description)
                .add("system", systemName)
                .add("series", jab.build())
                .build();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {return true;}
        if (!(obj instanceof SeriesSet)) { return false;}
        SeriesSet s = (SeriesSet) obj;

        // Compare the simple attributes
        if (id != s.id) { return false; }
        if (!name.equals(s.name)) { return false; }
        if (!systemName.equals(s.systemName)) { return false; }
        if (!description.equals(s.description)) { return false; }

        // Compare the series each one contains.
        if (list.size() != s.list.size()) { return false; }
        for (Series series : list) {
            boolean foundIt = false;
            for (Series oSeries : s.list) {
                if (series.equals(oSeries)) {
                    foundIt = true;
                    break;
                }
            }
            if (!foundIt) { return false; }
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(list, name, id, systemName, description);
    }
}
