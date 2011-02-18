Refine.ImportProjectUI = function(elmt) {
  elmt.html(DOM.loadHTML("core", "scripts/index/import-project-ui.html"));
  
  this._elmt = elmt;
  this._elmts = DOM.bind(elmt);
};

Refine.actionAreas.push({
  id: "import-project",
  label: "Import Project",
  uiClass: Refine.ImportProjectUI
});
