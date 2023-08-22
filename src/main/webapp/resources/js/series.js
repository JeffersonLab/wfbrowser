var jlab = jlab || {};
jlab.wfb = jlab.wfb || {};

// Override some parameters from the smoothness template
jlab.editableRowTable.entity = 'Series';
//jlab.editableRowTable.dialog.width = 10;
//jlab.editableRowTable.dialog.height = 40;


jlab.wfb.deleteRow = function () {
    var $selectedRow = $(".editable-row-table tbody tr.selected-row");

    if ($selectedRow.length < 1) {
        return;
    }

    if (jlab.isRequest()) {
        window.console && console.log("Ajax already in progress");
        return;
    }

    var seriesName = $selectedRow.find("td:nth-child(1)").text();

    if (!confirm("Are you sure you want to delete the series " + seriesName + "?")) {
        return;
    }

    var seriesId = $selectedRow.data("series-id");
    var url = jlab.contextPath + "/ajax/series-delete";
    var data = {'id': seriesId};
    $dialog = null;

    jlab.doAjaxJsonPostRequest(url, data, $dialog, true);
};

jlab.wfb.initDialogs = function () {
    $("#table-row-dialog").dialog({
        autoOpen: false,
        width: 700,
        height: 350,
        modal: true
    });
};

jlab.wfb.validateRowForm = function () {
    if ($("#row-name").val() === '') {
        alert("Please select a name");
        return false;
    }
    if ($("#row-description").val() === '') {
        alert("Please enter a description");
        return false;
    }
    if ($("#row-system").val() === '') {
        alert("Please enter a system");
        return false;
    }
    if ($("#row-pattern").val() === '') {
        alert("Please enter a pattern");
        return false;
    }

    return true;
};

jlab.wfb.editRow = function () {
    if (!jlab.wfb.validateRowForm()) {
        return;
    }

    var seriesId = $(".editable-row-table tbody tr.selected-row").data("series-id");
    var name = $("#row-name").val();
    var description = $("#row-description").val();
    var system = $("#row-system").val();
    var pattern = $("#row-pattern").val();
    var units = $("#row-units").val();
    var ymin = $("#row-y-min").val();
    var ymax = $("#row-y-max").val();
    var url = jlab.contextPath + "/ajax/series-update";
    var data = {"id": seriesId, "name": name, "description": description, "pattern": pattern, "system": system,
        "units": units, "ymin": ymin, "ymax": ymax};
    var $dialog = $("#table-row-dialog");
    jlab.doAjaxJsonPostRequest(url, data, $dialog, true);
};

jlab.wfb.addRow = function () {
    if (!jlab.wfb.validateRowForm()) {
        return;
    }
    var name = $("#row-name").val();
    var description = $("#row-description").val();
    var system = $("#row-system").val();
    var pattern = $("#row-pattern").val();
    var units = $("#row-units").val();
    var ymin = $("#row-y-min").val();
    var ymax = $("#row-y-max").val();
    var url = jlab.contextPath + "/ajax/series";
    var data = {"name": name, "description": description, "pattern": pattern, "system": system, "units": units,
        "ymin": ymin, "ymax": ymax};
    var $dialog = $("table-row-dialog");

    jlab.doAjaxJsonPostRequest(url, data, $dialog, true);
};


$(document).on("click", "#remove-row-button", function () {
    jlab.wfb.deleteRow();
});

$(document).on("click", "#open-edit-row-dialog-button", function () {
    var $selectedRow = $(".editable-row-table tbody tr.selected-row");

    if ($selectedRow.length < 1) {
        return;
    }

    var name = $selectedRow.find("td:nth-child(1)").text();
    var pattern = $selectedRow.find("td:nth-child(2)").text();
    var units = $selectedRow.find("td:nth-child(3)").text();
    var description = $selectedRow.find("td:nth-child(4)").text();
    var system = $selectedRow.find("td:nth-child(5)").text();
    var ymin = $selectedRow.find("td:nth-child(6)").text();
    var ymax = $selectedRow.find("td:nth-child(7)").text();

    $("#row-name").val(name);
    $("#row-pattern").val(pattern);
    $("#row-units").val(units);
    $("#row-description").val(description);
    $("#row-system").val(system);
    $("#row-y-min").val(ymin);
    $("#row-y-max").val(ymax);
});

$(document).on("table-row-add", function () {
    jlab.wfb.addRow();
});

$(document).on("table-row-edit", function () {
    jlab.wfb.editRow();
});

$(function () {
    jlab.wfb.initDialogs();
});