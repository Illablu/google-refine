package com.google.refine.importing;

import java.util.List;

import org.json.JSONObject;

import com.google.refine.ProjectMetadata;
import com.google.refine.model.Project;

public interface ImportingParser {
    /**
     * Create data sufficient for the parser UI on the client side to do its work.
     * For example, an XML parser UI would need to know some sample elements so it
     * can let the user pick which the path to the record elements.
     * 
     * @param job
     * @param fileRecords
     * @param format
     * @return JSONObject options
     */
    public JSONObject createParserUIInitializationData(
        ImportingJob job,
        List<JSONObject> fileRecords,
        String format
    );
    
    /**
     * 
     * @param project
     * @param metadata
     * @param fileRecords
     * @param format
     * @param limit maximum number of rows to create
     * @param options custom options put together by the UI corresponding to this parser,
     *                which the parser should understand
     * @param exceptions
     */
    public void parse(
        Project project,
        ProjectMetadata metadata,
        ImportingJob job,
        List<JSONObject> fileRecords,
        String format,
        int limit,
        JSONObject options,
        List<Exception> exceptions
    );
}
