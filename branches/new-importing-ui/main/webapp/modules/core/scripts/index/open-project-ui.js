Refine.OpenProjectUI = function(elmt) {
  var self = this;
  
  elmt.html(DOM.loadHTML("core", "scripts/index/open-project-ui.html"));
  
  this._elmt = elmt;
  this._elmts = DOM.bind(elmt);
  
  $("#project-file-input").change(function() {
      if ($("#project-name-input")[0].value.length == 0) {
          var fileName = this.files[0].fileName;
          if (fileName) {
              $("#project-name-input")[0].value = fileName.replace(/\.\w+/, "").replace(/[_-]/g, " ");
          }
          $("#project-name-input").focus().select();
      }
  }).keypress(function(evt) {
      if (evt.keyCode == 13) {
          return self._onClickUploadFileButton(evt);
      }
  });
  
  $("#upload-file-button").click(function(evt) {
    return self._onClickUploadFileButton(evt)
  });

  $('#projects-workspace-open').click(function() {
      $.ajax({
          type: "POST",
          url: "/command/core/open-workspace-dir",
          dataType: "json",
          success: function (data) {
              if (data.code != "ok" && "message" in data) {
                  alert(data.message);
              }
          }
      });
  });
  
  this._fetchProjects();
};

Refine.OpenProjectUI.prototype._fetchProjects = function() {
    var self = this;
    $.getJSON(
        "/command/core/get-all-project-metadata",
        null,
        function(data) {
            self._renderProjects(data);
        },
        "json"
    );
};

Refine.OpenProjectUI.prototype._renderProjects = function(data) {
    var projects = [];
    for (var n in data.projects) {
        if (data.projects.hasOwnProperty(n)) {
            var project = data.projects[n];
            project.id = n;
            project.date = Date.parseExact(project.modified, "yyyy-MM-ddTHH:mm:ssZ");
            projects.push(project);
        }
    }
    projects.sort(function(a, b) { return b.date.getTime() - a.date.getTime(); });

    var container = $("#projects-container").empty();
    if (!projects.length) {
        $("#no-project-message").clone().show().appendTo(container);
    } else {
        Refine.selectActionArea('open-project');
      
        var table = $(
            '<table class="list-table"><tr>' +
                '<th>Name</th>' +
                '<th></th>' +
                '<th></th>' +
                '<th align="right">Last&nbsp;modified</th>' +
            '</tr></table>'
        ).appendTo(container)[0];

        var formatDate = function(d) {
            var d = new Date(d);
            var last_year = Date.today().add({ years: -1 });
            var last_month = Date.today().add({ months: -1 });
            var last_week = Date.today().add({ days: -7 });
            var today = Date.today();
            var tomorrow = Date.today().add({ days: 1 });

            if (d.between(today, tomorrow)) {
                return "today " + d.toString("h:mm tt");
            } else if (d.between(last_week, today)) {
                var diff = Math.floor(today.getDayOfYear() - d.getDayOfYear());
                return (diff <= 1) ? ("yesterday " + d.toString("h:mm tt")) : (diff + " days ago");
            } else if (d.between(last_month, today)) {
                var diff = Math.floor((today.getDayOfYear() - d.getDayOfYear()) / 7);
                return (diff == 1) ? "a week ago" : diff.toFixed(0) + " weeks ago" ;
            } else if (d.between(last_year, today)) {
                var diff = Math.floor(today.getMonth() - d.getMonth());
                return (diff == 1) ? "a month ago" : diff + " months ago";
            } else {
                var diff = Math.floor(today.getYear() - d.getYear());
                return (diff == 1) ? "a year ago" : diff + " years ago";
            }
        };
        
        var renderProject = function(project) {
            var tr = table.insertRow(table.rows.length);
            tr.className = "project";

            var nameLink = $('<a></a>')
                .addClass("list-table-itemname")
                .text(project.name)
                .attr("href", "/project?project=" + project.id)
                .appendTo(tr.insertCell(tr.cells.length));

            var renameLink = $('<a></a>')
                .text("rename")
                .addClass("secondary")
                .attr("href", "javascript:{}")
                .css("visibility", "hidden")
                .click(function() {
                    var name = window.prompt("New project name:", project.name);
                    if (name == null) {
                        return;
                    }

                    name = $.trim(name);
                    if (project.name == name || name.length == 0) {
                        return;
                    }

                    $.ajax({
                        type: "POST",
                        url: "/command/core/rename-project",
                        data: { "project" : project.id, "name" : name },
                        dataType: "json",
                        success: function (data) {
                            if (data && typeof data.code != 'undefined' && data.code == "ok") {
                                nameLink.text(name);
                            } else {
                                alert("Failed to rename project: " + data.message)
                            }
                        }
                    });
                }).appendTo(tr.insertCell(tr.cells.length));

            var deleteLink = $('<a></a>')
                .addClass("delete-project")
                .attr("title","Delete this project")
                .attr("href","")
                .css("visibility", "hidden")                
                .html("<img src='/images/close.png' />")
                .click(function() {
                    if (window.confirm("Are you sure you want to delete project \"" + project.name + "\"?")) {
                        $.ajax({
                            type: "POST",
                            url: "/command/core/delete-project",
                            data: { "project" : project.id },
                            dataType: "json",
                            success: function (data) {
                                if (data && typeof data.code != 'undefined' && data.code == "ok") {
                                    fetchProjects();
                                }
                            }
                        });
                    }
                    return false;
                }).appendTo(tr.insertCell(tr.cells.length));


            $('<div></div>')
                .html(formatDate(project.date))
                .addClass("last-modified")
                .attr("title", project.date.toString())
                .appendTo(tr.insertCell(tr.cells.length));

            $(tr).mouseenter(function() {
                renameLink.css("visibility", "visible");
                deleteLink.css("visibility", "visible");
            }).mouseleave(function() {
                renameLink.css("visibility", "hidden");
                deleteLink.css("visibility", "hidden");
            });
        };

        for (var i = 0; i < projects.length; i++) {
            renderProject(projects[i]);
        }
    }
};

Refine.OpenProjectUI.prototype._onClickUploadFileButton = function(evt) {
    var projectName = $("#project-name-input")[0].value;
    var dataURL = $.trim($("#project-url-input")[0].value);
    if (! $.trim(projectName).length) {
        window.alert("You must specify a project name.");

    } else if ($("#project-file-input")[0].files.length === 0 && ! dataURL.length) {
        window.alert("You must specify a data file to upload or a URL to retrieve.");

    } else {
        $("#file-upload-form").attr("action",
            "/command/core/create-project-from-upload?" + [
                "url=" +                escape(dataURL),
                "split-into-columns=" + $("#split-into-columns-input")[0].checked,
                "separator=" +          $("#separator-input")[0].value,
                "ignore=" +             $("#ignore-input")[0].value,
                "header-lines=" +       $("#header-lines-input")[0].value,
                "skip=" +               $("#skip-input")[0].value,
                "limit=" +              $("#limit-input")[0].value,
                "guess-value-type=" +   $("#guess-value-type-input")[0].checked,
                "ignore-quotes=" +      $("#ignore-quotes-input")[0].checked
            ].join("&"));

        return true;
    }

    evt.preventDefault();
    return false;
};

Refine.actionAreas.push({
  id: "open-project",
  label: "Open Project",
  uiClass: Refine.OpenProjectUI
});
