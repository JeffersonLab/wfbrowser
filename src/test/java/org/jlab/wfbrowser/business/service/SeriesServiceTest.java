package org.jlab.wfbrowser.business.service;

import org.jlab.wfbrowser.business.filter.SeriesFilter;
import org.jlab.wfbrowser.business.filter.SeriesSetFilter;
import org.jlab.wfbrowser.model.Series;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import javax.naming.NamingException;

import static org.junit.Assert.assertEquals;

public class SeriesServiceTest {

    public static void oneTimeSetUp() throws NamingException, SQLException, IOException {

        // Create the Series and add the SeriesSets
        SeriesService ss = new SeriesService();
        Series s1 = ss.getSeries(new SeriesFilter(List.of("stest1"), "test", null)).get(0);
        Series s2 = ss.getSeries(new SeriesFilter(List.of("stest2"), "test", null)).get(0);
        List<Series> exp1 = Arrays.asList(s1, s2);
        List<Series> exp2 = Arrays.asList(s2, s1);
        ss.addSeriesSet("SS1", "test", "one ordering", exp1);
        ss.addSeriesSet("SS2", "test", "other ordering", exp2);


        SeriesSetFilter ssf1 = new SeriesSetFilter(null, null, List.of("ss1"));
        SeriesSetFilter ssf2 = new SeriesSetFilter(null, null, List.of("ss2"));
        List<Series> result1 = ss.getSeriesSets(ssf1).get(0).getList();
        List<Series> result2 = ss.getSeriesSets(ssf2).get(0).getList();

        assertEquals(exp1, result1);
        assertEquals(exp2, result2);
    }
}
