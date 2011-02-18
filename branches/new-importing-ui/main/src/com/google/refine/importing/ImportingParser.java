package com.google.refine.importing;

import java.util.List;

import org.json.JSONObject;

import com.google.refine.ProjectMetadata;
import com.google.refine.model.Project;

public interface ImportingParser {
    /**
     * Some common options include
     *     - encoding: such as "utf-8", applicable to text files
     *     - lineBreakCharacter: such as "\n", applicable to line-based files
     *     - columnSeparatorCharacter: such as "\t"
     *     - keepBlankLines: boolean, applicable to line-based files
     * 
     * @param job
     * @param fileRecords
     * @param format
     * @return JSONObject options
     */
    public JSONObject createDefaultOptions(
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
