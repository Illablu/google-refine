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

Refine.SeparatorBasedParserUI = function(jobID, job, format, config, dataContainerElmt, optionContainerElmt) {
    this._jobID = jobID;
    this._job = job;
    this._format = format;
    this._config = config;
    
    this._dataContainer = dataContainerElmt;
    this._optionContainer = optionContainerElmt;
    
    console.log(config);
    
    this._initialize();
};
Refine.DefaultImportingController.parserUIs["SeparatorBasedParserUI"] = Refine.SeparatorBasedParserUI;

Refine.SeparatorBasedParserUI.encodeSeparator = function(s) {
    return s.replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\t", "\\t");
};

Refine.SeparatorBasedParserUI.decodeSeparator = function(s) {
    return s.replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\\\", "\\");
};

Refine.SeparatorBasedParserUI.prototype._initialize = function() {
    this._optionContainer.unbind().empty().html(
        DOM.loadHTML("core", "scripts/index/parser-interfaces/separator-based-parser-ui.html"));
    this._optionContainerElmts = DOM.bind(this._optionContainer);
    
    var rowSeparatorValue = (this._config.lineSeparator == "\n") ? 'new-line' : 'custom';
    this._optionContainer.find(
        "input[name='row-separator'][value='" + rowSeparatorValue + "']").attr("checked", "checked");
    this._optionContainerElmts.rowSeparatorInput[0].value =
        Refine.SeparatorBasedParserUI.encodeSeparator(this._config.lineSeparator);
    
    var columnSeparatorValue = (this._config.separator == ",") ? 'comma' :
        ((this._config.separator == "\t") ? 'tab' : 'custom');
    this._optionContainer.find(
        "input[name='column-separator'][value='" + columnSeparatorValue + "']").attr("checked", "checked");
    this._optionContainerElmts.columnSeparatorInput[0].value =
        Refine.SeparatorBasedParserUI.encodeSeparator(this._config.separator);
    
    if (this._config.ignoreLines > 0) {
        this._optionContainerElmts.ignoreCheckbox.attr("checked", "checked");
        this._optionContainerElmts.ignoreInput[0].value = this._config.ignoreLines.toString();
    }
    if (this._config.headerLines > 0) {
        this._optionContainerElmts.headerLinesCheckbox.attr("checked", "checked");
        this._optionContainerElmts.headerLinesInput[0].value = this._config.headerLines.toString();
    }
    if (this._config.skipDataLines > 0) {
        this._optionContainerElmts.skipCheckbox.attr("checked", "checked");
        this._optionContainerElmts.skipInput.value[0].value = this._config.skipDataLines.toString();
    }
    if (this._config.storeBlankRows) {
        this._optionContainerElmts.storeBlankRowsCheckbox.attr("checked", "checked");
    }
    
    if (this._config.guessCellValueTypes) {
        this._optionContainerElmts.guessCellValueTypesCheckbox.attr("checked", "checked");
    }
    if (this._config.processQuotes) {
        this._optionContainerElmts.processQuoteMarksCheckbox.attr("checked", "checked");
    }
    
    if (this._config.storeBlankCellsAsNulls) {
        this._optionContainerElmts.storeBlankCellsAsNullsCheckbox.attr("checked", "checked");
    }
    if (this._config.includeFileSources) {
        this._optionContainerElmts.includeFileSourcesCheckbox.attr("checked", "checked");
    }
};

Refine.SeparatorBasedParserUI.prototype.dispose = function() {
    
};
