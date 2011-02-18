/*

Copyright 2011, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

Refine.DefaultImportingController = function(createProjectUI) {
    this._createProjectUI = createProjectUI;
    
    this._progressPanel = createProjectUI.addCustomPanel();
    this._progressPanel.html(DOM.loadHTML("core", "scripts/index/default-importing-controller/progress-panel.html"));
    
    this._errorPanel = createProjectUI.addCustomPanel();
    this._errorPanel.html(DOM.loadHTML("core", "scripts/index/default-importing-controller/error-panel.html"));
    
    this._fileSelectionPanel = createProjectUI.addCustomPanel();
    this._parsingPanel = createProjectUI.addCustomPanel();
    
    for (var i = 0; i < Refine.DefaultImportingController.sources.length; i++) {
        var sourceSelectionUI = Refine.DefaultImportingController.sources[i];
        sourceSelectionUI.ui = new sourceSelectionUI.uiClass(this);
        
        createProjectUI.addSourceSelectionUI(sourceSelectionUI);
    }
};
Refine.CreateProjectUI.controllers.push(Refine.DefaultImportingController);

Refine.DefaultImportingController.sources = [];
Refine.DefaultImportingController.parserUIs = {};

Refine.DefaultImportingController.prototype._startOver = function() {
    this._fileSelectionPanel.unbind().empty();
    this._parsingPanel.unbind().empty();
    
    delete this._fileSelectionPanelElmts;
    delete this._parsingPanelElmts;
    
    delete this._jobID;
    delete this._job;
    delete this._extensions;
    
    this._createProjectUI.showSourceSelectionPanel();
};

Refine.DefaultImportingController.prototype._prepareFileSelectionPanel = function() {
    var self = this;
    
    this._fileSelectionPanel.unbind().empty().html(
        DOM.loadHTML("core", "scripts/index/default-importing-controller/file-selection-panel.html"));
    
    var elmts = DOM.bind(this._fileSelectionPanel);
    elmts.wizardHeader.html(
        DOM.loadHTML("core", "scripts/index/default-importing-controller/wizard-header.html"));
    
    this._fileSelectionPanelElmts = elmts = DOM.bind(this._fileSelectionPanel);
    this._fileSelectionPanelElmts.startOverButton.click(function() {
        self._startOver();
    })
    
    var resize = function() {
        var width = self._fileSelectionPanel.width();
        var height = self._fileSelectionPanel.height();
        var headerHeight = elmts.wizardHeader.outerHeight(true);
        var controlPanelWidth = 350;
        
        elmts.controlPanel
            .css("left", "0px")
            .css("top", headerHeight + "px")
            .css("width", (controlPanelWidth - DOM.getHPaddings(elmts.controlPanel)) + "px")
            .css("height", (height - headerHeight - DOM.getVPaddings(elmts.controlPanel)) + "px");
        
        elmts.filePanel
            .css("left", controlPanelWidth + "px")
            .css("top", headerHeight + "px")
            .css("width", (width - controlPanelWidth - DOM.getHPaddings(elmts.filePanel)) + "px")
            .css("height", (height - headerHeight - DOM.getVPaddings(elmts.filePanel)) + "px");
    };
    $(window).resize(resize);
    resize();
};

Refine.DefaultImportingController.prototype.startImportJob = function(form, progressMessage, callback) {
    var self = this;
    $.post(
        "/command/core/create-importing-job",
        null,
        function(data) {
            var jobID = self._jobID = data.jobID;

            form.attr("method", "post")
                .attr("enctype", "multipart/form-data")
                .attr("accept-charset", "UTF-8")
                .attr("target", "importing-iframe")
                .attr("action", "/command/core/importing-controller?" + $.param({
                    "controller": "core/default-importing-controller",
                    "jobID": jobID,
                    "subCommand": "load-raw-data"
                }));
            form[0].submit();

            var start = new Date();
            var timerID = window.setInterval(
                function() {
                    self._pollImportJob(start, jobID, timerID, callback);
                },
                1000
            );
            self._initializeImportProgressPanel(progressMessage, jobID, timerID);
        },
        "json"
    );
};

Refine.DefaultImportingController.prototype._initializeImportProgressPanel = function(progressMessage, jobID, timerID) {
    var self = this;
    
    this._createProjectUI.showCustomPanel(this._progressPanel);
    
    $('#default-importing-progress-message').text(progressMessage);
    $('#default-importing-progress-bar-body').css("width", "0%");
    $('#default-importing-progress-message-left').text('Starting');
    $('#default-importing-progress-message-center').empty();
    $('#default-importing-progress-message-right').empty();
    $('#default-importing-progress-timing').empty();

    $('#default-importing-progress-cancel-button').unbind().click(function() {
        // stop the iframe
        $('#default-importing-iframe')[0].contentWindow.stop();

        // stop the timed polling
        window.clearInterval(timerID);
        
        // explicitly cancel the import job
        $.post("/command/core/importing-controller?" + $.param({
            "controller": "core/default-importing-controller",
            "jobID": jobID,
            "subCommand": "cancel-load-raw-data"
        }));
        
        self._createProjectUI.showSourceSelectionPanel();
    });
};

Refine.DefaultImportingController.prototype._pollImportJob = function(start, jobID, timerID, callback) {
    var self = this;
    $.post(
        "/command/core/get-importing-job-status?" + $.param({
            "jobID": jobID
        }),
        null,
        function(data) {
            if (!(data)) {
                self._showImportJobError("Unknown error");
                window.clearInterval(timerID);
                return;
            } else if (data.code == "error" || !("job" in data)) {
                self._showImportJobError(data.message || "Unknown error");
                window.clearInterval(timerID);
                return;
            }
            
            var job = data.job;
            var config = job.config;
            if (config.hasData) {
                $('#default-importing-progress-message').text('Done.');

                window.clearInterval(timerID);
                if (callback) {
                    callback(jobID, data.job);
                }
                
                self._job = job;
                self._onImportJobReady();
            } else {
                var progress = config.progress;
                if (progress.percent > 0) {
                    var secondsSpent = (new Date().getTime() - start.getTime()) / 1000;
                    var secondsRemaining = (100 / progress.percent) * secondsSpent - secondsSpent;
                    
                    $('#default-importing-progress-bar-body')
                        .removeClass('indefinite')
                        .css("width", progress.percent + "%");
                    
                    if (secondsRemaining > 1) {
                        if (secondsRemaining > 60) {
                            $('#default-importing-progress-timing').text(
                                Math.ceil(secondsRemaining / 60) + " minutes remaining");
                        } else {
                            $('#default-importing-progress-timing').text(
                                Math.ceil(secondsRemaining) + " seconds remaining");
                        }
                    } else {
                        $('#default-importing-progress-timing').text('almost done ...');
                    }
                } else {
                    $('#default-importing-progress-bar-body').addClass('indefinite');
                    $('#default-importing-progress-timing').empty();
                }
                $('#default-importing-progress-message').text(progress.message);
            }
        },
        "json"
    );
};

Refine.DefaultImportingController.prototype._showImportJobError = function(message, stack) {
    var self = this;
    
    $('#default-importing-error-message').text(message);
    $('#default-importing-error-stack').text(stack || 'No technical details.');

    this._createProjectUI.showCustomPanel(this._errorPanel);
    $('#default-importing-error-ok-button').unbind().click(function() {
        self._createProjectUI.showSourceSelectionPanel();
    });
};

Refine.DefaultImportingController.prototype._onImportJobReady = function() {
    var self = this;
    
    this._prepareData();
    
    this._prepareParsingPanel();
    this._parsingPanelElmts.nextButton.click(function() {
        //self._createProjectUI.showCustomPanel(self._fileSelectionPanel);
    });
    
    if (this._job.config.retrievalRecord.files.length > 1) {
        this._prepareFileSelectionPanel();
        
        this._fileSelectionPanelElmts.previousButton.attr("disabled", "disabled").addClass("button-disabled");
        this._fileSelectionPanelElmts.nextButton.click(function() {
            self._commitFileSelection();
        });
        
        this._parsingPanelElmts.previousButton.click(function() {
            self._createProjectUI.showCustomPanel(self._fileSelectionPanel);
        });
        
        this._renderFileSelectionPanel();
        
        this._createProjectUI.showCustomPanel(this._fileSelectionPanel);
    } else {
        this._parsingPanelElmts.previousButton.attr("disabled", "disabled").addClass("button-disabled");
        
        this._createProjectUI.showCustomPanel(this._parsingPanel);
    }
};

Refine.DefaultImportingController.prototype._prepareData = function() {
    var extensionMap = {};
    var extensionList = [];
    
    var files = this._job.config.retrievalRecord.files;
    var fileSelection = this._job.config.fileSelection;
    for (var i = 0; i < files.length; i++) {
        var file = files[i];
        file.selected = false;
        
        var slash = file.fileName.lastIndexOf('/');
        var dot = file.fileName.lastIndexOf('.');
        if (dot > slash + 1) {
            var extension = file.fileName.substring(dot);
            if (extension in extensionMap) {
                extensionMap[extension].count++;
            } else {
                extensionMap[extension] = { extension: extension, count: 1 };
                extensionList.push(extensionMap[extension]);
            }
        }
    }
    for (var i = 0; i < fileSelection.length; i++) {
        files[fileSelection[i]].selected = true;
    }
    
    extensionList.sort(function(a, b) {
        return b.count - a.count;
    });
    this._extensions = extensionList;
};

Refine.DefaultImportingController.prototype._renderFileSelectionPanel = function() {
    console.log(this._job.config);
    this._renderFileTable();
    this._renderControlPanel();
};

Refine.DefaultImportingController.prototype._renderFileTable = function() {
    var self = this;
    
    this._fileSelectionPanelElmts.filePanel.empty();
    
    var fileTable = $('<table><tr><th></th><th>Name</th><th>Format</th><th>Size</th></tr></table>')
        .appendTo(this._fileSelectionPanelElmts.filePanel)[0];

    var files = this._job.config.retrievalRecord.files;
    var renderFile = function(fileRecord, index) {
        var tr = fileTable.insertRow(fileTable.rows.length);
        $(tr).addClass(index % 2 == 0 ? 'even' : 'odd');
        
        var tdSelect = $('<td>').appendTo(tr);
        var checkbox = $('<input>')
            .attr("type", "checkbox")
            .attr("index", index)
            .appendTo(tdSelect)
            .click(function() {
                files[index].selected = this.checked;
                self._updateFileSelectionSummary();
            });
        if (fileRecord.selected) {
            checkbox.attr("checked", "checked");
        }
        
        $('<td>').text(fileRecord.fileName).addClass("default-importing-file-selection-filename").appendTo(tr);
        $('<td>').text(fileRecord.format || "unknown").appendTo(tr);
        $('<td>').text(fileRecord.size + " bytes").appendTo(tr);
    };
    
    for (var i = 0; i < files.length; i++) {
        renderFile(files[i], i);
    }
};

Refine.DefaultImportingController.prototype._renderControlPanel = function() {
    var self = this;
    var files = this._job.config.retrievalRecord.files;
    
    this._fileSelectionPanelElmts.extensionContainer.empty();
    this._fileSelectionPanelElmts.selectAllButton.unbind().click(function(evt) {
        for (var i = 0; i < files.length; i++) {
            files[i].selected = true;
        }
        self._fileSelectionPanelElmts.filePanel.find("input").attr("checked", "checked");
        self._updateFileSelectionSummary();
    });
    this._fileSelectionPanelElmts.unselectAllButton.unbind().click(function(evt) {
        for (var i = 0; i < files.length; i++) {
            files[i].selected = false;
        }
        self._fileSelectionPanelElmts.filePanel.find("input").removeAttr("checked");
        self._updateFileSelectionSummary();
    });
    
    var table = $('<table></table>')
        .appendTo(this._fileSelectionPanelElmts.extensionContainer)[0];
        
    var renderExtension = function(extension) {
        var tr = table.insertRow(table.rows.length);
        $('<td>').text(extension.extension).appendTo(tr);
        $('<td>').text(extension.count + (extension.count > 1 ? " files" : " file")).appendTo(tr);
        $('<button>')
            .text("Select")
            .addClass("button")
            .appendTo($('<td>').appendTo(tr))
            .click(function() {
                for (var i = 0; i < files.length; i++) {
                    var file = files[i];
                    if (!file.selected) {
                        if (file.fileName.endsWith(extension.extension)) {
                            file.selected = true;
                            self._fileSelectionPanelElmts.filePanel
                                .find("input[index='" + i + "']")
                                .attr("checked", "checked");
                        }
                    }
                }
                self._updateFileSelectionSummary();
            });
        $('<button>')
            .text("Unselect")
            .addClass("button")
            .appendTo($('<td>').appendTo(tr))
            .click(function() {
                for (var i = 0; i < files.length; i++) {
                    var file = files[i];
                    if (file.selected) {
                        if (file.fileName.endsWith(extension.extension)) {
                            file.selected = false;
                            self._fileSelectionPanelElmts.filePanel
                                .find("input[index='" + i + "']")
                                .removeAttr("checked");
                        }
                    }
                }
                self._updateFileSelectionSummary();
            });
    };
    for (var i = 0; i < this._extensions.length; i++) {
        renderExtension(this._extensions[i]);
    }
    
    this._updateFileSelectionSummary();
    
    this._fileSelectionPanelElmts.regexInput.unbind().keypress(function() {
        var count = 0;
        var elmts = self._fileSelectionPanelElmts.filePanel
            .find(".default-importing-file-selection-filename")
            .removeClass("highlighted");
        try {
            var regex = new RegExp(this.value);
            elmts.each(function() {
                if (regex.test($(this).text())) {
                    $(this).addClass("highlighted");
                    count++;
                }
            });
        } catch (e) {
            // Ignore
        }
        self._fileSelectionPanelElmts.regexSummary.text(count + (count == 1 ? " match" : " matches"));
    });
    this._fileSelectionPanelElmts.selectRegexButton.unbind().click(function() {
        self._fileSelectionPanelElmts.filePanel
            .find(".default-importing-file-selection-filename")
            .removeClass("highlighted");
        try {
            var regex = new RegExp(self._fileSelectionPanelElmts.regexInput[0].value);
            for (var i = 0; i < files.length; i++) {
                var file = files[i];
                if (!file.selected) {
                    if (regex.test(file.fileName)) {
                        file.selected = true;
                        self._fileSelectionPanelElmts.filePanel
                            .find("input[index='" + i + "']")
                            .attr("checked", "checked");
                    }
                }
            }
            self._updateFileSelectionSummary();
        } catch (e) {
            // Ignore
        }
    });
    this._fileSelectionPanelElmts.unselectRegexButton.unbind().click(function() {
        self._fileSelectionPanelElmts.filePanel
            .find(".default-importing-file-selection-filename")
            .removeClass("highlighted");
        try {
            var regex = new RegExp(self._fileSelectionPanelElmts.regexInput[0].value);
            for (var i = 0; i < files.length; i++) {
                var file = files[i];
                if (file.selected) {
                    if (regex.test(file.fileName)) {
                        file.selected = false;
                        self._fileSelectionPanelElmts.filePanel
                            .find("input[index='" + i + "']")
                            .removeAttr("checked");
                    }
                }
            }
            self._updateFileSelectionSummary();
        } catch (e) {
            // Ignore
        }
    });
};

Refine.DefaultImportingController.prototype._updateFileSelectionSummary = function() {
    var fileSelection = [];
    var files = this._job.config.retrievalRecord.files;
    for (var i = 0; i < files.length; i++) {
        if (files[i].selected) {
            fileSelection.push(i);
        }
    }
    this._job.config.fileSelection = fileSelection;
    this._fileSelectionPanelElmts.summary.text(fileSelection.length + " of " + files.length + " files selected");
};

Refine.DefaultImportingController.prototype._commitFileSelection = function() {
    if (this._config.fileSelection.length == 0) {
        alert("Please select at least one file.");
        return;
    }
    
    var self = this;
    var dismissBusy = DialogSystem.showBusy("Inspecting selected files ...");
    $.post(
        "/command/core/importing-controller?" + $.param({
            "controller": "core/default-importing-controller",
            "jobID": this._jobID,
            "subCommand": "update-file-selection"
        }),
        {
            "fileSelection" : JSON.stringify(this._job.config.fileSelection)
        },
        function(data) {
            if (!(data)) {
                self._showImportJobError("Unknown error");
                window.clearInterval(timerID);
                return;
            } else if (data.code == "error" || !("job" in data)) {
                self._showImportJobError(data.message || "Unknown error");
                window.clearInterval(timerID);
                return;
            }
            
            self._job = data.job;
            
            dismissBusy();
            self._createProjectUI.showCustomPanel(self._parsingPanel);
        },
        "json"
    );
};

Refine.DefaultImportingController.prototype._prepareParsingPanel = function() {
    var self = this;
    
    this._parsingPanel.unbind().empty().html(
        DOM.loadHTML("core", "scripts/index/default-importing-controller/parsing-panel.html"));
    
    var elmts = DOM.bind(this._parsingPanel);
    elmts.wizardHeader.html(
        DOM.loadHTML("core", "scripts/index/default-importing-controller/wizard-header.html"));

    this._parsingPanelElmts = elmts = DOM.bind(this._parsingPanel);
    this._parsingPanelElmts.startOverButton.click(function() {
        self._startOver();
    })
};

Refine.DefaultImportingController.prototype._createProject = function() {
};
